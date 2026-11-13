import type { ParticipantStatus, PickupLocation } from "./api";
import { isStale } from "./pickup-state";

export function validPickupCoordinates(position: Pick<PickupLocation, "latitude" | "longitude">): boolean {
  return Number.isFinite(position.latitude) && position.latitude >= -90 && position.latitude <= 90
    && Number.isFinite(position.longitude) && position.longitude >= -180 && position.longitude <= 180;
}
export function locationIsStale(location: PickupLocation | null, now: number): boolean {
  return !location || !validPickupCoordinates(location) || isStale(location.observedAt, now) || isStale(location.receivedAt, now);
}
export function locationAge(location: PickupLocation | null, now: number): string {
  if (!location) return "아직 위치가 없어요";
  const observed = Date.parse(location.observedAt);
  if (!Number.isFinite(observed)) return "갱신 시각 확인 필요";
  const seconds = Math.max(0, Math.floor((now - observed) / 1000));
  if (seconds < 5) return "방금 갱신";
  if (seconds < 60) return `${seconds}초 전 갱신`;
  if (seconds < 3600) return `${Math.floor(seconds / 60)}분 전 갱신`;
  return "오래된 위치";
}
export const participantLabels: Record<ParticipantStatus, string> = {
  JOINED: "참가 완료", DEPARTED: "이동 중", NEARBY: "곧 도착", ARRIVED: "도착", CANCELLED: "취소됨",
};
export function canSetParticipantStatus(current: ParticipantStatus, next: "DEPARTED" | "NEARBY"): boolean {
  return next === "DEPARTED" ? current === "JOINED" : current === "JOINED" || current === "DEPARTED";
}
export function pickupErrorMessage(error: unknown): string {
  const code = error && typeof error === "object" && "code" in error ? String(error.code) : "";
  if (/INVITATION|PICKUP_CLOSED|PICKUP_NOT_FOUND/.test(code)) return "초대가 만료되었거나 연결이 종료됐어요. 차량에서 새 링크를 받아 주세요.";
  if (code === "PARTICIPANT_LIMIT_REACHED") return "이미 다른 참가자가 연결되어 있어요. 운전자에게 새 초대를 요청해 주세요.";
  if (code === "anonymous_provider_disabled") return "지금은 참가 연결을 사용할 수 없어요. 잠시 후 다시 시도해 주세요.";
  if (/PARTICIPANT.*(FULL|EXISTS)|ALREADY.*(BOUND|REDEEMED)/.test(code)) return "이미 다른 참가자가 연결되어 있어요. 운전자에게 새 초대를 요청해 주세요.";
  if (code === "INVALID_REQUEST") return "입력 내용을 확인하고 다시 시도해 주세요.";
  if (code === "CONSENT_REQUIRED") return "Pickup 참가 동의를 확인해 주세요.";
  if (/RATE|LIMIT/.test(code)) return "요청이 많아요. 잠시 후 다시 시도해 주세요.";
  if (/AUTH|TOKEN|JWT/.test(code)) return "인증을 확인하지 못했어요. 다시 시도해 주세요.";
  if (/STATUS|TRANSITION/.test(code)) return "상태가 변경됐어요. 최신 상태를 확인하고 다시 시도해 주세요.";
  return "연결하지 못했어요. 네트워크를 확인하고 다시 시도해 주세요.";
}
export type PickupBinding = { participantId: string; sessionId: string; invitationFingerprint?: string };
export function parsePickupBinding(raw: string | null): PickupBinding | null {
  if (!raw) return null;
  try {
    const value = JSON.parse(raw);
    const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
    return uuid.test(value?.participantId) && uuid.test(value?.sessionId)
      ? { participantId: value.participantId, sessionId: value.sessionId,
          ...(typeof value.invitationFingerprint === "string" && /^[a-f0-9]{64}$/.test(value.invitationFingerprint) ? { invitationFingerprint: value.invitationFingerprint } : {}) }
      : null;
  } catch { return null; }
}
export function bindingMatchesInvitation(binding: PickupBinding, fingerprint: string | null): boolean {
  return fingerprint === null || binding.invitationFingerprint === fingerprint;
}
/** Coordinate distance, not a road route or an arrival estimate. Hidden when either sample is stale. */
export function pickupStraightLineDistance(vehicle: PickupLocation | null, participant: PickupLocation | null, now: number): string | null {
  if (!vehicle || !participant || locationIsStale(vehicle, now) || locationIsStale(participant, now)) return null;
  const radians = (degrees: number) => degrees * Math.PI / 180;
  const deltaLat = radians(participant.latitude - vehicle.latitude);
  const deltaLng = radians(participant.longitude - vehicle.longitude);
  const a = Math.sin(deltaLat / 2) ** 2 + Math.cos(radians(vehicle.latitude)) * Math.cos(radians(participant.latitude)) * Math.sin(deltaLng / 2) ** 2;
  const meters = 6_371_000 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(Math.max(0, 1 - a)));
  return meters < 1000 ? `약 ${Math.round(meters / 10) * 10}m` : `약 ${(meters / 1000).toFixed(1)}km`;
}