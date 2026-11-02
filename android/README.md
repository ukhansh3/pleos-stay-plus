# Android (Pleos Connect)

The Kotlin/Jetpack Compose project now contains the Parking search flow:

- Manual WGS84 coordinate, radius, planned-duration, purpose, and free-only inputs
- Parking list and detail views connected to the Fastify API
- Explicit loading, empty, stale-data, missing-field, and error states
- `NavigationGateway` backed by official NaviHelper 3.0.5 `requestRoute(RouteInfo)`
- `pleos.car.permission.NAVI_ROUTE_SEARCH` only; no broad location or package permission

The debug API URL defaults to `http://10.0.2.2:3000/api/v1/`. Override it at build time with `-PPARKING_API_BASE_URL=https://.../api/v1/`. Cleartext traffic is enabled only in the debug manifest.

`assembleDebug` verifies compilation against the official dependency. It does not prove that the Navi service accepted a route. Check `docs/verification/2026-11-02-parking-api.md` before emulator validation.

See [DEVELOPMENT_GUIDE.md](../DEVELOPMENT_GUIDE.md) and [AGENTS.md](../AGENTS.md).
