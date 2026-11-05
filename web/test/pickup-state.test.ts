import assert from "node:assert/strict";
import { test } from "node:test";
import { isStale, shouldApplyRevision, shouldRefreshForEvent, shouldRefreshSession, shouldUploadPosition } from "../lib/pickup-state.js";
import { pickupProxyPath } from "../lib/pickup-proxy.js";

test("ignores duplicate and out-of-order revisions", () => {
  assert.equal(shouldApplyRevision(4, 4), false);
  assert.equal(shouldApplyRevision(4, 3), false);
  assert.equal(shouldApplyRevision(4, 5), true);
});
test("refreshes snapshots only for newer or unversioned realtime events", () => {
  assert.equal(shouldRefreshForEvent(4, 4), false);
  assert.equal(shouldRefreshForEvent(4, 3), false);
  assert.equal(shouldRefreshForEvent(4, 5), true);
  assert.equal(shouldRefreshForEvent(4, undefined), true);
});
test("refreshes auth sessions before expiry", () => {
  const now = Date.parse("2026-11-03T00:00:00Z");
  assert.equal(shouldRefreshSession(now / 1000 + 30, now), true);
  assert.equal(shouldRefreshSession(now / 1000 + 120, now), false);
});
test("throttles continuous GPS uploads to the target interval", () => {
  assert.equal(shouldUploadPosition(null, 10_000), true);
  assert.equal(shouldUploadPosition(10_000, 14_999), false);
  assert.equal(shouldUploadPosition(10_000, 15_000), true);
});
test("marks missing and old locations stale", () => {
  const now = Date.parse("2026-11-03T00:00:20Z");
  assert.equal(isStale(null, now), true);
  assert.equal(isStale("2026-11-03T00:00:10Z", now), false);
  assert.equal(isStale("2026-11-03T00:00:00Z", now), true);
});
test("allows only fixed Pickup proxy paths without duplicating the prefix", () => {
  assert.equal(pickupProxyPath(["pickup", "sessions", "123"]), "pickup/sessions/123");
  assert.equal(pickupProxyPath(["waiting", "sessions"]), null);
  assert.equal(pickupProxyPath(["pickup", "..", "admin"]), null);
});
