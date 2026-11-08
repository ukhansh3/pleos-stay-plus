# Premium feature asset review — 2026-11-08

## Ownership and reference

Reviewed latest dev `51eccaa` plus the eight PNG files resolved from the current Notion Premium UI page. The local reference files are comparison inputs only; none is rendered as an app background or committed as a generated UI. The home source, home images, brand logo and asset manifest are protected by the supervisor.

## Existing raster inventory

| Resource | Pixels | Use in this task | SHA-256 |
|---|---|---|---|
| `entertainment_visual.png` | 1448 × 1086 | Preserved; existing resource | `d6d51d22c0dd35561f47f23f8997ae3453c855f79b43271eba5fbc4be218c13b` |
| `files_visual.png` | 1774 × 887 | Preserved; existing resource | `39181ef0f906c26d5b157a6c3653e6acebdef61c930c48a121178ee2dd76105e` |
| `hero_daylight_expanded.png` | 2172 × 724 | Protected home/brand resource; no modification | `b84a1e5e9fd1e40ee6d7960afbb35025a35d2ad30e5f14108b27ff8b03178eac` |
| `hero_daylight_rear.png` | 2172 × 724 | Protected home/brand resource; no modification | `a62ce6cbf8d8600623f73e837d2dc2daf12f402958d82c7126f4354ee53fb024` |
| `hero_sunset_rear.png` | 2172 × 724 | Protected home/brand resource; no modification | `a19cc030a981e4c0d82c38ffe99d1d010dd15c6685a35a052edf4185dccef96d` |
| `parking_visual.png` | 1774 × 887 | Preserved; existing resource | `77a2ef7cb92f77af33cdb27e41202dd87da7366398b32a71bd8cea31ddaa78db` |
| `pickup_visual.png` | 1774 × 887 | Preserved; existing resource | `d7084a070a86addb0a4ef89736c78a07b62818910e2dfcdea6d4a76d53167fc6` |
| `rest_pillow.png` | 1448 × 1086 | Read-only reuse behind the Rest timer; native timer/buttons overlaid | `5d2a3b88d983ad8f978070222be4ce692a178a858d5ec055136ab6ac637112c7` |
| `stay_brand_logo.png` | 1228 × 1281 | Protected home/brand resource; no modification | `e432863c477739605d7b3d1cf9aa5950013a1bf206acef98dc89e159d02d8aa4` |
| `study_book.png` | 1448 × 1086 | Preserved; existing resource | `930c745375c31f2cfdbf6a26866b7680ecdc6e0ecd289adb5aa0f505ac11ba4a` |
| `work_laptop.png` | 1448 × 1086 | Preserved; existing resource | `663e93039e507e76fe5bf5eac813bc2df92a0106c95be64d8322a66ff722eb5f` |

## Approved reference files

| PNG | Pixels |
|---|---|
| `02_parking.png` | 1682 × 1210 |
| `03_waiting.png` | 1682 × 1210 |
| `04_pickup.png` | 1682 × 1210 |
| `05_realtime.png` | 1682 × 1210 |
| `06_rest.png` | 1682 × 1210 |
| `07_study.png` | 1682 × 1210 |
| `08_work_library.png` | 1682 × 1210 |
| `09_work_editor.png` | 1682 × 1210 |

## Decision

- No new image generation, third-party download, font, SDK or dependency is needed for Rest or the game/quiz presentation. Existing generated assets already have provenance in `PREMIUM_ASSET_MANIFEST.json`.
- Rest retains the reference’s broad left timer/photo area and right settings column; runtime text, presets, EV readings, alerts and buttons stay native Compose. The minimal-display preset intentionally removes decorative photography during an active session.
- Entertainment 2048 tiles, directional controls, scores and quiz options are native Compose driven by the real existing domain services. No fabricated participant, vehicle, document or API response is shown.
- No microphone, karaoke or external-media launch controls are exposed. Runtime-supported game/quiz UI exists behind a host-supplied `EntertainmentRuntime`; production navigation remains BLOCKED because a real shared activity admission adapter is absent. The current `EntertainmentActivityAdmission` contract explicitly describes that coordinator as future work; a permissive permit is not acceptable in production. Test-only permits are confined to `androidTest`.
- No approved standalone Entertainment PNG was supplied. Its optional unregistered game/quiz screen follows the current colors/type scale without claiming pixel-level equivalence.

## Verification status

- Image inventory/dimensions/SHA-256: inspected directly from local PNG metadata.
- `06_rest.png`: visually inspected; 2-column geometry and timer hierarchy used.
- `git diff --check` for owned source files: PASS at handoff.
- Build, emulator, UI screenshots, and new `EntertainmentUiTest` execution are performed by the supervisor, not by this asset/UI agent. No result is assumed.
