# Home brand and card polish — 2026-11-08

- Repository: `ukhansh3/pleos-stay-plus`; branch: `dev`.
- Starting HEAD: `458e6b2072dd5d0eae9b633efb640b2725a6bce2`.
- Scope: VEH-01 home and shared navigation/card presentation only.

## Implementation

- `StayAppShell.kt`: replace the rail P with the approved Stay+ arch/desk/seat logo. Fit a transparent image to the existing rail; preserve route selection and click handlers.
- `StayHomeScreen.kt`: use the explicitly selected blue-sky, green-mountain daytime rear-car panorama, preserving the existing 0.82 alpha and navy gradient blending.
- Remove the blue Rest/Study/Work capability captions; keep descriptions at the bottom of the cards using the existing weighted spacer.
- Retain the unavailable Entertainment indication at the top of its card.
- Remove the decorative white chevrons on Parking/Pickup/Files shortcuts; retain the clickable cards and existing destinations.
- Add `stay_brand_logo.png` and `hero_daylight_rear.png` in `drawable-nodpi`; retain the earlier sunset image without overwriting it.
- Update `docs/ui/PREMIUM_ASSET_MANIFEST.json` with provenance, dimensions, SHA-256, and selected assets.

## Assets

The logo was redrawn from the user-supplied approved Stay+ mark with the built-in image generation tool, requesting true transparency, preserved shapes and lettering, pale-blue strokes, and lavender plus signs. The PNG is RGBA with transparent corners; dimensions and checksum are verified. Very low-alpha background samples remain in the generated PNG; actual device appearance has not been inspected.

The daylight mountain/car panorama is the existing generated image explicitly selected by the user on 2026-11-08. Both files were copied without raster postprocessing. No external SDK, font, or library was added.

## Validation

| Check | Result |
|---|---|
| `:app:assembleDebug` | PASS; final Gradle invocation successful with current outputs up to date |
| `:app:testDebugUnitTest` | PASS; current XML results contain 127 tests, 0 failures, 0 errors, 0 skipped; final invocation up to date |
| Asset dimensions, alpha metadata and manifest SHA-256 | PASS |
| `git diff --check` | PASS |
| Emulator launch/install, touch tests and visual screenshot comparison | NOT RUN, as explicitly requested by the user |
| Backend/API and device integration tests | NOT RUN; this change is presentation/assets only |

No API contract, backend, ViewModel, Repository, authentication, document engine, safety gate or SDK behavior was changed. Actual logo size, background blending and description placement still need the user's visual check on the PLEOS display. No push or deployment was performed.
