# 2026-11-11 QA follow-up — foreground ownership and connection readiness

## Baseline and scope

- Actual checkout: `C:/Users/ukhan/pleos-stay-plus`, branch `dev`, starting HEAD `dfcb3752e544f12ca48351c319e5598bc94a7428`.
- `git fetch origin dev` confirmed HEAD and origin/dev matched. Both earlier QA commits (`5a9fd96`, `f536877`) are ancestors. This was not a missing pull.
- Before this change, the installed APK SHA-256 matched the existing locally built Debug APK. That proves which binary was installed, not that all features were configured or working.
- Existing `web/next-env.d.ts` modification is unrelated and is excluded from this change.
- No emulator start/restart, app installation, deployment, cloud write, credential change, new SDK/permission, schema change or API-contract change was performed.

## Findings and changes

| Finding | Evidence / cause | Change | Result |
| --- | --- | --- | --- |
| P gear but foreground safety block | Previous read-only Android lifecycle diagnostics found overlapping MainActivity instances. A newer instance's onStart(true) preceded an older instance's onStop(false), both writing the process-shared gateway. The screenshot message was the background/foreground branch, independent of gear. | Process-scoped owner identity; only the current owner may release shared safety/Rest foreground state. API29+ uses top-resumed callbacks; API28 uses resume. Pause/stop remain fail-closed. singleTop reduces repeated top-instance launches; onNewIntent updates the Activity intent. | PASS JVM handoff/delayed-stop, background, repeat-callback and fresh-signal tests. Actual PLEOS callback rerun NOT RUN. |
| Giant Work/Study setup modal | MainActivity embedded the entire Realtime connection screen in a fixed 820dp preparation body. Missing configuration or PIN therefore appeared as nested settings with blank space. | Preparation now shows a content-sized prerequisite prompt, a route to File operations, an explicit readiness recheck, and return. It does not embed the setup screen. The existing Study duration input is retained in the compact prompt. Existing save/leave confirmation is retained for active document work. | PASS controller tests for no premature start/stop, busy guard and Work save confirmation. Visual/touch verification NOT RUN. |
| Generic connection error with no network attempt | The Debug local form validates a 32+ character token before any request; the shown empty token failed that check. The old button read Connect although it only saved settings. | Separate missing-token, malformed-URL and absent-service feedback; label changed to Save settings, plus return from developer settings. Saved configuration is not treated as an authenticated/paired PC. | PASS validation tests. |
| Real authenticated local Realtime server rejected | RealtimeApi already accepted Debug loopback HTTP; RealtimeDeployment rejected every non-public-HTTPS endpoint, including the documented authenticated port3012 development server. | Debug permits only the existing loopback hosts and canonical API path; real Supabase authentication remains required in the normal-auth path. Fixed-token QA mode is separately labelled. Release still requires public HTTPS and rejects fixed-token local QA. | PASS Debug tests for real-auth loopback acceptance and explicit production-mode rejection. Release Kotlin compile PASS. |
| QR failure disappears or progress looks complete | Successful periodic Pickup snapshots clear the general error even if invitation issuance failed. Active session and vehicle-position updates are separate from invitation readiness. | Invitation error has its own state and QR-panel display, survives successful polling and clears on successful issuance/end. No-invitation/no-participant active session is labelled invitation preparation. Vehicle-position status says updating rather than implying a connected participant. | PASS retry/idempotency/polling regression. Existing session/location/synchronization policy preserved. |

Safety is not unlocked by owning the foreground: fresh park gear, fresh zero speed, platform UX restrictions and existing PIN/auth gates are still required. No conversion of UNKNOWN to PARKED and no SDK method changes.

## Current environment blockers

- The current Android build has no `REALTIME_API_BASE_URL`. Existing Supabase URL/public settings are present, but cannot replace the Realtime Fastify endpoint.
- `backend/src/realtime/.env.local` is absent in the actual checkout. No Realtime server listener was found on 3010/3012 during the configuration check. Starting Parking on3000 and web on3001 alone does not start Realtime.
- 3012 is the existing real Supabase-authenticated development server. 3010 is a separate explicit local QA/fixed-token harness; its token is not a Supabase key. Neither is automatically started or configured here.
- Earlier decision remains: no approved public HTTPS address and no deployment. Pickup therefore correctly refuses external invitation issuance. Do not substitute localhost or a fabricated public domain.
- Work/Study still require the existing authenticated Realtime profile/connection and PIN. This prerequisite was explicitly approved earlier; the editor and local storage policy were not changed to bypass it.

## Verification

| Check | Result |
| --- | --- |
| `:app:testDebugUnitTest` | PASS: 212 total, 211 passed, 1 expected release-only Parking test skipped in Debug; 0 failures/errors. |
| `:app:assembleDebug` | PASS. APK built locally; not installed. |
| `:app:assembleDebugAndroidTest` | PASS compilation/package only; not executed. Relevant Pickup selector/error assertions updated. |
| `:app:compileReleaseKotlin` | PASS. |
| Backend `npm test` | PASS: 30 tests, including Parking normalization/fees, Pickup share-origin rejection, expiry/revocation/location cleanup, and Realtime real local HTTP pairing/file-transfer regressions. No Cloud E2E. |
| Separate `:app:testReleaseUnitTest` | NOT RUN: this checkout exposes no task with that name. No production Gradle configuration was changed just to enable it. Release rejection is additionally exercised by explicit false flags in JVM validator tests, not reported as a Release runtime test. |
| `git diff --check` | PASS. |
| PLEOS runtime, screenshots, launch/return/PIN transitions | NOT RUN per user's emulator restriction. |
| Real Supabase / Windows GUI / external Pickup QR E2E | BLOCKED by absent Realtime setup/public origin; no live Cloud writes/deployment attempted. |

## Protected surfaces and remaining QA

- Home renderer, home image/logo resources, shared Premium theme, Parking API/location adapter, Waiting/ModeStartManager policies, Study storage and DOCX engine are unchanged.
- New Debug APK must be installed by the user to exercise these changes. Building does not replace the currently installed/running app.
- Next checks on the user's device: launch/launcher/re-entry with P + fresh zero speed, background restriction, Work prerequisite navigation and PIN, configured real-auth Realtime pairing, QR issuance using an approved reachable origin, and error retry/end.
- Commit contains only these Android changes/tests and this report; no push as part of this follow-up.

## Official platform references checked 2026-11-11

- [Activity.onTopResumedActivityChanged](https://developer.android.com/reference/android/app/Activity#onTopResumedActivityChanged(boolean)): API29+ distinguishes top-resumed ownership from several simultaneously resumed activities. API28 fallback uses onResume.
- [Activity launchMode](https://developer.android.com/guide/topics/manifest/activity-element#lmode): singleTop reuses an existing top instance via onNewIntent; it does not guarantee a process-wide single Activity. Owner-aware release is still necessary.
