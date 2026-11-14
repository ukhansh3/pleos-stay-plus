import { buildApp } from "../app.js";
import { hostedParkingConfig } from "./config.js";

async function start() {
  const config = hostedParkingConfig(process.env);
  const app = buildApp(config);
  for (const signal of ["SIGTERM", "SIGINT"] as const) {
    process.once(signal, () => { void app.close(); });
  }
  await app.listen({ host: config.host, port: config.port });
}
void start().catch(() => {
  console.error("HOSTED_PARKING_START_FAILED");
  process.exitCode = 1;
});
