# Parking API server

Node.js 20+, TypeScript, and Fastify implementation of the Parking v0.2 contract.

## Local setup

Set the public-data key outside Git. Do not add it to `.env.example`, logs, screenshots, or Android resources.

```powershell
$env:PARKING_PROVIDER_API_KEY = '<your-decoding-key>'
npm install
npm run dev
```

The server listens on `127.0.0.1:3000` by default. Health checks are available at `/health` and `/api/v1/health`. Android debug builds use `http://10.0.2.2:3000/api/v1/`; override it with `-PPARKING_API_BASE_URL=https://.../api/v1/` when needed.

## Data and cache policy

- Source: 공공데이터포털 `전국주차장정보표준데이터`
- Official endpoint: `https://api.data.go.kr/openapi/tn_pubr_prkplce_info_api`
- Supported live adapters: `PARKING_LOT`, `ON_STREET_PARKING`
- Disabled adapter boundaries returning no invented records: `PICKUP_ZONE`, `TIME_LIMITED_ZONE`, `PUBLIC_REST_AREA`
- Refresh after 24 hours; serve the last valid snapshot as `STALE` for at most seven days; return an error after that.
- Snapshot files contain public parking records only and are excluded from Git.
- Live availability is always `UNKNOWN`; mixed, discounted, or incomplete fee rules are not guessed.

## Verification

```powershell
npm audit
npm test
npm run build
```

An actual provider call additionally requires an approved key in `PARKING_PROVIDER_API_KEY`.
