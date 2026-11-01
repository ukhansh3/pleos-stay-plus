# Auth implementation review — 2026-11-01

Decision: Supabase anonymous sign-in for driver (ADR-001).

## Delivered
- Draft SQL migration for driver-owned session and pickup/event tables with RLS isolation policies.
- Explicit separation between driver (Supabase Auth JWT) and guest (invitation exchange).
- All guest direct table privileges denied. Server guest handler is **not implemented**.

## Important remaining blockers
- Migration has not been executed in Supabase; no actual RLS test run.
- Owner update RLS prevents cross-user edits but does **not** enforce state transition, idempotency or transaction boundaries. Before deploying, route critical transitions through authenticated server handlers, removing direct update grant.
- Guest invitation/token protocol, anti-abuse, expiration policy, anonymous cleanup/retention and Realtime auth require review.
- Parking API provider remains REVIEW_REQUIRED; SDK runtime check deferred by user.

## Test cases (NOT RUN)
1. Driver A creates session and reads only own rows.
2. Driver B cannot read/update session A.
3. Unauthenticated public client cannot access tables.
4. Client cannot directly insert pickup invitation or participant.
5. Guest expired/revoked token cannot redeem.
6. State revision conflict and concurrent invite consumption are atomic.
7. Realtime channel denies unauthorized subscriptions.
