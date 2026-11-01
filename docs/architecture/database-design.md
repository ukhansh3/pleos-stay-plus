# Supabase DB/RLS design — REVIEW_REQUIRED

Status: **design only**; no live database/migration applied.

## Proposed tables
| Table | Fields | Constraints |
|---|---|---|
| waiting_sessions | id uuid, owner_id uuid, parking_place_id text nullable, status, active_mode, started_at, planned_end_at, ended_at, revision int | owner lookup + one-active-session policy TBD |
| pickup_invitations | id uuid, session_id uuid FK, token_hash text, expires_at, revoked_at, max_uses, used_count | token_hash unique |
| pickup_participants | id uuid, session_id uuid FK, invitation_id uuid FK, status, display_name, updated_at | participant scoped to session |
| pickup_status_events | id uuid, participant_id uuid FK, client_event_id uuid, status, created_at | unique(participant_id,client_event_id) |
| session_events | id uuid, session_id uuid FK, revision int, event_type, payload jsonb, created_at | unique(session_id,revision) |
| parking_cache | provider, source_id, payload jsonb, cached_at, expires_at | unique(provider,source_id) |

## Trust boundaries
- Driver: authenticated principal may access own waiting sessions only.
- Invite visitor: link alone grants no owner privileges; redemption requires consent, expiry, use-count check, atomic token consumption.
- Participant: short-lived credential restricts read/write to its own participant state; do not expose service_role to browser.
- Realtime: private channel authorization and RLS must be tested independently; do not assume REST RLS also protects all broadcast channels.
- End session: revoke invitation and access tokens, transition state transactionally or with compensating action.
- Tokens: random high entropy, hash at rest, never log plaintext, no query string analytics on invite pages.
- Data retention and guest location collection **REVIEW_REQUIRED**.

## Proposed transactions/tests
1. Duplicate session start by same driver; decide reject vs resume.
2. Two simultaneous invite redemptions at max uses; only allowed participants accepted.
3. Owner A cannot read B's session; participant cannot list driver sessions.
4. Repeat participant status request with same clientEventId; single event emitted.
5. Revoked/expired invitation returns 410.
6. Concurrent session revision update rejects stale writer.
7. Offline Realtime client resynchronizes by GET.

## NOT YET DECIDED
Auth provider (Supabase Auth anonymous/email/device), guest token exchange architecture, data retention, precise RLS SQL, storage of parking-provider cache, hosted backend boundary (Edge Functions vs Next.js routes).

**No executable migration included intentionally:** implementing RLS before deciding authentication/session credential architecture would risk creating a false-security implementation.
