# Work Document Coding Agent — Ownership and Contract

Applies to `android/app/src/main/java/com/pleosstay/app/work/` and descendants.

## Read first
- Root `AGENTS.md` and `DEVELOPMENT_GUIDE.md`.
- `docs/features/WORK_DOCUMENT_MVP_SPEC.md`, `docs/contracts/DOCUMENT_WORKSPACE_CONTRACT.md`, `docs/api/MODE_API_MATRIX.md`.
- [Notion Work](https://app.notion.com/p/40cb74b2b905810c9093ee9f243c678c) and [common mode retrospective](https://app.notion.com/p/3edb74b2b905833c8e258157332a17bb).
- Existing Realtime API, `RealtimeWorkBridge.kt`, `FileTransferViewModel.kt`, `ModeStartManager.kt`, `WaitingSafetyGateway`, Rest lifecycle.

## Product scope
- Work MVP = **PDF and DOCX creation AND advanced edits on existing documents**, including actual original PDF text/image modifications and page management; DOCX body/style/table/image preserving edits. Load source from shared Realtime, export **real valid files**, upload revision through **existing Realtime**. AirDesk/remote PC screen, slides, spreadsheets, browser work, coding and tasks are deferred.
- True PDF body modification is a HARD acceptance gate. Overlay-only annotation, drawn fake edit, plain text re-export that drops DOCX richness, or local fake success is **NOT COMPLETE**.

## Implementation sequence & gates
1. Inspect real Realtime file/lock/version APIs, existing binary size/hash policy and current `RealtimeWorkBridge` `content://` staging contract. Do not create another transfer service/auth.
2. Research/evaluate document editors by licensing/Android-PLEOS ABI/API34/offline/CJK/performance/output fidelity and **ask approval before adding SDKs, paid services or uploading any document to external converter**.
3. Execute proof on real fixture: existing PDF body text changed in resulting bytes and extracted/rendered content; complex DOCX structure retained across open-edit-export-reopen in independent reader. If unsuccessful mark BLOCKED and report next options, not pretend to finish.
4. Add work-domain editor/draft ports, user-scoped private persistence, cancellation, undo/redo where proven, transport adapter, version conflict/restore.
5. Implement Work time flow using `ModeStartManager`/ActivityMode.WORK; Ready-only start, NeedsChoice resolve, safety and PIN gating. No duplicate Waiting.
6. Provide minimal integration diff for Activity owner; real PLEOS E2E with existing Windows app and Cloud server once approved.

## Ownership
- Own new `work/` files/tests/docs only. `MainActivity`, `modes/`, `rest/`, `waiting/`, `realtime/`, `entertainment/`, backend/DB are shared team-owned. **Do not edit these without coordination/approval.**
- Existing server can prepare upload with omitted `fileId/baseVersionId` but Android VEHICLE permission for **new original** is UNVERIFIED. Test first; no silent server/RLS changes.
- Preserve imported original bytes; upload new immutable version with `originalFileId/baseVersionId`, hash, size, idempotency. `stageRevision` currently expects content:// URI; no fabricated URI or direct Storage access.
- Fail closed: UNKNOWN/DRIVING/UX/PIN lock or revoked pairing must mask editor and terminate I/O immediately. OS recents snapshots and temporary file lifecycle require review.

## Reporting
- PASS/FAIL/NOT RUN separately for engine/format cases, PDF actual-body test, DOCX roundtrip, file version download/upload, lifecycle, mode management, security.
- A blocked engine does not authorize a reduced-scope implementation to be called done; present the gap for user decision.
- Never modify main, deploy Cloud, reapply DB migrations, log confidential documents, install paid SDK, or modify other agents' code without approval.
