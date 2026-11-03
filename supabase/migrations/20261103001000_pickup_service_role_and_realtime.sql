-- Fastify is the only writer. New Supabase secret keys map to service_role,
-- which still needs explicit table privileges before it can bypass RLS.
grant usage on schema public to service_role;
grant select, insert, update, delete on
  public.pickup_sessions,
  public.pickup_invitations,
  public.pickup_participants,
  public.pickup_current_locations,
  public.pickup_events,
  public.pickup_request_idempotency
to service_role;

-- Only authenticated, RLS-filtered state required by the Pickup web flow is
-- published. Invitation hashes and request idempotency records stay private.
do $$
declare
  table_name text;
begin
  foreach table_name in array array[
    'pickup_sessions',
    'pickup_participants',
    'pickup_current_locations',
    'pickup_events'
  ] loop
    if not exists (
      select 1 from pg_publication_tables
      where pubname = 'supabase_realtime'
        and schemaname = 'public'
        and tablename = table_name
    ) then
      execute format('alter publication supabase_realtime add table public.%I', table_name);
    end if;
  end loop;
end
$$;
