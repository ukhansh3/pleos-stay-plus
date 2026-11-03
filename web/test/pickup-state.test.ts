import assert from "node:assert/strict";
import { test } from "node:test";
import { isStale, shouldApplyRevision } from "../lib/pickup-state.js";

test("ignores duplicate and out-of-order revisions", () => {
  assert.equal(shouldApplyRevision(4, 4), false);
  assert.equal(shouldApplyRevision(4, 3), false);
  assert.equal(shouldApplyRevision(4, 5), true);
});
test("marks missing and old locations stale", () => {
  const now = Date.parse("2026-11-03T00:00:20Z");
  assert.equal(isStale(null, now), true);
  assert.equal(isStale("2026-11-03T00:00:10Z", now), false);
  assert.equal(isStale("2026-11-03T00:00:00Z", now), true);
});
