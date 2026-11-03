import assert from "node:assert/strict";
import { test } from "node:test";
import { buildApp } from "../src/app.js";
import type { AppConfig } from "../src/config.js";
import type { AuthPrincipal } from "../src/pickup/domain.js";
import type { PickupAuthVerifier } from "../src/pickup/auth.js";
import { InMemoryPickupRepository } from "../src/pickup/memory-repository.js";
import { PickupService } from "../src/pickup/service.js";
import type { ParkingProvider } from "../src/provider.js";

const config: AppConfig = {
  host: "127.0.0.1", port: 3000, providerBaseUrl: "https://example.test", providerApiKey: null,
  restAreaProviderBaseUrl: "https://example.test/rest", restAreaProviderApiKey: null,
  cacheTtlMs: 1_000, staleMaxAgeMs: 2_000, cacheFile: ".cache/test.json", requestTimeoutMs: 100, rateLimitPerMinute: 1_000,
  pickupDevMode: false, supabaseUrl: null, supabasePublishableKey: null, supabaseSecretKey: null, pickupStorage: "disabled", publicWebBaseUrl: "http://localhost:3001",
  pickupInvitationHmacSecret: null, pickupProximityMaxAccuracyMeters: null, pickupProximityMaxAgeMs: null,
};
const provider: ParkingProvider = { async fetchSnapshot() { return { fetchedAt: new Date(0).toISOString(), places: [] }; } };
const auth: PickupAuthVerifier = {
  async verify(token: string): Promise<AuthPrincipal> {
    if (!token.startsWith("user-")) throw new Error("unexpected token");
    return { uid: token };
  },
};
const key = (suffix: number) => `00000000-0000-4000-8000-${String(suffix).padStart(12, "0")}`;
const headers = (uid: string, idempotencyKey?: string) => ({ authorization: `Bearer ${uid}`, ...(idempotencyKey ? { "idempotency-key": idempotencyKey } : {}) });

test("independent Pickup enforces owner, one participant, revocation, and location cleanup", async () => {
  let now = new Date("2026-11-03T00:00:00.000Z");
  const service = new PickupService(new InMemoryPickupRepository(), {
    publicWebBaseUrl: "https://pickup.example.test",
    invitationHmacSecret: "test-secret-that-is-at-least-thirty-two-characters",
    now: () => now,
  });
  const app = buildApp(config, { provider, pickupService: service, pickupAuth: auth });

  const created = await app.inject({ method: "POST", url: "/api/v1/pickup/sessions", headers: headers("user-driver-a", key(1)), payload: { locationSharingConsent: true, waitingSessionId: null } });
  assert.equal(created.statusCode, 201);
  const session = created.json();
  assert.equal(session.waitingSessionId, null);

  const otherRead = await app.inject({ method: "GET", url: `/api/v1/pickup/sessions/${session.id}`, headers: headers("user-driver-b") });
  assert.equal(otherRead.statusCode, 404);

  const invitation = await app.inject({ method: "POST", url: `/api/v1/pickup/sessions/${session.id}/invitations`, headers: headers("user-driver-a", key(2)) });
  assert.equal(invitation.statusCode, 201);
  const token = new URL(invitation.json().shareUrl).pathname.split("/").at(-1)!;

  const joined = await app.inject({ method: "POST", url: "/api/v1/pickup/invitations/redeem", headers: headers("user-guest-a"), payload: { token, consent: true } });
  assert.equal(joined.statusCode, 200);
  assert.equal(joined.json().status, "JOINED");

  const secondGuest = await app.inject({ method: "POST", url: "/api/v1/pickup/invitations/redeem", headers: headers("user-guest-b"), payload: { token, consent: true } });
  assert.equal(secondGuest.statusCode, 409);

  const departed = await app.inject({ method: "POST", url: `/api/v1/pickup/participants/${joined.json().participantId}/status`, headers: headers("user-guest-a"), payload: { status: "DEPARTED", clientEventId: key(3) } });
  assert.equal(departed.statusCode, 200);
  const replay = await app.inject({ method: "POST", url: `/api/v1/pickup/participants/${joined.json().participantId}/status`, headers: headers("user-guest-a"), payload: { status: "DEPARTED", clientEventId: key(3) } });
  assert.deepEqual(replay.json(), departed.json());
  const backwards = await app.inject({ method: "POST", url: `/api/v1/pickup/participants/${joined.json().participantId}/status`, headers: headers("user-guest-a"), payload: { status: "JOINED", clientEventId: key(4) } });
  assert.equal(backwards.statusCode, 409);

  const vehicleLocation = await app.inject({ method: "PUT", url: `/api/v1/pickup/sessions/${session.id}/vehicle-location`, headers: headers("user-driver-a"), payload: { latitude: 37.5, longitude: 127, confidence: 0.8, observedAt: now.toISOString(), source: "REAL" } });
  assert.equal(vehicleLocation.statusCode, 200);
  assert.equal(vehicleLocation.json().proximityCandidate, false);
  const simulated = await app.inject({ method: "PUT", url: `/api/v1/pickup/sessions/${session.id}/vehicle-location`, headers: headers("user-driver-a"), payload: { latitude: 37.5, longitude: 127, observedAt: now.toISOString(), source: "SIMULATED" } });
  assert.equal(simulated.statusCode, 403);

  const completed = await app.inject({ method: "POST", url: `/api/v1/pickup/sessions/${session.id}/complete`, headers: headers("user-guest-a", key(5)) });
  assert.equal(completed.statusCode, 200);
  assert.equal(completed.json().status, "COMPLETED");
  const snapshot = await app.inject({ method: "GET", url: `/api/v1/pickup/sessions/${session.id}/snapshot`, headers: headers("user-driver-a") });
  assert.equal(snapshot.statusCode, 410);
  await app.close();
});

test("invitation expires after 60 minutes and reissue revokes the old guest", async () => {
  let now = new Date("2026-11-03T00:00:00.000Z");
  const service = new PickupService(new InMemoryPickupRepository(), {
    publicWebBaseUrl: "https://pickup.example.test",
    invitationHmacSecret: "test-secret-that-is-at-least-thirty-two-characters",
    now: () => now,
  });
  const app = buildApp(config, { provider, pickupService: service, pickupAuth: auth });
  const created = await app.inject({ method: "POST", url: "/api/v1/pickup/sessions", headers: headers("user-driver-a", key(11)), payload: { locationSharingConsent: true } });
  const id = created.json().id;
  const invite = await app.inject({ method: "POST", url: `/api/v1/pickup/sessions/${id}/invitations`, headers: headers("user-driver-a", key(12)) });
  const token = new URL(invite.json().shareUrl).pathname.split("/").at(-1)!;
  now = new Date("2026-11-03T01:00:00.001Z");
  const expired = await app.inject({ method: "POST", url: "/api/v1/pickup/invitations/redeem", headers: headers("user-guest-a"), payload: { token, consent: true } });
  assert.equal(expired.statusCode, 410);
  await app.close();
});

test("reissuing invitation revokes the old participant and token", async () => {
  const service = new PickupService(new InMemoryPickupRepository(), {
    publicWebBaseUrl: "https://pickup.example.test",
    invitationHmacSecret: "test-secret-that-is-at-least-thirty-two-characters",
    now: () => new Date("2026-11-03T00:00:00.000Z"),
  });
  const app = buildApp(config, { provider, pickupService: service, pickupAuth: auth });
  const created = await app.inject({ method: "POST", url: "/api/v1/pickup/sessions", headers: headers("user-driver-a", key(21)), payload: { locationSharingConsent: true } });
  const id = created.json().id;
  const first = await app.inject({ method: "POST", url: `/api/v1/pickup/sessions/${id}/invitations`, headers: headers("user-driver-a", key(22)) });
  const oldToken = new URL(first.json().shareUrl).pathname.split("/").at(-1)!;
  await app.inject({ method: "POST", url: "/api/v1/pickup/invitations/redeem", headers: headers("user-guest-a"), payload: { token: oldToken, consent: true } });
  const second = await app.inject({ method: "POST", url: `/api/v1/pickup/sessions/${id}/invitations`, headers: headers("user-driver-a", key(23)) });
  assert.equal(second.statusCode, 201);
  const oldAccess = await app.inject({ method: "GET", url: `/api/v1/pickup/sessions/${id}/snapshot`, headers: headers("user-guest-a") });
  assert.equal(oldAccess.statusCode, 404);
  const oldRedeem = await app.inject({ method: "POST", url: "/api/v1/pickup/invitations/redeem", headers: headers("user-guest-b"), payload: { token: oldToken, consent: true } });
  assert.equal(oldRedeem.statusCode, 410);
  await app.close();
});

test("proximity requires accurate fresh positions for 20m during 30 seconds", async () => {
  let now = new Date("2026-11-03T00:00:00.000Z");
  const service = new PickupService(new InMemoryPickupRepository(), {
    publicWebBaseUrl: "https://pickup.example.test",
    invitationHmacSecret: "test-secret-that-is-at-least-thirty-two-characters",
    now: () => now,
    proximityPolicy: { maxAccuracyMeters: 20, maxAgeMs: 60_000, distanceMeters: 20, dwellMs: 30_000 },
  });
  const app = buildApp(config, { provider, pickupService: service, pickupAuth: auth });
  const created = await app.inject({ method: "POST", url: "/api/v1/pickup/sessions", headers: headers("user-driver-a", key(31)), payload: { locationSharingConsent: true } });
  const id = created.json().id;
  const invite = await app.inject({ method: "POST", url: `/api/v1/pickup/sessions/${id}/invitations`, headers: headers("user-driver-a", key(32)) });
  const token = new URL(invite.json().shareUrl).pathname.split("/").at(-1)!;
  const joined = await app.inject({ method: "POST", url: "/api/v1/pickup/invitations/redeem", headers: headers("user-guest-a"), payload: { token, consent: true } });
  const participantId = joined.json().participantId;
  const position = { latitude: 37.5, longitude: 127, accuracyMeters: 5, observedAt: now.toISOString(), source: "REAL" };
  await app.inject({ method: "PUT", url: `/api/v1/pickup/sessions/${id}/vehicle-location`, headers: headers("user-driver-a"), payload: position });
  const first = await app.inject({ method: "PUT", url: `/api/v1/pickup/participants/${participantId}/location`, headers: headers("user-guest-a"), payload: position });
  assert.equal(first.json().proximityCandidate, false);
  now = new Date("2026-11-03T00:00:31.000Z");
  const second = await app.inject({ method: "PUT", url: `/api/v1/pickup/participants/${participantId}/location`, headers: headers("user-guest-a"), payload: { ...position, observedAt: now.toISOString() } });
  assert.equal(second.json().proximityCandidate, true);
  await app.close();
});
