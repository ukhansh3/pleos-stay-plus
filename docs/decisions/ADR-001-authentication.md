# ADR-001 — MVP identity: Supabase anonymous sign-in

- Status: **ACCEPTED** by user on 2026-11-01
- Scope: Pleos Stay+ driver and Pickup participant identity in MVP
- Official reference: https://supabase.com/docs/guides/auth/auth-anonymous

## Decision
- Android driver client uses Supabase Auth `signInAnonymously()`; each driver has an `auth.users.id`.
- Persistent data owner key is `auth.uid()`; never rely on phone device ID, an unauthenticated `anon` key, or user-supplied `ownerId`.
- Supabase's **anonymous signed-in users are in PostgreSQL `authenticated` role**. They differ from requests using public `anon` key with no user identity.
- A future Pleos account integration is **REVIEW_REQUIRED**, not a blocker for initial MVP.
- Guest pickup webpage **does not share driver JWT**. It performs a separate, UI-less Supabase anonymous sign-in and exchanges the invitation through Fastify. The backend binds that guest `auth.uid()` to one participant and one Pickup session.
- The invitation token is not an authentication session. It only permits a one-time, consented participant binding before expiry; subsequent access requires the bound guest JWT and active participant authorization.

## Consequences
- No registration UI in first release.
- On sign-out, app data reset, or use on another device, the anonymous identity may be unrecoverable. Do not promise cross-device recovery.
- Driver switching must invalidate local session and remote-PC access; vehicle profile integration not assumed.
- Prevent abuse with anonymous sign-in rate limiting/CAPTCHA/monitoring; user consent for retention period pending.

## Security review gate
- RLS enforces ownership using `auth.uid()` and tests two different anonymous identities.
- Backend server is responsible for atomic revision/state changes; guest link reuse, expiry, revocation, participant UID binding, and rate limits must be tested.
- Reissuing or revoking an invitation and completing/cancelling/ending Pickup must invalidate the participant binding and private Realtime authorization, even while the Supabase anonymous user itself still exists.
- Never expose Supabase secret/service-role keys in Android/web bundles.
