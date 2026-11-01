# Pleos SDK feasibility verification — 2026-11-01

Status: **NOT RUN**. This is a test procedure, not evidence of successful SDK integration. User's Android Studio / Pleos Emulator exists, but is not connected to this GitHub tool.

## Official sources
- Vehicle SDK: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/intro
- NaviHelper SDK: https://document.pleos.ai/api-reference/connect-sdk-pleos/NaviHelper/intro
- NaviHelper init: https://document.pleos.ai/api-reference/connect-sdk-pleos/NaviHelper/initialization-and-release
- NaviHelper route: https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/NaviHelper/destinations

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
| NaviHelper route | pending | pending | pending | NOT RUN | pending | pending |
| Remote desktop stream | pending | pending | pending | NOT RUN | pending | pending |

## Decision gates
- **SDK_VERIFY:** Can we reliably obtain park state? Until then, non-driving modes fail closed.
- **REVIEW_REQUIRED:** Remote desktop embedded client vs separate app; AAOS policies and permissions.
- **REVIEW_REQUIRED:** Using GPS/FusedLocation vs manual pickup/place entry.
