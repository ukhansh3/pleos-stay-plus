# Work Document MVP — Advanced PDF/DOCX Editing v1.0

**2026-11-07 · PRODUCT SCOPE CONFIRMED · editor engine selection/implementation NOT COMPLETE.**
[Notion Work](https://app.notion.com/p/40cb74b2b905810c9093ee9f243c678c) · [Shared contract](../contracts/DOCUMENT_WORKSPACE_CONTRACT.md) · [Mode retrospective](https://app.notion.com/p/3edb74b2b905833c8e258157332a17bb)

## Product target

Vehicle Work is a **real authoring/editor workspace**, not only a file gallery. First release **creates and edits PDF and DOCX**—including actual original PDF body text, images/pages; DOCX text, styles, images, tables—and returns a valid new document version via the **existing Realtime file-transfer API**. Realtime connects Windows Stay+.exe and vehicle; it is the ONLY document transport/auth/pairing service. AirDesk/remote mouse/screen is **DEFERRED**.

Long-term backlog only (NOT included): spreadsheets, slides, browser work, scheduling/tasks, coding, AirDesk.

## Requirements and evidence gates

| ID | Function | Contract / acceptance |
| --- | --- | --- |
| WORK-01 | Work session | `ModeStartRequest(ActivityMode.WORK, minutes)`; handle complete `ModeStartResult` state machine; no duplicate Waiting |
| WORK-02 | Open existing file | Realtime library/versions + PIN + safety → verified local PDF/DOCX |
| WORK-03 | Create PDF/DOCX | blank or supported template → well-formed PDF/DOCX → reopen |
| WORK-04 | Advanced PDF body editing | **real existing PDF text-object change, image-object replace, page insert/delete/reorder**; independent text extraction + rendering confirms bytes changed; overlay annotations alone DO NOT PASS |
| WORK-05 | Advanced DOCX editing | text, paragraphs, font/style, tables, images, page/layout where supported; OOXML valid and independently reopenable |
| WORK-06 | Drafting | private per-user/per-version drafts; dirty flag, undo/redo if engine supports, auto-save and explicit user confirmation |
| WORK-07 | Export and version upload | preserve immutable original, baseVersionId and originalFileId; validated PDF/DOCX bytes, SHA256 and same idempotency key; stage+upload via existing bridge |
| WORK-08 | New document publish | server `POST /uploads` without fileId/baseVersionId **only if VEHICLE permission verified**; no unauthorized DB grant; otherwise local draft and BLOCKED |
| WORK-09 | Conflict/version awareness | detect prior revision changes; never silently overwrite or flatten, user chooses before replacing draft |
| WORK-10 | Lifecycle/security | safe state + PIN + authorization; hide text, stop edit/transfer in UNKNOWN/DRIVING; user-switch & process recreation |
| WORK-11 | Failure UX | corrupt/encrypted/scanned/unsupported PDF; malformed DOCX; missing fonts; big file; connection loss; license failure clearly reported |
| WORK-12 | Mode transitions | close local editor/renderer resources, manage unsaved work; Waiting survives Work exit; no conflict with Rest/Entertainment |

## Advanced PDF: REQUIRED distinction

PDF edit is **not** equivalent to a preview with text box overlay. Proof must include a fixture with existing text/image, a transformation of that underlying content, export, independent extraction of new text, visual comparison of rendered pages and absence of hidden old text or overpaint artifacts.

PDF can contain embedded/subset fonts, inaccessible encodings, flattened scans, digital signatures, encrypted permissions, rotated/cropped objects, complex forms. No universal fidelity can be guaranteed. A third-party editing engine is a **hard technical decision gate** and must be separately approved after evaluating:
- actual existing PDF text-object modification (not just annotation/redaction);
- local Android/PLEOS API34-compatible native/Java artifacts and license/distribution conditions;
- Korean fonts, image operations, page manipulation, CPU/memory limits and offline mode;
- independent output validation (signature damage / encrypted/scan unsupported cases surfaced).

**Do not purchase, import new paid SDK, upload private documents to cloud conversion, or pretend a WebView-based fake editor fulfills the scope without approval.** Failed fidelity tests become BLOCKED/UNSUPPORTED and recorded as such.

## DOCX: REQUIRED compatibility

Roundtrip export must preserve OOXML structure, original paragraphs/styling, tables, embedded images, ordered content and Korean fonts. Test opening the result in an independent DOCX reader and diff key XML nodes. Do not flatten all text into a new DOCX as a shortcut. Complex macros, protected docs, tracked changes, section formats remain explicit compatibility gate cases until proven.

## Existing Realtime — NO NEW HTTP API

Base `/api/v1/realtime`; read `GET /files`, `GET /files/{id}/versions`, `GET /versions/{id}/content`; revision `POST /uploads`, `PUT /uploads/{id}/content`, `POST /uploads/{id}/complete`, `GET/DELETE /uploads/{id}`. Existing whitelist PDF/DOCX/TXT and 20MiB cap. PDF/DOCX chosen for advanced Work editor.

`RealtimeWorkBridge` currently exposes `stageRevision(WorkRevisionRequest)` and `uploadRevision(StagedRevision, progress)`; `stageRevision` expects `content://` source. Work must create valid app-scoped URI granting safe read and coordinate with Realtime owner for minimal adapter additions **only if required**. It must not bypass PIN, safety or token. Backend `prepare` supports neither/both of `fileId` and `baseVersionId`, but no assumption that a VEHICLE may create a brand-new original before DB/Cloud permission verification.

## Proposed local editor ports (not implemented)

```kotlin
enum class WorkFormat { PDF, DOCX }
data class WorkSource(val userScope: String, val connectionId: String, val fileId: String?, val baseVersionId: String?, val format: WorkFormat)
data class WorkDraft(val localId: String, val source: WorkSource, val dirty: Boolean, val createdAt: Long)
sealed interface ExportResult {
  data class Saved(val contentUri: android.net.Uri, val mimeType: String, val sha256: String): ExportResult
  data class Unsupported(val reason: String): ExportResult
  data class Failed(val reason: String): ExportResult
}
interface WorkDocumentEngine {
  suspend fun openOrCreate(source: WorkSource, inputFile: java.io.File?): WorkDraft
  suspend fun edit(draft: WorkDraft, command: WorkEditCommand): ExportResult
  suspend fun export(draft: WorkDraft): ExportResult
  fun close()
}
```

`WorkEditCommand` depends on actual proven editor engine abilities; specify it only AFTER research & API prototype, do not invent an SDK. Proposed UI stages: `CHOOSE_FILE | CHOOSE_FORMAT | OPENING | EDITING | DIRTY | EXPORTING | UPLOADING | SAVED | BLOCKED | ERROR`.

## Safety, persistence and UX

- Start timer ONLY after `Ready`; missing Waiting supports create, existing supports reuse, shortage offers extend/shorten/back. For `StorageError` independent operation only by explicit user consent and safety re-check. No changes to `ModeStartManager`.
- A stopped/DRIVING/UNKNOWN app state or locked PIN must **mask document pixels and prevent editor input/output/upload immediately**. Do not allow unauthorized background cloud sync. Respect existing foreground UX and secure local cache policy.
- Work drafts are private local copies scoped to UID/connection/file/version. Do not delete unexported edits silently; consent and retention policy needed. Explicit save revision to cloud, never in-place overwrite original.
- If editor cannot truly change an imported PDF, show an honest unsupported state; the product goal remains advanced editing but is not marked complete.

## Tests / DoD

- [ ] Approved PDF editor engine candidate proves existing text object modifications and page/image changes on PLEOS.
- [ ] Approved DOCX editor engine proves style/table/image roundtrip and independent file compatibility.
- [ ] Create new PDF/DOCX then reopen; test VEHICLE initial upload permission or transparently block remote publish.
- [ ] Windows file upload → actual PLEOS editor open → actual changes → Realtime new version → Windows file download validates hash/content.
- [ ] Restricted/scan/signed/encrypted PDFs, complex DOCX, missing CJK font, over-limit, disconnect, retry, duplicate save, crash recovery.
- [ ] UNKNOWN/DRIVING/PIN lock/user switching/revoked pairing masks data & cancels I/O.
- [ ] Existing Waiting, Rest, Pickup, Realtime tests green; app builds and actual PLEOS UI tested; honest PASS/FAIL/NOT RUN report.
