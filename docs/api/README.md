# API contracts

Existing `openapi.yaml` contains the Parking/Pickup REST contract and historical proposed Waiting HTTP paths. **Some Waiting routes in that YAML are legacy drafts, not running endpoints**: the current Waiting MVP is Android LOCAL Room/Vehicle Safety, with independent server-side Pickup sessions. Refer to runtime `backend/src/app.ts` for actual exposed routes; keep design history instead of silently creating missing endpoints.

- [Study / Work operation matrix](MODE_API_MATRIX.md) — defines **LOCAL vs existing Realtime REST** ownership; no Study/Work new HTTP API.
- [Document workspace cross-module contract](../contracts/DOCUMENT_WORKSPACE_CONTRACT.md) — shared file reference, locking, authorization, editing/export boundary.
- [Study scope](../features/STUDY_MVP_SPEC.md) · [Work scope](../features/WORK_DOCUMENT_MVP_SPEC.md).
- [Current Realtime file REST server](../../backend/src/realtime/routes.ts) — its `/api/v1/realtime` routes are already implemented but not duplicated into the older Parking/Pickup OpenAPI document.
- [Existing Realtime implementation guidance](../realtime/LOCAL-IMPLEMENTATION.md).

**Documentation is not runtime verification**. When extending HTTP APIs in future, coordinate with server owner, formally update OpenAPI/contracts/tests, and obtain user approval; never invent a route because an Android local function exists.
