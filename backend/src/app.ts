import Fastify, { LogController, type FastifyInstance, type FastifyRequest } from "fastify";
import type { AppConfig } from "./config.js";
import type { ParkingPurpose, ParkingSearchQuery } from "./domain.js";
import { ParkingApiError } from "./errors.js";
import {
  CompositeParkingProvider,
  PickupZoneProvider,
  PublicDataParkingProvider,
  RestAreaProvider,
  TimedZoneProvider,
  type ParkingProvider,
} from "./provider.js";
import { ParkingService, ParkingSnapshotCache } from "./service.js";

interface AppDependencies {
  provider?: ParkingProvider;
  service?: ParkingService;
}

function one(value: unknown): string | undefined {
  return typeof value === "string" ? value : undefined;
}

function numeric(value: unknown, name: string, minimum: number, maximum: number, fallback?: number): number {
  if (value === undefined && fallback !== undefined) return fallback;
  const raw = one(value);
  const parsed = raw !== undefined && raw.trim() !== "" ? Number(raw) : Number.NaN;
  if (!Number.isFinite(parsed) || parsed < minimum || parsed > maximum) {
    throw new ParkingApiError(400, "INVALID_QUERY", `${name} must be between ${minimum} and ${maximum}`);
  }
  return parsed;
}

function integer(value: unknown, name: string, minimum: number, maximum: number, fallback?: number): number {
  const parsed = numeric(value, name, minimum, maximum, fallback);
  if (!Number.isInteger(parsed)) throw new ParkingApiError(400, "INVALID_QUERY", `${name} must be an integer`);
  return parsed;
}

function boolean(value: unknown, name: string, fallback: boolean): boolean {
  if (value === undefined) return fallback;
  if (value === "true") return true;
  if (value === "false") return false;
  throw new ParkingApiError(400, "INVALID_QUERY", `${name} must be true or false`);
}

function purpose(value: unknown): ParkingPurpose {
  if (value === undefined) return "SHORT_WAIT";
  if (value === "PICKUP" || value === "SHORT_WAIT" || value === "LONG_STAY") return value;
  throw new ParkingApiError(400, "INVALID_QUERY", "purpose must be PICKUP, SHORT_WAIT, or LONG_STAY");
}

function searchQuery(request: FastifyRequest): ParkingSearchQuery {
  const query = request.query as Record<string, unknown>;
  return {
    lat: numeric(query.lat, "lat", -90, 90),
    lng: numeric(query.lng, "lng", -180, 180),
    radiusMeters: integer(query.radiusMeters, "radiusMeters", 1, 10_000, 2_000),
    plannedMinutes: integer(query.plannedMinutes, "plannedMinutes", 1, 1_440, 10),
    purpose: purpose(query.purpose),
    freeOnly: boolean(query.freeOnly, "freeOnly", false),
    limit: integer(query.limit, "limit", 1, 50, 20),
  };
}

function plannedMinutes(request: FastifyRequest): number {
  return integer((request.query as Record<string, unknown>).plannedMinutes, "plannedMinutes", 1, 1_440);
}

export function buildApp(config: AppConfig, dependencies: AppDependencies = {}): FastifyInstance {
  const app = Fastify({
    logger: { redact: ["req.headers.authorization", "req.query.serviceKey"] },
    requestIdHeader: "x-request-id",
    logController: new LogController({ disableRequestLogging: true }),
  });
  const provider = dependencies.provider ?? new CompositeParkingProvider([
    new PublicDataParkingProvider({
      baseUrl: config.providerBaseUrl,
      apiKey: config.providerApiKey,
      timeoutMs: config.requestTimeoutMs,
    }),
    new PickupZoneProvider(),
    new TimedZoneProvider(),
    new RestAreaProvider(),
  ]);
  const service = dependencies.service ?? new ParkingService(new ParkingSnapshotCache(provider, {
    cacheFile: config.cacheFile,
    ttlMs: config.cacheTtlMs,
    staleMaxAgeMs: config.staleMaxAgeMs,
  }));

  const rateWindows = new Map<string, { start: number; count: number }>();
  app.addHook("onRequest", async (request) => {
    const now = Date.now();
    const current = rateWindows.get(request.ip);
    const window = !current || now - current.start >= 60_000 ? { start: now, count: 0 } : current;
    window.count += 1;
    rateWindows.set(request.ip, window);
    if (rateWindows.size > 1_000) {
      for (const [ip, candidate] of rateWindows) if (now - candidate.start >= 60_000) rateWindows.delete(ip);
    }
    if (window.count > config.rateLimitPerMinute) throw new ParkingApiError(429, "RATE_LIMITED", "요청이 너무 많습니다. 잠시 후 다시 시도해 주세요");
  });

  app.get("/health", async () => ({ status: "ok" }));
  app.get("/api/v1/health", async () => ({ status: "ok" }));
  app.get("/api/v1/parking/places", async (request) => service.search(searchQuery(request)));
  app.get<{ Params: { placeId: string } }>("/api/v1/parking/places/:placeId", async (request) =>
    service.detail(request.params.placeId));
  app.get<{ Params: { placeId: string } }>("/api/v1/parking/places/:placeId/estimate", async (request) =>
    service.estimate(request.params.placeId, plannedMinutes(request)));

  app.setNotFoundHandler((request, reply) => reply.code(404).send({ code: "NOT_FOUND", message: "요청한 경로를 찾을 수 없습니다", requestId: request.id }));
  app.setErrorHandler((error, request, reply) => {
    const known = error instanceof ParkingApiError;
    const statusCode = known ? error.statusCode : 500;
    if (!known) request.log.error({ err: error }, "Unhandled request error");
    reply.code(statusCode).send({
      code: known ? error.code : "INTERNAL_ERROR",
      message: known ? error.message : "서버 오류가 발생했습니다",
      requestId: request.id,
    });
  });
  return app;
}
