import { timingSafeEqual } from "node:crypto";
import type { FastifyInstance, FastifyRequest } from "fastify";
import { bearerToken, type PickupAuthVerifier } from "./auth.js";
import { PickupApiError, type PickupLocationSource, type PickupLocationUpdate, type PickupParticipantStatus } from "./domain.js";
import type { PickupService } from "./service.js";

const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
const statuses = new Set<PickupParticipantStatus>(["JOINED", "DEPARTED", "NEARBY", "ARRIVED", "CANCELLED"]);

function body(request: FastifyRequest): Record<string, unknown> { return (request.body ?? {}) as Record<string, unknown>; }
function string(value: unknown, name: string, required = true): string | undefined {
  if (value === undefined && !required) return undefined;
  if (typeof value !== "string" || value.trim() === "") throw new PickupApiError(400, "INVALID_REQUEST", `${name} 값이 올바르지 않습니다`);
  return value.trim();
}
function idempotency(request: FastifyRequest): string {
  const value = string(request.headers["idempotency-key"], "Idempotency-Key");
  if (!value || !uuid.test(value)) throw new PickupApiError(400, "INVALID_IDEMPOTENCY_KEY", "Idempotency-Key는 UUID여야 합니다");
  return value;
}
async function principal(request: FastifyRequest, auth: PickupAuthVerifier): Promise<string> {
  return (await auth.verify(bearerToken(request.headers.authorization))).uid;
}
function location(value: Record<string, unknown>, allowSimulated = false): PickupLocationUpdate {
  const latitude = Number(value.latitude); const longitude = Number(value.longitude);
  if (!Number.isFinite(latitude) || latitude < -90 || latitude > 90 || !Number.isFinite(longitude) || longitude < -180 || longitude > 180) throw new PickupApiError(400, "INVALID_LOCATION", "위치 좌표가 올바르지 않습니다");
  const accuracy = value.accuracyMeters === undefined || value.accuracyMeters === null ? null : Number(value.accuracyMeters);
  if (accuracy !== null && (!Number.isFinite(accuracy) || accuracy <= 0)) throw new PickupApiError(400, "INVALID_LOCATION", "위치 정확도가 올바르지 않습니다");
  const confidence = value.confidence === undefined || value.confidence === null ? null : Number(value.confidence);
  if (confidence !== null && !Number.isFinite(confidence)) throw new PickupApiError(400, "INVALID_LOCATION", "위치 confidence가 올바르지 않습니다");
  const observedAt = string(value.observedAt, "observedAt")!;
  if (!Number.isFinite(Date.parse(observedAt))) throw new PickupApiError(400, "INVALID_LOCATION", "observedAt이 올바르지 않습니다");
  if (value.source !== "REAL" && value.source !== "SIMULATED") throw new PickupApiError(400, "INVALID_LOCATION", "source가 올바르지 않습니다");
  if (value.source === "SIMULATED" && !allowSimulated) throw new PickupApiError(403, "SIMULATED_SOURCE_FORBIDDEN", "일반 Pickup API에는 시뮬레이션 위치를 전송할 수 없습니다");
  return { latitude, longitude, accuracyMeters: accuracy, confidence, observedAt, source: value.source as PickupLocationSource };
}

function afterRevision(request: FastifyRequest): number {
  const raw = (request.query as Record<string, unknown>).afterRevision;
  if (raw === undefined) return 0;
  const value = typeof raw === "string" && raw.trim() !== "" ? Number(raw) : Number.NaN;
  if (!Number.isSafeInteger(value) || value < 0) throw new PickupApiError(400, "INVALID_REVISION", "afterRevision은 0 이상의 정수여야 합니다");
  return value;
}

function simulatedNow(value: unknown): string | null {
  if (value === undefined || value === null) return null;
  if (typeof value !== "string" || !Number.isFinite(Date.parse(value))) throw new PickupApiError(400, "INVALID_SIMULATED_TIME", "simulatedNow이 올바른 ISO 시각이어야 합니다");
  return new Date(value).toISOString();
}

export interface PickupSimulatorRouteOptions { enabled: boolean; token: string | null }

function verifySimulatorToken(request: FastifyRequest, expected: string): void {
  const provided = request.headers["x-pickup-simulator-token"];
  if (typeof provided !== "string") throw new PickupApiError(403, "SIMULATOR_ACCESS_DENIED", "Pickup 시뮬레이터 접근이 거부되었습니다");
  const actualBytes = Buffer.from(provided); const expectedBytes = Buffer.from(expected);
  if (actualBytes.length !== expectedBytes.length || !timingSafeEqual(actualBytes, expectedBytes)) throw new PickupApiError(403, "SIMULATOR_ACCESS_DENIED", "Pickup 시뮬레이터 접근이 거부되었습니다");
}

export function registerPickupRoutes(app: FastifyInstance, service: PickupService, auth: PickupAuthVerifier, simulator: PickupSimulatorRouteOptions = { enabled: false, token: null }): void {
  app.post("/api/v1/pickup/sessions", async (request, reply) => {
    const input = body(request); if (input.locationSharingConsent !== true) throw new PickupApiError(400, "CONSENT_REQUIRED", "차량 위치 공유 동의가 필요합니다");
    const waiting = input.waitingSessionId === null || input.waitingSessionId === undefined ? null : string(input.waitingSessionId, "waitingSessionId")!;
    return reply.code(201).send(await service.createSession(await principal(request, auth), waiting, idempotency(request)));
  });
  app.get<{ Params: { sessionId: string } }>("/api/v1/pickup/sessions/:sessionId", async (request) => service.getSession(await principal(request, auth), request.params.sessionId));
  app.post<{ Params: { sessionId: string } }>("/api/v1/pickup/sessions/:sessionId/invitations", async (request, reply) => reply.code(201).send(await service.issueInvitation(await principal(request, auth), request.params.sessionId, idempotency(request))));
  app.post("/api/v1/pickup/invitations/redeem", async (request) => {
    const input = body(request); return service.redeemInvitation(await principal(request, auth), string(input.token, "token")!, input.consent === true, string(input.displayName, "displayName", false));
  });
  app.post<{ Params: { participantId: string } }>("/api/v1/pickup/participants/:participantId/status", async (request) => {
    const input = body(request); if (typeof input.status !== "string" || !statuses.has(input.status as PickupParticipantStatus)) throw new PickupApiError(400, "INVALID_STATUS", "Pickup 상태가 올바르지 않습니다");
    const eventId = string(input.clientEventId, "clientEventId")!; if (!uuid.test(eventId)) throw new PickupApiError(400, "INVALID_EVENT_ID", "clientEventId는 UUID여야 합니다");
    return service.updateParticipantStatus(await principal(request, auth), request.params.participantId, input.status as PickupParticipantStatus, eventId);
  });
  app.put<{ Params: { sessionId: string } }>("/api/v1/pickup/sessions/:sessionId/vehicle-location", async (request) => service.updateVehicleLocation(await principal(request, auth), request.params.sessionId, location(body(request))));
  app.put<{ Params: { participantId: string } }>("/api/v1/pickup/participants/:participantId/location", async (request) => service.updateParticipantLocation(await principal(request, auth), request.params.participantId, location(body(request))));
  app.get<{ Params: { sessionId: string } }>("/api/v1/pickup/sessions/:sessionId/snapshot", async (request) => service.getSnapshot(await principal(request, auth), request.params.sessionId));
  app.get<{ Params: { sessionId: string } }>("/api/v1/pickup/sessions/:sessionId/events", async (request) => service.listEvents(await principal(request, auth), request.params.sessionId, afterRevision(request)));
  app.post<{ Params: { sessionId: string } }>("/api/v1/pickup/sessions/:sessionId/complete", async (request) => service.close(await principal(request, auth), request.params.sessionId, "COMPLETED", idempotency(request), null, false));
  app.post<{ Params: { sessionId: string } }>("/api/v1/pickup/sessions/:sessionId/cancel", async (request) => {
    const reason = string(body(request).reason, "reason")!;
    if (reason.length > 120) throw new PickupApiError(400, "INVALID_REQUEST", "취소 사유는 120자 이하여야 합니다");
    return service.close(await principal(request, auth), request.params.sessionId, "CANCELLED", idempotency(request), reason, false);
  });
  app.post<{ Params: { sessionId: string } }>("/api/v1/pickup/sessions/:sessionId/end", async (request) => service.close(await principal(request, auth), request.params.sessionId, "ENDED", idempotency(request), null, true));
  app.post<{ Params: { sessionId: string } }>("/api/v1/pickup/sessions/:sessionId/revoke-invitation", async (request, reply) => { await service.revoke(await principal(request, auth), request.params.sessionId, idempotency(request)); return reply.code(204).send(); });

  if (simulator.enabled) {
    if (!simulator.token) throw new Error("Pickup simulator token is required");
    const expectedToken = simulator.token;
    app.put<{ Params: { sessionId: string } }>("/api/v1/qa/pickup-simulator/sessions/:sessionId/vehicle-location", async (request) => {
      verifySimulatorToken(request, expectedToken);
      const input = body(request); const now = simulatedNow(input.simulatedNow); const parsed = location({ ...input, source: "SIMULATED" }, true); const actor = await principal(request, auth);
      return now === null ? service.updateVehicleLocation(actor, request.params.sessionId, parsed) : service.updateSimulatedVehicleLocation(actor, request.params.sessionId, parsed, now);
    });
    app.put<{ Params: { participantId: string } }>("/api/v1/qa/pickup-simulator/participants/:participantId/location", async (request) => {
      verifySimulatorToken(request, expectedToken);
      const input = body(request); const now = simulatedNow(input.simulatedNow); const parsed = location({ ...input, source: "SIMULATED" }, true); const actor = await principal(request, auth);
      return now === null ? service.updateParticipantLocation(actor, request.params.participantId, parsed) : service.updateSimulatedParticipantLocation(actor, request.params.participantId, parsed, now);
    });
  }
}
