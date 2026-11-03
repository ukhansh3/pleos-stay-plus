# Pickup v0.3 integration verification — 2026-11-03

Environment: Windows, Node.js 24.13.0, Pleos Android project compile/target 37, local worktrees based on `dev` commit `f8d2c6c`. No production deployment, paid-service change, or `main` merge was performed. Supabase Pickup migrations were applied with explicit user approval.

## Verified

| Area | Result | Evidence |
|---|---|---|
| Contract | PASS | OpenAPI 3.1 YAML/local references validation passed. Pickup uses independent `/pickup/sessions`; Waiting is optional linkage only. |
| Backend domain/API | PASS (local test adapter) | TypeScript typecheck/build and automated tests cover owner isolation, one participant, 60-minute expiry, invitation reissue revocation, idempotent participant events, backward-state rejection, close cleanup, and configured 20m/30s proximity. |
| Parking regression | PASS | Existing Parking provider/cache/search/detail tests remained green in the same Backend suite. |
| Supabase design | PASS (applied) | Three versioned migrations add the independent schema, service-role grants, Realtime publication, cleanup trigger, and non-recursive owner/participant RLS. |
| Supabase Auth/RLS E2E | PASS | Temporary anonymous driver, guest, and outsider verified invitation redemption, latest locations, status, both snapshots, outsider denial, close cleanup, and post-close access revocation. All test users/data were removed. |
| Production Pickup persistence | PASS (local server to Cloud DB) | Fastify used the server-only secret key through the `apikey` header and persisted the complete E2E flow to the approved Supabase project. |
| Android compile/unit | PASS | `testDebugUnitTest assembleDebug` succeeded with Parking, Waiting, and Pickup sources together. |
| Android location truthfulness | PASS (compile boundary) | FusedLocation 1.1.3 `LocationData` exposes confidence but no meter accuracy; the adapter sends confidence and never invents `accuracyMeters`. |
| Web unit/type/build | PASS | Revision/staleness tests, TypeScript check, and Next.js production build succeeded. Production build gates the simulator to not-found. |
| Secret handling | PASS (repository scan) | Tracked sources contain example placeholders only; actual `.env`, `.env.local`, and `android/local.properties` are Git-ignored. |

## BLOCKED / not executed

| Item | Status | Reason / required action |
|---|---|---|
| Realtime WebSocket delivery | NOT EXECUTED | Required tables are published and RLS REST reads passed, but an actual browser/WebSocket delivery trace was not captured in this pass. |
| Kakao map browser | BLOCKED | JavaScript key exists only in ignored local config. Kakao Map API activation, allowed localhost/demo domain, quota, and real browser rendering were not verified. |
| Pleos emulator Pickup E2E | NOT EXECUTED | Backend Cloud persistence/auth E2E and a configured local dev HMAC secret are prerequisites. APK build success is not runtime proof. |
| Pleos TextToSpeech | SDK_VERIFY | Official Maven coordinate/API/permission and emulator playback were not verified. Only an unsupported gateway and deduplication test exist. |
| Automatic vehicle proximity | REVIEW_REQUIRED | Pleos vehicle location lacks meter accuracy. Such positions are shown/stored but excluded from automatic proximity accumulation. Manual status/completion remains available. |
| External mobile/Quick Tunnel | OUT OF SCOPE | No Cloudflare install, public tunnel, Vercel deployment, or Kakao domain mutation was performed. |

## Commands

```powershell
cd backend
npm run typecheck
npm test
npm run build
npm run validate:openapi

cd ../android
.\gradlew.bat testDebugUnitTest assembleDebug

cd ../web
npm test
npm run typecheck
npm run build
```

## Security notes

- Invitation plaintext appears only in the one-time share URL. Persistence stores SHA-256 only; deterministic replay uses a separate server HMAC secret and a UUID idempotency key.
- Share handoff does not fetch location. Guest location access requires its own Supabase anonymous JWT and an active participant UID binding.
- Closing Pickup deletes current coordinates and revokes invitation/participant access. No location history table exists.
- The development simulator is labelled `SIMULATED`, has no production coordinate-write path, and is server-gated to 404 outside an explicitly enabled non-production environment.
