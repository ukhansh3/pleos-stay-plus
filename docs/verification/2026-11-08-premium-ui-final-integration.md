# Premium UI 기능 화면 통합 — 2026-11-08

## Git 기준과 승인 범위

실제 PC 저장소 `C:\Users\ukhan\pleos-stay-plus`, 브랜치 dev. 시작 로컬 HEAD는 `42e7565dbb901494a782d14ffff12e938878cc3f`. fetch 후 동료의 최신 `51eccaa`로 fast-forward하고 Entertainment 도메인·런타임·테스트 19개 파일을 보존했다. 구현 기준은 이 최신 버전이다. 최종 커밋은 이 보고서를 포함하는 Git 커밋 및 작업 완료 응답에서 확인한다.

main, Backend/API/DB/RLS, 새 SDK·의존성·권한, 배포는 수정하지 않는다. 사용자는 이번 작업의 dev 커밋·push를 승인했으며, 이후 **에뮬레이터는 렉 때문에 실행 중지**를 지시했다. 해당 지시 이후 기기 테스트/설치/캡처를 더 실행하지 않았다. 에뮬레이터 프로세스 없음 확인. 재시작하지 않는다.

## 확인한 자료와 시안

AGENTS.md, DEVELOPMENT_GUIDE.md 14~18 및 최신 결정, Premium UI 명세·이미지 manifest, API README·OpenAPI·MODE_API_MATRIX, DOCUMENT_WORKSPACE_CONTRACT, Study/Work/DOCX 명세, 11/07~11/08 기능·UI 검증 보고서와 실제 최신 Android 소스를 대조했다.

Notion [프로젝트](https://app.notion.com/p/40ab74b2b905814db50eedba99a53559), [API 색인](https://app.notion.com/p/40bb74b2b905818d894ffd1676fd1be0), [Premium UI](https://app.notion.com/p/40fb74b2b905813e9840fb333a4c2ddc), Parking/Waiting/Pickup/Realtime/Rest/Study/Work/Entertainment 하위 명세를 읽기 전용으로 확인했다. 최신 사용자 지시·현재 코드가 오래된 예정 문구보다 우선한다. Waiting HTTP 초안, Work PDF 편집·자동 업로드는 구현하지 않았다.

Premium 노션의 새 첨부 8개를 실제 내려받아 담당자가 열어 확인했다. 같은 페이지 하단의 과거 업로드 현황을 최신 시안으로 오인하지 않았다. 서명된 다운로드 URL은 저장하지 않는다.

| 실제 시안 파일 | 해상도 | SHA-256 |
|---|---|---|
| `02_parking.png` | 1682 x 1210 | `336478cf248ad70d4ae53ab07e07a5d74b6691857d8f684f0b4b3748e3f5432e` |
| `03_waiting.png` | 1682 x 1210 | `d7c48a7a5b1c2ff1133cc837575d6869d3344cf1905669b643d59bb548f7f9b3` |
| `04_pickup.png` | 1682 x 1210 | `a259d1e2db2e2178934a01af5d66c952d4dc5fe75ec409f7841e939bb4106efc` |
| `05_realtime.png` | 1682 x 1210 | `bfd2904c6c905e057e13b3f49ef2515ace395a20dc8eb546b385cd9e8b947f66` |
| `06_rest.png` | 1682 x 1210 | `b715de1212e7d4e83cf9cdd136b1c4331b4ad15809b1b41c5030da42ce97bf3e` |
| `07_study.png` | 1682 x 1210 | `5b8d8c6e9e13c3d55e0bc1def57bec87b42ac58101ad3d9e691c09ee8c5481da` |
| `08_work_library.png` | 1682 x 1210 | `cf835abf03525a6e4a1fefc918ca7b7f1f626b7d88fbe5fc696f4d7a5a9deea8` |
| `09_work_editor.png` | 1682 x 1210 | `038f5d62355a1b01871e1641a71db5ed61cab04f600e29a1580a7b61ce5d91d6` |

원본은 작업자의 임시 premium-references 폴더에 보존했다. 전체 PNG를 실제 UI 배경으로 사용하지 않았다.

## 담당 및 구현 결과

Supervisor는 공통 컴포넌트·홈 보호·문구·빌드·Git을 담당했다. UI A는 Parking/Waiting/Pickup 구현 후 화면·반응형 QA 테스트를 작성했다. UI B는 Realtime/Study/Work/DOCX와 관련 기존 테스트를 맡았다. UI C는 Rest·Entertainment·에셋 검토 후 독립 정적 QA를 수행했다. 공통 MainActivity/StayAppShell은 Supervisor만 수정했고 파일 소유권을 분리했다.

| 화면 | 기존 UI에서 변경한 부분 및 실제 연결 |
|---|---|
| Parking | 조건/목록/선택 상세 3열. 기존 현재 위치·반경·시간·목적·무료 필터, 검색/select/navigation/Waiting 콜백. 요금·운영·조건·출처 표시. 좁은 창 세로 대응 |
| Waiting | 큰 시간·원형 타이머/현재 장소·시작·예정 종료 2열, 연장·종료 확인·기록·모드 진입. 기존 Room·ModeStartManager 유지 |
| Pickup | QR/참가자·위치 상태/절차 3열. 실제 invitation.shareUrl만 QR 생성, 만료 QR 숨김, 재발급·합류 완료·취소 확인. blocked 시 내용 차폐 |
| Realtime | 연결·8자리 코드/파일·버전/선택 전송 3열. 기존 쌍방 승인·PIN·다운로드·진행률·전환·해제 유지. Fake Work 사용자 버튼 제거 |
| Rest | 큰 사진·타이머/설정 2열, 실제 프리셋·시간·연장·종료·빗소리/무음·알람·EV 유지. 기존 MINIMAL 프리셋은 사진을 제거하므로 시안과 차이가 있음 |
| Study | 자료/실제 PDF·DOCX·TXT/메모·진도 3열. 기존 버전·페이지·확대·CRUD·완료·시간·복원 콜백 유지 |
| Work 자료함 | 기존 파일·버전·초안·최근 작업, 새 DOCX·로컬 파일 열기·타이머. PDF는 기존 지원 동작만 표시 |
| Work DOCX | 구조/실제 본문/서식·표·이미지 도구 3열. 기존 Controller/엔진의 선택·편집·Undo/Redo·암호화 초안·내보내기·재열기 재사용 |
| Entertainment | Runtime 주입형 2048·퀴즈 UI와 테스트 작성. **실제 공통 EntertainmentActivityAdmission adapter 부재로 production 진입은 BLOCKED**. 임의 permit·마이크·영상 실행 노출 없음. 기존 준비 중 유지 |
| Settings | 실제 설정 항목이 없어 간단한 빈 상태. 가짜 버튼·토글 없음 |

각 화면의 Compose 구현은 완료했으나 최신 기기 실행·시각적 일치·터치 검증은 아래와 같이 미완료다. 문서 편집 화면은 기존 reflow 엔진이며 Microsoft Word 고정 페이지와 동일한 renderer로 주장하지 않는다.

## 디자인·문구·에셋

StayColors·typed route·기존 앱 셸과 액션을 재사용했다. 기능 전용 FeaturePanel은 얇은 테두리와 절제된 네이비 표면을 제공하며 홈 GlassPanel/PhotoPanel을 바꾸지 않는다. Study/Work/Realtime 문서 패널은 네이티브 Compose다.

상시 API 연동/SDK 검증/개발 설명, OOXML·암호화 구현 설명, 장황한 예정 기능 안내, Fake Work 버튼을 제거/간결화했다. 실제 오류·권한·정차·PIN·저장·공개 Downloads 확인은 상황에 맞게 유지한다. 로그와 QA 문서의 SDK_VERIFY는 사용자 화면이 아니므로 보존한다.

신규 이미지/라이브러리 없음. 기존 parking_visual/rest_pillow/work_laptop 등 무문자 에셋만 재사용했다. 홈 이미지·로고 변경 없음. [에셋 크기·해시·출처 검토](../ui/2026-11-08-feature-assets.md). 사용자 문서를 외부 AI/변환 서버에 업로드하지 않았다.

## 기존 기능과 홈 보호

정적 검사 PASS: StayHomeScreen, StayVisuals, StayDesign/StayColors, Theme, 모든 drawable-nodpi PNG, PREMIUM_ASSET_MANIFEST가 기준 51eccaa와 동일하다. StayAppShell은 Settings 본문만 변경됐고 rail/홈 관련 코드 구간은 동일하다. MainActivity는 Work 이동 확인 문구만 변경했다.

ViewModel, Repository, HTTP DTO/Backend, SDK adapters, ModeStartManager, Study 데이터/저장, DOCX 엔진, RestController, Entertainment 도메인, Manifest/Gradle 의존성/권한, Supabase/RLS는 수정하지 않았다. PIN/foreground/FLAG_SECURE/저장중 navigationGuard와 공개 Downloads 명시적 확인 경로를 유지했다. 이는 소스 보호 확인이며 홈 전후 스크린샷 비교 PASS가 아니다.

## QA에서 수정한 문제

- Work 목록의 실제 모델에 없는 mimeType 참조를 수정해 컴파일 회복.
- 좁은 Pickup 화면의 무한 높이 중첩 세로 스크롤을 제거하고 wide일 때만 내부 스크롤.
- Parking 위치 실패의 개발자/FusedLocation 문구를 UI에서 짧게 매핑. 권한 거부·비활성·실패에 기존 권한 안내 액션 제공.
- Study 문서 열기 때 상시 노출되던 긴 DOCX 기술 안내를 짧은 호환성 안내로 매핑.
- Realtime 일반 API busy 상태에 이전 다운로드100%가 보이지 않도록 실제 다운로드 요청의 새 샘플만 UI 진행률로 추적. ViewModel은 변경하지 않음.

## 검증 결과

| 검사 | 상태와 실제 결과 |
|---|---|
| assembleDebug | PASS |
| assembleDebugAndroidTest | PASS. **계측 코드 컴파일·APK 생성**이며 기기 테스트 PASS가 아님 |
| Android JVM 전체 | PASS 204개, 실패/오류/skip0 |
| Backend npm test | PASS 28개, Parking/Pickup/Realtime 기존 회귀 |
| Backend typecheck / build | PASS |
| diff --check, 홈 보호·변경 경계·실제 키 노출 검사 | PASS |
| 새 캡처 fixture10 / 900dp 반응형3 / Entertainment UI2 | NOT RUN. 컴파일만 PASS, 사용자 에뮬레이터 중지 |
| 수정된 기존 UI/PIN/DOCX 기기 회귀 | NOT RUN. 컴파일만 PASS, 사용자 에뮬레이터 중지 |
| 실제 앱 진입 기준 테스트 시도 | FAIL. 변경 전 앱에서 navigation 후 No compose hierarchies found. 중지 전 원인 확정/해결/최종 재검증 못 함 |
| 시안 대 실제 UI·홈 전후 이미지 비교 | NOT RUN. 최종 화면 스크린샷 확보 못 함 |
| 실제 FusedLocation/NaviHelper·Cloud Auth/RLS/초대·PC 왕복·실차 | NOT RUN. 별도 Cloud/DB/배포 변경 없음 |
| Entertainment production 실행 권한 adapter | BLOCKED |

검증용 AVD는 기존 실사용 AVD와 별도의 임시 데이터 영역을 사용했다. 초기 디스크 요구량 문제 이후 작은 데이터 설정을 사용했지만 두 차례 Windows headless QEMU 접근 위반(0xc0000005)이 발생했다. 일반 host 렌더러로 재시도 중 사용자가 렉 때문에 중지를 지시해 종료 상태를 확인했다. 부팅·설치만으로 UI 성공을 주장하지 않는다.

## 스크린샷·남은 QA

이번 최종 UI의 성공한 전후 스크린샷은 없다. 과거11/07 캡처나 새 생성 이미지로 대체하지 않았다. 시안과 최종 실행 화면의 시각적 동일성은 확인되지 않았다.

새 test-only 캡처 코드10개는 홈+8개 시안+Parking 상세를 실제 Compose/VM/Room/Reader/OOXML/암호화 저장에 연결한다. 합성 위치·문서·loopback HTTP·시험용 알람/오디오는 androidTest에만 있으며 파일명에도 fixture를 명시한다. 테스트 코드 존재와 컴파일은 실제 API·인증·차량 E2E 증거가 아니다.

사용자 직접 QA: 1682x1210 비율·잘림·스크롤·터치·활성 메뉴, 홈 유지, 실제 정차/UNKNOWN/주행·PIN·전경 차폐, 한글 입력, DOCX 확인 내보내기/재열기, 실제 서버·파일·초대 흐름. 플랫폼 SAF 제한은 기존 미해결 사항이며 이 UI 작업으로 해결됐다고 주장하지 않는다. PDF 편집/자동 PC 업로드는 구현하지 않았다. Entertainment는 실제 공통 실행 권한 계약/adapter 이후 별도 연결한다.

사용자가 다시 요청하기 전 에뮬레이터를 실행하지 않는다.

## 변경 파일

- `android/app/src/androidTest/java/com/pleosstay/app/ui/ParkingPremiumTest.kt`
- `android/app/src/androidTest/java/com/pleosstay/app/ui/PremiumFeatureActionsTest.kt`
- `android/app/src/androidTest/java/com/pleosstay/app/work/DocxEditorDeviceTest.kt`
- `android/app/src/androidTest/java/com/pleosstay/app/work/WorkPinGateDeviceTest.kt`
- `android/app/src/main/java/com/pleosstay/app/MainActivity.kt`
- `android/app/src/main/java/com/pleosstay/app/parking/ui/ParkingScreen.kt`
- `android/app/src/main/java/com/pleosstay/app/pickup/ui/PickupScreen.kt`
- `android/app/src/main/java/com/pleosstay/app/realtime/FileTransferScreen.kt`
- `android/app/src/main/java/com/pleosstay/app/realtime/RealtimeEntry.kt`
- `android/app/src/main/java/com/pleosstay/app/realtime/RealtimeLockedScreen.kt`
- `android/app/src/main/java/com/pleosstay/app/rest/RestScreen.kt`
- `android/app/src/main/java/com/pleosstay/app/study/StudyScreen.kt`
- `android/app/src/main/java/com/pleosstay/app/ui/premium/StayAppShell.kt`
- `android/app/src/main/java/com/pleosstay/app/ui/premium/StayFeatureComponents.kt`
- `android/app/src/main/java/com/pleosstay/app/waiting/ui/WaitingScreen.kt`
- `android/app/src/main/java/com/pleosstay/app/work/WorkScreen.kt`
- `android/app/src/main/java/com/pleosstay/app/work/docx/DocxEditorPanel.kt`
- `android/app/src/androidTest/java/com/pleosstay/app/entertainment/EntertainmentUiTest.kt`
- `android/app/src/androidTest/java/com/pleosstay/app/ui/PremiumFeatureScreenshotsTest.kt`
- `android/app/src/androidTest/java/com/pleosstay/app/ui/PremiumProofAdapters.kt`
- `android/app/src/androidTest/java/com/pleosstay/app/ui/PremiumProofFixtures.kt`
- `android/app/src/androidTest/java/com/pleosstay/app/ui/PremiumResponsiveLayoutsTest.kt`
- `android/app/src/main/java/com/pleosstay/app/entertainment/EntertainmentScreen.kt`
- `docs/ui/2026-11-08-feature-assets.md`
- `docs/verification/2026-11-08-premium-ui-final-integration.md`
