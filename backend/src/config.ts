import path from "node:path";

const DEFAULT_PROVIDER_URL = "https://api.data.go.kr/openapi/tn_pubr_prkplce_info_api";

function positiveNumber(value: string | undefined, fallback: number, name: string): number {
  if (value === undefined || value.trim() === "") return fallback;
  const parsed = Number(value);
  if (!Number.isFinite(parsed) || parsed <= 0) throw new Error(`${name} must be a positive number`);
  return parsed;
}

export interface AppConfig {
  host: string;
  port: number;
  providerBaseUrl: string;
  providerApiKey: string | null;
  cacheTtlMs: number;
  staleMaxAgeMs: number;
  cacheFile: string;
  requestTimeoutMs: number;
  rateLimitPerMinute: number;
}

export function loadConfig(env: NodeJS.ProcessEnv = process.env, cwd = process.cwd()): AppConfig {
  const port = positiveNumber(env.PARKING_API_PORT, 3000, "PARKING_API_PORT");
  if (!Number.isInteger(port) || port > 65_535) throw new Error("PARKING_API_PORT must be an integer between 1 and 65535");

  const providerBaseUrl = env.PARKING_PROVIDER_BASE_URL?.trim() || DEFAULT_PROVIDER_URL;
  const parsedUrl = new URL(providerBaseUrl);
  if (parsedUrl.protocol !== "https:") throw new Error("PARKING_PROVIDER_BASE_URL must use HTTPS");

  return {
    host: env.PARKING_API_HOST?.trim() || "127.0.0.1",
    port,
    providerBaseUrl,
    providerApiKey: env.PARKING_PROVIDER_API_KEY?.trim() || null,
    cacheTtlMs: positiveNumber(env.PARKING_CACHE_TTL_HOURS, 24, "PARKING_CACHE_TTL_HOURS") * 60 * 60 * 1000,
    staleMaxAgeMs: positiveNumber(env.PARKING_STALE_MAX_DAYS, 7, "PARKING_STALE_MAX_DAYS") * 24 * 60 * 60 * 1000,
    cacheFile: path.resolve(cwd, env.PARKING_CACHE_FILE?.trim() || ".cache/parking-snapshot.json"),
    requestTimeoutMs: positiveNumber(env.PARKING_PROVIDER_TIMEOUT_MS, 15_000, "PARKING_PROVIDER_TIMEOUT_MS"),
    rateLimitPerMinute: positiveNumber(env.PARKING_RATE_LIMIT_PER_MINUTE, 120, "PARKING_RATE_LIMIT_PER_MINUTE"),
  };
}
