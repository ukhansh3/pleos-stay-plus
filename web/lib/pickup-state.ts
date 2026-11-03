export function shouldApplyRevision(current: number, incoming: number): boolean { return Number.isInteger(incoming) && incoming > current; }
export function isStale(receivedAt: string | null, now = Date.now(), thresholdMs = 15_000): boolean {
  if (!receivedAt) return true;
  const value = Date.parse(receivedAt);
  return !Number.isFinite(value) || now - value > thresholdMs;
}
