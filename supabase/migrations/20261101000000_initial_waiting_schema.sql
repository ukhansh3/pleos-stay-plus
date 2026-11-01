-- Pleos Stay+ initial schema. REVIEW_REQUIRED: not deployed.
-- Driver = Supabase Auth user, including signInAnonymously() users.
-- Guest invitation redemption and participant status mutations MUST use trusted server handlers.
create extension if not exists pgcrypto;

create table public.waiting_sessions (
  id uuid primary key default gen_random_uuid(),
  owner_id uuid not null references auth.users(id) on delete cascade default auth.uid(),
  parking_place_id text,
  status text not null default 'ACTIVE' check (status in ('ACTIVE','ENDING','ENDED','EXPIRED')),
  active_mode text not null default 'NONE' check (active_mode in ('NONE','WORK','STUDY','ENTERTAINMENT','REST')),
  started_at timestamptz not null default now(),
  planned_end_at timestamptz not null,
  ended_at timestamptz,
  revision integer not null default 0 check (revision >= 0),
  constraint valid_end check (planned_end_at > started_at),
  constraint ended_status check ((status in ('ACTIVE','ENDING') and ended_at is null) or (status in ('ENDED','EXPIRED')))
);
create index waiting_sessions_owner_idx on public.waiting_sessions(owner_id);
create index waiting_sessions_owner_status_idx on public.waiting_sessions(owner_id,status);
-- REVIEW_REQUIRED: one ACTIVE session per driver rule; intentionally no unique partial index.

create table public.pickup_invitations (
  id uuid primary key default gen_random_uuid(),
  session_id uuid not null references public.waiting_sessions(id) on delete cascade,
  token_hash text not null unique,
  expires_at timestamptz not null,
  revoked_at timestamptz,
  max_uses integer not null default 1 check (max_uses > 0),
  used_count integer not null default 0 check (used_count >= 0 and used_count <= max_uses),
  created_at timestamptz not null default now()
);
create index pickup_invitations_session_idx on public.pickup_invitations(session_id);

create table public.pickup_participants (
  id uuid primary key default gen_random_uuid(),
  session_id uuid not null references public.waiting_sessions(id) on delete cascade,
  invitation_id uuid not null references public.pickup_invitations(id) on delete cascade,
  display_name text,
  status text not null default 'JOINED' check (status in ('JOINED','DEPARTED','NEARBY','ARRIVED','CANCELLED')),
  updated_at timestamptz not null default now(),
  constraint display_name_length check (display_name is null or char_length(display_name) <= 60)
);
create index pickup_participants_session_idx on public.pickup_participants(session_id);

create table public.pickup_status_events (
  id uuid primary key default gen_random_uuid(),
  participant_id uuid not null references public.pickup_participants(id) on delete cascade,
  client_event_id uuid not null,
  status text not null,
  created_at timestamptz not null default now(),
  unique(participant_id,client_event_id)
);
create table public.session_events (
  id uuid primary key default gen_random_uuid(),
  session_id uuid not null references public.waiting_sessions(id) on delete cascade,
  revision integer not null check(revision >= 0),
  event_type text not null,
  payload jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now(),
  unique(session_id,revision)
);

-- No public guest table privileges or RLS policies. Guests access *only* through
-- server handlers that verify expiry, revocation, limited-scope token and consent.
alter table public.waiting_sessions enable row level security;
alter table public.pickup_invitations enable row level security;
alter table public.pickup_participants enable row level security;
alter table public.pickup_status_events enable row level security;
alter table public.session_events enable row level security;

revoke all on public.waiting_sessions,public.pickup_invitations,public.pickup_participants,public.pickup_status_events,public.session_events from anon,authenticated;
grant select,insert,update on public.waiting_sessions to authenticated;
grant select on public.pickup_invitations,public.pickup_participants,public.pickup_status_events,public.session_events to authenticated;

create policy "driver read own sessions" on public.waiting_sessions
for select to authenticated using ((select auth.uid()) = owner_id);
create policy "driver create own sessions" on public.waiting_sessions
for insert to authenticated with check ((select auth.uid()) = owner_id and status = 'ACTIVE' and revision = 0);
create policy "driver update own sessions" on public.waiting_sessions
for update to authenticated using ((select auth.uid()) = owner_id) with check ((select auth.uid()) = owner_id);

create policy "driver read own invites" on public.pickup_invitations
for select to authenticated using (
  exists (select 1 from public.waiting_sessions s where s.id = session_id and s.owner_id = (select auth.uid()))
);
create policy "driver read own participants" on public.pickup_participants
for select to authenticated using (
  exists (select 1 from public.waiting_sessions s where s.id = session_id and s.owner_id = (select auth.uid()))
);
create policy "driver read own pickup events" on public.pickup_status_events
for select to authenticated using (
  exists (select 1 from public.pickup_participants p join public.waiting_sessions s on s.id=p.session_id
          where p.id=participant_id and s.owner_id=(select auth.uid()))
);
create policy "driver read own session events" on public.session_events
for select to authenticated using (
  exists (select 1 from public.waiting_sessions s where s.id=session_id and s.owner_id=(select auth.uid()))
);

-- SECURITY TODO BEFORE PRODUCTION:
-- Avoid direct client UPDATE for critical transitions: server-side revision CAS,
-- invariants, invitation revocation and event append must be atomic.
-- Current UPDATE policy intentionally only isolates ownership, NOT state transitions.
-- Remove authenticated UPDATE grant and move to secure server RPC/handler before production.
