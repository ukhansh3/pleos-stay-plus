# UI/UX and local quiz matches - 2026-11-09

## Scope

Content-sized activity dialogs, Rest environment-sound selection/volume/mute and explicit preset saving, guarded audio-focus resume, 2048 and solo-quiz presentation, local passenger quiz matches, Study/Work recovery presentation, and responsive layouts. Existing Waiting, safety gateway, shared transition coordinator, document storage/export, logo/sidebar/assets, Manifest, and production Gradle configuration are preserved.

Rest presets without a volume field retain the prior MediaPlayer level (100%). Muting preserves the selected volume; session changes do not overwrite a preset until explicitly saved. Local matches support 2-4 players, 5/10/15 questions each, 10/0 points, joint winners, and in-process return. Explicit activity end or process death discards unfinished matches/names.

The offline bank contains 120 Korean questions derived from source-checked Wikidata structured facts (CC0): 60 element atomic-number questions and 60 capital questions. Source URLs and review state are stored with each question. Independent human review and empirical difficulty calibration remain pending. Source and licensing: https://www.wikidata.org/wiki/Wikidata:Licensing .

## Validation recorded before publication

- Android unit tests: 267 passed, zero failures/errors/skips.
- Backend regression tests: 28 passed.
- Debug APK and AndroidTest APK built; neither was installed or run on a device.
- Official host Compose Screenshot Test Suite: 54 journey/adaptive states rendered, followed by re-rendering 12 affected states after final presentation updates. These are generated reference images, not an approved-image comparison or click E2E test.
- Existing lint errors remain: MissingPermission in AndroidKaraokeMicrophone.kt, ResourceType in PreviewSamples.kt, NewApi in DocxFileGateway.kt. No newly introduced code diagnostics in the tested workspace.
- Workspace lint: 3 errors/49 warnings; three warnings belong to the excluded, pre-existing VisualVerificationStates.kt. That file is left uncommitted. Full lint is not passing and some dependency custom checks report obsolete APIs.
- `git diff --check` passed. Original assets, safety/Waiting/storage code, and unrelated uncommitted artifacts were preserved.

## Reproduce host rendering

The source snapshot includes JourneyPreviews.kt, JourneyScreenshotTests.kt, and their existing PreviewFrame/PreviewModels dependencies. No PDF, PNG, backup, or build outputs are committed. The prior visual-report screenshots and Notion handoff artifacts remain outside this commit.

The existing verified host setup uses AGP 9.5.0, Compose screenshot engine 0.0.1-alpha16 and Layoutlib 16.1.0-jdk17. The init script configures a test-only suite without editing production Gradle files. Official method: https://developer.android.com/studio/preview/compose-screenshot-testing-with-testsuites .

From `android/`, with the existing JDK/SDK and Gradle cache configured:

```powershell
.\gradlew.bat --offline --no-daemon --max-workers=1 --no-configuration-cache --console=plain '-Dorg.gradle.jvmargs=-Xmx2048m -Dfile.encoding=COMPAT' '-Pandroid.experimental.enableScreenshotTest=true' '-Pandroid.experimental.testSuiteSupport=true' -I ../tools/compose-preview/render-preview.init.gradle :app:updateDebugVisualPreviewDefaultTestSuite --tests '*Journey*'
```

Generated references appear under `android/app/src/visualPreviewDefaultDebug/reference/`; keep generated binaries out of the commit. All state data are synthetic. Rest's 14:00/14:20 clock is inspection-only, while remaining-time, 2048, quiz, and document-engine calculations reuse production code. Screens are actual production screens or actual component compositions with preview-only state, not screenshots of the PLEOS system UI.

## Unverified / deferred

Actual PLEOS microphone/PCM/audio routing, audio focus with calls/navigation, vehicle safety signals and input races, Suspend/Resume, IME/system panels, physical touch/readability and day/night checks, external navigation and live Pickup/Cloud sharing remain unverified. The full active Entertainment and Work-recovery hosts are not proven by component-only captures. Spotify integration and changes to sensitive temporary-file/public DOCX export policy are deferred.

No emulator, real device, Cloud changes, deployment, or Notion edits were used for validation.
