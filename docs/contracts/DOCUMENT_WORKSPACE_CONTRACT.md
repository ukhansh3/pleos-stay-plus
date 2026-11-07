# Document Workspace Contract v1.0 — 2026-11-07

Status: **approved user goals, proposed local interfaces, implementation NOT COMPLETE**.

2026-11-07 DOCX follow-up: [Work DOCX editor](../features/WORK_DOCX_EDITOR_SPEC.md) adds local/new DOCX drafts under the same authenticated Work profile, separate from server DocumentRef identities. Exact remote source identity is preserved when present. User approved only explicit-confirmation public `Download/StayPlus` export as fallback for the observed DocumentsUI stub; private encrypted retention remains default. No Realtime uploads or PDF editing in this phase.
Applies to **Study and Work** only. Reuses existing Windows Stay+.exe ↔ Realtime Fastify ↔ Supabase ↔ Android data path. AirDesk is deferred.

## 1. Current sources and non-negotiable boundaries

- [2026-11-06 common mode retrospective](https://app.notion.com/p/3edb74b2b905833c8e258157332a17bb): `ModeStartManager` already implemented. **DO NOT rewrite, reconfigure, or take ownership of this component**.
- [Realtime transfer implementation](../realtime/LOCAL-IMPLEMENTATION.md): existing devices, pairings, connections, files, versions, uploads, content, auth, storage, and PIN. Study/Work **must not create a second transfer server, Supabase schema, separate pairing flow or JWT scheme**.
- [Study Notion](https://app.notion.com/p/40cb74b2b90581fda3c6f3fddcd6b6fa) and [Work Notion](https://app.notion.com/p/40cb74b2b905810c9093ee9f243c678c) are product scopes; this document details technical seams.
- `MainActivity`, `ModeStartManager`, `WaitingSafetyGateway`, Rest and Entertainment are **shared/team-owned integration surfaces**. Agents working inside new Study/Work packages must not modify them without explicit coordination.
- Confirmed existing: `RealtimeApi`, `FileTransferViewModel`, `RealtimeWorkBridge` (revision staging/upload), `LocalFileStore`, `RealtimeEntry`. Those do **not** yet expose a complete unified document-workspace API: design an adapter, do not claim the desired interface already exists.

## 2. Shared document identity and state

Proposed local model (not a current server DTO):
```kotlin
data class DocumentRef(
    val userScope: String,
    val connectionId: String,
    val fileId: String,
    val versionId: String,
    val name: String,
    val mimeType: String,
    val sha256: String,
    val sizeBytes: Long,
)
sealed interface DocumentOpenResult {
    data class Ready(val verifiedLocalFile: java.io.File, val ref: DocumentRef): DocumentOpenResult
    data class Blocked(val reason: String): DocumentOpenResult
    data class Failed(val reason: String): DocumentOpenResult
}
interface SharedDocumentGateway {
    suspend fun listFiles(): List<DocumentRef>             // return canonical file references/versions without exposing token
    suspend fun downloadVerified(ref: DocumentRef): DocumentOpenResult
    suspend fun uploadRevision(ref: DocumentRef, contentUri: android.net.Uri, idempotencyKey: String): UploadOutcome
}
```
Types are **design proposals**, not signatures to copy blindly. Use typed DTOs and available fields from existing server JSON; do not fabricate server fields. New Work documents may require a separate `uploadOriginal` adapter after verifying permissions.

Identity keys are `(userScope, connectionId, fileId, versionId)`. Study notes/progress and Work drafts must never leak across different users, connections, or document versions. Realtime server remains source of truth for file version & access rights; a local cache is **not** an access grant.

## 3. Existing Realtime HTTP endpoints (already implemented)

Prefix: `/api/v1/realtime`; Bearer token, device registration and two-sided pairing are mandatory.
| Method | Path | Consumer |
| --- | --- | --- |
| POST | `/devices` | register Windows/vehicle identity; reuse existing flow |
| POST | `/pairings`, `/pairings/claim`, `/pairings/{id}/approve` | user-approved device pairing |
| GET | `/connections/current` | authorize current link |
| GET | `/files` | document library |
| GET | `/files/{id}/versions` | exact version list |
| GET | `/versions/{id}/content` | secured bytes streaming |
| POST | `/uploads` | initialize original or revision transfer |
| PUT | `/uploads/{id}/content` | bytes, application/octet-stream |
| POST | `/uploads/{id}/complete` | verify and commit new version |
| GET/DELETE | `/uploads/{id}` | status/cancel/recovery |

Current file whitelist: **TXT/PDF/DOCX, 20 MiB**; preserve format, SHA-256, size checks, idempotency, immutable originals/versions and server-side authorization.

For a revision, existing `RealtimeWorkBridge` accepts `WorkRevisionRequest(originalFileId, baseVersionId, sourceUri, fileName, mimeType, idempotencyKey)` and performs stage + upload. It accepts **content:// URI** and stores staged bytes in no-backup app-private storage. Work must return a valid, readable content URI with limited grant/lifetime, or obtain explicit review to extend this adapter—**not** substitute a made-up URI. Downloaded file path exposure also requires a narrowly scoped gateway reviewed against existing cache/lock cleanup.

The server upload `prepare` validates **both** `fileId` and `baseVersionId` present or **both** absent. This indicates a possible new-file path, but **DO NOT claim Android VEHICLE original creation works** until permission/RPC/Cloud tests prove it. If blocked, allow local draft creation and label online publishing blocked; do not change DB permissions silently.

## 4. Study vs Work ownership

| Concern | Study | Work |
| --- | --- | --- |
| Realtime library / download | shared gateway; read only | shared gateway; versioned upload |
| Local data | notes + progress keyed to exact version | editable drafts + export/recovery keyed to base version |
| PDF | page viewer + side text notes; no markup in PDF | real text/image-object edits, creation, page ops |
| DOCX | reflow viewer + side notes; structural anchors | content, paragraph format, tables/images; creation |
| TXT | read-only UTF-8 viewer | existing transfer supported; advanced editing priority PDF/DOCX |
| Server changes | NONE | NONE unless explicit new approval after contract gap confirmed |
| Timer | shared ModeStartManager / Study presentation state | shared ModeStartManager / Work presentation state |

Common `ModeStartResult` handling: `Ready` starts only requested mode after safety is rechecked; `NeedsChoice` uses `resolve(EXTEND/SHORTEN/BACK)`; `WaitingChanged` reloads; `Cancelled` preserves draft form; duplicate handled prompts ignored; `SafetyBlocked/RecoveryRequired/StorageError` are surfaced and prevent false success. Mode exit does not end Waiting or disconnect Realtime.

## 5. Security and UX lifecycle

- Read existing `WaitingSafetyGateway` state on entry, resume, document selection, render, input and upload. **UNKNOWN/DRIVING/UX-restricted = hide sensitive surface and suspend work immediately**, not just disable buttons.
- A PIN/user lock and authorized device pair remain mandatory; never call `unlockAfterUserVerification` from an unverified UI callback.
- Do not log document text, filenames with sensitive details, annotations, access tokens, file content, or clipboard contents. No secret in APK.
- User-switch/revocation: clear display, editor buffers, temporary previews and drafts as per approved retention rules; preserve recoverable failed upload only within same authorized user and connection.
- Drafts and study notes are not new Supabase tables. Local persistence security, key binding, delete/retention and backup exclusion require implementation review.
- **2026-11-07 user-approved retention:** app-private, no-backup encrypted notes/progress/drafts are locked and retained after revocation or user switch until explicit user deletion. Clear display/editor buffers and temporary previews immediately; retained bytes never grant offline access. Reading requires fresh authorization for the same endpoint/user/connection/file/version.
- Different versions must not overwrite existing notes/drafts. Never silently flatten DOCX or destroy PDF editability. Explicit error if engine lacks features.
- Ensure no screen previews/recents leaks, clipboard leaks, unsupported file intent exports, or unauthorized SAF grant persistence; test PLEOS runtime, not build-only.

## 6. Quality gates before declaring complete

1. Existing `ModeStartManager` tests, current Waiting/Rest/Pickup/Realtime regressions green.
2. Real paired Windows → server → Android file opened, SHA-256 checked; revision → Windows download validated.
3. Study PDF/DOCX/TXT viewer + side notes + progress persistence and version isolation tested.
4. Work PDF **existing text object** changed in exported PDF (not mere overlay); images/pages and CJK font/layout tested. DOCX structure/style/table/image preserved across export/reopen in independent viewer.
5. PLEOS emulator: safety transitions, lock/PIN, device switch, app resume, failure/retry/revoke, corrupted/locked files.
6. Editor library/engine licensing, API34/PLEOS support, CPU/memory/latency, font embedding, offline behavior and output compatibility approved. **No editor engine chosen yet.**
7. No real database or live server migration/deployment without separate approval. No main edits.

Related: [Study spec](../features/STUDY_MVP_SPEC.md), [Work spec](../features/WORK_DOCUMENT_MVP_SPEC.md), [API matrix](../api/MODE_API_MATRIX.md).
