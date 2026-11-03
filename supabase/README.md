# Supabase backend

`migrations/20261103000000_independent_pickup.sql` defines the approved independent Pickup tables, latest-location storage, cleanup trigger, read-only client grants, and RLS ownership/participant policies.
`migrations/20261103001000_pickup_service_role_and_realtime.sql` grants the server-only role and publishes only RLS-filtered Pickup state for Realtime.
`migrations/20261103002000_pickup_rls_helpers.sql` removes recursive session/participant policy evaluation while preserving owner/participant isolation.

All three migrations were applied to the approved Supabase project on 2026-11-03 and passed the live Auth/RLS E2E. New environments must apply them in filename order. No database credentials are stored here.
