# Android (Pleos Connect)

The Kotlin/Jetpack Compose project now contains the Parking search flow:

- “내 주변 검색” backed by official Pleos FusedLocation 1.1.3 and `pleos.car.permission.FUSED_LOCATION`
- Permission denial, disabled location, unsupported runtime, timeout, and SDK error guidance with manual fallback
- Manual WGS84 coordinates retained behind a developer-test toggle
- Parking list and detail views connected to the Fastify API
- Explicit loading, empty, stale-data, missing-field, and error states
- `NavigationGateway` backed by official NaviHelper 3.0.5 `requestRoute(RouteInfo)`
- Separate current-vehicle, manual-test, and future destination search-origin models
- Only the documented Pleos permissions: `FUSED_LOCATION` and `NAVI_ROUTE_SEARCH`; no guessed Android fine/coarse permission

The debug API URL defaults to `http://10.0.2.2:3000/api/v1/`. Override it at build time with `-PPARKING_API_BASE_URL=https://.../api/v1/`. Cleartext traffic is enabled only in the debug manifest.

`assembleDebug` verifies compilation against the official dependencies. FusedLocation permission denial and last-known coordinate reception were also exercised on Pleos Connect Emulator v2.1.0. The emulator did not propagate `adb emu geo fix` into the Pleos FusedLocation service, and Navi route acceptance remains unverified. Check `docs/verification/2026-11-02-parking-api.md` for exact evidence and `SDK_VERIFY` items.

See [DEVELOPMENT_GUIDE.md](../DEVELOPMENT_GUIDE.md) and [AGENTS.md](../AGENTS.md).
