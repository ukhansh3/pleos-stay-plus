# Pickup v0.3 final verification — 2026-11-04

Branch: `dev`. Scope: independent Pickup Backend, Android/Pleos client, participant Web, local-only developer simulator, and Supabase integration. Parking and Waiting behavior was preserved. No production hosting, paid service, or `main` merge was performed.

## Results

| Area | Result | Evidence |
|---|---|---|
| Backend contract/build | PASS | TypeScript typecheck/build and OpenAPI 3.1 reference validation passed. |
| Backend/Parking regression | PASS | 18/18 automated tests passed, including invitation reissue, idempotency, concurrent atomic commands, durable events, 20m/30s proximity, cleanup, simulator gate, and Parking tests. |
| Web | PASS (automated) | 6/6 tests, TypeScript check, and Next.js production build passed. Same-origin fixed proxy, refresh recovery, token refresh, Realtime recovery, continuous optional GPS, and local-only simulator boundaries are implemented. |
| Android | PASS (build/unit) | `testDebugUnitTest assembleDebug` passed. QR reissue, GPS-independent sync, durable event ordering/deduplication, TTS adapter, and safety recovery tests passed. |
| Supabase migration | PASS (applied by user) | `20261103003000_pickup_atomic_commands.sql` is active. Atomic invitation/reissue, participant binding, status/location commands, close cleanup, and per-participant proximity-event deduplication are used by Fastify. |
| Supabase Auth/RLS | PASS (live) | Temporary anonymous driver/guest/outsider verified session isolation, invitation redemption/reissue, latest locations, snapshots, post-close denial, and current-location deletion. Test users and cascaded data were removed. |
| Supabase Realtime | PASS (live) | An authenticated filtered `pickup_events` subscription received the actual `DEPARTED` insert; revision, client event ID, and payload matched the API result. |
| Secrets | PASS | Actual `.env`, `.env.local`, and `android/local.properties` remain ignored. No Supabase secret credential is tracked. |

## Verified behavior

- Pickup starts independently from Waiting; optional Waiting linkage remains local metadata.
- QR reissue is atomic and revokes the old invitation, participant access, and participant current location.
- Vehicle status/event synchronization continues when FusedLocation read or upload fails.
- Realtime disconnect recovery uses revision-ordered durable events plus snapshot polling; duplicate and out-of-order events are ignored.
- Participant GPS uses consented `watchPosition` with a five-second upload target. Browser background throttling is shown as a limitation; manual status and map viewing remain available without GPS.
- Session completion/cancellation/end revokes access and removes current coordinates. No location history is added.
- The developer simulator is registered only with explicit enablement, loopback hosting, normal actor authentication, and a separate server-only token. Normal Pickup routes reject `SIMULATED` coordinates.

## SDK_VERIFY / not claimed complete

- Pleos TextToSpeech 3.3.1 Maven resolution, official API signatures, permission declaration, lifecycle, and Android compilation are verified. Emulator permission/audio playback is not claimed; runtime failure falls back safely and reports `SDK_VERIFY` without inventing an API.
- Pleos FusedLocation 1.1.3 exposes confidence but no meter accuracy. The app never invents `accuracyMeters`; those vehicle positions do not trigger automatic proximity.
- Per the user's final instruction, further Pleos emulator and developer-simulator execution was stopped and is not part of the completion claim.
- Kakao Maps browser rendering and registered-domain acceptance remain manual verification items. The SDK is limited to the development-only simulator and is not part of the production Pickup UI.

Official SDK references:

- TextToSpeech overview: https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/TextToSpeech/intro
- TextToSpeech initialization/release: https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/TextToSpeech/initialization-and-release
- FusedLocation location data: https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/FusedLocation/location-data-access
