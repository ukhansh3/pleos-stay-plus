# Supabase backend

`migrations/20261103000000_independent_pickup.sql` defines the approved independent Pickup tables, latest-location storage, cleanup trigger, read-only client grants, and RLS ownership/participant policies.

The SQL is versioned but has **not** been applied to Supabase Cloud. Applying it, enabling Realtime publication/private channels, and running two-user RLS tests require separate approval and Anonymous Auth activation. No database credentials are stored here.
