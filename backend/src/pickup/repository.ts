import type {
  PickupEventPage,
  PickupLocationUpdate,
  PickupParticipantStatus,
  PickupSession,
  PickupSnapshot,
} from "./domain.js";

export interface PickupRepository {
  createSession(input: { ownerUid: string; waitingSessionId: string | null; idempotencyKey: string; now: string }): Promise<PickupSession>;
  getSession(input: { actorUid: string; sessionId: string; now: string }): Promise<PickupSession>;
  issueInvitation(input: { ownerUid: string; sessionId: string; idempotencyKey: string; tokenHash: string; expiresAt: string; now: string }): Promise<{ invitationId: string; expiresAt: string }>;
  redeemInvitation(input: { guestUid: string; tokenHash: string; displayName: string; now: string }): Promise<{ participantId: string; sessionId: string; status: PickupParticipantStatus; revision: number }>;
  updateParticipantStatus(input: { guestUid: string; participantId: string; status: PickupParticipantStatus; clientEventId: string; now: string }): Promise<{ accepted: boolean; status: PickupParticipantStatus; revision: number }>;
  updateVehicleLocation(input: { ownerUid: string; sessionId: string; location: PickupLocationUpdate; now: string; proximity: ProximityPolicy | null }): Promise<{ revision: number; proximityCandidate: boolean }>;
  updateParticipantLocation(input: { guestUid: string; participantId: string; location: PickupLocationUpdate; now: string; proximity: ProximityPolicy | null }): Promise<{ revision: number; proximityCandidate: boolean }>;
  getSnapshot(input: { actorUid: string; sessionId: string; now: string }): Promise<PickupSnapshot>;
  listEvents(input: { actorUid: string; sessionId: string; afterRevision: number }): Promise<PickupEventPage>;
  closeSession(input: { actorUid: string; sessionId: string; action: "COMPLETED" | "CANCELLED" | "ENDED"; reason: string | null; idempotencyKey: string; now: string; driverOnly: boolean }): Promise<PickupSession>;
  revokeInvitation(input: { ownerUid: string; sessionId: string; idempotencyKey: string; now: string }): Promise<void>;
}

export interface ProximityPolicy {
  maxAccuracyMeters: number;
  maxAgeMs: number;
  distanceMeters: number;
  dwellMs: number;
}
