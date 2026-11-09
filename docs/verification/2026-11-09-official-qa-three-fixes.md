# PLEOS 공식 QA 후속 3건

## 기준과 확인 자료

작업을 시작할 때 로컬 `dev`는 `79fbcc4`였다. 원격 `dev`를 fetch한 뒤 `4feba6a`까지 fast-forward한 트리에서 구현했다. 변경 파일과 최종 커밋은 이 보고서가 포함된 커밋의 diff에 기록된다.

`AGENTS.md`, `DEVELOPMENT_GUIDE.md`, UI 여정·Premium 통합 QA, Realtime API와 문서 워크스페이스·DOCX 내보내기 계약을 대조했다. Notion의 [공식 문서 정리](https://app.notion.com/p/413b74b2b9058192adace10dcdc1bc25), [공식 문서 기반 감사](https://app.notion.com/p/413b74b2b905815d82e5fda13b03f095), [Realtime](https://app.notion.com/p/40cb74b2b90581e4865cdea39157d799), [Work](https://app.notion.com/p/40cb74b2b905810c9093ee9f243c678c), [Premium UI](https://app.notion.com/p/40fb74b2b905813e9840fb333a4c2ddc)를 확인했다.

## A. Rest와 Entertainment 오디오

- Android의 자동 감쇄에 맡긴다. 명시적으로 `AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK`가 전달되면 앱 플레이어의 현재 설정 음량에 0.2를 곱한다. 반복 이벤트에서 감쇄를 중첩하지 않는다. `GAIN`에서 가장 최근 음량을 적용하고 음소거를 보존한다.
- Rest는 일시 상실 시 일시정지한다. 정차, 활성 화면, 유효한 세션과 사용자 음향 설정이 모두 허용할 때만 `GAIN`에서 재개한다. 영구 상실이나 화면 비활성화 뒤에는 직접 재개가 필요하다. 타이머와 프리셋은 유지한다.
- 노래방은 일시·영구 상실 시 반주와 마이크를 중단하고 반주 위치를 보존한다. 포커스 복귀만으로 반주나 마이크를 다시 시작하지 않는다. 정차 확인과 마이크 권한이 필요하다.
- 화면에서 직접 재생하는 Rest 알람은 화면 비활성화나 상실 이벤트에서 포커스를 반납한다. 예약 알람의 OS 알림 채널과 Pickup TTS 어댑터는 기존 경로를 사용한다. Android 화면 비활성화와 기존 앱 생명주기 신호를 사용한다. 실제 차량 IGN·Suspend 전이는 기기 검증 대상이다.

공식 근거는 [PLEOS 오디오 포커스](https://document.pleos.ai/docs/pleos-only/vehicle-app-developer-guide/audio-focus-development-guide/audio-focus-development), [PLEOS Suspend](https://document.pleos.ai/docs/pleos-only/vehicle-app-developer-guide/audio-focus-development-guide/suspend-mode-audio-focus), [Android 오디오 포커스](https://developer.android.com/media/optimize/audio-focus), [PLEOS 주행 규제](https://document.pleos.ai/docs/pleos-only/vehicle-app-developer-guide/driving-restriction-development-guide)다.

## B. Parking 사용자 화면

- Debug 소스 세트에서만 개발자 좌표 입력과 검색을 표시한다. 사용자가 좌표 패널을 직접 연 뒤 사용할 수 있다. Release 소스 세트에는 빈 패널을 컴파일하고 ViewModel도 토글·좌표 변경·수동 검색 요청을 거부한다.
- 권한 거부, 위치 설정 꺼짐, 수신 실패에는 상황에 맞는 안내와 다시 검색을 제공한다. 좌표 입력 패널은 자동으로 열리지 않는다. 차량 위치와 입력 좌표를 분리했다. 반경·시간·목적·무료 필터 및 기존 Parking 서버와 차량 위치 SDK를 사용한다.

## C. Work DOCX 수동 전송

1. Work에서 기존 공개 저장 확인을 거쳐 `Download/StayPlus`로 DOCX를 내보낸다.
2. Realtime에서 PIN을 풀고 **차량 파일 보내기**를 누른다. 파일 크기, 연결된 PC의 식별자 끝 6자리, 새 파일 또는 원본 문서·기준 버전을 확인한 뒤 **전송**을 누른다.
3. 서버 업로드 완료 후 Windows Stay+에서 목록을 새로 고치고 버전을 선택해 직접 내려받는다.

파일 선택은 Android 10 이상에서 앱이 직접 내보낸 MediaStore 항목의 기록을 사용한다. 기록은 앱 전용 암호화 저장소에 인증된 사용자·서버·PC 연결, URI, 원본 파일·버전, 크기, SHA-256을 묶어 보관한다. 이전 공개 내보내기 파일은 출처 기록이 없어 Work에서 다시 내보내야 한다. 공개 파일 자체는 유지된다. [Android 공유 미디어 저장소](https://developer.android.com/training/data-storage/shared/media#storage-permission)

PC 원본을 편집한 DOCX는 해당 연결의 실제 기준 버전을 다시 조회한 뒤 수정본으로 준비한다. Work에서 만든 새 DOCX는 기존 Realtime의 원본 업로드 계약을 사용한다. 호스트에서 차량으로 등록한 클라이언트의 최초 업로드를 검증했다. 업로드 직전에는 형식, 20 MiB 제한, SHA-256과 대상 연결을 검사한다. 업로드를 중단하면 동일한 키와 스테이징 파일로 명시적으로 재시도한다. 서버 전송 세션이 만료된 경우 다시 내보내야 한다. 주행·UNKNOWN·PIN 잠금 및 화면 이탈 시 앱은 전송 작업을 취소한다. 네트워크에 이미 제출된 요청의 처리 결과는 서버 상태와 동일 키 재시도로 확인한다.

## 검증

| 상태 | 항목 | 확인 결과 |
| --- | --- | --- |
| PASS | Android Debug APK 및 계측 테스트 APK | `assembleDebug`, `assembleDebugAndroidTest` |
| PASS | Android Debug 전체 JVM | 284 통과, 실패·오류 0. Release 전용 테스트 1개는 이 변형에서 제외 |
| PASS | Release Parking JVM | 임시 Gradle init 스크립트로 Release 호스트 테스트 활성화. 5개 통과, Debug 전용 테스트 1개 제외 |
| PASS | Release 정적 검사 | 컴파일된 좌표 패널에 입력·수동검색 호출 없음. ViewModel 활성 필드가 false로 컴파일됨. Realtime의 정식 전송 경로도 Release에서 컴파일됨 |
| PASS | 호스트 Compose | 5개 화면 렌더링 성공. Parking 결과·위치 실패·Debug 좌표 패널, 활성 Rest, 실제 Premium Realtime 배경의 DOCX 전송 확인창. 로컬 산출물은 `android/app/build/reports/official-qa-three-fixes/`에 보관 |
| PASS | 백엔드 회귀 | 28개, 타입 검사·빌드·OpenAPI 검사 통과 |
| PASS | Windows Debug 빌드 | 오류 0, 경고 0 |
| PASS | 로컬 HTTP·Windows C# 클라이언트 | TXT·PDF·DOCX 원본/수정본, 멱등성, 연결 철회. 차량 최초 DOCX를 PC 목록과 버전 목록에서 조회하고 수동 다운로드해 해시·원본 바이트와 문서 XML 재열기 검증 |
| BLOCKED | 전체 Release 패키징 | 오프라인 실행에서 `lint-gradle:32.5.0` 캐시가 없어 `lintVitalAnalyzeRelease`를 실행하지 못함 |
| NOT RUN | PLEOS 에뮬레이터·실차 | 사용자 지시에 따라 기기 테스트 미실행. 실제 내비·통화·TTS·알람 라우팅, 스피커, 마이크 권한 철회, Suspend/Resume, 위치 권한 UI, MediaStore 및 PIN·주행 중 네트워크 차단은 기기 검증 대상 |
| NOT RUN | Cloud와 Windows UI | 운영 연결, 실제 Windows 앱 화면 조작과 Microsoft Word 열기는 미실행. 로컬 C# 클라이언트와 독립 ZIP/XML 파서 결과는 위 항목에 기록 |

## 변경 파일

Android 경로는 `android/app/src/` 기준이다.

- 오디오 앱 코드 `main/java/com/pleosstay/app/audio/{MediaFocusPlayback,PlaybackScreenState}.kt`, `rest/{RestAudio,RestAlarmPlayer,RestController}.kt`, `entertainment/audio/{AndroidKaraokeBackingPlayer,AndroidKaraokeMicrophone}.kt`, `entertainment/karaoke/KaraokeSession.kt`
- Parking 앱 코드 `main/java/com/pleosstay/app/parking/ui/{ParkingScreen,ParkingViewModel}.kt` 및 `debug`·`release`의 `java/com/pleosstay/app/parking/ui/ParkingDeveloperCoordinates.kt`
- Realtime 앱 코드 `main/java/com/pleosstay/app/realtime/{FileTransferScreen,FileTransferViewModel,RealtimeApi,RealtimeEntry,RealtimeWorkBridge,WorkExportCatalog,WorkTransferPickerDialog}.kt`
- Work 내보내기 연동 `main/java/com/pleosstay/app/work/docx/{DocxEditorController,DocxFileGateway}.kt`
- JVM 테스트 `test/java/com/pleosstay/app/` 아래 `audio/MediaFocusPlaybackTest.kt`, `entertainment/AndroidKaraokeMicrophoneTest.kt`, `rest/RestControllerTest.kt`, `parking/ui/ParkingViewModelTest.kt`, `realtime/WorkExportReceiptTest.kt`
- 계측 테스트 `androidTest/java/com/pleosstay/app/realtime/WorkExportCatalogDeviceTest.kt`
- 호스트 화면 `debug/java/com/pleosstay/app/realtime/WorkTransferPreview.kt`, `visualPreview/kotlin/com/pleosstay/app/preview/OfficialQaParkingScreenshots.kt`, `visualPreview/kotlin/com/pleosstay/app/realtime/OfficialQaWorkTransferConfirmation.kt`
- Windows 로컬 검증 `windows/StayPlus.LocalTests/Program.cs` 및 이 보고서

홈과 공통 Premium 디자인, MainActivity, 모드 전환·Waiting 안전 정책, Pickup, Study 저장, DOCX 편집 엔진, Backend·Supabase 및 기존 Windows 제품 코드는 기준 커밋과 동일하다.
