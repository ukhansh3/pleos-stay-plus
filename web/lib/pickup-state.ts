export function shouldApplyRevision(current: number, incoming: number): boolean { return Number.isInteger(incoming) && incoming > current; }
export function shouldRefreshForEvent(current: number, incoming: unknown): boolean {
  return typeof incoming !== "number" || !Number.isInteger(incoming) || incoming > current;
}
export function shouldRefreshSession(expiresAtSeconds: number | undefined, now = Date.now(), marginMs = 60_000): boolean {
  return expiresAtSeconds === undefined || expiresAtSeconds * 1000 <= now + marginMs;
}
export function shouldUploadPosition(lastUploadAt: number | null, now: number, intervalMs = 5_000): boolean {
  return lastUploadAt === null || now - lastUploadAt >= intervalMs;
}
export function isStale(receivedAt: string | null, now = Date.now(), thresholdMs = 15_000): boolean {
  if (!receivedAt) return true;
  const value = Date.parse(receivedAt);
  return !Number.isFinite(value) || now - value > thresholdMs;
}
