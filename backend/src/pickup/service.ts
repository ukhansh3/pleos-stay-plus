import { createHash, createHmac } from "node:crypto";
import type { PickupEventPage, PickupLocationUpdate, PickupParticipantStatus, PickupSession, PickupSnapshot } from "./domain.js";
import { PickupApiError } from "./domain.js";
import type { PickupRepository, ProximityPolicy } from "./repository.js";

export interface PickupServiceOptions {
  publicWebBaseUrl: string;
  invitationHmacSecret: string;
  invitationTtlMs?: number;
  proximityPolicy?: ProximityPolicy | null;
  now?: () => Date;
}

export class PickupService {
  private readonly invitationTtlMs: number;
  private readonly proximityPolicy: ProximityPolicy | null;
  private readonly now: () => Date;

  constructor(private readonly repository: PickupRepository, private readonly options: PickupServiceOptions) {
    if (options.invitationHmacSecret.length < 32) throw new Error("PICKUP_INVITATION_HMAC_SECRET must be at least 32 characters");
    this.invitationTtlMs = options.invitationTtlMs ?? 60 * 60 * 1000;
    this.proximityPolicy = options.proximityPolicy ?? null;
    this.now = options.now ?? (() => new Date());
  }

  createSession(ownerUid: string, waitingSessionId: string | null, idempotencyKey: string): Promise<PickupSession> {
    return this.repository.createSession({ ownerUid, waitingSessionId, idempotencyKey, now: this.isoNow() });
  }

  getSession(actorUid: string, sessionId: string): Promise<PickupSession> {
    return this.repository.getSession({ actorUid, sessionId, now: this.isoNow() });
  }

  async issueInvitation(ownerUid: string, sessionId: string, idempotencyKey: string): Promise<{ invitationId: string; shareUrl: string; expiresAt: string }> {
    const token = createHmac("sha256", this.options.invitationHmacSecret).update(`${sessionId}:${idempotencyKey}`).digest("base64url");
    const expiresAt = new Date(this.now().getTime() + this.invitationTtlMs).toISOString();
    const issued = await this.repository.issueInvitation({ ownerUid, sessionId, idempotencyKey, tokenHash: hashToken(token), expiresAt, now: this.isoNow() });
    const shareUrl = new URL(`/share/${encodeURIComponent(token)}`, this.options.publicWebBaseUrl).toString();
    return { invitationId: issued.invitationId, shareUrl, expiresAt: issued.expiresAt };
  }

  redeemInvitation(guestUid: string, token: string, consent: boolean, displayName: string | undefined) {
    if (!consent) throw new PickupApiError(400, "CONSENT_REQUIRED", "Pickup 정보 제공 동의가 필요합니다");
    const normalizedName = displayName?.trim() || "픽업 대상";
    if (normalizedName.length > 60) throw new PickupApiError(400, "INVALID_DISPLAY_NAME", "별명은 60자 이하여야 합니다");
    return this.repository.redeemInvitation({ guestUid, tokenHash: hashToken(token), displayName: normalizedName, now: this.isoNow() });
  }

  updateParticipantStatus(guestUid: string, participantId: string, status: PickupParticipantStatus, clientEventId: string) {
    return this.repository.updateParticipantStatus({ guestUid, participantId, status, clientEventId, now: this.isoNow() });
  }

  updateVehicleLocation(ownerUid: string, sessionId: string, location: PickupLocationUpdate) {
    return this.repository.updateVehicleLocation({ ownerUid, sessionId, location, now: this.isoNow(), proximity: this.proximityPolicy });
  }

  updateParticipantLocation(guestUid: string, participantId: string, location: PickupLocationUpdate) {
    return this.repository.updateParticipantLocation({ guestUid, participantId, location, now: this.isoNow(), proximity: this.proximityPolicy });
  }

  updateSimulatedVehicleLocation(ownerUid: string, sessionId: string, location: PickupLocationUpdate, simulatedNow: string) {
    return this.repository.updateVehicleLocation({ ownerUid, sessionId, location, now: simulatedNow, proximity: this.proximityPolicy });
  }

  updateSimulatedParticipantLocation(guestUid: string, participantId: string, location: PickupLocationUpdate, simulatedNow: string) {
    return this.repository.updateParticipantLocation({ guestUid, participantId, location, now: simulatedNow, proximity: this.proximityPolicy });
  }

  getSnapshot(actorUid: string, sessionId: string): Promise<PickupSnapshot> {
    return this.repository.getSnapshot({ actorUid, sessionId, now: this.isoNow() });
  }

  listEvents(actorUid: string, sessionId: string, afterRevision: number): Promise<PickupEventPage> {
    return this.repository.listEvents({ actorUid, sessionId, afterRevision });
  }

  close(actorUid: string, sessionId: string, action: "COMPLETED" | "CANCELLED" | "ENDED", idempotencyKey: string, reason: string | null, driverOnly: boolean) {
    return this.repository.closeSession({ actorUid, sessionId, action, reason, idempotencyKey, now: this.isoNow(), driverOnly });
  }

  revoke(ownerUid: string, sessionId: string, idempotencyKey: string): Promise<void> {
    return this.repository.revokeInvitation({ ownerUid, sessionId, idempotencyKey, now: this.isoNow() });
  }

  private isoNow(): string { return this.now().toISOString(); }
}

export function hashToken(token: string): string {
  if (token.length < 32) throw new PickupApiError(410, "INVITATION_INVALID", "초대가 만료되었거나 철회되었습니다");
  return createHash("sha256").update(token).digest("hex");
}
