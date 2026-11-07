# Study Coding Agent — Ownership and Contract

Applies to `android/app/src/main/java/com/pleosstay/app/study/` and descendant files.

## Read first
- Root `AGENTS.md`, `DEVELOPMENT_GUIDE.md`.
- `docs/features/STUDY_MVP_SPEC.md`, `docs/contracts/DOCUMENT_WORKSPACE_CONTRACT.md`, `docs/api/MODE_API_MATRIX.md`.
- [Notion Study](https://app.notion.com/p/40cb74b2b90581fda3c6f3fddcd6b6fa) and [ModeStartManager retrospective](https://app.notion.com/p/3edb74b2b905833c8e258157332a17bb).
- Existing `ModeStartManager.kt`, `WaitingSafety.kt`, `RealtimeApi.kt`, `FileTransferViewModel.kt`, `RealtimeWorkBridge.kt`, `RestController.kt`.

## Scope — ONLY Study implementation
- TXT/PDF/DOCX **real** reader adapters, side text notes, per-version progress/last location, completed marker, local persistence, Study timer using the existing shared mode manager.
- Reuse existing Realtime file list/versions/verified download, authorization, PIN and safety. No new study HTTP API or Supabase table.
- Not in scope: PDF ink/markup, editing PDF/DOCX, AI tutor, advanced tasks, AirDesk, common mode controller rewrite.

## Ownership and conflict control
- Own new `study/` files and their dedicated tests/docs only.
- `MainActivity`, `modes/`, `rest/`, `waiting/`, `realtime/`, `entertainment/`, `backend/`, `web/` are team/shared. Do **not** change, reformat, push conflicting edits, or merge without user/owner coordination.
- To connect the Study entry point, first provide exact minimal integration diff to common owner; do not silently alter shared Activity or mode coordinator.
- Existing `ModeStartManager` is already implemented and MUST be used, not reimplemented. Use `ActivityMode.STUDY`, handle every `ModeStartResult` branch; only start on `Ready`, `NeedsChoice` via `resolve`.
- Read-only integration with Realtime; download file after PIN/permission; use a narrow shared adapter as approved. Keep `MaterialKey` including owner/connection/file/version. No cross-user notes/cache.

## Prohibitions
- No unverified viewer library or binary, no assumed PDF/DOCX formatting correctness, no fake page counts or mocked PDF rendered as real.
- No safe-state bypass: UNKNOWN/DRIVING or UX restricted = mask sensitive content and stop interactions.
- No external document upload, secret logging, credential copying, DB migration, paid SDK install, or main modification without explicit approval.

## Acceptance
- Unit tests: mode results, note CRUD, version isolation, progress anchors, restart, auth/permission/safety changes.
- Device tests: actual TXT/PDF/DOCX read on PLEOS, Korean text, tables/images and errors.
- Regressions: Waiting, Rest, Pickup, Realtime. Build; report PASS/FAIL/NOT RUN separately.
- Final report: changed files, proven runtime behavior, unresolved viewer/retention/authorization limits and evidence.
