# Isolated document-engine evaluation

This is a separate **test-only** Android project. It is not part of the Stay+ app and is not a production engine selection. It has no internet, vehicle, location or external-storage permission. Only original synthetic fixtures are used.

See [official sources, versions, SHA-256 and actual failures](../../docs/verification/2026-11-07-work-engine-review.md). Obtain the exact official Android evaluation artifacts there and place them under the ignored paths referenced in `app/build.gradle.kts`. Do not commit vendor binaries, license files, APKs or generated output.

From repository root, with the existing Android SDK/JDK configured:

```powershell
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
.\android\gradlew.bat -p tools/work-engine-poc :app:assembleDebug --max-workers=2 --console=plain
$adb = "$env:ANDROID_HOME\platform-tools\adb.exe"
& $adb -s emulator-5554 install -r tools/work-engine-poc/app/build/outputs/apk/debug/app-debug.apk
& $adb -s emulator-5554 shell am instrument -w com.pleosstay.workenginepoc/.EngineProbe
```

The instrumentation writes `files/evaluation-output` in its own private directory. Pleos uses Android user10 in the verified environment; `run-as com.pleosstay.workenginepoc --user 10` accesses that test package only. Use binary-safe subprocess stdout when retrieving PDFs. Do not treat instrumentation exit code0 as success: inspect each field in `report.json`.

Final revision3 on API34/x86_64 still reports PDF self-validation FAIL and Words VerifyError. The actual exported synthetic PDF passed independent pypdf checks. No trial restriction was bypassed. The documentation distinguishes those results; neither proves a complete Work editor.

With bundled or separately configured pypdf6.10.0/Pillow available, independently inspect the synthetic export directory:

```powershell
python tools/work-engine-poc/verify_export.py tools/work-engine-poc/output/revision-3
```

This checker checks only the known fixture: page order, new/absent body text, removed page and blue image pixels. It does not validate arbitrary documents, Korean editing, visual layout fidelity or licensing. Poppler page rendering was inspected separately. Temporary-license requests, commercial licenses, product SDK adoption and publishing are separate approval gates.
