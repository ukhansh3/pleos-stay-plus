import { PickupApiError, type PickupEvent, type PickupEventPage, type PickupLocation, type PickupLocationUpdate, type PickupParticipantStatus, type PickupSession, type PickupSnapshot } from "./domain.js";
import type { PickupRepository, ProximityPolicy } from "./repository.js";

type Row = Record<string, unknown>;

export class SupabasePickupRepository implements PickupRepository {
  constructor(private readonly baseUrl: string, private readonly secretKey: string, private readonly timeoutMs: number) {}

  async createSession(input: { ownerUid: string; waitingSessionId: string | null; idempotencyKey: string; now: string }): Promise<PickupSession> {
    return this.session(await this.command("CREATE_SESSION", input.ownerUid, input));
  }

  async getSession(input: { actorUid: string; sessionId: string; now: string }): Promise<PickupSession> {
    const row = await this.authorized(input.actorUid, input.sessionId);
    return this.hydrate(row);
  }

  async issueInvitation(input: { ownerUid: string; sessionId: string; idempotencyKey: string; tokenHash: string; expiresAt: string; now: string }): Promise<{ invitationId: string; expiresAt: string }> {
    const result = await this.command("ISSUE_INVITATION", input.ownerUid, input);
    return { invitationId: String(result.invitationId), expiresAt: String(result.expiresAt) };
  }

  async redeemInvitation(input: { guestUid: string; tokenHash: string; displayName: string; now: string }): Promise<{ participantId: string; sessionId: string; status: PickupParticipantStatus; revision: number }> {
    const result = await this.command("REDEEM_INVITATION", input.guestUid, input);
    return { participantId: String(result.participantId), sessionId: String(result.sessionId), status: result.status as PickupParticipantStatus, revision: Number(result.revision) };
  }

  async updateParticipantStatus(input: { guestUid: string; participantId: string; status: PickupParticipantStatus; clientEventId: string; now: string }): Promise<{ accepted: boolean; status: PickupParticipantStatus; revision: number }> {
    if (input.status === "CANCELLED") {
      // Resolve only this guest's participant. The existing CLOSE_SESSION RPC
      // rechecks authorization under its lock and atomically triggers revocation
      // and location deletion. Its actor-scoped key also replays a lost reply.
      const [linked] = await this.rows("pickup_participants", `id=eq.${encodeURIComponent(input.participantId)}&auth_uid=eq.${encodeURIComponent(input.guestUid)}`);
      if (!linked) throw this.notFound();
      const closed = await this.closeSession({ actorUid: input.guestUid, sessionId: String(linked.session_id), action: "CANCELLED", reason: "PARTICIPANT_CANCELLED", idempotencyKey: input.clientEventId, now: input.now, driverOnly: false });
      if (closed.id !== String(linked.session_id) || closed.status !== "CANCELLED") throw new PickupApiError(409, "INVALID_PARTICIPANT_TRANSITION", "이미 처리된 요청입니다");
      return { accepted: true, status: "CANCELLED", revision: closed.revision };
    }
    const result = await this.command("UPDATE_PARTICIPANT_STATUS", input.guestUid, input);
    return { accepted: Boolean(result.accepted), status: result.status as PickupParticipantStatus, revision: Number(result.revision) };
  }

  updateVehicleLocation(input: { ownerUid: string; sessionId: string; location: PickupLocationUpdate; now: string; proximity: ProximityPolicy | null }) {
    return this.locationCommand("VEHICLE", input.ownerUid, input.sessionId, null, input.location, input.now, input.proximity);
  }

  async updateParticipantLocation(input: { guestUid: string; participantId: string; location: PickupLocationUpdate; now: string; proximity: ProximityPolicy | null }) {
    return this.locationCommand("PARTICIPANT", input.guestUid, null, input.participantId, input.location, input.now, input.proximity);
  }

  async getSnapshot(input: { actorUid: string; sessionId: string; now: string }): Promise<PickupSnapshot> {
    const row = await this.authorized(input.actorUid, input.sessionId);
    if (row.status !== "ACTIVE") throw new PickupApiError(410, "PICKUP_CLOSED", "종료된 Pickup 세션입니다");
    const locations = await this.rows("pickup_current_locations", `session_id=eq.${input.sessionId}`);
    return { session: await this.hydrate(row), vehicleLocation: this.location(locations.find((it) => it.actor_type === "VEHICLE")), participantLocation: this.location(locations.find((it) => it.actor_type === "PARTICIPANT")), proximityCandidate: Boolean(row.proximity_candidate), revision: Number(row.revision), occurredAt: input.now };
  }

  async listEvents(input: { actorUid: string; sessionId: string; afterRevision: number }): Promise<PickupEventPage> {
    const session = await this.authorized(input.actorUid, input.sessionId);
    const rows = await this.rows("pickup_events", `session_id=eq.${input.sessionId}&revision=gt.${input.afterRevision}&order=revision.asc`);
    return {
      events: rows.map((row): PickupEvent => ({
        id: String(row.id),
        eventType: row.event_type as PickupEvent["eventType"],
        revision: Number(row.revision),
        occurredAt: String(row.occurred_at),
        payload: (row.payload ?? {}) as Record<string, unknown>,
      })),
      latestRevision: Number(session.revision),
    };
  }

  async closeSession(input: { actorUid: string; sessionId: string; action: "COMPLETED" | "CANCELLED" | "ENDED"; reason: string | null; idempotencyKey: string; now: string; driverOnly: boolean }): Promise<PickupSession> {
    return this.session(await this.command("CLOSE_SESSION", input.actorUid, input));
  }

  async revokeInvitation(input: { ownerUid: string; sessionId: string; idempotencyKey: string; now: string }): Promise<void> {
    await this.command("REVOKE_INVITATION", input.ownerUid, input);
  }

  private async locationCommand(actorType: "VEHICLE" | "PARTICIPANT", uid: string, sessionId: string | null, participantId: string | null, location: PickupLocationUpdate, now: string, policy: ProximityPolicy | null): Promise<{ revision: number; proximityCandidate: boolean }> {
    const result = await this.command("UPDATE_LOCATION", uid, { actorType, sessionId, participantId, ...location, now, maxAccuracyMeters: policy?.maxAccuracyMeters ?? null, maxAgeMs: policy?.maxAgeMs ?? null, distanceMeters: policy?.distanceMeters ?? null, dwellMs: policy?.dwellMs ?? null });
    return { revision: Number(result.revision), proximityCandidate: Boolean(result.proximityCandidate) };
  }

  private async authorized(uid: string, sessionId: string): Promise<Row> {
    const sessions = await this.rows("pickup_sessions", `id=eq.${sessionId}`); const row = sessions[0]; if (!row) throw this.notFound();
    if (row.owner_id === uid) return row;
    const participants = await this.rows("pickup_participants", `session_id=eq.${sessionId}&auth_uid=eq.${uid}&access_revoked_at=is.null`); if (!participants[0]) throw this.notFound(); return row;
  }
  private async hydrate(row: Row): Promise<PickupSession> {
    const participants = await this.rows("pickup_participants", `session_id=eq.${row.id}&access_revoked_at=is.null`); const p = participants[0];
    const invitations = await this.rows("pickup_invitations", `session_id=eq.${row.id}&revoked_at=is.null&order=created_at.desc&limit=1`); const invite = invitations[0];
    return { ...this.session(row), participant: p ? { id: String(p.id), displayName: String(p.display_name), status: p.status as PickupParticipantStatus, updatedAt: String(p.updated_at) } : null, invitationExpiresAt: invite ? String(invite.expires_at) : null };
  }
  private session(row: Row): PickupSession { return { id: String(row.id), waitingSessionId: row.waiting_session_id ? String(row.waiting_session_id) : null, status: row.status as PickupSession["status"], locationSharingConsent: Boolean(row.location_sharing_consent), participant: null, invitationExpiresAt: null, revision: Number(row.revision), createdAt: String(row.created_at), endedAt: row.ended_at ? String(row.ended_at) : null }; }
  private location(row?: Row): PickupLocation | null { return row ? { latitude: Number(row.latitude), longitude: Number(row.longitude), accuracyMeters: row.accuracy_meters === null ? null : Number(row.accuracy_meters), confidence: row.confidence === null ? null : Number(row.confidence), observedAt: String(row.observed_at), receivedAt: String(row.received_at), source: row.source as PickupLocation["source"] } : null; }
  private notFound() { return new PickupApiError(404, "PICKUP_NOT_FOUND", "Pickup 세션을 찾을 수 없습니다"); }

  private command(action: string, actorUid: string, input: object): Promise<Row> {
    return this.request("rpc/pickup_command", { method: "POST", body: JSON.stringify({ p_action: action, p_actor_uid: actorUid, p_input: input }) });
  }
  private rows(table: string, query: string): Promise<Row[]> { return this.request(`${table}?${query}`, { method: "GET" }); }
  private async request<T>(path: string, init: RequestInit): Promise<T> {
    const controller = new AbortController(); const timer = setTimeout(() => controller.abort(), this.timeoutMs);
    try {
      const response = await fetch(new URL(`/rest/v1/${path}`, this.baseUrl), { ...init, signal: controller.signal, headers: { apikey: this.secretKey, "User-Agent": "pleos-stay-plus-backend/0.1", "Content-Type": "application/json", ...init.headers } });
      if (!response.ok) {
        const body = await response.json().catch(() => ({})) as { message?: string; code?: string };
        throw this.storageError(body.message, response.status, body.code);
      }
      if (response.status === 204) return undefined as T;
      return await response.json() as T;
    } catch (error) { if (error instanceof PickupApiError) throw error; throw new PickupApiError(503, "SUPABASE_UNAVAILABLE", "Pickup 저장소에 연결할 수 없습니다"); }
    finally { clearTimeout(timer); }
  }

  private storageError(message: string | undefined, status: number, code: string | undefined): PickupApiError {
    const known: Record<string, [number, string, string]> = {
      PICKUP_NOT_FOUND: [404, "PICKUP_NOT_FOUND", "Pickup 세션을 찾을 수 없습니다"],
      PICKUP_NOT_ACTIVE: [409, "PICKUP_NOT_ACTIVE", "활성 Pickup 세션이 아닙니다"],
      INVITATION_EXPIRED: [410, "INVITATION_EXPIRED", "초대가 만료되었거나 철회되었습니다"],
      PARTICIPANT_LIMIT_REACHED: [409, "PARTICIPANT_LIMIT_REACHED", "이미 Pickup 참가자가 연결되어 있습니다"],
      INVALID_PARTICIPANT_TRANSITION: [409, "INVALID_PARTICIPANT_TRANSITION", "Pickup 상태를 이전 단계로 변경할 수 없습니다"],
      INVALID_LOCATION_ACTOR: [400, "INVALID_LOCATION_ACTOR", "위치 주체가 올바르지 않습니다"],
      INVALID_COMMAND: [400, "INVALID_COMMAND", "Pickup 명령이 올바르지 않습니다"],
      UNKNOWN_PICKUP_COMMAND: [400, "UNKNOWN_PICKUP_COMMAND", "Pickup 명령이 올바르지 않습니다"],
    };
    const mapped = message ? known[message] : undefined;
    if (mapped) return new PickupApiError(...mapped);
    if (status === 409 || code === "23505" || code === "40001") return new PickupApiError(409, "STORAGE_CONFLICT", "Pickup 데이터가 동시에 변경되었습니다. 다시 시도해 주세요");
    return new PickupApiError(502, "SUPABASE_STORAGE_ERROR", "Pickup 저장 처리에 실패했습니다");
  }
}
