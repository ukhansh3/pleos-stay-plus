# Compose Preview — 2026-11-08

- Repository: `C:\Users\ukhan\pleos-stay-plus`, branch dev, start HEAD `309681d`.
- User requested UI inspection without the lagging emulator. No emulator, Android Studio window automation, backend server, SDK service, deployment, DB or live credentials used in this task.

## Implementation

Eight separate debug-only `@Preview` entry files at `android/app/src/debug/java/com/pleosstay/app/preview/`: Parking, Waiting, Pickup, Realtime, Rest, Study, WorkLibrary and WorkEditor. Each renders one 1682x1210dp API34 Korean app-content frame, using the actual navigation rail and production screen composables. Separate files avoid rendering all eight together by default.

`PreviewSamples` contains synthetic clearly named places/documents/participant/EV examples. The QR points to example.invalid, never a real invitation. It does not read user files. DOCX artwork reuses the packaged work_laptop resource only. No new image asset or dependency.

`PreviewModels` supplies blocked in-memory/no-operation ports rather than SDK/Room/Keystore/live authentication. No HTTP methods are invoked; the existing concrete Realtime client constructor is retained as a signature dependency, but its identity provider throws and its model remains locked. Actual operations are not previewed or reported as working.

`FeatureInspection` in main is only a typed inspection-state binding. It ignores providers outside `LocalInspectionMode`. Ordinary production UI keeps the same real flows and initial selection. Rest's unsupported settings resolver is skipped only in inspection. Controllers/ViewModels/Repositories/API/auth/safety/domain remain unchanged. No change to home, image assets, logo, shell, MainActivity or LocationGateway.

The debug frame refuses to create sample models outside inspection, supplies inert ActivityResult/back owners, and cancels preview coroutine scopes when disposed. No application navigation entry or exported component is added.

## Validation

| Check | Result |
|---|---|
| compileDebugKotlin | PASS |
| compileReleaseKotlin | PASS |
| Existing JVM suite | PASS 204, 0 failures/errors/skipped |
| Eight compiled Preview entry classes in debug; none in release | PASS |
| diff --check, protected source and real secret-value scan | PASS |
| Android Studio Preview actual render | NOT RUN; user will open Design/Split |
| Emulator/device, screenshot comparison, interactive functionality | NOT RUN by user instruction |

Compilation does not prove Layoutlib rendering, device font fidelity, or functional SDK/server behavior. This is a layout inspection tool, not a safety/auth bypass, API mock demo in production, or device acceptance test.

## How to use

See [Compose Preview guide](../ui/COMPOSE_PREVIEW_GUIDE.md). In Android Studio choose debug, search `ParkingPreview.kt`, open Design/Split and use Build & Refresh if requested. Do not select Run Preview, which is a device deployment action. Open one file at a time. Current public Android guidance: [Compose Preview](https://developer.android.com/develop/ui/compose/tooling/previews).

## Changed files

- `android/README.md`
- `android/app/src/main/java/com/pleosstay/app/parking/ui/ParkingScreen.kt`
- `android/app/src/main/java/com/pleosstay/app/pickup/ui/PickupScreen.kt`
- `android/app/src/main/java/com/pleosstay/app/realtime/FileTransferScreen.kt`
- `android/app/src/main/java/com/pleosstay/app/rest/RestScreen.kt`
- `android/app/src/main/java/com/pleosstay/app/study/StudyScreen.kt`
- `android/app/src/main/java/com/pleosstay/app/waiting/ui/WaitingScreen.kt`
- `android/app/src/main/java/com/pleosstay/app/work/WorkScreen.kt`
- `android/app/src/main/java/com/pleosstay/app/work/docx/DocxEditorPanel.kt`
- `android/app/src/debug/java/com/pleosstay/app/preview/ParkingPreview.kt`
- `android/app/src/debug/java/com/pleosstay/app/preview/PickupPreview.kt`
- `android/app/src/debug/java/com/pleosstay/app/preview/PreviewFrame.kt`
- `android/app/src/debug/java/com/pleosstay/app/preview/PreviewModels.kt`
- `android/app/src/debug/java/com/pleosstay/app/preview/PreviewSamples.kt`
- `android/app/src/debug/java/com/pleosstay/app/preview/RealtimePreview.kt`
- `android/app/src/debug/java/com/pleosstay/app/preview/RestPreview.kt`
- `android/app/src/debug/java/com/pleosstay/app/preview/StudyPreview.kt`
- `android/app/src/debug/java/com/pleosstay/app/preview/WaitingPreview.kt`
- `android/app/src/debug/java/com/pleosstay/app/preview/WorkEditorPreview.kt`
- `android/app/src/debug/java/com/pleosstay/app/preview/WorkLibraryPreview.kt`
- `android/app/src/main/java/com/pleosstay/app/ui/premium/FeatureInspection.kt`
- `docs/ui/COMPOSE_PREVIEW_GUIDE.md`

## Follow-up: all-screen gallery

Added debug-only `AllScreensPreview.kt` at the user's request. Nine independent native previews (approved home + eight existing feature previews) can be displayed in the IDE Grid/List, or selected individually in Focus mode. Existing per-screen files remain available. The home gallery reuses StayHomeScreen and the real rail without modifying production home code. All entry points remain inspection-only. No SDK/server/file operation or emulator launch. `compileDebugKotlin` and diff --check PASS; gallery rendering in Android Studio NOT RUN. Existing JVM tests were not repeated for this debug-wrapper/documentation-only change.

## Follow-up: Realtime Preview version schema

The Realtime sample used `sizeBytes` while the actual FileTransferScreen calls `getLong("size")`. This missing required field can throw while rendering the version row. Corrected the debug-only sample to `size`; the existing server/UI contract is unchanged. Both the standalone RealtimePreview and gallery reuse this sample. Compared the display's required id/name/fileName/versionNo/size keys with the fixture. compileDebugKotlin PASS; actual IDE rendering NOT RUN (user operates Studio). No emulator or live server used.
