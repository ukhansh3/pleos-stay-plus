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
