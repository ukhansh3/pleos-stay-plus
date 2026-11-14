import { loadConfig, type AppConfig } from "../config.js";

type Environment = NodeJS.ProcessEnv;

function required(env: Environment, name: string): string {
  const value = env[name]?.trim();
  if (!value) throw new Error(`${name}_REQUIRED`);
  return value;
}

function httpsOrigin(value: string, name: string): string {
  const url = new URL(value);
  if (url.protocol !== "https:" || url.username || url.password || url.search || url.hash ||
      (url.pathname !== "" && url.pathname !== "/")) throw new Error(`${name}_HTTPS_ORIGIN_REQUIRED`);
  return url.origin;
}

export function hostedPort(env: Environment): number {
  const value = Number(env.PORT ?? "10000");
  if (!Number.isInteger(value) || value < 1 || value > 65535) throw new Error("PORT_INVALID");
  return value;
}

function rejectDevelopment(env: Environment): void {
  for (const name of ["PICKUP_DEV_MODE", "PICKUP_SIMULATOR_ENABLED", "ENABLE_PICKUP_SIMULATOR", "REALTIME_LOCAL_DEMO"]) {
    if (env[name]?.trim() === "true") throw new Error("DEVELOPMENT_MODE_FORBIDDEN");
  }
}

export function hostedParkingConfig(env: Environment): AppConfig {
  rejectDevelopment(env);
  if (env.PICKUP_STORAGE && env.PICKUP_STORAGE !== "supabase") throw new Error("SUPABASE_STORAGE_REQUIRED");
  const supabaseUrl = httpsOrigin(required(env, "SUPABASE_URL"), "SUPABASE_URL");
  const publishableKey = required(env, "SUPABASE_PUBLISHABLE_KEY");
  if (!publishableKey.startsWith("sb_publishable_")) throw new Error("PUBLISHABLE_KEY_REQUIRED");
  const invitationSecret = required(env, "PICKUP_INVITATION_HMAC_SECRET");
  if (invitationSecret.length < 32) throw new Error("INVITATION_SECRET_TOO_SHORT");
  required(env, "SUPABASE_SECRET_KEY");
  required(env, "PARKING_PROVIDER_API_KEY");
  // During initial service creation the web URL may not exist yet.
  // Existing invitation creation fails closed until this real HTTPS origin is set.
  const publicWebBaseUrl = env.PUBLIC_WEB_BASE_URL?.trim();
  return loadConfig({
    ...env,
    PARKING_API_HOST: "0.0.0.0",
    PARKING_API_PORT: String(hostedPort(env)),
    SUPABASE_URL: supabaseUrl,
    PICKUP_STORAGE: "supabase",
    PICKUP_DEV_MODE: "false",
    PICKUP_SIMULATOR_ENABLED: "false",
    PUBLIC_WEB_BASE_URL: publicWebBaseUrl ? httpsOrigin(publicWebBaseUrl, "PUBLIC_WEB_BASE_URL") : "",
  });
}

export function hostedRealtimeConfig(env: Environment) {
  rejectDevelopment(env);
  if (env.REALTIME_SUPABASE_ENABLED !== "true") throw new Error("REALTIME_SUPABASE_ENABLED_REQUIRED");
  const url = httpsOrigin(required(env, "REALTIME_SUPABASE_URL"), "REALTIME_SUPABASE_URL");
  const publicKey = required(env, "REALTIME_SUPABASE_PUBLIC_KEY");
  if (!publicKey.startsWith("sb_publishable_")) throw new Error("PUBLISHABLE_KEY_REQUIRED");
  const pairingSecret = required(env, "REALTIME_PAIRING_SECRET");
  if (pairingSecret.length < 32) throw new Error("PAIRING_SECRET_TOO_SHORT");
  const maxBytes = Number(env.REALTIME_MAX_BYTES ?? 20 * 1024 * 1024);
  if (!Number.isSafeInteger(maxBytes) || maxBytes < 1 || maxBytes > 20 * 1024 * 1024) throw new Error("MAX_BYTES_INVALID");
  return { url, publicKey, serverSecret: required(env, "REALTIME_SUPABASE_SERVER_SECRET"), pairingSecret, maxBytes };
}
