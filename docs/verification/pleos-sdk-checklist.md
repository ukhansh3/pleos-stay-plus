# Pleos SDK feasibility verification — 2026-11-01

Status: **FUSEDLOCATION EMULATOR PARTIAL PASS / NAVI RUNTIME NOT RUN**. FusedLocation 1.1.3 compiles, binds, handles runtime permission denial, and returns the emulator's last-known coordinate. A fresh injected/live update remains `SDK_VERIFY`. NaviHelper 3.0.5 route code compiles; route execution remains deferred.

## Official sources
- Vehicle SDK: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/intro
- NaviHelper SDK: https://document.pleos.ai/api-reference/connect-sdk-pleos/NaviHelper/intro
- NaviHelper init: https://document.pleos.ai/api-reference/connect-sdk-pleos/NaviHelper/initialization-and-release
- NaviHelper route: https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/NaviHelper/destinations
- FusedLocation setup: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/FusedLocation/project-setup
- FusedLocation permission: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/FusedLocation/declaring-permissions
- FusedLocation lifecycle: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/FusedLocation/initialization-and-release
- FusedLocation access: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/FusedLocation/location-data-access

## Steps on local Windows machine
1. Create Android test project for emulator **after explicit choice of package ID and Gradle dependencies**.
2. Confirm AVD Pleos Connect v2.1.0 / Android API 34 boot completes. Record screenshot and logcat.
3. From current official SDK docs, copy dependency coordinates/Manifest permissions; do not guess.
4. Instantiate Vehicle, check capability, subscribe to allowed **read-only** states, identify exact park/drive signal from documentation and logs.
5. For NaviHelper v3.0.5 documentation: instantiate NaviHelper, initialize with connected/failed callbacks, addListener, call route request for valid test destination, verify callbacks, removeListener and release.
6. Validate denial / unavailable service / emulator limitations.
7. Test safety gate with PARKED, MOVING and UNKNOWN: UNKNOWN must be locked.
8. Independently test Remote Desktop (Moonlight/Sunshine) in emulator: video decode, input, app distribution restrictions, licensing. Do not claim success based on generic Android alone.

## Evidence template
| Test | Emulator/version | SDK/version | Steps | Result | Logs | Reviewer |
|---|---|---|---|---|---|---|
| Vehicle initialization | pending | pending | pending | NOT RUN | pending | pending |
| Park state signal | pending | pending | pending | NOT RUN | pending | pending |
| NaviHelper route | Pleos Connect v2.1.0 planned | 3.0.5 | Parking detail coordinates → `requestRoute(RouteInfo)` | COMPILE PASS / RUNTIME NOT RUN | Gradle build; runtime log pending | pending |
| FusedLocation permission denial | Pleos Connect v2.1.0 | 1.1.3 | Revoke → nearby search → deny | PASS | UI dump showed denial guidance and coordinate fallback | Codex |
| FusedLocation last-known coordinate | Pleos Connect v2.1.0 | 1.1.3 | Grant → nearby search | PASS | SDK service and UI showed `37.421998, -122.084000` | Codex |
| FusedLocation fresh update injection | Pleos Connect v2.1.0 | 1.1.3 | `adb emu geo fix 127.039 37.501` → nearby search | SDK_VERIFY | command returned OK; Pleos service retained cached coordinate | Codex |
| Remote desktop stream | pending | pending | pending | NOT RUN | pending | pending |

## Decision gates
- **SDK_VERIFY:** Can we reliably obtain park state? Until then, non-driving modes fail closed.
- **REVIEW_REQUIRED:** Remote desktop embedded client vs separate app; AAOS policies and permissions.
- **SDK_VERIFY:** Confirm approved production FusedLocation entitlement, fresh update behavior on Devbox/vehicle, and location-disabled runtime behavior.
