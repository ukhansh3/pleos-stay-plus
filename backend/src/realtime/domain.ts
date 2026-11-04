export type DeviceKind = "WINDOWS" | "VEHICLE";
export interface Device { id: string; uid: string; kind: DeviceKind; name: string }
export interface Connection { id: string; pcDeviceId: string; vehicleDeviceId: string; revokedAt: number | null }
export class RealtimeError extends Error {
  constructor(readonly statusCode: number, readonly code: string) { super(code); }
}
export function requireCondition(value: unknown, status: number, code: string): asserts value {
  if (!value) throw new RealtimeError(status, code);
}
