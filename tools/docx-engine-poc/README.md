# Isolated DOCX engine PoC

This project is an evaluation harness, not a production dependency or a distributable editor SDK. The user approved engine evaluation and chose **custom OOXML** after both candidates ran on Pleos API34. Production code is under `android/app/src/main/java/com/pleosstay/app/work/docx/`.

- Apache POI XWPF **5.4.1**, official Maven artifact, Apache-2.0. XMLBeans and other transitive components are test-only. If POI were adopted/distributed, its licenses/notices and transitive licenses would need to be retained/reviewed.
- The engine file copied here is the **frozen initial PoC**, not a second production engine. Final regression tests exercise the current main-app engine.
- No INTERNET, vehicle, external-storage or location permission. Package `com.pleosstay.docxprobe` cannot read the main app's private credentials/documents.
- Fixtures contain generated Korean text, simple tables and solid-colour PNGs. Build/output/cache/local SDK configuration is ignored. Do not add real documents or SDK binary distributions.
- AGP9.5.0, compileSdk37, target/min34. The actual test runtime was Pleos Connect v2.1.0 / Android API34 x86_64.

## Reproduce the isolated probes

From repository root, with Android Studio JBR and Android SDK installed:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
.\android\gradlew.bat -p tools/docx-engine-poc :app:assembleDebug --max-workers=2 --console=plain
$adb = "$env:ANDROID_HOME\platform-tools\adb.exe"
& $adb -s emulator-5554 install -r tools/docx-engine-poc/app/build/outputs/apk/debug/app-debug.apk
& $adb -s emulator-5554 shell am instrument -w com.pleosstay.docxprobe/.PoiProbe
& $adb -s emulator-5554 shell am instrument -w com.pleosstay.docxprobe/.OoxmlProbe
```

Each probe creates, modifies, exports and reopens its own actual DOCX in its private files directory. The result bundle reports PASS/FAIL and elapsed time. These small smoke probes are not a performance benchmark or a guarantee for arbitrary Word documents.

## Final production-editor tests

```powershell
.\android\gradlew.bat -p android :app:assembleDebug :app:testDebugUnitTest :app:assembleDebugAndroidTest --max-workers=2 --console=plain
& $adb -s emulator-5554 install -r android/app/build/outputs/apk/debug/app-debug.apk
& $adb -s emulator-5554 install -r android/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
& $adb -s emulator-5554 shell am instrument -w -e class com.pleosstay.app.work.DocxEditorDeviceTest,com.pleosstay.app.work.DocxExternalDocumentDeviceTest com.pleosstay.app.test/androidx.test.runner.AndroidJUnitRunner
```

Run the process-boundary test in two separate invocations (the default suite intentionally skips it):
```powershell
& $adb -s emulator-5554 shell am instrument -w -e class com.pleosstay.app.work.DocxProcessDeviceTest -e docxPhase seed com.pleosstay.app.test/androidx.test.runner.AndroidJUnitRunner
& $adb -s emulator-5554 shell am force-stop --user 10 com.pleosstay.app
& $adb -s emulator-5554 shell am instrument -w -e class com.pleosstay.app.work.DocxProcessDeviceTest -e docxPhase verify com.pleosstay.app.test/androidx.test.runner.AndroidJUnitRunner
```
Use the emulator's actual Android user instead of 10 where different. Tests use isolated test records, not live pairing/auth. Do not clear app data.

After the editor and external-document tests, independently verify the **production** exports:
```powershell
python tools/docx-engine-poc/verify_main_exports.py --pull --user 10
```
This requires existing `python-docx` and Pillow in the verification environment; they are not Android/production dependencies. It checks ZIP/XML, Korean/range formatting, table-property order, image pixels/dimensions, and byte preservation of every unmodified external-fixture part. Outputs/report stay under ignored `output/main/`. It does not render Microsoft Word pages or prove live Windows/Cloud E2E.

Sources and actual results: [verification report](../../docs/verification/2026-11-07-work-docx-editor.md).
