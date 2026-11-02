# 2026-11-02 Parking API implementation verification

## Scope

Implemented Parking only: current-location search, recommendation order, detail, conservative fee estimate, source metadata, Android list/detail connection, official rest-area normalization, and NaviHelper coordinate handoff. No map SDK or in-app map was added. Waiting API and Waiting Safety Monitor were not changed.

## Approved decisions

- Provider: 공공데이터포털 전국주차장정보표준데이터 (`15012896`)
- Endpoint: `https://api.data.go.kr/openapi/tn_pubr_prkplce_info_api`
- Cache: refresh after 24 hours; stale fallback for at most seven days; fail when no usable snapshot exists
- “Waiting time” in recommendation: requested planned parking duration (`plannedMinutes`), not live entrance queue time
- Provider key: server environment only (`PARKING_PROVIDER_API_KEY`)
- Optional rest-area provider: 공공데이터포털 전국휴게소정보표준데이터 (`15025446`), separately activated with server-only `REST_AREA_PROVIDER_API_KEY`

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
| Rest area with valid coordinates, allowed official type, and `prkplceCo > 0` | `PUBLIC_REST_AREA`, `allowedUse=[PARK]` |
| Rest-area route direction | Preserved as a restriction; no cross-direction access is assumed |
| Rest-area tariff absent from source | Fee remains `UNKNOWN` |

The parking dataset covers local-government-managed public/private, on-street, off-street, and attached parking. The official rest-area standard describes general, truck, and simple rest areas as facilities for drivers/passengers and defines `prkplceCo` as the number of automobiles that can park. Truck rest areas add truck facilities to general-rest-area facilities, so they are not treated as truck-exclusive; road direction and field rules are still shown as conditions. Both adapters fetch paginated JSON and normalize only validated coordinates. Provider keys were not present on this machine, so live authenticated public-data responses have **not** been claimed.

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
- Pleos Connect Emulator v2.1.0 / Android 14: `requestRoute` reached the OEM navigation provider with the selected name, coordinates, ID, and address. The navigation app displayed three route previews and the Parking UI received `onRouteStarted` after returning to the app.
- The test used a repository-labelled fixture (`test-fixture:pleos-navi`) only to exercise the SDK handoff; it was not displayed as real public data and is not used by production configuration.
- Error callback handling compiles against NaviHelper 3.0.5, but a deterministic emulator `NaviErrorCode` scenario was not produced.

The UI reports the exact callback name (`onRouteStarted` / `onRoutingCompleted`) instead of inferring route semantics, and shows `NaviErrorCode` through the error state.

## FusedLocation implementation and verification

- Official dependency: `ai.pleos.playground:FusedLocation:1.1.3`.
- Official permission: `pleos.car.permission.FUSED_LOCATION` (`dangerous`). No unverified `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`, or Google Play Services class was added.
- The gateway checks `isSupported()`, initializes with the app/ViewModel lifecycle, registers `FusedLocationListener`, requests `getLastKnownLocation()`, unregisters the same listener, and calls `release()` when cleared.
- “내 주변 검색” passes the received WGS84 latitude/longitude to the same Parking repository search used by manual input. `ParkingSearchOrigin` currently separates `CurrentVehicle` and `ManualTest`; destination search is not faked while its Places provider remains undecided.
- Permission denial, platform location-disabled state (`LocationManager.isLocationEnabled`), unsupported SDK, null/error, and 12-second timeout paths show guidance and open the developer coordinate fallback.

Pleos Connect Emulator v2.1.0 evidence on 2026-11-02:

| Scenario | Result |
|---|---|
| Runtime permission denied | PASS — denial message shown and developer coordinates expanded |
| Runtime permission granted | PASS |
| `isSupported()` / service bind | PASS |
| Official `getLastKnownLocation()` callback | PASS — UI received and displayed `37.421998, -122.084000` from the Pleos service |
| Parking repository handoff | PASS by unit test — recorded request received the exact gateway latitude/longitude |
| Live update after `adb emu geo fix 127.039 37.501` | **SDK_VERIFY** — Android emulator command returned `OK`, but Pleos FusedLocation continued returning its own cached coordinate; official docs do not specify an ADB injection bridge |
| GPS/location disabled runtime | **SDK_VERIFY** — handler and unit test pass; OS privacy/location setting was not changed during automated verification |
| Real vehicle fresh GPS | **SDK_VERIFY** — requires approved SDK access and target vehicle/Devbox validation |

The official project-access page currently marks FusedLocation as “Not Available Yet” for SDK access requests. The artifact compiles and the emulator service responds, but production entitlement and App Market approval must be confirmed before release.

## Commands and results

| Command | Result |
|---|---|
| `npm audit` | 0 vulnerabilities after pinning Fastify 5.12.7 |
| `npm test` | Passed, 12 tests (parking/rest-area mapping and pagination, fee rules, Haversine, cache/stale, query validation, empty result, detail/estimate 404, provider failure) |
| `npm run build` | Passed |
| `gradlew testDebugUnitTest assembleDebug` | Passed with Android Studio JBR and local Android SDK |
| Pleos emulator FusedLocation permission denial | Passed; guidance and fallback rendered |
| Pleos emulator FusedLocation last-known location | Passed; official SDK value rendered in the app |
| Actual public-data request | Not run: `PARKING_PROVIDER_API_KEY` absent |
| Pleos emulator Navi route | PASS — OEM route preview opened for selected coordinates and `onRouteStarted` callback was displayed in Parking UI |
| NaviHelper error callback | `SDK_VERIFY` — handler compiled; deterministic emulator failure not reproduced |
| Destination name/address search | `REVIEW_REQUIRED` — actual Places supplier and key not selected |
| `PICKUP_ZONE`, `TIME_LIMITED_ZONE` | `UNSUPPORTED` — no sufficiently reliable official dataset connected |

## Manual follow-up

1. Set the public-data decoding key in the server process environment and start the backend.
2. Confirm `/api/v1/parking/places` returns current public and on-street records near a known coordinate.
3. Start the Android debug app with the backend reachable from the emulator and confirm “내 주변 검색” produces a Parking API request for the displayed coordinate.
4. On a Devbox or approved vehicle, verify a fresh FusedLocation update, location-disabled behavior, and production permission entitlement.
5. Repeat route verification with an actual public-data place after provider credentials are configured, and capture any exact `NaviErrorCode` failure scenario.
6. Confirm the installed navigation service and app-review permission approval before treating the integration as deployable.
