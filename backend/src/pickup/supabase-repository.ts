import { PickupApiError, canTransitionParticipant, type PickupLocation, type PickupLocationUpdate, type PickupParticipantStatus, type PickupSession, type PickupSnapshot } from "./domain.js";
import type { PickupRepository, ProximityPolicy } from "./repository.js";

type Row = Record<string, unknown>;

export class SupabasePickupRepository implements PickupRepository {
  constructor(private readonly baseUrl: string, private readonly secretKey: string, private readonly timeoutMs: number) {}

  async createSession(input: { ownerUid: string; waitingSessionId: string | null; idempotencyKey: string; now: string }): Promise<PickupSession> {
    const existing = await this.rows("pickup_sessions", `owner_id=eq.${input.ownerUid}&create_idempotency_key=eq.${input.idempotencyKey}`);
    if (existing[0]) return this.session(existing[0]);
    const [created] = await this.write("pickup_sessions", "POST", [{ owner_id: input.ownerUid, waiting_session_id: input.waitingSessionId, location_sharing_consent: true, create_idempotency_key: input.idempotencyKey, created_at: input.now }]);
    return this.session(this.requiredRow(created));
  }

  async getSession(input: { actorUid: string; sessionId: string; now: string }): Promise<PickupSession> {
    const row = await this.authorized(input.actorUid, input.sessionId);
    return this.hydrate(row);
  }

  async issueInvitation(input: { ownerUid: string; sessionId: string; idempotencyKey: string; tokenHash: string; expiresAt: string; now: string }): Promise<{ invitationId: string; expiresAt: string }> {
    const session = await this.ownerActive(input.ownerUid, input.sessionId);
    const replay = await this.rows("pickup_invitations", `session_id=eq.${input.sessionId}&issue_idempotency_key=eq.${input.idempotencyKey}`);
    if (replay[0]) return { invitationId: String(replay[0].id), expiresAt: String(replay[0].expires_at) };
    await this.patch("pickup_invitations", `session_id=eq.${input.sessionId}&revoked_at=is.null`, { revoked_at: input.now });
    await this.patch("pickup_participants", `session_id=eq.${input.sessionId}&access_revoked_at=is.null`, { access_revoked_at: input.now });
    await this.remove("pickup_current_locations", `session_id=eq.${input.sessionId}&actor_type=eq.PARTICIPANT`);
    const [invite] = await this.write("pickup_invitations", "POST", [{ session_id: input.sessionId, token_hash: input.tokenHash, issue_idempotency_key: input.idempotencyKey, expires_at: input.expiresAt, created_at: input.now }]);
    await this.patch("pickup_sessions", `id=eq.${input.sessionId}&revision=eq.${session.revision}`, { revision: Number(session.revision) + 1, proximity_since_at: null, proximity_candidate: false });
    const createdInvite = this.requiredRow(invite);
    return { invitationId: String(createdInvite.id), expiresAt: String(createdInvite.expires_at) };
  }

  async redeemInvitation(input: { guestUid: string; tokenHash: string; displayName: string; now: string }): Promise<{ participantId: string; sessionId: string; status: PickupParticipantStatus; revision: number }> {
    const invitations = await this.rows("pickup_invitations", `token_hash=eq.${input.tokenHash}`);
    const invite = invitations[0];
    if (!invite || invite.revoked_at || Date.parse(String(invite.expires_at)) <= Date.parse(input.now)) throw new PickupApiError(410, "INVITATION_EXPIRED", "초대가 만료되었거나 철회되었습니다");
    const sessionId = String(invite.session_id); const session = await this.active(sessionId);
    const linked = await this.rows("pickup_participants", `session_id=eq.${sessionId}&access_revoked_at=is.null`);
    if (linked[0]) {
      if (linked[0].auth_uid === input.guestUid) return { participantId: String(linked[0].id), sessionId, status: linked[0].status as PickupParticipantStatus, revision: Number(session.revision) };
      throw new PickupApiError(409, "PARTICIPANT_LIMIT_REACHED", "이미 Pickup 참가자가 연결되어 있습니다");
    }
    const [participant] = await this.write("pickup_participants", "POST", [{ session_id: sessionId, invitation_id: invite.id, auth_uid: input.guestUid, display_name: input.displayName, status: "JOINED", updated_at: input.now }]);
    const revision = Number(session.revision) + 1;
    await this.patch("pickup_sessions", `id=eq.${sessionId}&revision=eq.${session.revision}`, { revision });
    return { participantId: String(this.requiredRow(participant).id), sessionId, status: "JOINED", revision };
  }

  async updateParticipantStatus(input: { guestUid: string; participantId: string; status: PickupParticipantStatus; clientEventId: string; now: string }): Promise<{ accepted: boolean; status: PickupParticipantStatus; revision: number }> {
    const participants = await this.rows("pickup_participants", `id=eq.${input.participantId}&auth_uid=eq.${input.guestUid}&access_revoked_at=is.null`);
    const participant = participants[0]; if (!participant) throw this.notFound();
    const session = await this.active(String(participant.session_id));
    const replay = await this.rows("pickup_events", `session_id=eq.${session.id}&client_event_id=eq.${input.clientEventId}`);
    if (replay[0]) return { accepted: true, status: String((replay[0].payload as Row).status) as PickupParticipantStatus, revision: Number(replay[0].revision) };
    const current = participant.status as PickupParticipantStatus;
    if (!canTransitionParticipant(current, input.status)) throw new PickupApiError(409, "INVALID_PARTICIPANT_TRANSITION", "Pickup 상태를 이전 단계로 변경할 수 없습니다");
    const revision = Number(session.revision) + 1;
    await this.patch("pickup_participants", `id=eq.${input.participantId}&status=eq.${current}`, { status: input.status, updated_at: input.now });
    await this.patch("pickup_sessions", `id=eq.${session.id}&revision=eq.${session.revision}`, { revision });
    await this.write("pickup_events", "POST", [{ session_id: session.id, revision, event_type: "PARTICIPANT_STATUS", client_event_id: input.clientEventId, payload: { status: input.status }, occurred_at: input.now }]);
    return { accepted: true, status: input.status, revision };
  }

  updateVehicleLocation(input: { ownerUid: string; sessionId: string; location: PickupLocationUpdate; now: string; proximity: ProximityPolicy | null }) {
    return this.updateLocation("VEHICLE", input.ownerUid, input.sessionId, null, input.location, input.now, input.proximity);
  }

  async updateParticipantLocation(input: { guestUid: string; participantId: string; location: PickupLocationUpdate; now: string; proximity: ProximityPolicy | null }) {
    const participants = await this.rows("pickup_participants", `id=eq.${input.participantId}&auth_uid=eq.${input.guestUid}&access_revoked_at=is.null`);
    if (!participants[0]) throw this.notFound();
    return this.updateLocation("PARTICIPANT", input.guestUid, String(participants[0].session_id), input.participantId, input.location, input.now, input.proximity);
  }

  async getSnapshot(input: { actorUid: string; sessionId: string; now: string }): Promise<PickupSnapshot> {
    const row = await this.authorized(input.actorUid, input.sessionId);
    if (row.status !== "ACTIVE") throw new PickupApiError(410, "PICKUP_CLOSED", "종료된 Pickup 세션입니다");
    const locations = await this.rows("pickup_current_locations", `session_id=eq.${input.sessionId}`);
    return { session: await this.hydrate(row), vehicleLocation: this.location(locations.find((it) => it.actor_type === "VEHICLE")), participantLocation: this.location(locations.find((it) => it.actor_type === "PARTICIPANT")), proximityCandidate: Boolean(row.proximity_candidate), revision: Number(row.revision), occurredAt: input.now };
  }

  async closeSession(input: { actorUid: string; sessionId: string; action: "COMPLETED" | "CANCELLED" | "ENDED"; reason: string | null; idempotencyKey: string; now: string; driverOnly: boolean }): Promise<PickupSession> {
    const replay = await this.rows("pickup_request_idempotency", `actor_uid=eq.${input.actorUid}&idempotency_key=eq.${input.idempotencyKey}&operation=eq.close`);
    if (replay[0]) return replay[0].response as unknown as PickupSession;
    const row = input.driverOnly ? await this.ownerActive(input.actorUid, input.sessionId) : await this.authorized(input.actorUid, input.sessionId);
    if (row.status !== "ACTIVE") throw new PickupApiError(409, "PICKUP_NOT_ACTIVE", "활성 Pickup 세션이 아닙니다");
    const revision = Number(row.revision) + 1;
    const [closed] = await this.patch("pickup_sessions", `id=eq.${input.sessionId}&revision=eq.${row.revision}`, { status: input.action, ended_at: input.now, revision, proximity_since_at: null, proximity_candidate: false });
    const response = await this.hydrate(this.requiredRow(closed));
    await this.write("pickup_request_idempotency", "POST", [{ actor_uid: input.actorUid, idempotency_key: input.idempotencyKey, operation: "close", session_id: input.sessionId, response, created_at: input.now }]);
    return response;
  }

  async revokeInvitation(input: { ownerUid: string; sessionId: string; idempotencyKey: string; now: string }): Promise<void> {
    const operation = "revoke"; const replay = await this.rows("pickup_request_idempotency", `actor_uid=eq.${input.ownerUid}&idempotency_key=eq.${input.idempotencyKey}&operation=eq.${operation}`); if (replay[0]) return;
    const session = await this.ownerActive(input.ownerUid, input.sessionId);
    await this.patch("pickup_invitations", `session_id=eq.${input.sessionId}&revoked_at=is.null`, { revoked_at: input.now });
    await this.patch("pickup_participants", `session_id=eq.${input.sessionId}&access_revoked_at=is.null`, { access_revoked_at: input.now });
    await this.remove("pickup_current_locations", `session_id=eq.${input.sessionId}&actor_type=eq.PARTICIPANT`);
    await this.patch("pickup_sessions", `id=eq.${input.sessionId}&revision=eq.${session.revision}`, { revision: Number(session.revision) + 1, proximity_since_at: null, proximity_candidate: false });
    await this.write("pickup_request_idempotency", "POST", [{ actor_uid: input.ownerUid, idempotency_key: input.idempotencyKey, operation, session_id: input.sessionId, response: {}, created_at: input.now }]);
  }

  private async updateLocation(actorType: "VEHICLE" | "PARTICIPANT", uid: string, sessionId: string, participantId: string | null, location: PickupLocationUpdate, now: string, policy: ProximityPolicy | null): Promise<{ revision: number; proximityCandidate: boolean }> {
    const row = actorType === "VEHICLE" ? await this.ownerActive(uid, sessionId) : await this.active(sessionId);
    await this.write(`pickup_current_locations?on_conflict=session_id,actor_type`, "POST", [{ session_id: sessionId, actor_type: actorType, participant_id: participantId, latitude: location.latitude, longitude: location.longitude, accuracy_meters: location.accuracyMeters, confidence: location.confidence, observed_at: location.observedAt, received_at: now, source: location.source }], "resolution=merge-duplicates,return=representation");
    const locations = await this.rows("pickup_current_locations", `session_id=eq.${sessionId}`);
    const vehicle = this.location(locations.find((it) => it.actor_type === "VEHICLE")); const guest = this.location(locations.find((it) => it.actor_type === "PARTICIPANT"));
    const near = this.near(vehicle, guest, policy, now); const previousSince = row.proximity_since_at ? Date.parse(String(row.proximity_since_at)) : null;
    const since = near ? previousSince ?? Date.parse(now) : null; const candidate = near && since !== null && Date.parse(now) - since >= (policy?.dwellMs ?? Number.POSITIVE_INFINITY);
    const revision = Number(row.revision) + 1;
    await this.patch("pickup_sessions", `id=eq.${sessionId}&revision=eq.${row.revision}`, { revision, proximity_since_at: since === null ? null : new Date(since).toISOString(), proximity_candidate: candidate });
    return { revision, proximityCandidate: candidate };
  }

  private near(a: PickupLocation | null, b: PickupLocation | null, policy: ProximityPolicy | null, now: string): boolean {
    if (!a || !b || !policy || a.accuracyMeters === null || b.accuracyMeters === null) return false;
    const nowMs = Date.parse(now); if ([a, b].some((v) => v.accuracyMeters! > policy.maxAccuracyMeters || nowMs < Date.parse(v.observedAt) || nowMs - Date.parse(v.observedAt) > policy.maxAgeMs)) return false;
    const rad = (v: number) => v * Math.PI / 180; const dLat = rad(b.latitude - a.latitude); const dLng = rad(b.longitude - a.longitude); const h = Math.sin(dLat / 2) ** 2 + Math.cos(rad(a.latitude)) * Math.cos(rad(b.latitude)) * Math.sin(dLng / 2) ** 2;
    return 6_371_000 * 2 * Math.atan2(Math.sqrt(h), Math.sqrt(1 - h)) <= policy.distanceMeters;
  }

  private async authorized(uid: string, sessionId: string): Promise<Row> {
    const sessions = await this.rows("pickup_sessions", `id=eq.${sessionId}`); const row = sessions[0]; if (!row) throw this.notFound();
    if (row.owner_id === uid) return row;
    const participants = await this.rows("pickup_participants", `session_id=eq.${sessionId}&auth_uid=eq.${uid}&access_revoked_at=is.null`); if (!participants[0]) throw this.notFound(); return row;
  }
  private async ownerActive(uid: string, sessionId: string): Promise<Row> { const row = await this.active(sessionId); if (row.owner_id !== uid) throw this.notFound(); return row; }
  private async active(sessionId: string): Promise<Row> { const rows = await this.rows("pickup_sessions", `id=eq.${sessionId}`); if (!rows[0]) throw this.notFound(); if (rows[0].status !== "ACTIVE") throw new PickupApiError(409, "PICKUP_NOT_ACTIVE", "활성 Pickup 세션이 아닙니다"); return rows[0]; }
  private async hydrate(row: Row): Promise<PickupSession> {
    const participants = await this.rows("pickup_participants", `session_id=eq.${row.id}&access_revoked_at=is.null`); const p = participants[0];
    const invitations = await this.rows("pickup_invitations", `session_id=eq.${row.id}&revoked_at=is.null&order=created_at.desc&limit=1`); const invite = invitations[0];
    return { ...this.session(row), participant: p ? { id: String(p.id), displayName: String(p.display_name), status: p.status as PickupParticipantStatus, updatedAt: String(p.updated_at) } : null, invitationExpiresAt: invite ? String(invite.expires_at) : null };
  }
  private session(row: Row): PickupSession { return { id: String(row.id), waitingSessionId: row.waiting_session_id ? String(row.waiting_session_id) : null, status: row.status as PickupSession["status"], locationSharingConsent: Boolean(row.location_sharing_consent), participant: null, invitationExpiresAt: null, revision: Number(row.revision), createdAt: String(row.created_at), endedAt: row.ended_at ? String(row.ended_at) : null }; }
  private location(row?: Row): PickupLocation | null { return row ? { latitude: Number(row.latitude), longitude: Number(row.longitude), accuracyMeters: row.accuracy_meters === null ? null : Number(row.accuracy_meters), confidence: row.confidence === null ? null : Number(row.confidence), observedAt: String(row.observed_at), receivedAt: String(row.received_at), source: row.source as PickupLocation["source"] } : null; }
  private notFound() { return new PickupApiError(404, "PICKUP_NOT_FOUND", "Pickup 세션을 찾을 수 없습니다"); }
  private requiredRow(row: Row | undefined): Row { if (!row) throw new PickupApiError(409, "STORAGE_CONFLICT", "Pickup 데이터가 동시에 변경되었습니다. 다시 시도해 주세요"); return row; }

  private rows(table: string, query: string): Promise<Row[]> { return this.request(`${table}?${query}`, { method: "GET" }); }
  private write(table: string, method: "POST", body: unknown, prefer = "return=representation"): Promise<Row[]> { return this.request(table, { method, body: JSON.stringify(body), headers: { Prefer: prefer } }); }
  private patch(table: string, query: string, body: unknown): Promise<Row[]> { return this.request(`${table}?${query}`, { method: "PATCH", body: JSON.stringify(body), headers: { Prefer: "return=representation" } }); }
  private remove(table: string, query: string): Promise<Row[]> { return this.request(`${table}?${query}`, { method: "DELETE", headers: { Prefer: "return=representation" } }); }
  private async request(path: string, init: RequestInit): Promise<Row[]> {
    const controller = new AbortController(); const timer = setTimeout(() => controller.abort(), this.timeoutMs);
    try {
      const response = await fetch(new URL(`/rest/v1/${path}`, this.baseUrl), { ...init, signal: controller.signal, headers: { apikey: this.secretKey, "User-Agent": "pleos-stay-plus-backend/0.1", "Content-Type": "application/json", ...init.headers } });
      if (!response.ok) throw new PickupApiError(response.status === 409 ? 409 : 502, "SUPABASE_STORAGE_ERROR", "Pickup 저장 처리에 실패했습니다");
      if (response.status === 204) return []; return await response.json() as Row[];
    } catch (error) { if (error instanceof PickupApiError) throw error; throw new PickupApiError(503, "SUPABASE_UNAVAILABLE", "Pickup 저장소에 연결할 수 없습니다"); }
    finally { clearTimeout(timer); }
  }
}
