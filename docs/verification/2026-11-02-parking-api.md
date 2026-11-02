# 2026-11-02 Parking API implementation verification

## Scope

Implemented Parking only: search, recommendation order, detail, conservative fee estimate, source metadata, Android list/detail connection, and NaviHelper coordinate handoff. Waiting API and Waiting Safety Monitor were not changed.

## Approved decisions

- Provider: 공공데이터포털 전국주차장정보표준데이터 (`15012896`)
- Endpoint: `https://api.data.go.kr/openapi/tn_pubr_prkplce_info_api`
- Cache: refresh after 24 hours; stale fallback for at most seven days; fail when no usable snapshot exists
- “Waiting time” in recommendation: requested planned parking duration (`plannedMinutes`), not live entrance queue time
- Provider key: server environment only (`PARKING_PROVIDER_API_KEY`)

## Provider mapping and limitations

| Source field/rule | Normalized result |
|---|---|
| `prkplceType=노상` | `ON_STREET_PARKING` |
| Other standard parking structures | `PARKING_LOT` |
| Explicit `parkingchrgeInfo=무료` | `FREE`, 0 KRW |
| Clear paid base/additional units without complex terms | `ESTIMATED` |
| Mixed, discount, day-pass, or incomplete terms | `UNKNOWN` |
| No live-space source field | `liveAvailability=UNKNOWN` |
| Public-data record | `CONDITIONAL`; field conditions and on-site signs still take precedence |

The official dataset covers local-government-managed public/private, on-street, off-street, and attached parking and is refreshed on a half-year cycle. The backend fetches the actual paginated JSON API and deduplicates by provider ID. A provider key was not present on this machine, so a live authenticated response has **not** been claimed.

## Recommendation order

1. Filter by radius, purpose compatibility, and explicit free-only selection.
2. Prefer `OPEN`, then `UNKNOWN`, then `CLOSED` operating state.
3. Sort by Haversine straight-line distance.
4. Use the planned duration to calculate only supported fee rules, then use known fee as a tie-breaker.
5. Never give an advantage to an unknown fee or invented availability.

## NaviHelper verification status

- Verified from official NaviHelper 3.0.5 artifact source and API reference: constructor with `Context`, callback `initialize`, listener lifecycle, `requestRoute(RouteInfo)`, `removeListener`, `release`.
- Verified permission: `pleos.car.permission.NAVI_ROUTE_SEARCH`.
- Verified by Gradle compilation: coordinates, name, provider ID, address, and source map to `RouteInfo`.
- **Not run by user request:** emulator route acceptance, navigation app callback behavior, and route start.

The UI therefore reports initialization/request/callback/error states and does not label the feature successful until an actual route callback is received.

## Commands and results

| Command | Result |
|---|---|
| `npm audit` | 0 vulnerabilities after pinning Fastify 5.12.7 |
| `npm test` | Passed (provider mapping/pagination, fee rules, Haversine, cache/stale, query validation, empty result, detail/estimate 404, provider failure) |
| `npm run build` | Passed |
| `gradlew testDebugUnitTest assembleDebug` | Passed with Android Studio JBR and local Android SDK |
| Actual public-data request | Not run: `PARKING_PROVIDER_API_KEY` absent |
| Pleos emulator Navi route | Not run: deferred by user |

## Manual follow-up

1. Set the public-data decoding key in the server process environment and start the backend.
2. Confirm `/api/v1/parking/places` returns current public and on-street records near a known coordinate.
3. Start the Android debug app with the backend reachable from the emulator.
4. Select a returned place, tap “길 안내”, and record `Ready → Requesting → Started` or the exact `NaviErrorCode`.
5. Confirm the installed navigation service and app-review permission approval before treating the integration as deployable.
