import { buildSupabaseRealtimeServer } from "../realtime/supabase-server.js";
import { hostedPort, hostedRealtimeConfig } from "./config.js";

async function start() {
  const { app } = await buildSupabaseRealtimeServer(hostedRealtimeConfig(process.env));
  app.get("/health", async () => ({ status: "ok" }));
  // Review deployment: do not schedule database/storage cleanup or run migrations.
  // Existing RPC authorization and session-close cleanup remain unchanged.
  for (const signal of ["SIGTERM", "SIGINT"] as const) {
    process.once(signal, () => { void app.close(); });
  }
  await app.listen({ host: "0.0.0.0", port: hostedPort(process.env) });
}
void start().catch(() => {
  console.error("HOSTED_REALTIME_START_FAILED");
  process.exitCode = 1;
});
