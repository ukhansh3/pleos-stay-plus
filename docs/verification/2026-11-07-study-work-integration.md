# Study / Work integration — 2026-11-07

## Baseline, ownership and approvals

- Repository: `ukhansh3/pleos-stay-plus`, working copy `C:\Users\ukhan\pleos-stay-plus`, branch `dev`.
- Starting HEAD: `28ca968ff8465aa7214803db42750b9a04bdd97d` (fast-forward from clean dev).
- Study agent owns `study/` and dedicated tests; Work agent owns `work/`, dedicated tests and isolated `tools/work-engine-poc/`; document agent owns `documents/` and reviewed bridge tests. Supervisor owns Activity/workspace wiring and minimal existing Realtime changes.
- User approved encrypted app-private/no-backup retention: lock and retain after revocation/user switch, manual deletion. Cached records never authorize reads.
- User approved only an isolated Aspose Android trial PoC using nonconfidential synthetic fixtures. No product engine selection, license purchase, publishing, external document upload or main-app SDK dependency was authorized.
- Existing ModeStartManager policies, Rest, Entertainment, Waiting/Parking/Pickup domain logic, backend contracts, DB/RLS and deployment are preserved. Preserved premium UI branches/assets are outside this change.

## Integration structure

`MainActivity` → existing `RealtimeEntry` configuration/PIN → `DocumentWorkspaceEntry` → independent Study/Work ViewModels → repositories/readers and `SharedDocumentGateway` → the **same** existing RealtimeApi / RealtimeWorkBridge.

- Entry routes retain all existing features and add Study/Work tabs. No new file server, auth provider or document REST API.
- Existing PIN verification remains the only unlock path. Document adapters do not obtain tokens themselves.
- Actual Android top-resumed lifecycle feeds foreground gating. Document screens set `FLAG_SECURE` and dispose in-memory models on PIN, safety or route exit. Waiting and pairing survive mode exit.
- Server endpoint SHA-256 + devices/me UID namespace document records. Exact connection/file/version and live server metadata are validated before reads, notes/progress restore and revision operations.
- Remote revocation is checked by foreground polling about every five seconds and per operation. HTTP response latency/timeouts can extend detection; this is not an instantaneous server-push subscription. Local safety/PIN/foreground locks mask content and cancel coroutine work without waiting for a server response.
- Existing upload journal is serialized, bound to the current connection, and reused only for the same key/base/name/type/bytes. Interrupted retries never silently replace pending content. Download cancellation cleans completed and partial temporary files.

## Implemented / intentionally blocked

- Study: actual native PDF rendering/page/zoom; bounded DOCX reflow of text/direct formatting/tables/embedded PNG/JPEG; UTF-8 TXT; side-note CRUD, exact-version progress and explicit restore; shared manager Ready/NeedsChoice/failure handling; encrypted persistence.
- DOCX is a reflow reader, not Word pagination or full layout fidelity. Complex styles, equations, headers and non-PNG/JPEG pictures are not claimed.
- Work: shared manager timer/time choices, real library/version selection, hash-verified source retention, independent encrypted opaque-draft persistence/recovery boundaries, masking and manual deletion for currently authorized records.
- Work's PDF/DOCX create/edit/export/upload controls remain explicitly disabled until a proven engine and product licensing are approved. Retaining source bytes is not editing a file. Evaluation PoC is not production adoption.
- Former-connection retained records stay locked. A UI to erase inaccessible prior-identity records under a separately verified local identity has not been added; OS app-data clearing remains possible. No new authentication policy was invented.
- New original upload from VEHICLE is not exposed: local route/SQL review alone does not prove deployed Cloud permission. No live DB changes or Cloud write probes were performed.

## Evidence

See [Study](2026-11-07-study-implementation.md), [shared documents](2026-11-07-shared-documents.md), [Work engine](2026-11-07-work-engine-review.md).

The following commands actually ran. Compilation and fixture tests are not a complete paired-device E2E substitute.

- Backend regression: `npm test` — PASS, 28/28; initial sandbox EPERM prevented spawning, escalated local rerun passed.
- Backend typecheck/build/OpenAPI validation — PASS.
- Pleos Connect v2.1.0 AVD — booted, API34, `Connect_on_x86_64_emulator`; no wipe/reset.
- Real paired Windows→Cloud→vehicle→Windows flow — NOT RUN. No new approval to mutate live services was assumed.

## Official Android references

- [Top-resumed activity lifecycle](https://developer.android.com/reference/android/app/Activity#onTopResumedActivityChanged(boolean)) — multi-window focus handling.
- [FLAG_SECURE](https://developer.android.com/reference/android/view/WindowManager.LayoutParams#FLAG_SECURE) — protects sensitive document windows from screenshots/non-secure displays.
- [PdfRenderer](https://developer.android.com/reference/android/graphics/pdf/PdfRenderer) — native viewer; not proof of body editing on API34.

## Final verification matrix

| Check | Result | Actual evidence / limit |
| --- | --- | --- |
| `:app:assembleDebug :app:testDebugUnitTest :app:assembleDebugAndroidTest --max-workers=2` | PASS | Final run succeeded; 116 JVM tests, zero failures/errors. Includes Study13, Work17, documents8, existing ModeStartManager15, Rest23 and Parking/Waiting/Pickup/Realtime tests. |
| Pleos API34 instrumentation | PASS | 9 tests in 15.022 seconds: native Study3, real encrypted document store1, loopback Realtime bridge4, existing isolated Waiting DB1. No live auth bypass. |
| Final Debug APK normal launch | PASS, smoke only | Installed without clearing data; `am start -W` returned Status ok / COLD, process remained alive, no FATAL AndroidRuntime entry for that PID. UI interactions/typography were not visually inspected. |
| Backend regression / typecheck / build / OpenAPI | PASS | 28 tests; contract validator reported OpenAPI3.1/local references OK. Backend code unchanged. |
| Isolated candidate APK build/install/API34 execution | PASS | Official PDF23.2 + Words25.9 Android artifacts, separate package and no INTERNET permission. Main app has no new dependency. |
| Independent synthetic PDF export verification | PARTIAL PASS | `verify_export.py` using pypdf6.10.0 verified all five checks on revision3 exported bytes: three pages, order, original body absence, deleted page absence, actual blue replacement image. Poppler revision2 page2 also visually inspected. This proves one ASCII fixture's real object edits, not Korean or arbitrary document fidelity. |
| Candidate PDF's final self-validation | FAIL | Revision3 still reports PDF_BODY_REPLACE_FAILED; engine text extraction is truncated. Official trial four-item limits may contribute, but this cause is not proven. Do not replace this failure with the independent partial PASS. |
| Words Android actual runtime | FAIL / BLOCKED | ART VerifyError in Node constructor/uninitialized StringBuilder, propagating to DocumentBase/Document. No DOCX edit/export ran and no verifier/bytecode bypass was attempted. |
| Product Work editor / create / export / publish | BLOCKED | No approved compatible production engine/license; controls explicitly disabled. Trial approval is not product adoption. |
| Real Windows / Cloud / RLS / Storage E2E | NOT RUN | Fixture/loopback tests do not establish deployed Cloud or Windows behavior. No live migrations, deployment or external document upload. |
| Full Study Compose UI and vehicle safety-transition E2E | NOT RUN | Native renderer/Keystore and domain lock tests passed; complete authorized file/PIN/UI flow and physical vehicle support require further runtime evidence. |

Initial test-compilation issues (test safety fake type erasure and PdfDocument not implementing Closeable) were corrected. The deep XML test initially expected only the custom error, although host JAXP rejected it earlier under its stricter built-in depth limit; assertions now recognize that security rejection without weakening product limits. Final suite is green.

Sensitive local settings remain ignored (`backend/.env`, `web/.env.local`, `android/local.properties`). SDK archives/JARs, trial APKs, fixtures, exports and license files are ignored. Actual local key values were checked against changed source without printing them; no matches. No production editor/Gradle dependency, DB, backend, Rest/Entertainment, Waiting/Parking/Pickup domain changes.

## Changed files and next gates

- Common integration: `MainActivity.kt`, `workspace/DocumentWorkspaceEntry.kt`; existing RealtimeEntry, RealtimeLockedScreen, FileTransferViewModel and RealtimeApi minimal seams/retry/cleanup changes.
- Shared documents: SharedDocumentGateway, DocumentTransport, RealtimeDocumentGateway, AndroidEncryptedDocumentStore; JVM gateway tests and Android encrypted-store/Realtime bridge tests.
- Study: AndroidStudyStorage, StudyReader, StudyRepository, StudyViewModel, StudyScreen; StudyReader/Repository/ViewModel JVM tests and NativeStudyReaderDeviceTest.
- Work: WorkModels, WorkDraftRepository, WorkViewModel, WorkScreen; WorkDraftRepositoryTest and WorkViewModelTest.
- Contract and evidence: DOCUMENT_WORKSPACE_CONTRACT retention decision; four 2026-11-07 verification reports.
- Isolated evaluation: `tools/work-engine-poc` settings/build/manifest/EngineProbe, ignore rules and independent `verify_export.py`. Vendor binaries are not committed.

**Integration judgment:** the proven vendor-independent implementation can be integrated on dev with the above explicit limitations. Study is implemented and platform-tested within its documented reflow/native-reader scope; full live paired E2E is not complete. Work advanced editing is not complete. Next gates are a vendor-supported DOCX runtime or approved alternate evaluation, licensing/fidelity approval, real paired Windows/Cloud tests, full vehicle UI/safety lifecycle tests, and reviewed deletion UX for inaccessible prior identities. Future Premium UI can replace screens while keeping the independent ViewModel/repository contracts.

Git starting HEAD is recorded above; final commit IDs and remote push verification are reported by the supervisor after commits. `main` remains `0ea77f3394b74cd873055b2c4358893ed6e95653`.
