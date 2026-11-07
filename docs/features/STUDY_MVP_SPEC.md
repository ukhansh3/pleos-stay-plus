# Study MVP — Feature and Local API Specification v1.0

**2026-11-07 · scope APPROVED · implementation NOT STARTED (as of baseline dev dd2140e).**
[Notion Study](https://app.notion.com/p/40cb74b2b90581fda3c6f3fddcd6b6fa) · [shared contract](../contracts/DOCUMENT_WORKSPACE_CONTRACT.md) · [mode retrospective](https://app.notion.com/p/3edb74b2b905833c8e258157332a17bb)

## Goal

PLEOS users access **the same PC documents uploaded through Realtime** as Work; in Study they **read** TXT/PDF/DOCX, write **side-panel text notes**, and track reading progress per exact document version. This is NOT PDF in-place annotation, a cloud study sync system, or a new file server.

## User acceptance scenarios

- **S1:** From vehicle Study, choose 5–720 minutes; start only after `ModeStartManager.start(ModeStartRequest(ActivityMode.STUDY, minutes))` returns `Ready`. If Waiting is absent it is created, if long enough reused, if shorter prompt EXTEND/SHORTEN/BACK via `resolve`. Do not touch shared manager code.
- **S2:** Select exact file & version from current **authenticated, paired, PIN-unlocked Realtime**; verify hash and size; show correct content and clear unsupported reasons; no raw source preview when locked.
- **S3:** PDF viewer: page movement, zoom, remembered `pageIndex`; avoid fake page count. DOCX: text/format/tables/images where renderer supports, track structural block anchor rather than fixed page number. TXT UTF-8 read + offset.
- **S4:** Side text note create/list/edit/delete; notes and progress persist to private Android storage by `(userScope, connectionId, fileId, versionId)`. A new version keeps original notes intact and does not auto-migrate.
- **S5:** Last document, location, notes and COMPLETE indicator restored only for same authorized user and current safe vehicle state.
- **S6:** Leaving Study stops Study timers/renderer only; Waiting and Realtime pairing remain. New mode must not inherit unlocked sensitive content.

## Proposed local state and ports (not extant implementation)

```kotlin
enum class StudyMaterialFormat { PDF, DOCX, TXT }
enum class StudyReadState { NOT_STARTED, IN_PROGRESS, COMPLETED }
data class StudyMaterialKey(val userScope: String, val connectionId: String, val fileId: String, val versionId: String)
data class StudyNote(val id: String, val key: StudyMaterialKey, val anchor: String?, val body: String, val updatedAt: Long)
data class StudyProgress(val key: StudyMaterialKey, val anchorKind: String, val anchor: String, val fraction: Float?, val completed: Boolean)
interface StudyStore {
  suspend fun listNotes(key: StudyMaterialKey): List<StudyNote>
  suspend fun upsertNote(note: StudyNote)
  suspend fun removeNote(key: StudyMaterialKey, noteId: String)
  suspend fun getProgress(key: StudyMaterialKey): StudyProgress?
  suspend fun putProgress(value: StudyProgress)
}
```

State suggestions: `LOADING | CHOOSE_MATERIAL | READING | SAVING_NOTE | PAUSED | BLOCKED | ERROR`; a `StudySession` may hold `waitingId, startTimestamp, modeDeadline, currentMaterialKey`. The storage choice (Room vs DataStore) and note retention require explicit review; do not share Waiting database migrations by default.

## Feature IDs

| ID | Capability | Required evidence |
| --- | --- | --- |
| STUDY-01 | Shared Waiting mode start/shorten/extend/cancel, study timer | mode manager cases and duplicate tap unit tests |
| STUDY-02 | Realtime read-only library and version select | source auth/PIN/safety; tampering/revoke tests |
| STUDY-03 | Actual PDF viewer | multipage/zoom/invalid/encrypted test files |
| STUDY-04 | Actual DOCX viewer | Korean text/tables/images/reflow; unsupported handling |
| STUDY-05 | Actual TXT viewer | UTF-8, long file, invalid encoding |
| STUDY-06 | Side-panel text notes CRUD | save failure, restart, document isolation |
| STUDY-07 | Position/progress/complete and restore | PDF page vs DOCX block vs TXT offset |
| STUDY-08 | Safety/foreground/user-switch privacy | content masked instantly, no cross-user reappearance |

## Realtime interaction

Existing GET `/api/v1/realtime/files`, GET `/files/{id}/versions`, GET `/versions/{id}/content`. **No Study HTTP endpoints**. Use the shared document gateway and Realtime's safe download, cache invalidation, login/authorization and PIN. No direct client read to Supabase private Storage or service-role. Missing network or broken session must not be interpreted as cached authorization.

## Explicitly out of scope

PDF ink/markup, editing source PDF, independent Study server or storage bucket, AI tutor, flash cards, advanced task management, study-data cloud synchronization, AirDesk.

## Required test matrix

- No Waiting / existing sufficient / insufficient extend/shorten/back; old choice denied; storage uncertainty; safe fail-closed.
- Exact document version binding, hash/size, locked/no-connection/revoked, file deletion, changing user, network fail/retry.
- Memo CRUD, crash/relaunch recovery, note save fail, PDF page offset, DOCX reflow anchor, TXT offset; no invented progress fraction.
- Browser/renderer library license and PLEOS API34 actual compatibility; no arbitrary artifact render success.
- Android test + emulator screenshots + unchanged Rest/Waiting/Pickup/Realtime behavior. Mark every test PASS/FAIL/NOT RUN.
