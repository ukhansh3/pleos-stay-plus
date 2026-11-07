# Study implementation — 2026-11-07

Base: `dev` / `28ca968`. Study agent scope: new Study sources and dedicated tests only. Integration, Git, build and broader regression execution are owned by the supervisor; see its final verification report for resulting HEAD and commands/results. No server API, Supabase schema, shared mode policy, or existing Rest/Entertainment implementation was changed by this agent.

## Implemented boundaries

- STUDY-01: `StudyViewModel` calls the actual `ModeStartManager` using `ActivityMode.STUDY`. Ready alone starts the timer. All result variants are handled; EXTEND/SHORTEN/BACK use the original prompt and `resolve`. Failed storage/recovery prevents start. Study end/route exit stops only Study, preserving Waiting and pairing.
- STUDY-02: injected `SharedDocumentGateway` lists canonical files then exact versions; verified download and fresh `validate(ref)` are required before display and again before local-record restoration. The shared gateway owns foreground polling, pairing, PIN, identity, SHA-256 and HTTP; Study creates no credentials or server routes.
- STUDY-03: native `PdfRenderer` reads actual page count, renders selected pages and zooms with memory bounds. Descriptors/renderer/page handles are closed per operation. Invalid/encrypted PDFs produce an error; no fake text preview or password guessing.
- STUDY-04: native bounded OOXML ZIP/XML reader provides Korean/Unicode paragraph text, direct bold/italic/underline/font-size/color, table cells, embedded PNG/JPEG, and explicit unsupported messages. It does not execute links or fetch external resources. DTD/entity declarations, archive traversal and oversized parts are rejected. This is a reflow viewer: no claim of Word-identical pagination, inherited styles, equations, headers, floating image placement, or full OOXML compatibility. Strict OOXML namespaces are unsupported.
- STUDY-05: strict UTF-8 TXT reader preserves character offsets, rejects invalid encoding, and splits long text into actual reading sections.
- STUDY-06/07: side-note CRUD, position/progress and manual completed flag persist by exact user/connection/file/version. Native AES-GCM/AndroidKeyStore encrypts records and last-material pointer under `noBackupFilesDir`. Record identity is authenticated as GCM associated data. Reopen validates the current server reference; retained bytes grant no offline access. Corruption produces an error instead of silently replacing data with empty records. Atomic replacement preserves previously committed records on failed writes. Note input clears only after a confirmed authorized save.
- STUDY-08: safety, meaningful shared-access revision/identity changes, route exit and backgrounding clear displayed documents, notes, rendered pixels and temporary sources. Existing UNKNOWN/DRIVING/UX restrictions are used; no synthetic parked signal. Encrypted notes/progress stay locked until explicit user deletion, per the approved retention decision. Screen privacy/recents and actual lifecycle are handled by the supervisor's common entry host.

UI state and domain/storage/reader responsibilities are separated. `StudyScreen` has actual file/version selection, page/section movement, zoom, side notes and completion controls. It is functionality-first and does not claim the final Premium redesign. `StudyViewModel` is constructed with the shared mode manager, safety gateway, document gateway and Study repository; parent calls `setForeground(topResumed && currentRoute)`, `leave()` on route exit and `close()` on disposal. An active timer is not silently resumed after process death; the user explicitly starts Study then selects “마지막 학습 복원”.

## Files

- `android/app/src/main/java/com/pleosstay/app/study/StudyRepository.kt`
- `android/app/src/main/java/com/pleosstay/app/study/AndroidStudyStorage.kt`
- `android/app/src/main/java/com/pleosstay/app/study/StudyReader.kt`
- `android/app/src/main/java/com/pleosstay/app/study/StudyViewModel.kt`
- `android/app/src/main/java/com/pleosstay/app/study/StudyScreen.kt`
- `android/app/src/test/java/com/pleosstay/app/study/StudyRepositoryTest.kt`
- `android/app/src/test/java/com/pleosstay/app/study/StudyReaderTest.kt`
- `android/app/src/test/java/com/pleosstay/app/study/StudyViewModelTest.kt`
- `android/app/src/androidTest/java/com/pleosstay/app/study/NativeStudyReaderDeviceTest.kt`
- this verification record.

## Verification status at agent handoff

No Gradle process or emulator was launched by the Study agent, to avoid concurrent builds or unauthorized runtime claims. Supervisor results supersede this handoff state.

| Check | Status at handoff | Evidence / remaining action |
| --- | --- | --- |
| Official native API/format documentation | PASS (documentation review only) | Sources below; no third-party artifact added |
| Study JVM tests (13) | NOT RUN by Study agent | 3 repository, 4 reader, 6 VM integration tests; supervisor runs Gradle |
| Native instrumentation tests (3) | NOT RUN by Study agent | Actual two-page PDF rendering/pixels; actual Korean/table/PNG OOXML; actual Keystore restart persistence |
| Android build and full regressions | NOT RUN by Study agent | Supervisor owns coordinated build/test |
| Real Windows → Realtime → Study | NOT RUN | Requires paired/authenticated actual runtime; fixtures do not establish this result |
| Pleos API34 UI, typography, memory and safety transitions | NOT RUN | Device evidence required; native platform support alone is insufficient |
| Existing Parking/Waiting/Pickup/Rest/Entertainment | NOT RUN by Study agent | No direct edits; supervisor regression suite required |
| New external dependencies or permissions | NONE | Native Android/JVM APIs and existing Compose/coroutines only |

JVM tests cover notes CRUD/restart/version isolation/encryption, tamper and swapped ciphertext rejection, length-prefixed identity separation; actual OOXML parsing of Korean run formatting/table/image bytes, DTD/path rejection; TXT offset and invalid UTF-8; real shared mode Ready/recovery/invalid duration/storage failure/extend/shorten/back; Waiting preservation; revoked/UNKNOWN/DRIVING/background masks; source-validation failure; note-save success acknowledgement absent on failure. JVM test fixture image bytes verify parser extraction only; valid PNG decode is reserved for instrumentation.

Remaining acceptance gaps include full real-server paired E2E, device Korean font fidelity, complex DOCX corpus compatibility, encrypted/corrupt PDF device samples, renderer memory/performance, screenshot comparison, and physical vehicle signals. No PDF editing, Word editing or Study HTTP endpoints are provided.

## Official sources checked

- [Android PdfRenderer](https://developer.android.com/reference/android/graphics/pdf/PdfRenderer): actual page count/open/render/close and native availability; no Pleos SDK method invented.
- [Android cryptography](https://developer.android.com/privacy-and-security/cryptography) and [Android Keystore](https://developer.android.com/privacy-and-security/keystore): AES-GCM with protected keys; runtime is tested separately.
- [Microsoft WordprocessingML overview](https://learn.microsoft.com/en-us/office/open-xml/word/overview) and [tables](https://learn.microsoft.com/en-us/office/open-xml/word/working-with-wordprocessingml-tables): document text/paragraph/run/table structures.
- [Office Open XML fundamentals](https://download.microsoft.com/download/e/1/4/e14fb96f-83b8-4a2a-84db-7fa8acbe061a/Office%20Open%20XML%20Part%201%20-%20Fundamentals.pdf): package/relationship structure.
- [Android DocumentBuilderFactory](https://developer.android.com/reference/kotlin/javax/xml/parsers/DocumentBuilderFactory): documents `FEATURE_SECURE_PROCESSING` as unsupported on Android, so this implementation does not assume that feature. DTD/entity rejection, entity resolver denial, bounded SAX preflight (depth 128 / 100,000 elements), iterative post-parse DOM bounds and part-size limits precede recursive content extraction. Deep and excessive-element fixtures test the resource limits separately from normal documents.

The mandatory Git documents and Study package instructions were reviewed. Supervisor supplied the fetched Notion Study v1.0 and common-mode retrospective and confirmed alignment with the Git contract. No Notion write was performed.

## Supervisor execution update — 2026-11-07

The supervisor subsequently executed the coordinated Android checks:

| Executed check | Result |
| --- | --- |
| `assembleDebug` | PASS |
| `assembleDebugAndroidTest` | PASS |
| `testDebugUnitTest` | PASS — 116/116 total, including all 13 Study tests |
| `NativeStudyReaderDeviceTest` on actual Pleos API34 emulator | PASS — 3/3: AndroidKeyStore persistence, actual Korean/table/PNG OOXML extraction and PNG decode, native PDF actual page-count/pixel rendering |

The first JVM run rejected a deeply nested XML fixture at the host JAXP's stricter built-in depth limit 100 (`JAXP00010006`) before the explicit SAX depth limit 128. The test originally required the application-specific rejection message and therefore reported a false failure. The assertion now accepts that precise built-in resource-limit rejection as well; no production resource limit was relaxed. The final full run passed.

These executed results supersede the corresponding NOT RUN handoff rows above. Device tests verify native parsing/rendering/storage behavior; they do not prove full Compose GUI Korean font fidelity, visual design, real vehicle safety signals, or paired Windows→live Realtime→Study E2E. Those acceptance checks remain NOT RUN. No Cloud/DB write or deployment is claimed by this update.
