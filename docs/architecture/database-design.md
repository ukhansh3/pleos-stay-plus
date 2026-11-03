# Supabase DB/RLS design — Pickup v0.3 approved, cloud application pending

Status: **design only**; no live database/migration applied.

## Proposed tables
| Table | Fields | Constraints |
|---|---|---|
| pickup_sessions | id uuid, owner_id uuid, waiting_session_id text nullable, status, location_sharing_consent, revision, created_at, ended_at | owner lookup; Waiting is not a parent FK |
| pickup_invitations | id uuid, session_id uuid FK, token_hash text, expires_at, revoked_at, used_at | token_hash unique; one current invitation |
| pickup_participants | id uuid, session_id uuid FK, invitation_id uuid FK, auth_uid uuid, status, display_name, access_revoked_at, updated_at | one participant per active Pickup; auth UID binding |
| pickup_current_locations | session_id uuid, actor_type, actor_id, latitude, longitude, accuracy_meters, observed_at, received_at, source | latest coordinate only; delete on close |
| pickup_events | id uuid, session_id uuid FK, revision int, event_type, client_event_id nullable, payload jsonb, occurred_at | unique(session_id,revision), idempotency uniqueness |
| parking_cache | provider, source_id, payload jsonb, cached_at, expires_at | unique(provider,source_id) |

## Trust boundaries
- Driver: authenticated principal may access own waiting sessions only.
- Invite visitor: link alone grants no location access; redemption requires a separate guest anonymous JWT, consent, expiry check, and atomic one-person binding.
- Participant: `auth.uid()` must match its active participant binding and may access only that Pickup's allowed snapshot and its own location/status writes.
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

## Remaining review gates
GPS accuracy/freshness thresholds, operational backup deletion guarantees, orphan cleanup interval, Kakao allowed domains, and production hosting/CORS remain `REVIEW_REQUIRED`.

An executable migration is versioned under `supabase/migrations`, but it must not be applied to Supabase Cloud until separately approved and reviewed.
