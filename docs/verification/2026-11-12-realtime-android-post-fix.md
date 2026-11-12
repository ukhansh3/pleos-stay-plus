# Realtime Android control POST runtime fix — 2026-11-12

## Runtime evidence

Baseline dev HEAD: `1de17cf`. Existing unrelated `web/next-env.d.ts` and Next.js-generated `web/AGENTS.md` were preserved.

The actual user-triggered Android requests reached the running authenticated Supabase-backed Realtime server:

- POST /devices: HTTP200.
- GET /connections/current: HTTP200.
- POST /pairings: HTTP500, with internal Fastify error `FST_ERR_CTP_INVALID_MEDIA_TYPE` in ContentTypeParser.run.

The previous healthy unauthenticated HTTP401 probe proved that the process was listening and enforcing authentication; it did not prove Android code issuance worked. Read-only checks also confirmed enabled anonymous Auth and available Realtime schema/RPC. No schema/RLS change was needed.

A standalone Fastify injection reproduction used synthetic auth and pairing responses, no live database: a body-less form-encoded POST returned the old misleading HTTP500; no Content-Type and explicit JSON `{}` both reached the handler successfully. This reproduced the Android HttpURLConnection implicit form-encoding failure path that C#-only host tests had missed.

## Changes

- `RealtimeApi.call` now sends an explicit UTF-8 JSON body for POST/PUT/PATCH control writes, including `{}` when callers supply no body. Pairing issuance, approval and upload completion all use this boundary. GET/DELETE remain body-less. Binary uploads retain their separate octet-stream API.
- Backend error handling preserves framework HTTP4xx statuses. Unsupported media returns415 / UNSUPPORTED_CONTENT_TYPE, malformed JSON returns400 / INVALID_INPUT, and unknown server exceptions remain500 / INTERNAL_ERROR. Unsupported form data is still rejected; no broad form parser was enabled.
- Added Android payload contract tests and real route injection regressions for code issuance/approval/completion media-type rejection, explicit JSON success and malformed JSON400.

## Validation

- PASS Android Debug build and full JVM tests: 221 total, 220 passed, 1 expected Release-only Parking test skipped, failures0.
- PASS Backend31 tests and TypeScript noEmit. Existing Parking/Pickup/Realtime regression tests included.
- Updated the running Realtime QA process with the fix; background Cloud cleanup remains disabled.
- Installed the fixed APK with `install -r --user 10`, preserved app data, and relaunched MainActivity successfully. The emulator itself was not restarted. Existing Windows Stay+ and other servers remain running.
- Actual post-fix Android code issuance: awaiting the user's PIN/connection retry at the time of this record. Diagnostic output checks only response shape and HTTP status, never the code/token/UID.
- Android-to-Windows final approvals/file transfer after this fix: NOT RUN by the agent. Remain explicit user actions; no automated approval or file upload.
- Release build not repeated during this live emulator QA to limit resource use. This change adds no SDK, permissions or build dependencies.

## Separate Pickup blocker

The screenshot's Pickup error is the existing share-origin guard: an approved reachable public HTTPS Next.js origin has not been supplied. Starting local services on3000/3001 does not create a public mobile address. Localhost must not be emitted as a phone share URL. No domain/tunnel/hosting/provider was selected, no sharing scope was changed, and no deployment was performed.

## Diagnostics / privacy

QA hooks record only canonical route templates, HTTP status, known error codes and function names; no tokens, request bodies, user IDs, filenames or pairing codes. The runtime launcher lives under ignored backend/.cache/qa-runtime and is not product source. Automated Windows capture timed out, so the agent did not issue blind coordinate input or automate PIN authentication.
