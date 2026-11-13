# Realtime / Pickup interactive QA — 2026-11-13

## Scope and baseline

- Actual user checkout: `C:\Users\ukhan\pleos-stay-plus`, branch `dev`.
- Starting HEAD: `11f4a43`; fetched `origin/dev` was `dfcb375`. Local work was four commits ahead, no upstream commits missing. No reset, force push, main change or old-code rollback.
- User approved starting emulator/servers, then explicitly approved Cloudflare Quick Tunnel for this QA only. No system service installation, subscription, stable deployment, schema/RLS change or new application dependency.
- Protected home/assets/domain code unchanged. Existing unrelated `web/next-env.d.ts` and generated `web/AGENTS.md` excluded from this change.

## Runtime and exposure

- Android Studio not started. One Pleos Connect v2.1.0 emulator, API34, GPU host, boot animation disabled. APK installed with `-r` to current Android user, no wipe/data clearing.
- Parking/Pickup API3000, web3001, authenticated Supabase Realtime API3012 listen on loopback only. Windows Stay+ launched. Cloud background cleanup timer is intentionally not scheduled.
- Cloudflared2026.11.0 executable downloaded from official Cloudflare GitHub release, SHA256 matched release asset digest. Executable and QA launchers live under ignored `backend/.cache/qa-runtime`.
- Temporary HTTPS traffic goes through a loopback allow-list proxy on3013. Only Pickup share/participant pages, required static resources and authenticated participant redeem/status/location/snapshot/events are forwarded. Owner session creation, general backend, Realtime, developer simulator, HMR and source maps are not publicly forwarded. Rate/body/origin limits apply. Both server-side simulator flags disabled in QA runtime.
- Temporary public origin is held in ignored `pickup-public-qa.json` and injected into the QA backend launcher only. Existing `.env` public URL is not permanently replaced with an expiring address.
- Next incoming/fetch/browser logs disabled for this QA; tunnel output filtered so invitation URLs are not recorded. No secret, PIN, invitation token, user ID or private document content in this report.

## Product change

Yesterday's trace also contained DELETE pairing-cancel requests rejected with415 because Android supplied implicit form Content-Type. Realtime control calls now explicitly use JSON headers; body-less DELETE sends `{}` too. Fastify rejects JSON with a missing DELETE body as400, so a header-only fix was insufficient. Binary file uploads remain octet-stream. GET/HEAD remain body-less.

Added/updated regression checks cover successful pairing cancellation204, CANCELLED state and explicit DELETE payload. No pairing policy or backend authorization change.

## Automated verification

| Check | Result | Evidence / boundary |
|---|---|---|
| Android assembleDebug | PASS | Final DELETE fix built and installed |
| Android JVM suite | PASS | 221 total;220 passed;1 expected Release-only Parking skip;0 failures |
| Backend suite/typecheck | PASS | 31 tests, no TypeScript errors |
| Web suite/typecheck | PASS | 6 tests, no TypeScript errors |
| Real authenticated HTTP / Supabase QA | PASS | Final run13 scenarios, all passed; separate QA actors and nonconfidential fixtures |
| Focused Pickup/Realtime actual API34 UI test | PASS | `PickupRealtimeEntryTest`: Pickup→Files→Pickup→Home, real MainActivity and menu selection; no fake safety or session creation |
| Existing full production-entry UI test | FAIL | Rest→Home assertion line43; reproduced twice, outside focused two-feature route test |
| Release build | NOT RUN | No Release-specific product change; avoided extra load during interactive QA |

## Real API integration scenarios (final run)

1. Public HTTPS root200; developer/simulator/.env/HMR/general API routes404; unauthenticated snapshot401.
2. Participant and session-recovery pages200; all19 static resource requests200.
3. Actual Supabase anonymous Auth, no mock identity.
4. Independent Pickup creation without Waiting, duplicate-create/invitation request replay,60-minute invitation and correct temporary HTTPS origin.
5. Consent required, actual public-proxy invitation redemption and single participant enforcement.
6. Unauthorized snapshot denial and Supabase RLS outsider rows hidden.
7. Authenticated Pickup event subscription, DEPARTED status, duplicate client event handling, durable event query and public snapshot state.
8. Reissued invitation invalidates previous invitation and participant access; new invitation usable.
9. Manual completion blocks guest reentry/snapshot, current-location table empty for this QA session. No real GPS was collected, so deletion of previously populated real coordinates was not exercised here.
10. Realtime eight-digit issue/cancel, CANCELLED state, PC claim, no connection before both approvals, outsider denial.
11. Actual Storage original/revision upload, idempotent prepare, version2, download byte/hash equality and outsider denial.
12. Vehicle new DOCX original upload accepted by current deployed contract; PC API download SHA256 matched bundled nonconfidential DOCX fixture. This verifies server support, not the Windows UI or Work editing/export UI.
13.20MiB bound, removal of own synthetic test files, connection revocation and cleared current connection.

## First-run failures and corrections

- Public static allow-list initially rejected encoded square brackets in a generated Next chunk filename. Expanded only the static filename character set in the ignored QA proxy; public pages and all19 assets then passed.
- Initial outsider API checks expected404 for an unregistered test device; actual409 DEVICE_NOT_REGISTERED also denied access. Registered the separate outsider test device and reran the isolation checks, obtaining the intended404 path. No product auth policy changed.
- First Pickup Realtime event wait timed out at12seconds. Rerun verified owner RLS and durable event visibility, and event delivery passed with a20-second wait. No DB/publication changes made. Delivery latency/reliability still needs observation; a single successful rerun is not proof of consistently low latency. Existing web15-second snapshot polling and Android5-second event/snapshot polling remain unchanged.

## UI / device boundaries and open findings

- Windows automation kernel failed to initialize (`failed to write kernel assets`, path not found), including one reset/retry. No blind clicks, PIN bypass or permission grants. Windows Stay+.exe is running but full app-to-app manual pairing/upload/download UI is NOT RUN.
- FAIL existing `PremiumProductionEntryTest`, reproduced twice on actual API34 emulator. Pickup/Files and other preceding navigation assertions passed, but the last Rest-to-home selected-menu assertion failed at line43. This requires investigation of asynchronous activity transition/confirmation or test expectations; neither product navigation nor this failing test was silently changed to force a pass. It does not prove full functional E2E.
- Actual phone camera QR scan, mobile session refresh, GPS consent/refusal/background collection, measured20m/30s proximity, Pleos TTS/audio output and real driving/safety transitions: NOT RUN. No simulated coordinates were presented as actual locations.
- Code review finding: Pickup start rejects any non-null session; terminal session remains non-null, and terminal UI exposes no start button. Same-screen second Pickup requires a follow-up fix; not marked complete.
- Code review finding: invitation reissue failure retains old invitation state. If the server revoked old invitation but the response was lost, the old QR may remain visible. Recovery needs follow-up; not reproduced through forced response loss in this run.
- Code review finding: initial Pickup location acquisition is outside cancellable `locationJob`; safety loss while awaiting the first result may allow one late send. Needs a targeted race regression and fix; no real driving transition exercised.

## Test data and cleanup

Two API QA attempts created six separate anonymous test accounts total. Both QA Pickup sessions were closed; own uploaded synthetic files removed and QA Realtime connections revoked through existing authenticated APIs. No account-wide/admin deletion, database cleanup or unrelated record modification. Anonymous test account/device/idempotency metadata remains; no real personal/GPS data was used.

Official references: https://developers.cloudflare.com/tunnel/get-started/quick-tunnels/ and https://developers.cloudflare.com/cloudflare-one/networks/connectors/cloudflare-tunnel/downloads/ . Local installed Next docs for logging/custom-server options were checked. Quick Tunnel is ephemeral, has no production availability guarantee and does not support SSE; current Pickup uses Supabase WebSocket plus snapshots, not SSE through this tunnel.
## Local QA handoff

Services and emulator remain running for the user. The temporary origin is available from ignored backend/.cache/qa-runtime/pickup-public-qa.json. To close external access while preserving local servers, run backend/.cache/qa-runtime/stop-public-qa.ps1. The next QA origin changes after a tunnel restart. Final change is a local dev commit only; no push was performed in this QA turn.
