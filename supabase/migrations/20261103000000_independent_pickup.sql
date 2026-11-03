-- Independent Pickup schema for OpenAPI v0.3.
-- Cloud application approved by the user on 2026-11-03. Apply through the reviewed
-- migration workflow only; never paste secrets into SQL or migration history.
-- Fastify is the only writer through a server-only secret. Authenticated clients receive
-- RLS-filtered SELECT access for private Realtime/snapshot support and cannot write tables directly.

create extension if not exists pgcrypto;

create table public.pickup_sessions (
  id uuid primary key default gen_random_uuid(),
  owner_id uuid not null references auth.users(id) on delete cascade,
  waiting_session_id text,
  status text not null default 'ACTIVE' check (status in ('ACTIVE','COMPLETED','CANCELLED','ENDED','EXPIRED')),
  location_sharing_consent boolean not null check (location_sharing_consent),
  create_idempotency_key uuid not null,
  revision integer not null default 1 check (revision >= 1),
  proximity_since_at timestamptz,
  proximity_candidate boolean not null default false,
  created_at timestamptz not null default now(),
  ended_at timestamptz,
  unique(owner_id, create_idempotency_key),
  constraint pickup_ended_state check (
    (status = 'ACTIVE' and ended_at is null)
    or (status <> 'ACTIVE' and ended_at is not null)
  )
);
create index pickup_sessions_owner_status_idx on public.pickup_sessions(owner_id, status);

create table public.pickup_invitations (
  id uuid primary key default gen_random_uuid(),
  session_id uuid not null references public.pickup_sessions(id) on delete cascade,
  token_hash text not null unique check (char_length(token_hash) = 64),
  issue_idempotency_key uuid not null,
  expires_at timestamptz not null,
  revoked_at timestamptz,
  created_at timestamptz not null default now(),
  unique(session_id, issue_idempotency_key)
);
create index pickup_invitations_session_idx on public.pickup_invitations(session_id, created_at desc);

create table public.pickup_participants (
  id uuid primary key default gen_random_uuid(),
  session_id uuid not null references public.pickup_sessions(id) on delete cascade,
  invitation_id uuid not null references public.pickup_invitations(id) on delete restrict,
  auth_uid uuid not null references auth.users(id) on delete cascade,
  display_name text not null default '픽업 대상',
  status text not null default 'JOINED' check (status in ('JOINED','DEPARTED','NEARBY','ARRIVED','CANCELLED')),
  access_revoked_at timestamptz,
  updated_at timestamptz not null default now(),
  constraint pickup_display_name_length check (char_length(display_name) between 1 and 60)
);
create index pickup_participants_auth_idx on public.pickup_participants(auth_uid, session_id);
create unique index pickup_participants_one_active_per_session_idx
  on public.pickup_participants(session_id)
  where access_revoked_at is null;

create table public.pickup_current_locations (
  session_id uuid not null references public.pickup_sessions(id) on delete cascade,
  actor_type text not null check (actor_type in ('VEHICLE','PARTICIPANT')),
  participant_id uuid references public.pickup_participants(id) on delete cascade,
  latitude double precision not null check (latitude between -90 and 90),
  longitude double precision not null check (longitude between -180 and 180),
  accuracy_meters double precision check (accuracy_meters > 0),
  confidence double precision,
  observed_at timestamptz not null,
  received_at timestamptz not null default now(),
  source text not null check (source in ('REAL','SIMULATED')),
  primary key(session_id, actor_type),
  constraint pickup_location_actor check (
    (actor_type = 'VEHICLE' and participant_id is null)
    or (actor_type = 'PARTICIPANT' and participant_id is not null)
  )
);

create table public.pickup_events (
  id uuid primary key default gen_random_uuid(),
  session_id uuid not null references public.pickup_sessions(id) on delete cascade,
  revision integer not null check (revision >= 1),
  event_type text not null,
  client_event_id uuid,
  payload jsonb not null default '{}'::jsonb,
  occurred_at timestamptz not null default now(),
  unique(session_id, revision),
  unique(session_id, client_event_id)
);

create table public.pickup_request_idempotency (
  actor_uid uuid not null references auth.users(id) on delete cascade,
  idempotency_key uuid not null,
  operation text not null,
  session_id uuid references public.pickup_sessions(id) on delete cascade,
  response jsonb not null,
  created_at timestamptz not null default now(),
  primary key(actor_uid, idempotency_key, operation)
);

create or replace function public.cleanup_closed_pickup()
returns trigger language plpgsql security definer set search_path = public as $$
begin
  if old.status = 'ACTIVE' and new.status <> 'ACTIVE' then
    delete from public.pickup_current_locations where session_id = new.id;
    update public.pickup_invitations set revoked_at = coalesce(revoked_at, now()) where session_id = new.id;
    update public.pickup_participants set access_revoked_at = coalesce(access_revoked_at, now()) where session_id = new.id;
  end if;
  return new;
end;
$$;
create trigger pickup_closed_cleanup
after update of status on public.pickup_sessions
for each row execute function public.cleanup_closed_pickup();

alter table public.pickup_sessions enable row level security;
alter table public.pickup_invitations enable row level security;
alter table public.pickup_participants enable row level security;
alter table public.pickup_current_locations enable row level security;
alter table public.pickup_events enable row level security;
alter table public.pickup_request_idempotency enable row level security;

revoke all on public.pickup_sessions, public.pickup_invitations, public.pickup_participants,
  public.pickup_current_locations, public.pickup_events, public.pickup_request_idempotency
  from anon, authenticated;
grant select on public.pickup_sessions, public.pickup_participants,
  public.pickup_current_locations, public.pickup_events to authenticated;

create policy "pickup session authorized read" on public.pickup_sessions
for select to authenticated using (
  owner_id = (select auth.uid())
  or exists (
    select 1 from public.pickup_participants p
    where p.session_id = id and p.auth_uid = (select auth.uid()) and p.access_revoked_at is null
  )
);

create policy "pickup participant authorized read" on public.pickup_participants
for select to authenticated using (
  auth_uid = (select auth.uid()) and access_revoked_at is null
  or exists (select 1 from public.pickup_sessions s where s.id = session_id and s.owner_id = (select auth.uid()))
);

create policy "pickup location authorized read" on public.pickup_current_locations
for select to authenticated using (
  exists (
    select 1 from public.pickup_sessions s
    where s.id = session_id and s.status = 'ACTIVE' and (
      s.owner_id = (select auth.uid())
      or exists (
        select 1 from public.pickup_participants p
        where p.session_id = s.id and p.auth_uid = (select auth.uid()) and p.access_revoked_at is null
      )
    )
  )
);

create policy "pickup event authorized read" on public.pickup_events
for select to authenticated using (
  exists (
    select 1 from public.pickup_sessions s
    where s.id = session_id and (
      s.owner_id = (select auth.uid())
      or exists (
        select 1 from public.pickup_participants p
        where p.session_id = s.id and p.auth_uid = (select auth.uid()) and p.access_revoked_at is null
      )
    )
  )
);

-- Realtime publication is intentionally not mutated here. Enable only the required tables
-- in the Supabase dashboard after reviewing private-channel authorization and applying this migration.
