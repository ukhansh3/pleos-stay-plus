import test from "node:test";
import assert from "node:assert/strict";
import { LocalPairingService } from "../src/realtime/local-pairing-service.js";
function fixture() {
  let now = 1000; const service = new LocalPairingService("x".repeat(32), 100, () => now);
  service.register("pc", "WINDOWS", "PC"); service.register("car", "VEHICLE", "Car"); service.register("outsider", "VEHICLE", "Other");
  return { service, expire: () => { now += 101; } };
}
test("pairing requires both approvals, preserves a single connection and revokes access", () => {
  const { service: s } = fixture(); const p = s.issue("pc"); s.claim("car", p.code, "local");
  s.approve("pc", p.id); assert.equal(s.current("pc"), null);
  const result = s.approve("car", p.id); assert.ok(result.connectionId);
  assert.equal(s.approve("car", p.id).connectionId, result.connectionId);
  assert.throws(() => s.authorize("outsider", result.connectionId!));
  assert.throws(() => s.issue("pc"));
  s.revoke("car", result.connectionId!); assert.throws(() => s.authorize("pc", result.connectionId!));
});
test("expired codes, code reuse and wrong device types are rejected", () => {
  const { service: s, expire } = fixture(); const p = s.issue("pc");
  assert.throws(() => s.claim("pc", p.code, "same"));
  s.claim("car", p.code, "local"); assert.throws(() => s.claim("outsider", p.code, "other"));
  expire(); assert.throws(() => s.approve("car", p.id));
});
test("outsiders cannot inspect or cancel a pairing and repeated guesses are limited", () => {
  const { service: s } = fixture(); const p = s.issue("pc");
  assert.throws(() => s.get("outsider", p.id)); assert.throws(() => s.cancel("outsider", p.id));
  for (let i = 0; i < 5; i++) assert.throws(() => s.claim("outsider", "invalid", "local"));
  assert.throws(() => s.claim("outsider", p.code, "local"), { message: "RATE_LIMITED" });
});
