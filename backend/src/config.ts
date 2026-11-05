import path from "node:path";
import { fileURLToPath } from "node:url";
import dotenv from "dotenv";

const BACKEND_ENV_FILE = fileURLToPath(new URL("../.env", import.meta.url));

// Keep shell/hosting environment variables authoritative while filling missing
// local-development values from backend/.env for both src and dist execution.
dotenv.config({ path: BACKEND_ENV_FILE, override: false });

const DEFAULT_PROVIDER_URL = "https://api.data.go.kr/openapi/tn_pubr_prkplce_info_api";
const DEFAULT_REST_AREA_PROVIDER_URL = "https://api.data.go.kr/openapi/tn_pubr_public_rest_area_api";

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
  restAreaProviderBaseUrl: string;
  restAreaProviderApiKey: string | null;
  cacheTtlMs: number;
  staleMaxAgeMs: number;
  cacheFile: string;
  requestTimeoutMs: number;
  rateLimitPerMinute: number;
  pickupDevMode: boolean;
  supabaseUrl: string | null;
  supabasePublishableKey: string | null;
  supabaseSecretKey: string | null;
  pickupStorage: "disabled" | "memory" | "supabase";
  publicWebBaseUrl: string;
  pickupInvitationHmacSecret: string | null;
  pickupProximityMaxAccuracyMeters: number | null;
  pickupProximityMaxAgeMs: number | null;
  pickupSimulatorEnabled: boolean;
  pickupSimulatorToken: string | null;
}

export function loadConfig(env: NodeJS.ProcessEnv = process.env, cwd = process.cwd()): AppConfig {
  const port = positiveNumber(env.PARKING_API_PORT, 3000, "PARKING_API_PORT");
  if (!Number.isInteger(port) || port > 65_535) throw new Error("PARKING_API_PORT must be an integer between 1 and 65535");

  const providerBaseUrl = env.PARKING_PROVIDER_BASE_URL?.trim() || DEFAULT_PROVIDER_URL;
  const parsedUrl = new URL(providerBaseUrl);
  if (parsedUrl.protocol !== "https:") throw new Error("PARKING_PROVIDER_BASE_URL must use HTTPS");
  const restAreaProviderBaseUrl = env.REST_AREA_PROVIDER_BASE_URL?.trim() || DEFAULT_REST_AREA_PROVIDER_URL;
  const parsedRestAreaUrl = new URL(restAreaProviderBaseUrl);
  if (parsedRestAreaUrl.protocol !== "https:") throw new Error("REST_AREA_PROVIDER_BASE_URL must use HTTPS");
  const pickupDevMode = env.PICKUP_DEV_MODE?.trim() === "true";
  const pickupStorageRaw = env.PICKUP_STORAGE?.trim() || "disabled";
  if (pickupStorageRaw !== "disabled" && pickupStorageRaw !== "memory" && pickupStorageRaw !== "supabase") {
    throw new Error("PICKUP_STORAGE must be disabled, memory, or supabase");
  }
  const supabaseUrl = env.SUPABASE_URL?.trim() || null;
  if (supabaseUrl && new URL(supabaseUrl).protocol !== "https:") throw new Error("SUPABASE_URL must use HTTPS");
  const publicWebBaseUrl = env.PUBLIC_WEB_BASE_URL?.trim() || "http://localhost:3001";
  const publicWebUrl = new URL(publicWebBaseUrl);
  if (publicWebUrl.protocol !== "https:" && publicWebUrl.hostname !== "localhost" && publicWebUrl.hostname !== "127.0.0.1") {
    throw new Error("PUBLIC_WEB_BASE_URL must use HTTPS outside loopback development");
  }
  const optionalPositive = (value: string | undefined, name: string): number | null =>
    value === undefined || value.trim() === "" ? null : positiveNumber(value, 1, name);

  const pickupSimulatorEnabled = env.PICKUP_SIMULATOR_ENABLED?.trim() === "true";
  const pickupSimulatorToken = env.PICKUP_SIMULATOR_TOKEN?.trim() || null;
  const host = env.PARKING_API_HOST?.trim() || "127.0.0.1";
  if (pickupSimulatorEnabled && host !== "127.0.0.1" && host !== "localhost") throw new Error("Pickup simulator is restricted to a loopback host");
  if (pickupSimulatorEnabled && (!pickupSimulatorToken || pickupSimulatorToken.length < 32)) throw new Error("PICKUP_SIMULATOR_TOKEN must be at least 32 characters when the simulator is enabled");

  return {
    host,
    port,
    providerBaseUrl,
    providerApiKey: env.PARKING_PROVIDER_API_KEY?.trim() || null,
    restAreaProviderBaseUrl,
    restAreaProviderApiKey: env.REST_AREA_PROVIDER_API_KEY?.trim() || null,
    cacheTtlMs: positiveNumber(env.PARKING_CACHE_TTL_HOURS, 24, "PARKING_CACHE_TTL_HOURS") * 60 * 60 * 1000,
    staleMaxAgeMs: positiveNumber(env.PARKING_STALE_MAX_DAYS, 7, "PARKING_STALE_MAX_DAYS") * 24 * 60 * 60 * 1000,
    cacheFile: path.resolve(cwd, env.PARKING_CACHE_FILE?.trim() || ".cache/parking-snapshot.json"),
    requestTimeoutMs: positiveNumber(env.PARKING_PROVIDER_TIMEOUT_MS, 15_000, "PARKING_PROVIDER_TIMEOUT_MS"),
    rateLimitPerMinute: positiveNumber(env.PARKING_RATE_LIMIT_PER_MINUTE, 120, "PARKING_RATE_LIMIT_PER_MINUTE"),
    pickupDevMode,
    supabaseUrl,
    supabasePublishableKey: env.SUPABASE_PUBLISHABLE_KEY?.trim() || null,
    supabaseSecretKey: env.SUPABASE_SECRET_KEY?.trim() || null,
    pickupStorage: pickupStorageRaw,
    publicWebBaseUrl,
    pickupInvitationHmacSecret: env.PICKUP_INVITATION_HMAC_SECRET?.trim() || null,
    pickupProximityMaxAccuracyMeters: optionalPositive(env.PICKUP_PROXIMITY_MAX_ACCURACY_METERS, "PICKUP_PROXIMITY_MAX_ACCURACY_METERS"),
    pickupProximityMaxAgeMs: optionalPositive(env.PICKUP_PROXIMITY_MAX_AGE_SECONDS, "PICKUP_PROXIMITY_MAX_AGE_SECONDS") === null
      ? null
      : optionalPositive(env.PICKUP_PROXIMITY_MAX_AGE_SECONDS, "PICKUP_PROXIMITY_MAX_AGE_SECONDS")! * 1_000,
    pickupSimulatorEnabled,
    pickupSimulatorToken,
  };
}
