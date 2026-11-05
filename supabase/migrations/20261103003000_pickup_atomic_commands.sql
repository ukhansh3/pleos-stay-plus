-- Atomic server-only Pickup commands. Each RPC call is one PostgreSQL
-- transaction and locks the session row before mutating related records.
alter table public.pickup_events
  drop constraint if exists pickup_event_type_check;
alter table public.pickup_events
  add constraint pickup_event_type_check check (
    event_type in ('PARTICIPANT_STATUS','PROXIMITY_CANDIDATE','SESSION_COMPLETED','SESSION_CANCELLED','SESSION_ENDED')
  );
create unique index if not exists pickup_events_one_proximity_candidate_idx
  on public.pickup_events(session_id, (payload->>'participantId'))
  where event_type = 'PROXIMITY_CANDIDATE';

create or replace function public.pickup_command(
  p_action text,
  p_actor_uid uuid,
  p_input jsonb
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  s public.pickup_sessions%rowtype;
  i public.pickup_invitations%rowtype;
  p public.pickup_participants%rowtype;
  e public.pickup_events%rowtype;
  v public.pickup_current_locations%rowtype;
  g public.pickup_current_locations%rowtype;
  l public.pickup_current_locations%rowtype;
  result jsonb;
  next_revision integer;
  now_at timestamptz := (p_input->>'now')::timestamptz;
  requested_status text;
  requested_actor_type text;
  participant_uuid uuid;
  valid_near boolean := false;
  distance_meters double precision;
  since_at timestamptz;
  candidate boolean := false;
begin
  if p_actor_uid is null or now_at is null then
    raise sqlstate 'PT400' using message = 'INVALID_COMMAND';
  end if;

  if p_action = 'CREATE_SESSION' then
    insert into public.pickup_sessions(owner_id, waiting_session_id, location_sharing_consent, create_idempotency_key, created_at)
    values (p_actor_uid, nullif(p_input->>'waitingSessionId', ''), true, (p_input->>'idempotencyKey')::uuid, now_at)
    on conflict (owner_id, create_idempotency_key) do nothing;

    select * into strict s from public.pickup_sessions
    where owner_id = p_actor_uid and create_idempotency_key = (p_input->>'idempotencyKey')::uuid;
    return to_jsonb(s);
  end if;

  if p_action = 'ISSUE_INVITATION' then
    select * into s from public.pickup_sessions where id = (p_input->>'sessionId')::uuid for update;
    if not found or s.owner_id <> p_actor_uid then raise sqlstate 'PT404' using message = 'PICKUP_NOT_FOUND'; end if;
    if s.status <> 'ACTIVE' then raise sqlstate 'PT409' using message = 'PICKUP_NOT_ACTIVE'; end if;

    select * into i from public.pickup_invitations
    where session_id = s.id and issue_idempotency_key = (p_input->>'idempotencyKey')::uuid;
    if found then return jsonb_build_object('invitationId', i.id, 'expiresAt', i.expires_at, 'revision', s.revision); end if;

    update public.pickup_invitations set revoked_at = coalesce(revoked_at, now_at)
    where session_id = s.id and revoked_at is null;
    update public.pickup_participants set access_revoked_at = coalesce(access_revoked_at, now_at), updated_at = now_at
    where session_id = s.id and access_revoked_at is null;
    delete from public.pickup_current_locations where session_id = s.id and actor_type = 'PARTICIPANT';

    insert into public.pickup_invitations(session_id, token_hash, issue_idempotency_key, expires_at, created_at)
    values (s.id, p_input->>'tokenHash', (p_input->>'idempotencyKey')::uuid, (p_input->>'expiresAt')::timestamptz, now_at)
    returning * into i;
    update public.pickup_sessions set revision = revision + 1, proximity_since_at = null, proximity_candidate = false where id = s.id returning revision into next_revision;
    return jsonb_build_object('invitationId', i.id, 'expiresAt', i.expires_at, 'revision', next_revision);
  end if;

  if p_action = 'REDEEM_INVITATION' then
    select * into i from public.pickup_invitations where token_hash = p_input->>'tokenHash' for update;
    if not found or i.revoked_at is not null or i.expires_at <= now_at then raise sqlstate 'PT410' using message = 'INVITATION_EXPIRED'; end if;
    select * into s from public.pickup_sessions where id = i.session_id for update;
    if s.status <> 'ACTIVE' then raise sqlstate 'PT409' using message = 'PICKUP_NOT_ACTIVE'; end if;
    select * into p from public.pickup_participants where session_id = s.id and access_revoked_at is null for update;
    if found then
      if p.auth_uid = p_actor_uid then
        return jsonb_build_object('participantId', p.id, 'sessionId', s.id, 'status', p.status, 'revision', s.revision);
      end if;
      raise sqlstate 'PT409' using message = 'PARTICIPANT_LIMIT_REACHED';
    end if;
    insert into public.pickup_participants(session_id, invitation_id, auth_uid, display_name, status, updated_at)
    values (s.id, i.id, p_actor_uid, p_input->>'displayName', 'JOINED', now_at) returning * into p;
    update public.pickup_sessions set revision = revision + 1 where id = s.id returning revision into next_revision;
    return jsonb_build_object('participantId', p.id, 'sessionId', s.id, 'status', p.status, 'revision', next_revision);
  end if;

  if p_action = 'UPDATE_PARTICIPANT_STATUS' then
    select * into p from public.pickup_participants
    where id = (p_input->>'participantId')::uuid and auth_uid = p_actor_uid and access_revoked_at is null for update;
    if not found then raise sqlstate 'PT404' using message = 'PICKUP_NOT_FOUND'; end if;
    select * into s from public.pickup_sessions where id = p.session_id for update;
    if s.status <> 'ACTIVE' then raise sqlstate 'PT409' using message = 'PICKUP_NOT_ACTIVE'; end if;
    select * into e from public.pickup_events where session_id = s.id and client_event_id = (p_input->>'clientEventId')::uuid;
    if found then return jsonb_build_object('accepted', true, 'status', e.payload->>'status', 'revision', e.revision); end if;
    requested_status := p_input->>'status';
    if p.status = requested_status then
      return jsonb_build_object('accepted', true, 'status', p.status, 'revision', s.revision);
    end if;
    if not (
      (p.status = 'JOINED' and requested_status in ('DEPARTED','NEARBY','CANCELLED')) or
      (p.status = 'DEPARTED' and requested_status in ('NEARBY','ARRIVED','CANCELLED')) or
      (p.status = 'NEARBY' and requested_status in ('ARRIVED','CANCELLED'))
    ) then raise sqlstate 'PT409' using message = 'INVALID_PARTICIPANT_TRANSITION'; end if;
    update public.pickup_participants set status = requested_status, updated_at = now_at where id = p.id;
    update public.pickup_sessions set revision = revision + 1 where id = s.id returning revision into next_revision;
    insert into public.pickup_events(session_id, revision, event_type, client_event_id, payload, occurred_at)
    values (s.id, next_revision, 'PARTICIPANT_STATUS', (p_input->>'clientEventId')::uuid, jsonb_build_object('status', requested_status), now_at);
    return jsonb_build_object('accepted', true, 'status', requested_status, 'revision', next_revision);
  end if;

  if p_action = 'UPDATE_LOCATION' then
    requested_actor_type := p_input->>'actorType';
    if requested_actor_type = 'VEHICLE' then
      select * into s from public.pickup_sessions where id = (p_input->>'sessionId')::uuid for update;
      if not found or s.owner_id <> p_actor_uid then raise sqlstate 'PT404' using message = 'PICKUP_NOT_FOUND'; end if;
      participant_uuid := null;
    elsif requested_actor_type = 'PARTICIPANT' then
      participant_uuid := (p_input->>'participantId')::uuid;
      select * into p from public.pickup_participants where id = participant_uuid and auth_uid = p_actor_uid and access_revoked_at is null for update;
      if not found then raise sqlstate 'PT404' using message = 'PICKUP_NOT_FOUND'; end if;
      select * into s from public.pickup_sessions where id = p.session_id for update;
    else raise sqlstate 'PT400' using message = 'INVALID_LOCATION_ACTOR'; end if;
    if s.status <> 'ACTIVE' then raise sqlstate 'PT409' using message = 'PICKUP_NOT_ACTIVE'; end if;

    -- The session row is locked above, so duplicate and out-of-order location
    -- retries can be ignored without racing another actor update.
    select * into l from public.pickup_current_locations existing
    where existing.session_id = s.id and existing.actor_type = requested_actor_type;
    if found and l.observed_at >= (p_input->>'observedAt')::timestamptz then
      return jsonb_build_object('revision', s.revision, 'proximityCandidate', s.proximity_candidate);
    end if;

    insert into public.pickup_current_locations(session_id, actor_type, participant_id, latitude, longitude, accuracy_meters, confidence, observed_at, received_at, source)
    values (s.id, requested_actor_type, participant_uuid, (p_input->>'latitude')::double precision, (p_input->>'longitude')::double precision,
      nullif(p_input->>'accuracyMeters','')::double precision, nullif(p_input->>'confidence','')::double precision,
      (p_input->>'observedAt')::timestamptz, now_at, p_input->>'source')
    on conflict (session_id, actor_type) do update set
      participant_id = excluded.participant_id, latitude = excluded.latitude, longitude = excluded.longitude,
      accuracy_meters = excluded.accuracy_meters, confidence = excluded.confidence,
      observed_at = excluded.observed_at, received_at = excluded.received_at, source = excluded.source;

    select * into v from public.pickup_current_locations where session_id = s.id and actor_type = 'VEHICLE';
    select * into g from public.pickup_current_locations where session_id = s.id and actor_type = 'PARTICIPANT';
    if v.session_id is not null and g.session_id is not null
      and (p_input->>'maxAccuracyMeters') is not null and (p_input->>'maxAgeMs') is not null
      and v.accuracy_meters is not null and g.accuracy_meters is not null
      and v.accuracy_meters <= (p_input->>'maxAccuracyMeters')::double precision
      and g.accuracy_meters <= (p_input->>'maxAccuracyMeters')::double precision
      and v.observed_at <= now_at and g.observed_at <= now_at
      and extract(epoch from (now_at - v.observed_at)) * 1000 <= (p_input->>'maxAgeMs')::double precision
      and extract(epoch from (now_at - g.observed_at)) * 1000 <= (p_input->>'maxAgeMs')::double precision then
      distance_meters := 6371000 * 2 * atan2(
        sqrt(power(sin(radians(g.latitude - v.latitude) / 2), 2) + cos(radians(v.latitude)) * cos(radians(g.latitude)) * power(sin(radians(g.longitude - v.longitude) / 2), 2)),
        sqrt(1 - (power(sin(radians(g.latitude - v.latitude) / 2), 2) + cos(radians(v.latitude)) * cos(radians(g.latitude)) * power(sin(radians(g.longitude - v.longitude) / 2), 2)))
      );
      valid_near := distance_meters <= (p_input->>'distanceMeters')::double precision;
    end if;
    since_at := case when valid_near then coalesce(s.proximity_since_at, now_at) else null end;
    candidate := valid_near and since_at is not null and now_at - since_at >= make_interval(secs => (p_input->>'dwellMs')::double precision / 1000.0);
    update public.pickup_sessions set revision = revision + 1, proximity_since_at = since_at, proximity_candidate = candidate where id = s.id returning revision into next_revision;
    if not s.proximity_candidate and candidate and not exists (
      select 1 from public.pickup_events
      where session_id = s.id
        and event_type = 'PROXIMITY_CANDIDATE'
        and payload->>'participantId' = g.participant_id::text
    ) then
      insert into public.pickup_events(session_id, revision, event_type, payload, occurred_at)
      values (s.id, next_revision, 'PROXIMITY_CANDIDATE', jsonb_build_object('proximityCandidate', true, 'participantId', g.participant_id), now_at);
    end if;
    return jsonb_build_object('revision', next_revision, 'proximityCandidate', candidate);
  end if;

  if p_action = 'CLOSE_SESSION' then
    select * into s from public.pickup_sessions where id = (p_input->>'sessionId')::uuid for update;
    if not found then raise sqlstate 'PT404' using message = 'PICKUP_NOT_FOUND'; end if;
    select response into result from public.pickup_request_idempotency
    where actor_uid = p_actor_uid and idempotency_key = (p_input->>'idempotencyKey')::uuid and operation = 'close';
    if found then return result; end if;
    if (p_input->>'driverOnly')::boolean then
      if s.owner_id <> p_actor_uid then raise sqlstate 'PT404' using message = 'PICKUP_NOT_FOUND'; end if;
    elsif s.owner_id <> p_actor_uid and not exists (
      select 1 from public.pickup_participants where session_id = s.id and auth_uid = p_actor_uid and access_revoked_at is null
    ) then raise sqlstate 'PT404' using message = 'PICKUP_NOT_FOUND'; end if;
    if s.status <> 'ACTIVE' then raise sqlstate 'PT409' using message = 'PICKUP_NOT_ACTIVE'; end if;
    update public.pickup_sessions set status = p_input->>'action', ended_at = now_at, revision = revision + 1,
      proximity_since_at = null, proximity_candidate = false where id = s.id returning * into s;
    insert into public.pickup_events(session_id, revision, event_type, payload, occurred_at)
    values (
      s.id,
      s.revision,
      'SESSION_' || (p_input->>'action'),
      case when nullif(p_input->>'reason', '') is null then '{}'::jsonb else jsonb_build_object('reason', p_input->>'reason') end,
      now_at
    );
    result := to_jsonb(s);
    insert into public.pickup_request_idempotency(actor_uid, idempotency_key, operation, session_id, response, created_at)
    values (p_actor_uid, (p_input->>'idempotencyKey')::uuid, 'close', s.id, result, now_at);
    return result;
  end if;

  if p_action = 'REVOKE_INVITATION' then
    select * into s from public.pickup_sessions where id = (p_input->>'sessionId')::uuid for update;
    if not found or s.owner_id <> p_actor_uid then raise sqlstate 'PT404' using message = 'PICKUP_NOT_FOUND'; end if;
    select response into result from public.pickup_request_idempotency
    where actor_uid = p_actor_uid and idempotency_key = (p_input->>'idempotencyKey')::uuid and operation = 'revoke';
    if found then return result; end if;
    if s.status <> 'ACTIVE' then raise sqlstate 'PT409' using message = 'PICKUP_NOT_ACTIVE'; end if;
    update public.pickup_invitations set revoked_at = coalesce(revoked_at, now_at) where session_id = s.id and revoked_at is null;
    update public.pickup_participants set access_revoked_at = coalesce(access_revoked_at, now_at), updated_at = now_at where session_id = s.id and access_revoked_at is null;
    delete from public.pickup_current_locations where session_id = s.id and actor_type = 'PARTICIPANT';
    update public.pickup_sessions set revision = revision + 1, proximity_since_at = null, proximity_candidate = false where id = s.id;
    result := '{}'::jsonb;
    insert into public.pickup_request_idempotency(actor_uid, idempotency_key, operation, session_id, response, created_at)
    values (p_actor_uid, (p_input->>'idempotencyKey')::uuid, 'revoke', s.id, result, now_at);
    return result;
  end if;

  raise sqlstate 'PT400' using message = 'UNKNOWN_PICKUP_COMMAND';
end;
$$;

revoke all on function public.pickup_command(text, uuid, jsonb) from public, anon, authenticated;
grant execute on function public.pickup_command(text, uuid, jsonb) to service_role;
