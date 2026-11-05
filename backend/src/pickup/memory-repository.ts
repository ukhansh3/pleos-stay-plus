import { randomUUID } from "node:crypto";
import { PickupApiError, canTransitionParticipant, type PickupEvent, type PickupEventPage, type PickupLocation, type PickupLocationUpdate, type PickupParticipant, type PickupParticipantStatus, type PickupSession, type PickupSnapshot } from "./domain.js";
import type { PickupRepository, ProximityPolicy } from "./repository.js";

interface InvitationRecord { id: string; tokenHash: string; expiresAt: string; revoked: boolean; idempotencyKey: string }
interface ParticipantRecord extends PickupParticipant { guestUid: string; revoked: boolean; events: Map<string, { status: PickupParticipantStatus; revision: number }> }
interface SessionRecord {
  ownerUid: string; session: PickupSession; invitation: InvitationRecord | null; participant: ParticipantRecord | null;
  vehicleLocation: PickupLocation | null; participantLocation: PickupLocation | null;
  proximityCandidate: boolean; proximitySinceMs: number | null; createIdempotencyKey: string;
  closeKeys: Map<string, PickupSession>; revokeKeys: Set<string>; domainEvents: PickupEvent[];
}

const clone = <T>(value: T): T => structuredClone(value);
function active(record: SessionRecord): void { if (record.session.status !== "ACTIVE") throw new PickupApiError(409, "PICKUP_NOT_ACTIVE", "활성 Pickup 세션이 아닙니다"); }
function owner(record: SessionRecord, uid: string): void { if (record.ownerUid !== uid) throw new PickupApiError(404, "PICKUP_NOT_FOUND", "Pickup 세션을 찾을 수 없습니다"); }
function participant(record: SessionRecord, uid: string): ParticipantRecord {
  if (!record.participant || record.participant.guestUid !== uid || record.participant.revoked) throw new PickupApiError(404, "PICKUP_NOT_FOUND", "Pickup 세션을 찾을 수 없습니다");
  return record.participant;
}
function distanceMeters(a: PickupLocation, b: PickupLocation): number {
  const rad = (value: number) => value * Math.PI / 180;
  const dLat = rad(b.latitude - a.latitude); const dLng = rad(b.longitude - a.longitude);
  const x = Math.sin(dLat / 2) ** 2 + Math.cos(rad(a.latitude)) * Math.cos(rad(b.latitude)) * Math.sin(dLng / 2) ** 2;
  return 6_371_000 * 2 * Math.atan2(Math.sqrt(x), Math.sqrt(1 - x));
}
function evaluateProximity(record: SessionRecord, policy: ProximityPolicy | null, now: string): boolean {
  const vehicle = record.vehicleLocation; const guest = record.participantLocation;
  if (!policy || !vehicle || !guest || vehicle.accuracyMeters === null || guest.accuracyMeters === null) {
    record.proximitySinceMs = null; record.proximityCandidate = false; return false;
  }
  const nowMs = Date.parse(now);
  const valid = [vehicle, guest].every((location) => location.accuracyMeters !== null
    && location.accuracyMeters <= policy.maxAccuracyMeters
    && Number.isFinite(Date.parse(location.observedAt))
    && nowMs >= Date.parse(location.observedAt)
    && nowMs - Date.parse(location.observedAt) <= policy.maxAgeMs);
  if (!valid || distanceMeters(vehicle, guest) > policy.distanceMeters) {
    record.proximitySinceMs = null; record.proximityCandidate = false; return false;
  }
  record.proximitySinceMs ??= nowMs;
  record.proximityCandidate = nowMs - record.proximitySinceMs >= policy.dwellMs;
  return record.proximityCandidate;
}

export class InMemoryPickupRepository implements PickupRepository {
  private readonly sessions = new Map<string, SessionRecord>();
  private readonly invitationSessions = new Map<string, string>();
  private readonly participantSessions = new Map<string, string>();

  async createSession(input: { ownerUid: string; waitingSessionId: string | null; idempotencyKey: string; now: string }): Promise<PickupSession> {
    const replay = [...this.sessions.values()].find((record) => record.ownerUid === input.ownerUid && record.createIdempotencyKey === input.idempotencyKey);
    if (replay) return clone(replay.session);
    const id = randomUUID();
    const session: PickupSession = { id, waitingSessionId: input.waitingSessionId, status: "ACTIVE", locationSharingConsent: true, participant: null, invitationExpiresAt: null, revision: 1, createdAt: input.now, endedAt: null };
    this.sessions.set(id, { ownerUid: input.ownerUid, session, invitation: null, participant: null, vehicleLocation: null, participantLocation: null, proximityCandidate: false, proximitySinceMs: null, createIdempotencyKey: input.idempotencyKey, closeKeys: new Map(), revokeKeys: new Set(), domainEvents: [] });
    return clone(session);
  }

  async getSession(input: { actorUid: string; sessionId: string }): Promise<PickupSession> {
    const record = this.required(input.sessionId); if (record.ownerUid !== input.actorUid) participant(record, input.actorUid); return clone(record.session);
  }

  async issueInvitation(input: { ownerUid: string; sessionId: string; idempotencyKey: string; tokenHash: string; expiresAt: string }): Promise<{ invitationId: string; expiresAt: string }> {
    const record = this.required(input.sessionId); owner(record, input.ownerUid); active(record);
    if (record.invitation?.idempotencyKey === input.idempotencyKey) return { invitationId: record.invitation.id, expiresAt: record.invitation.expiresAt };
    if (record.invitation) record.invitation.revoked = true;
    if (record.participant) record.participant.revoked = true;
    record.participant = null; record.participantLocation = null; record.proximityCandidate = false; record.proximitySinceMs = null;
    const invitation = { id: randomUUID(), tokenHash: input.tokenHash, expiresAt: input.expiresAt, revoked: false, idempotencyKey: input.idempotencyKey };
    record.invitation = invitation; record.session.participant = null; record.session.invitationExpiresAt = input.expiresAt; record.session.revision += 1;
    this.invitationSessions.set(input.tokenHash, record.session.id);
    return { invitationId: invitation.id, expiresAt: invitation.expiresAt };
  }

  async redeemInvitation(input: { guestUid: string; tokenHash: string; displayName: string; now: string }): Promise<{ participantId: string; sessionId: string; status: PickupParticipantStatus; revision: number }> {
    const sessionId = this.invitationSessions.get(input.tokenHash);
    if (!sessionId) throw new PickupApiError(410, "INVITATION_INVALID", "초대가 만료되었거나 철회되었습니다");
    const record = this.required(sessionId); active(record); const invite = record.invitation;
    if (!invite || invite.tokenHash !== input.tokenHash || invite.revoked || Date.parse(invite.expiresAt) <= Date.parse(input.now)) throw new PickupApiError(410, "INVITATION_EXPIRED", "초대가 만료되었거나 철회되었습니다");
    if (record.participant) {
      if (record.participant.guestUid === input.guestUid && !record.participant.revoked) return { participantId: record.participant.id, sessionId, status: record.participant.status, revision: record.session.revision };
      throw new PickupApiError(409, "PARTICIPANT_LIMIT_REACHED", "이미 Pickup 참가자가 연결되어 있습니다");
    }
    const joined: ParticipantRecord = { id: randomUUID(), displayName: input.displayName, status: "JOINED", updatedAt: input.now, guestUid: input.guestUid, revoked: false, events: new Map() };
    record.participant = joined; record.session.participant = this.publicParticipant(joined); record.session.revision += 1; this.participantSessions.set(joined.id, sessionId);
    return { participantId: joined.id, sessionId, status: joined.status, revision: record.session.revision };
  }

  async updateParticipantStatus(input: { guestUid: string; participantId: string; status: PickupParticipantStatus; clientEventId: string; now: string }): Promise<{ accepted: boolean; status: PickupParticipantStatus; revision: number }> {
    const record = this.participantRecord(input.participantId); active(record); const linked = participant(record, input.guestUid);
    const replay = linked.events.get(input.clientEventId);
    if (replay) return { accepted: true, status: replay.status, revision: replay.revision };
    if (linked.status === input.status) return { accepted: true, status: linked.status, revision: record.session.revision };
    if (!canTransitionParticipant(linked.status, input.status)) throw new PickupApiError(409, "INVALID_PARTICIPANT_TRANSITION", "Pickup 상태를 이전 단계로 변경할 수 없습니다");
    linked.status = input.status; linked.updatedAt = input.now; record.session.participant = this.publicParticipant(linked); record.session.revision += 1; linked.events.set(input.clientEventId, { status: input.status, revision: record.session.revision });
    this.recordEvent(record, "PARTICIPANT_STATUS", input.now, { status: input.status });
    return { accepted: true, status: linked.status, revision: record.session.revision };
  }

  async updateVehicleLocation(input: { ownerUid: string; sessionId: string; location: PickupLocationUpdate; now: string; proximity: ProximityPolicy | null }): Promise<{ revision: number; proximityCandidate: boolean }> {
    const record = this.required(input.sessionId); owner(record, input.ownerUid); active(record);
    if (record.vehicleLocation && Date.parse(record.vehicleLocation.observedAt) >= Date.parse(input.location.observedAt)) return { revision: record.session.revision, proximityCandidate: record.proximityCandidate };
    record.vehicleLocation = { ...input.location, receivedAt: input.now }; record.session.revision += 1;
    const wasCandidate = record.proximityCandidate; const proximityCandidate = evaluateProximity(record, input.proximity, input.now);
    const participantId = record.participant?.id;
    if (!wasCandidate && proximityCandidate && participantId && !record.domainEvents.some((event) => event.eventType === "PROXIMITY_CANDIDATE" && event.payload.participantId === participantId)) this.recordEvent(record, "PROXIMITY_CANDIDATE", input.now, { proximityCandidate: true, participantId });
    return { revision: record.session.revision, proximityCandidate };
  }

  async updateParticipantLocation(input: { guestUid: string; participantId: string; location: PickupLocationUpdate; now: string; proximity: ProximityPolicy | null }): Promise<{ revision: number; proximityCandidate: boolean }> {
    const record = this.participantRecord(input.participantId); active(record); participant(record, input.guestUid);
    if (record.participantLocation && Date.parse(record.participantLocation.observedAt) >= Date.parse(input.location.observedAt)) return { revision: record.session.revision, proximityCandidate: record.proximityCandidate };
    record.participantLocation = { ...input.location, receivedAt: input.now }; record.session.revision += 1;
    const wasCandidate = record.proximityCandidate; const proximityCandidate = evaluateProximity(record, input.proximity, input.now);
    const participantId = record.participant?.id;
    if (!wasCandidate && proximityCandidate && participantId && !record.domainEvents.some((event) => event.eventType === "PROXIMITY_CANDIDATE" && event.payload.participantId === participantId)) this.recordEvent(record, "PROXIMITY_CANDIDATE", input.now, { proximityCandidate: true, participantId });
    return { revision: record.session.revision, proximityCandidate };
  }

  async getSnapshot(input: { actorUid: string; sessionId: string; now: string }): Promise<PickupSnapshot> {
    const record = this.required(input.sessionId); if (record.ownerUid !== input.actorUid) participant(record, input.actorUid);
    if (record.session.status !== "ACTIVE") throw new PickupApiError(410, "PICKUP_CLOSED", "종료된 Pickup 세션입니다");
    return clone({ session: record.session, vehicleLocation: record.vehicleLocation, participantLocation: record.participantLocation, proximityCandidate: record.proximityCandidate, revision: record.session.revision, occurredAt: input.now });
  }

  async listEvents(input: { actorUid: string; sessionId: string; afterRevision: number }): Promise<PickupEventPage> {
    const record = this.required(input.sessionId); if (record.ownerUid !== input.actorUid) participant(record, input.actorUid);
    return clone({ events: record.domainEvents.filter((event) => event.revision > input.afterRevision), latestRevision: record.session.revision });
  }

  async closeSession(input: { actorUid: string; sessionId: string; action: "COMPLETED" | "CANCELLED" | "ENDED"; reason: string | null; idempotencyKey: string; now: string; driverOnly: boolean }): Promise<PickupSession> {
    const record = this.required(input.sessionId); const replay = record.closeKeys.get(input.idempotencyKey); if (replay) return clone(replay);
    if (input.driverOnly) owner(record, input.actorUid); else if (record.ownerUid !== input.actorUid) participant(record, input.actorUid);
    active(record); record.session.status = input.action; record.session.endedAt = input.now; record.session.invitationExpiresAt = null; record.session.revision += 1;
    this.recordEvent(record, `SESSION_${input.action}` as "SESSION_COMPLETED" | "SESSION_CANCELLED" | "SESSION_ENDED", input.now, input.reason ? { reason: input.reason } : {});
    if (record.invitation) record.invitation.revoked = true; if (record.participant) record.participant.revoked = true;
    record.vehicleLocation = null; record.participantLocation = null; record.proximityCandidate = false; record.proximitySinceMs = null;
    const response = clone(record.session); record.closeKeys.set(input.idempotencyKey, response); return response;
  }

  async revokeInvitation(input: { ownerUid: string; sessionId: string; idempotencyKey: string }): Promise<void> {
    const record = this.required(input.sessionId); owner(record, input.ownerUid); active(record); if (record.revokeKeys.has(input.idempotencyKey)) return;
    if (record.invitation) record.invitation.revoked = true; if (record.participant) record.participant.revoked = true;
    record.participant = null; record.participantLocation = null; record.session.participant = null; record.session.invitationExpiresAt = null; record.session.revision += 1; record.revokeKeys.add(input.idempotencyKey);
  }

  private required(sessionId: string): SessionRecord { const record = this.sessions.get(sessionId); if (!record) throw new PickupApiError(404, "PICKUP_NOT_FOUND", "Pickup 세션을 찾을 수 없습니다"); return record; }
  private participantRecord(participantId: string): SessionRecord { const sessionId = this.participantSessions.get(participantId); if (!sessionId) throw new PickupApiError(404, "PICKUP_NOT_FOUND", "Pickup 참가자를 찾을 수 없습니다"); return this.required(sessionId); }
  private publicParticipant(value: ParticipantRecord): PickupParticipant { return { id: value.id, displayName: value.displayName, status: value.status, updatedAt: value.updatedAt }; }
  private recordEvent(record: SessionRecord, eventType: PickupEvent["eventType"], occurredAt: string, payload: Record<string, unknown>): void {
    record.domainEvents.push({ id: randomUUID(), eventType, revision: record.session.revision, occurredAt, payload });
  }
}
