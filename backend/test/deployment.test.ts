import assert from "node:assert/strict";
import test from "node:test";
import { hostedParkingConfig, hostedPort, hostedRealtimeConfig } from "../src/deployment/config.js";

const fixture = {
  PORT: "10000",
  SUPABASE_URL: "https://deployment-fixture.supabase.co",
  SUPABASE_PUBLISHABLE_KEY: "sb_publishable_nonsecret_test_fixture",
  SUPABASE_SECRET_KEY: "nonsecret-server-test-fixture",
  PICKUP_INVITATION_HMAC_SECRET: "nonsecret-invitation-fixture-0000000000",
  PARKING_PROVIDER_API_KEY: "nonsecret-provider-test-fixture",
  PUBLIC_WEB_BASE_URL: "https://web-fixture.example",
};
const realtime = {
  REALTIME_SUPABASE_ENABLED: "true",
  REALTIME_SUPABASE_URL: fixture.SUPABASE_URL,
  REALTIME_SUPABASE_PUBLIC_KEY: fixture.SUPABASE_PUBLISHABLE_KEY,
  REALTIME_SUPABASE_SERVER_SECRET: fixture.SUPABASE_SECRET_KEY,
  REALTIME_PAIRING_SECRET: "nonsecret-pairing-fixture-0000000000",
};

test("hosting config uses provider port and real Supabase adapter without developer routes", () => {
  const config = hostedParkingConfig(fixture);
  assert.equal(config.host, "0.0.0.0");
  assert.equal(config.port, 10000);
  assert.equal(config.pickupStorage, "supabase");
  assert.equal(config.pickupDevMode, false);
  assert.equal(config.pickupSimulatorEnabled, false);
  assert.equal(config.publicWebBaseUrl, fixture.PUBLIC_WEB_BASE_URL);
  assert.equal(hostedRealtimeConfig(realtime).maxBytes, 20 * 1024 * 1024);
});

test("hosting rejects invalid ports, missing secrets, local share origins and server keys as public keys", () => {
  for (const PORT of ["0", "65536", "1.2", "NaN"]) assert.throws(() => hostedPort({ PORT }));
  assert.throws(() => hostedParkingConfig({ ...fixture, SUPABASE_SECRET_KEY: "" }));
  assert.throws(() => hostedParkingConfig({ ...fixture, PUBLIC_WEB_BASE_URL: "http://localhost:3001" }));
  assert.throws(() => hostedParkingConfig({ ...fixture, SUPABASE_URL: "https://user:pass@example.com" }));
  assert.throws(() => hostedParkingConfig({ ...fixture, SUPABASE_PUBLISHABLE_KEY: "server-only-nonsecret-fixture" }));
  assert.throws(() => hostedRealtimeConfig({ ...realtime, REALTIME_MAX_BYTES: String(20 * 1024 * 1024 + 1) }));
});

test("hosting rejects memory storage, fixed-token auth and simulator flags", () => {
  assert.throws(() => hostedParkingConfig({ ...fixture, PICKUP_STORAGE: "memory" }));
  for (const name of ["PICKUP_DEV_MODE", "PICKUP_SIMULATOR_ENABLED", "ENABLE_PICKUP_SIMULATOR", "REALTIME_LOCAL_DEMO"]) {
    assert.throws(() => hostedParkingConfig({ ...fixture, [name]: "true" }));
    assert.throws(() => hostedRealtimeConfig({ ...realtime, [name]: "true" }));
  }
  assert.throws(() => hostedRealtimeConfig({ ...realtime, REALTIME_SUPABASE_ENABLED: "false" }));
});

test("initial service can start before web origin is assigned without inventing a share URL", () => {
  const config = hostedParkingConfig({ ...fixture, PUBLIC_WEB_BASE_URL: "" });
  assert.equal(config.publicWebBaseUrl, null);
  assert.equal(config.pickupSimulatorEnabled, false);
});
