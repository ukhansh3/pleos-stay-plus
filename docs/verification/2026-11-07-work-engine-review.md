# Work engine review — 2026-11-07

## Scope and current gate

Read Work Notion v1.0, common ModeStartManager retrospective, root/work AGENTS, DEVELOPMENT_GUIDE §22, Work feature spec, document workspace contract, operation matrix and actual Realtime/ModeStartManager source. The product requires actual existing PDF body-object edits and rich DOCX roundtrip. Annotation overlays and Office-to-PDF conversion do not satisfy that requirement.

**No production editor engine selected.** User approved only an isolated Aspose PDF/Words Android evaluation PoC with original nonconfidential fixtures. This does not approve commercial licensing, product adoption, publishing an evaluation APK, or external document upload. No editor SDK is added to the main Android app. All editor/create/export/upload buttons in the Work app remain BLOCKED.

## Primary-source candidate comparison

| Candidate | Official evidence | Important gaps |
| --- | --- | --- |
| Aspose.PDF Android + Aspose.Words Android | [PDF overview](https://docs.aspose.com/pdf/androidjava/overview/) explicitly includes text/image replacement and page manipulation. [Android Words feature matrix](https://docs.aspose.com/words/java/aspose-words-for-android-via-java-features/) includes paragraph, text, image, table and cell modification. Programmatic APIs allow a native adapter; editor UI must be built separately. | [PDF requirements](https://docs.aspose.com/pdf/androidjava/system-requirements/) lists API31/32 and older versions, so it is insufficient evidence for API34/PLEOS. Real ABI/device, Korean font/layout, output fidelity, memory and performance need actual tests. Words Android is a distinct classifier; do not substitute desktop Java binaries. |
| Nutrient Android PDF + Document Authoring | [Android Content Editor announcement](https://www.nutrient.io/blog/pspdfkit-android-8-7-adds-content-editor/) documents actual PDF text editing since Android8.7. [Authoring](https://www.nutrient.io/sdk/solutions/authoring/) describes DOCX import/export and rich document authoring locally in the browser. | Browser-based authoring is a separate product, not proof of native Android DOCX editing. Android WebView/AAOS/offline embedding, image/object coverage and production licenses need verification. [Pricing](https://www.nutrient.io/sdk/pricing/) is component/platform based with sales quotation; trial is evaluation only. |
| Apryse Mobile + possible WebViewer DOCX | [Mobile documentation](https://docs.apryse.com/mobile) describes native document viewing/annotation/text editing. [Android Office API](https://docs.apryse.com/android/guides/features/office/apis) specifically describes conversion to PDF. | DOCX viewing/conversion does not prove native DOCX open-edit-export. Browser editor embedding, exact Android support, licensed modules, fonts/fidelity and price would need separate confirmation. Not selected or installed. |

Aspose is a bounded **PoC candidate**, not a claim of the best product or complete compatibility. General Java feature pages cannot override the Android-specific API surface. The downloaded Android JARs' public signatures were inspected with javap, then the isolated probe compiled against the actual Android platform JAR.

## Platform-only / custom alternatives

[Official PdfRenderer.Page reference](https://developer.android.com/reference/android/graphics/pdf/PdfRenderer.Page) identifies `applyEdit(FormEditRecord)` as API35 form-widget editing; page-object add/remove is version36.1. [Android viewer guide](https://developer.android.com/develop/ui/views/layout/pdf/pdf-viewer) distinguishes older rendering APIs and newer APIs/backport. These do not establish existing PDF body text/image/page editing on the current PLEOS API34 emulator. Raising compileSdk does not raise the emulator API. No such unsupported call was added.

[Microsoft WordprocessingML structure](https://learn.microsoft.com/en-us/office/open-xml/word/structure-of-a-wordprocessingml-document) documents ZIP/XML parts, paragraphs, runs and formatting. A selective ZIP/XML editor can theoretically preserve untouched parts, but this is not a rendering/layout or rich editing engine. A bespoke complete OOXML editor would require separate design, security, formatting/layout and roundtrip acceptance work. It is not silently substituted for the requested verified editor.

## Licensing / price / evaluation limits

[Words licensing](https://docs.aspose.com/words/java/licensing/) specifies evaluation watermarks on load/save and a few-hundred-paragraph limit, with a separately requested 30-day temporary license option. [PDF Android licensing](https://docs.aspose.com/pdf/androidjava/licensing/) documents its own evaluation restrictions. No license key was requested, purchased or committed. The unlicensed evaluation output can contain watermarks and must not be offered as production exports.

[Words Android pricing](https://purchase.aspose.com/pricing/words/android-java/) was checked: Developer Small Business US$1,199 and Developer OEM US$3,597 were displayed. [PDF family pricing](https://purchase.aspose.com/pricing/pdf/family/) displayed US$1,679 and US$5,037 respectively. These are page snapshots, not a quote or an entitlement determination. [Android license types](https://purchase.aspose.com/policies/android-license-types) tie entitlement to developers/apps/deployment locations; the correct PLEOS distribution tier and commercial terms require vendor confirmation and user approval. No checkout, payment, account or paid service activation occurred.

## Isolated PoC artifacts and reproducibility

`tools/work-engine-poc/` is a separate Android project, package `com.pleosstay.workenginepoc`, with no INTERNET, vehicle, location or external-storage permission. It cannot access real Work documents, Realtime credentials or the production app's private directory. All fixtures are generated from original synthetic text/shapes/solid-colour images. No documents are uploaded externally.

Verified official artifacts:

- PDF Android23.2 official [release page](https://releases.aspose.com/pdf/androidjava/new-releases/aspose.pdf-for-android-via-java-23.2/); downloaded ZIP 54,479,059 bytes, SHA256 `3cf9926d530f9b124d0c9266d536785b836e62e81542a8fa44d17d4cd04a8de7`. The archive contains `lib/aspose-pdf-23.2-android.via.java.jar` (55,157,913 bytes), javadoc and readme. Official Maven23.2 directory did not list an Android classifier; the Android release archive was used instead of guessing one.
- Words Android25.9 [official Maven directory](https://releases.aspose.com/java/repo/com/aspose/aspose-words/25.9/), `aspose-words-25.9-android.via.java.jar`, 16,899,982 bytes, SHA256 `6414b4edaec9f5207ecb0fe241a49bdf12a4a06dadcaeb5434d0fe5c4a8d793e`.
- Artifacts, generated fixtures/exports/reports, build folders and license files are ignored by the local PoC `.gitignore`.

Root coordinator alone runs Gradle/emulator; commands from repository root:

```powershell
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
.\android\gradlew.bat -p tools/work-engine-poc :app:assembleDebug --console=plain
$adb = "$env:ANDROID_HOME\platform-tools\adb.exe"
& $adb -s emulator-5554 install -r tools/work-engine-poc/app/build/outputs/apk/debug/app-debug.apk
& $adb -s emulator-5554 shell am instrument -w com.pleosstay.workenginepoc/.EngineProbe
# Generated fixtures/report in files/evaluation-output inside this isolated debug package.
```

The probe uses Android PdfDocument to create three pages with existing text, Korean text and an image. It locates and changes original PDF text fragments, replaces an image resource, adds/deletes/reorders pages, exports/reopens, checks extracted content, and independently renders with Android PdfRenderer. DOCX probing creates/reopens and edits text, run formatting, table cell and image; exports/reopens and independently inspects ZIP/OOXML structure, then generates a preview PDF.

These smoke checks do not prove arbitrary file compatibility. Independent text extraction/render comparison, Korean replacement/font embedding, image pixel confirmation, original rich externally authored DOCX, Windows Word/compatible viewer roundtrip, protected/scanned/signed PDFs, large-file memory, process termination and latency still require explicit results. Failure in one engine does not suppress the other engine's report. Error details are confined to the isolated synthetic test report.

## Verification status at handoff

| Check | Status |
| --- | --- |
| Official Android evaluation artifacts downloaded and SHA256 recorded | PASS |
| Actual Android37.0 platform + Android candidate JARs javac --release11 compile | PASS |
| Production SDK selection/license/dependency | BLOCKED — not approved; PoC approval only |
| API34/PLEOS PDF real body/image/page fixture edit | PASS bounded standard-PDF fixture in independent extraction/rendering; not full editor acceptance |
| Aspose PDF probe self-validation, final revision3 | FAIL — own text extraction is truncated; independent exported-file checks remain separately PASS |
| API34/PLEOS DOCX runtime | FAIL / BLOCKED — actual ART verifier rejection before document creation |
| Main Android debug build and JVM regression tests | PASS — root final build and 116 JVM tests, including all 17 Work tests |
| Source/opaque draft store and UI/mode implementation | PASS automated Work storage/access/mode tests; advanced editing remains BLOCKED |
| Valid content:// editor output + Realtime revision to Windows | BLOCKED — no accepted editor output yet |
| New original VEHICLE upload | BLOCKED — server/Cloud permission not verified |
| Commercial distribution, Korean fidelity, performance | BLOCKED pending actual evidence and licensing decision |

### Actual API34 first probe — root-coordinated execution

Root built the isolated APK successfully (6m23s), installed it on the PLEOS API34 x86_64 emulator and executed the probe. **Both engine smoke paths failed; neither is accepted for production.**

- PDF23.2: `IllegalStateException: PDF_NO_ORIGINAL_TEXT_OBJECT` on exact phrase search in the Android PdfDocument/Skia-generated fixture. This is a failed test, not proof that every PDF is uneditable. Revision2 preserves that fixture, dumps extracted original text, and adds a separately authored standard PDF with actual Helvetica text/image objects and explicit xref to distinguish font mapping/fixture issues from editing support.
- Words25.9 Android: ART `VerifyError`, rejected `Document` because parent `DocumentBase` was erroneous (`classes7.dex`). It failed before a DOCX could be opened/created. License selection or successful Java compilation cannot turn this into support. Root is collecting the actual verifier cause. Official documentation search did not establish a matching known D8/API34 workaround. The downloaded `DocumentBase.class` is class major50 (Java6); calling this simply a Java17 bytecode incompatibility would be unsupported.
- Revision2 limits synthetic exception cause chains to eight entries/1600 characters each and targets the actual `EDITED_DOCX` run, rather than assuming firstRun remains user content after trial watermark insertion. It also finds a real image with `hasImage`, changes Korean text and checks target formatting on reopen. These strengthened checks are not yet passing results.

Downloaded binary inspection: PDF Android JAR SHA256 `73adccd3af90a408cae92dfd184918c3f3e5da71f54c07a6a188e0477943d763` contains `libtiff`/`libtiffdecoder` resources for arm64-v8a, armeabi-v7a, x86 and x86_64 among older ABIs. This is resource presence, not proof of APK native packaging/loading or every feature's ABI compatibility. Words Android JAR contains no `.so` entries. No undocumented loader or verifier bypass was added.

### Root integration verification received

- **PASS:** final main-app Android debug build and all 116 JVM tests. Work contributes 17 tests: nine repository tests for exact-version restoration, dirty snapshot preservation, version/owner isolation, revoked access, explicit local deletion, malformed bytes/retained records and unsupported format rejection; eight ViewModel tests for real ModeStartManager Ready/NeedsChoice, shortened duration, Waiting/pairing preservation, RecoveryRequired, duplicate taps, UNKNOWN/PIN/background locks, identity redaction versus actual user switching, invalid duration and cancellation/retry.
- **PASS:** root's nine native Android tests across shared Keystore/Realtime bridge, Study and Waiting. These are **not** Work editor acceptance E2E and do not establish PDF/DOCX support.
- **PASS:** `git diff --check` for owned Work/PoC paths; SDK JAR/ZIP, generated output/classes/builds and licenses are ignored. No actual source keys, token logging or document-content logging were added to the main Work package.
- **Diagnostic evidence:** root independently extracted the Android-generated PDF with pypdf; ASCII appeared as `O RIG IN AL_BO DY_1` with artificial spacing while Korean text extracted. Exact-phrase matching therefore failed for that fixture. This supports separating the fixture/encoding issue from the general body-edit capability; it does not turn the failed probe into a pass.
- **NOT RUN / BLOCKED:** full real Work edit/export/reopen/Windows revision roundtrip, production editor dependency adoption/license approval, new-original VEHICLE upload, complex external DOCX fidelity, scanned/signed/protected PDF behavior, CJK new-glyph substitution and performance acceptance.

### Revision2 independent PDF verification and bounded revision3

Revision2 successfully created `edited.pdf`, then its own TextAbsorber exact-token assertion reported `PDF_BODY_REPLACE_FAILED`. Root independently inspected the resulting file with pypdf and Poppler; the exported document itself passed these bounded checks:

- Three pages; original text `ORIGINAL_BODY_1` absent and replacement `EDITED_BODY_ONE` present.
- `NEW_PAGE_FOUR` present; deleted `ORIGINAL_BODY_2` absent.
- Correct page sequence: original third page → edited original first page → newly added page.
- Image replacement present: independently decoded 4,800 pixels were blue `(0,0,254)` instead of the original red image. Independent page 2 Poppler rendering was visually checked. Evaluation watermarks remain.

Revision3 preserves raw extraction **before** assertions and removes whitespace only for comparison with these explicitly whitespace-free test tokens; no source/export bytes are rewritten and the required checks are not removed. It additionally checks each page's expected token and records per-page raw extraction. Javac compilation passed. Root executed revision3 and **it still reported `PDF_BODY_REPLACE_FAILED`**. The raw engine-extracted output contained an evaluation notice and was truncated; the standard original extraction was also truncated. Whitespace normalization did not resolve this failure, so the earlier possible whitespace explanation is not established as its cause.

[Official PDF Android evaluation limitations](https://docs.aspose.com/pdf/androidjava/licensing/) state that only four items from a collection can be processed and that output is watermarked. Evaluation limits may explain the truncated extraction, but that causal link is an inference, not confirmed vendor evidence. We retain the failing self-validation result and separately record root's independently verified exported-file capability. No license restrictions were bypassed, and no temporary license was requested or applied. The bounded PoC is stopped here; no vendor-bytecode edits, verifier bypass, undocumented loader or unbounded SDK-version retry was performed.

**PDF result is a partial capability proof only:** direct existing ASCII body object replacement (not overlay), image resource replacement and page operations on this independently authored standard fixture worked on the actual API34 PLEOS emulator. The Android/Skia CJK fixture's extraction ambiguity, actual Korean new-glyph editing/font embedding, arbitrary customer documents, password/signature/scans, performance and complete editor UX remain unverified. There is no approved production editor dependency or commercial entitlement.

**Words result remains BLOCKED:** root's actual ART verifier diagnostics show `Node.<init>` returning without a superclass constructor and an uninitialized StringBuilder register in `zzYM1`; `DocumentBase`/`Document` are rejected. These are runtime observations under the isolated official Android25.9 artifact/AGP9.5/API34 combination, not a proven universal Aspose product defect or an officially confirmed workaround. DOCX creation, formatting, table/image edit and export did not run. The feature cannot be described as working, and vendor support or a separately approved alternate PoC is needed before adoption.

## Final Work handoff status

Supervisor's final revision3 independent check: `verify_export.py output/revision-3` using pypdf6.10.0 passed all five fixture checks against the actual final exported PDF. SDK raw results and exports are retained only in ignored local output; reproducible checker/source are tracked. This does not change the candidate's reported self-validation FAIL or Words runtime BLOCKED status.

- **PASS:** vendor-independent Work route/UI/ViewModel/repository integration; same live Realtime access/PIN/identity gateway; ModeStartManager and safety handling; encrypted source/opaque-draft retention seam; all 17 Work JVM tests; main-app build/regressions as reported above; isolated official-artifact compilation/build/install and actual API34 execution.
- **PARTIAL PASS:** one standard synthetic PDF's real ASCII text/image/page edits, independently extracted/rendered after export. This is not the Work production editing feature.
- **FAIL / BLOCKED:** final PDF candidate self-validation, Words Android runtime, production engine selection, actual advanced editor/new-document/export/undo-redo, Korean PDF text replacement, complete rich DOCX roundtrip, Work-to-Windows revision E2E and new-original upload permission. These UI actions remain disabled with an explicit BLOCKED reason.
- **Review required:** a vendor-supported SDK fix or approved alternative evaluation, appropriate distribution license/temporary license (if desired), font/fidelity/performance proof, and a verified local-user flow for explicit deletion of previously revoked connections' locked retained records. No arbitrary authentication boundary was added to expose those records.
- No main/backend/DB/Rest/Entertainment/Waiting/Realtime policy, vehicle permission or production editor dependency was changed by the Work agent. No cloud upload/deployment, payment or publisher decision occurred.

## Main-app implementation boundary

WorkModels / WorkDraftRepository / WorkViewModel / WorkScreen are independent of editor vendors. Existing `SharedDocumentGateway`, Realtime authorization/PIN, encryption helper, safety gateway and ModeStartManager are reused. Ready alone starts the timer; NeedsChoice/resolve and adjustEnd handle Waiting time choices. Exit leaves Waiting and pairing intact. Current UI can select exact versions, verify source bytes and retain encrypted original snapshots; it explicitly calls these source snapshots rather than edited files.

The draft codec preserves immutable original bytes plus an optional future engine-owned opaque snapshot atomically; it keys exact owner/connection/file/version and never deletes on lock or owner switch. Same-authorized source reimport preserves dirty engine bytes. Restoration revalidates current server authorization and original hash. Explicit local deletion is separate and does not touch the server. The app currently has no approved engine adapter to generate snapshots or editable/exported files; save/edit success is not claimed.

Host injects the existing live gateway into `WorkDraftRepository(AndroidEncryptedDocumentStore(context, "work-v1"), documents)` and `WorkViewModel(documents, drafts, modeStartManager, safetyGateway)`. `WorkScreen(model, onRealtime, onRecoverWaiting, onHome)` is display/actions only. Host sets `setForeground` on route/lifecycle changes and invokes `close()` for host-owned remember instances. No extra vehicle permissions, Realtime service, DB/auth changes or FileProvider grants were added by Work.
