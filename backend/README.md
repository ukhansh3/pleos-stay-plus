# Parking API server

Node.js 20+, TypeScript, and Fastify implementation of the Parking v0.2 contract.

## Local setup

Copy the backend example file and put the issued public-data key in the ignored local file. Do not add a real key to `.env.example`, logs, screenshots, or Android resources.

```powershell
Copy-Item .env.example .env
# Edit backend/.env and set PARKING_PROVIDER_API_KEY.
npm install
npm run dev
```

`npm run dev` and the built-server flow (`npm run build`, then `npm start`) both load `backend/.env` automatically. Existing process environment variables remain supported and take precedence over values in `.env`, so deployment secrets and temporary PowerShell overrides continue to work:

```powershell
$env:PARKING_PROVIDER_API_KEY = '<temporary-override>'
npm run dev
```

`REST_AREA_PROVIDER_API_KEY` is optional and must be set only after separately activating the official 전국휴게소정보표준데이터 API.

## Independent Pickup API

Pickup v0.3 is independent from Waiting and uses `/api/v1/pickup/**`. Driver and guest clients each send a Supabase Anonymous Auth JWT; request-body owner IDs are never trusted. Invitations last 60 minutes, only their SHA-256 hash is stored, and completion/cancellation/end removes current locations and revokes access.

The versioned schema/RLS migrations are under `../supabase/migrations/` and must be applied in filename order. Migrations through `20261103002000` were applied to the approved Supabase project on 2026-11-03 and verified with temporary anonymous users. The atomic-command migration `20261103003000` requires a separate reviewed application before live verification.

For Supabase persistence, set `PICKUP_STORAGE=supabase` together with `SUPABASE_URL`, `SUPABASE_PUBLISHABLE_KEY`, server-only `SUPABASE_SECRET_KEY`, `PUBLIC_WEB_BASE_URL`, and a separate random `PICKUP_INVITATION_HMAC_SECRET` of at least 32 characters. The secret key is sent only through Supabase's `apikey` header and never as a bearer token.

For loopback development only, `PICKUP_DEV_MODE=true` or `PICKUP_STORAGE=memory` selects the process-memory adapter. It is refused on non-loopback hosts and still validates real Supabase JWTs. Do not expose it through a public tunnel.

With an approved project and a running backend, `npm run verify:pickup:live` creates temporary anonymous driver, guest, and isolation users; validates RLS and cleanup; and removes all test users and cascaded Pickup data in `finally`.

Automatic 20m/30s proximity is disabled unless both accuracy/freshness environment values are explicitly configured after review. Pleos vehicle coordinates without meter accuracy are stored and displayed but never used to accumulate automatic proximity.

Clients recover missed state transitions with `GET /api/v1/pickup/sessions/{sessionId}/events?afterRevision=N`. Events are revision ordered; the owner can read the final close event, while a participant loses read access when the invitation is reissued or the session ends.

The developer simulator is opt-in and loopback-only. Set `PICKUP_SIMULATOR_ENABLED=true` and a separate random `PICKUP_SIMULATOR_TOKEN` of at least 32 characters only in local ignored environment files. The two `/api/v1/qa/pickup-simulator/**` location routes require both the actor's Supabase bearer token and the server-only simulator token, force `SIMULATED` source, and are not registered when disabled. The token must be injected by a same-origin server proxy and must never enter the browser bundle. Optional `simulatedNow` is accepted only by these QA routes for dwell-time acceleration.

The server listens on `127.0.0.1:3000` by default. Health checks are available at `/health` and `/api/v1/health`. Android debug builds use `http://10.0.2.2:3000/api/v1/`; override it with `-PPARKING_API_BASE_URL=https://.../api/v1/` when needed.

## Data and cache policy

- Sources: 공공데이터포털 `전국주차장정보표준데이터`, 선택적으로 `전국휴게소정보표준데이터`
- Official endpoints: `https://api.data.go.kr/openapi/tn_pubr_prkplce_info_api`, `https://api.data.go.kr/openapi/tn_pubr_public_rest_area_api`
- Supported live adapters: `PARKING_LOT`, `ON_STREET_PARKING`; `REST_AREA_PROVIDER_API_KEY` 설정 시 `PUBLIC_REST_AREA`
- Unsupported without verified data: `PICKUP_ZONE`, `TIME_LIMITED_ZONE`
- A rest-area record is included only when the official record has valid coordinates, an allowed standard type, and a positive parking-space count. Direction and on-site conditions remain mandatory checks; parking fees are `UNKNOWN` because the source has no tariff fields.
- Refresh after 24 hours; serve the last valid snapshot as `STALE` for at most seven days; return an error after that.
- Snapshot files contain public parking records only and are excluded from Git.
- Live availability is always `UNKNOWN`; mixed, discounted, or incomplete fee rules are not guessed.

## Verification

```powershell
npm audit
npm test
npm run build
```

Actual provider calls require separately approved keys for each activated public-data API. The repository never stores those keys.
