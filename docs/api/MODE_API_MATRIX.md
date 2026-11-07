# Study / Work Local + Realtime API Matrix (2026-11-07)

Status: **design contract; does not authorize server endpoint additions**.

## API ownership

- `ModeStartManager`: existing Android LOCAL (not HTTP); team-owned, shared. Study/Work call `start(ModeStartRequest(ActivityMode.STUDY|WORK, minutes))`, `resolve`, `adjustEnd` without reimplementing waiting.
- `WaitingSafetyGateway`: existing Android local vehicle safety; must fail closed.
- `Realtime`: existing Fastify `/api/v1/realtime` (see `backend/src/realtime/routes.ts`). Used identically by both Study and Work.
- `Study`: Android LOCAL notes/progress and renderer; no `/api/v1/study`.
- `Work`: Android LOCAL document editor/draft; existing Realtime for version upload; no `/api/v1/work`.
- `AirDesk`: separate deferred remote desktop project; not part of either MVP.
- The existing `docs/api/openapi.yaml` covers Parking/Pickup and **legacy Waiting HTTP draft paths**. Do not blindly implement unused Waiting HTTP drafts or append local mode operations as HTTP.

## Realtime server operations

| Existing HTTP | Study | Work |
| --- | --- | --- |
| POST devices, pairings, claim, approve | via shared connected file UX | via same connected file UX |
| GET connections/current | authorization prerequisite | authorization prerequisite |
| GET files | available source list | available source list |
| GET files/{id}/versions | exact version selection | exact base version |
| GET versions/{id}/content | verified read | verified edit input |
| POST uploads | not used for study notes | prepare original or revision after permission check |
| PUT uploads/{id}/content | not used | binary PDF/DOCX output |
| POST uploads/{id}/complete | not used | commit immutable new version |
| GET/DELETE uploads/{id} | not used | explicit retry/status/cancel |

## Technical blocking decisions

1. PDF engine MUST edit original body text objects and images, not only annotate.
2. DOCX engine MUST preserve rich OOXML on roundtrip, including font/table/image.
3. Both engines must pass Android/Pleos runtime, licensing, privacy, CJK/font and output-fidelity validation. Current SDK/engine choice: **UNDECIDED**.
4. VEHICLE initial upload of newly created original must be tested against existing server DB auth. **No implicit permission grant**.
5. Study and Work must reuse Realtime device identity, authorization, PIN and safety state; avoid creating independent client credentials or Storage access.

References: [Shared document workspace](../contracts/DOCUMENT_WORKSPACE_CONTRACT.md), [Study](../features/STUDY_MVP_SPEC.md), [Work](../features/WORK_DOCUMENT_MVP_SPEC.md).
