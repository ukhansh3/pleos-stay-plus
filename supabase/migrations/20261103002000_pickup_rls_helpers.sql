-- Break recursive RLS evaluation between pickup_sessions and
-- pickup_participants. These helpers reveal only a boolean authorization
-- result and always use the caller's explicit/authenticated uid.
create or replace function public.is_pickup_owner(target_session_id uuid, actor_uid uuid default auth.uid())
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select exists (
    select 1 from public.pickup_sessions
    where id = target_session_id and owner_id = actor_uid
  );
$$;

create or replace function public.is_active_pickup_participant(target_session_id uuid, actor_uid uuid default auth.uid())
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select exists (
    select 1 from public.pickup_participants
    where session_id = target_session_id
      and auth_uid = actor_uid
      and access_revoked_at is null
  );
$$;

revoke all on function public.is_pickup_owner(uuid, uuid) from public, anon;
revoke all on function public.is_active_pickup_participant(uuid, uuid) from public, anon;
grant execute on function public.is_pickup_owner(uuid, uuid) to authenticated, service_role;
grant execute on function public.is_active_pickup_participant(uuid, uuid) to authenticated, service_role;

drop policy if exists "pickup session authorized read" on public.pickup_sessions;
create policy "pickup session authorized read" on public.pickup_sessions
for select to authenticated using (
  owner_id = (select auth.uid())
  or public.is_active_pickup_participant(id, (select auth.uid()))
);

drop policy if exists "pickup participant authorized read" on public.pickup_participants;
create policy "pickup participant authorized read" on public.pickup_participants
for select to authenticated using (
  (auth_uid = (select auth.uid()) and access_revoked_at is null)
  or public.is_pickup_owner(session_id, (select auth.uid()))
);

drop policy if exists "pickup location authorized read" on public.pickup_current_locations;
create policy "pickup location authorized read" on public.pickup_current_locations
for select to authenticated using (
  exists (
    select 1 from public.pickup_sessions s
    where s.id = session_id
      and s.status = 'ACTIVE'
      and (
        public.is_pickup_owner(s.id, (select auth.uid()))
        or public.is_active_pickup_participant(s.id, (select auth.uid()))
      )
  )
);

drop policy if exists "pickup event authorized read" on public.pickup_events;
create policy "pickup event authorized read" on public.pickup_events
for select to authenticated using (
  public.is_pickup_owner(session_id, (select auth.uid()))
  or public.is_active_pickup_participant(session_id, (select auth.uid()))
);
