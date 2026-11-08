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

## Pickup

- Pickup starts independently from Home; an active local Waiting UUID is optional linkage only.
- The driver identity provider uses the official Supabase Auth REST endpoints for UI-less anonymous signup/refresh. Tokens are encrypted with an app-private Android Keystore AES-GCM key.
- Put `SUPABASE_URL` and `SUPABASE_PUBLISHABLE_KEY` in ignored `android/local.properties`, Gradle properties, or process environment. Never place `SUPABASE_SECRET_KEY` in Android.
- Vehicle sharing reuses Pleos FusedLocation, starts only after explicit consent and the existing fail-closed parked safety gate, targets roughly five-second refresh, and stops on driving/UNKNOWN/permission/location failure.
- FusedLocation 1.1.3 exposes provider `confidence` but no meter accuracy. The app does not invent `accuracyMeters`; such vehicle positions cannot contribute to automatic proximity until an approved accuracy source exists.
- QR encoding uses ZXing Core 3.5.4 (Apache-2.0). The QR opens the driver share handoff page and does not itself grant location access.
- QR reissue uses a stable idempotency key while retrying; a successful reissue receives a new key and relies on the atomic backend command to revoke the previous invitation and participant access.
- Participant/session synchronization runs independently from vehicle GPS upload. The app consumes the durable revision-ordered event cursor and refreshes a snapshot every five seconds, so a location failure does not stop status recovery.
- Official Pleos TextToSpeech 3.3.1 is integrated in `ON_DEVICE` mode with `pleos.car.permission.TTS_SERVICE`, initialization/error callbacks, listener cleanup, and event-id deduplication. Maven resolution and Android compilation are verified; permission grant and audible playback remain `SDK_VERIFY` until exercised on the Pleos emulator.

`assembleDebug` verifies compilation against the official dependencies. FusedLocation permission denial and last-known coordinate reception were also exercised on Pleos Connect Emulator v2.1.0. The emulator did not propagate `adb emu geo fix` into the Pleos FusedLocation service, and Navi route acceptance remains unverified. Check `docs/verification/2026-11-02-parking-api.md` for exact evidence and `SDK_VERIFY` items.

See [DEVELOPMENT_GUIDE.md](../DEVELOPMENT_GUIDE.md) and [AGENTS.md](../AGENTS.md).


## UI 미리보기

[에뮬레이터 없는 UI 미리보기](../docs/ui/COMPOSE_PREVIEW_GUIDE.md) — debug의 `ParkingPreview.kt` 등을 열고 Design/Split에서 한 화면씩 확인합니다. 에뮬레이터·서버를 실행하지 않습니다.
