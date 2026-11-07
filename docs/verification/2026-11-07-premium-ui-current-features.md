# Premium UI 복원·최신 기능 연결 — 2026-11-07

## 기준과 사용자 승인

- 실제 PC 작업 폴더: `C:\Users\ukhan\pleos-stay-plus`, `dev`.
- 시작 HEAD와 확인한 GitHub dev: `65785a43f327f186d360fa619c117203f3ec836b`. 시작 작업 트리 clean, fetch 후 양쪽 차이 0/0.
- `main` 기준: `0ea77f3394b74cd873055b2c4358893ed6e95653`. 변경하지 않는다.
- 바탕화면 `STAY 백업/2026-11-07_163623`을 읽어 확인했다. 기존 UI 보존 커밋 `d88e68c` 및 로컬 통합 보존 브랜치 `codex/ui-integrated-20261107` (`0abe33f684424681e304e56cbfddc8a0ee9eeada`)에서 **프레젠테이션 파일만** 선택 복원했다.
- 백업 전체/MainActivity/이전 Realtime 인증 코드로 덮어쓰지 않았다. 현재 Work DOCX·Study·Rest·ModeStartManager와 최신 PIN/foreground 경계를 기준으로 통합했다.
- 사용자는 새로 생성한 차량 후면·산/석양 및 네이비 카드 **8장 적용**을 명시적으로 선택했다. 백업과 비밀 설정 폴더는 수정하거나 복사하지 않았다.
- 작업 중 다른 에이전트는 모두 완료 상태였고, 이 작업은 총괄 단독으로 UI 경계만 수정했다.
- 이번 승인 범위는 Android UI 구현·연결·로컬 검증이다. GitHub Push, Notion 수정, 배포, DB 변경 및 신규 SDK/Gradle 의존성 추가는 수행하지 않는다.

## 확인한 명세와 차이 처리

- root AGENTS, DEVELOPMENT_GUIDE, [Premium UI 명세](../ui/PREMIUM_UI_IMPLEMENTATION_SPEC.md), [OpenAPI](../api/openapi.yaml), [API README](../api/README.md), [모드 API 분류](../api/MODE_API_MATRIX.md), 현재 소스.
- [Notion UI 시안](https://app.notion.com/p/40fb74b2b905813e9840fb333a4c2ddc): VEH-01~06 최신 _139 PNG 링크와 차량용 화면 범위 확인. 새 이미지·색상은 이번 사용자 선택이 우선한다.
- [Parking](https://app.notion.com/p/40cb74b2b90581cfb9f2e20d22428008), [Waiting](https://app.notion.com/p/40cb74b2b905815db121cf790c31eb0c), [Pickup](https://app.notion.com/p/40cb74b2b9058104a652dab710f91487), [Realtime](https://app.notion.com/p/40cb74b2b90581e4865cdea39157d799).
- [Study](https://app.notion.com/p/40cb74b2b90581fda3c6f3fddcd6b6fa), [Work](https://app.notion.com/p/40cb74b2b905810c9093ee9f243c678c), [공통 모드 계약](https://app.notion.com/p/3edb74b2b905833c8e258157332a17bb).
- 일부 Notion/UI 문서는 Study·Work를 예정 기능으로, 이전 Work 목표를 PDF 편집·업로드까지로 서술한다. 현재 사용자 승인과 `WORK_DOCX_EDITOR_SPEC.md`, 커밋65785a4가 우선한다. 현재 DOCX 작성/편집/로컬 내보내기를 연결하고 PDF 편집·Realtime 업로드는 다시 추가하지 않는다.
- 오래된 Pickup 설명의 Waiting 종속 흐름을 복구하지 않는다. 최신 독립 Pickup 및 명시적으로 연결된 과거 세션만 종료하는 현재 코드를 유지했다.
- OpenAPI의 Waiting HTTP 초안은 실행 API가 아니다. 새 UI도 Android Room 로컬 인터페이스를 호출한다. 디자인 때문에 새 서버 계약을 생성하지 않았다.

## 구현한 UI

- 공통 네이비 테마, 흰색 선형 아이콘/텍스트, 차분한 블루 액션·테두리. 보라색 강조는 활성 내비게이션에 집중했다.
- 홈: 넓은 현대차 후면·산·석양 사진, 이미지 alpha0.82와 네이비 페이드, 실제 Waiting 상태·장소·시간, 모드4종과 주요 작업3종.
- 이미지 속 글자/아이콘/주소/QR을 버튼·데이터로 사용하지 않는다. 사진과 별개인 실제 Compose 텍스트/액션/상태를 사용한다.
- Rest, Study, Work는 현재 기능으로 진입한다. Entertainment와 일반 설정은 명시적 준비 중이다.
- Parking 검색/상세, Waiting 타이머/기록, Pickup QR/상태, Realtime 연결/파일/버전/전송을 복원한 공통 스타일로 구성했다.
- Rest·Study·Work의 기존 기능 본문과 문서 편집기는 보존하고 공통 테마·내비게이션을 적용했다. 미첨부 상세 시안과 픽셀 동일성을 주장하지 않는다.
- 앱에 할당된 콘텐츠 영역에서 BoxWithConstraints/weight/dp/safeDrawing insets를 사용한다. PLEOS Driving View·시스템 바·공조를 그리거나 숨기지 않는다.
- 네비게이션 그룹: Home=HOME, 검색/상세/Waiting=PARKING, Pickup=PICKUP, Rest/Study/Work=MODES, 파일=FILES.
- 뒤로 가기·저장 상태 복원·Rest 알람 진입을 기존 콜백에 연결했다. Work 편집 중 Sidebar/뒤로 이동은 기존 암호화 초안 저장 상태 확인 뒤 진행하도록 host guard를 유지했다.

## 화면→기능 연결

| 화면 액션 | 실제 연결 | 이번 검증 / 한계 |
| --- | --- | --- |
| 내 주변 검색 / 필터 / 직접 테스트 좌표 | 기존 permission launcher → ParkingViewModel → HttpParkingRepository → GET parking/places | UI fixture 검색·오류·재시도 통과. 실제 FusedLocation adapter 미수정; 이번에 실제 위치 수신은 재검증하지 않음 |
| 장소 선택 / 상세 / 길 안내 | select → GET parking/places/{id}; 기존 NavigationGateway → 공식 NaviHelper adapter | 상세 거리 보존·선택 좌표 전달 UI 테스트. 이번에는 실제 NaviHelper route callback을 새로 요청하지 않음 |
| 장소에서 대기 관리 | selected id/name → WaitingPlace → WaitingScreen | UI 전달 callback 및 실제 Room 기반 시작/연장/종료/UNKNOWN 차폐 테스트 |
| 홈·대기 모드의 Rest/Study/Work | 기존 RestController / DocumentWorkspaceEntry / ProtectedWorkWorkspace | 실제 진입 경로 및 하위 기능 회귀. 공통 ModeStartManager 정책 미수정 |
| Pickup 시작 | 기존 위치 권한 흐름 → start() (Waiting ID 자동 전달 없음) → 현재 HttpPickupRepository | 실제 UI+ViewModel fixture로 독립 시작 확인. 서버 HTTP 도메인은 Backend 회귀; 이번 실 Supabase E2E는 미실행 |
| 초대 QR / 재발급 / 상태 / 완료 | 실제 invitation.shareUrl → ZXing; issueInvitation/complete/cancel 기존 메서드 | 새 URL 재인코딩·에러·완료 후 QR 제거 UI 테스트. 시안 QR 사용 안 함 |
| 파일 연결 / 코드 / 버전 / 전송 | 현재 RealtimeEntry/LockedScreen → FileTransferViewModel / 기존 RealtimeApi | 기존 PIN·인증·버전/해시 bridge 기기 회귀; Cloud 실계정 전송은 재실행 안 함 |
| Study 열람·메모·진도 / Work DOCX | 현재 Gateway/VM/Repository/엔진 | 기존 Study renderer, DOCX Compose·Keystore·내보내기, 안전 차폐 회귀 |

이 연결표는 코드 통합을 뜻하며 모든 실서버/실차 시나리오가 완료됐다는 뜻이 아니다. HTTP, Android LOCAL, 공식 SDK 경계를 분리했다.

## 인증·안전·기존 기능 보존

- Realtime 설정 및 PIN 화면은 레이아웃만 변경했다. 기존 content 콜백·managedWorkspace·foreground·실제 PIN 확인 흐름을 그대로 유지했다.
- 보호된 Work composable은 여전히 RealtimeLockedScreen 안에서만 실행된다. UI 디자인 때문에 unlock을 호출하거나 인증 우회를 만들지 않았다.
- 문서의 FLAG_SECURE와 foreground 수명주기, 기존 차량 권한 요청, 공유 Safety/Waiting/Rest runtime, 알람 진입을 유지했다.
- UNKNOWN/DRIVING에서 원래 화면·도메인 차단을 보존했다. UI 테스트에서 가짜 차량 상태는 androidTest에만 존재한다.
- Backend, DB/RLS, API DTO/Repository/ViewModel/문서 엔진/Rest/ModeStartManager·permission·Gradle 의존성 변경 없음.
- 새 지도/장소 검색 공급자, 날씨, 실시간 빈자리/ETA, PDF 편집·자동 업로드를 UI 시안만으로 추가하지 않았다.

## 이미지 에셋

[체크섬·해상도 manifest](../ui/PREMIUM_ASSET_MANIFEST.json). 내장 image_gen으로 새 생성한 무문자 장식 이미지다.

- `hero_sunset_rear.png`: 2172×724 (3:1), 현대차 후면·산·석양.
- `rest_pillow.png`, `study_book.png`, `work_laptop.png`, `entertainment_visual.png`: 1448×1086.
- `parking_visual.png`, `pickup_visual.png`, `files_visual.png`: 1774×887.
- 위치: `android/app/src/main/res/drawable-nodpi/`. 원본 파일을 그대로 복사했으며 이 단계에서 외부 이미지 편집기/변환 서비스는 사용하지 않았다.
- 현대 로고/차량 사진은 장식이며 차량 모델·실제 기어·주행 상태·공식 현대 제휴를 나타내는 데이터가 아니다.
- 그림에 합성된 UI가 없고 실제 QR은 발급된 URL에서 별도 생성한다.

## 검증 결과

| 검사 | 결과 |
| --- | --- |
| assembleDebug / assembleDebugAndroidTest | PASS |
| Android JVM | PASS 127개, failure/error0 |
| Backend npm test | PASS 28개 |
| 새 프리미엄 UI instrumentation | PASS 10개, 180.488초: shell6 + Parking2 + Waiting/Pickup actions2 |
| 기존 기능 기기 회귀 | PASS13 + 플랫폼 BLOCKED1, 134.652초: PIN1, DOCX Compose1, 외부 DOCX1, 안전 차폐1, Study3, Keystore1, Realtime bridge4, Waiting DB1 |
| 실제 MainActivity feature entry | PASS1, 53.421초. 실제 앱에서 Parking/Pickup/파일/Rest/Study/Work 경로 확인. 이후 마지막 사진 페이드 수정은 최종 빌드·JVM까지만 재확인 |
| 실제 홈 스크린샷·WindowMetrics | PASS: 1682×1210, density1.0, fontScale1.0, safeInsets0/0. 아래 증거는 마지막 사진 경계 페이드 보완 전 화면 |
| 최종 사진 페이드 기기 재확인 | NOT RUN: 마지막 재실행 중 장치 연결이 끊겼고 사용자가 이후 에뮬레이터 실행을 중지하고 직접 확인하겠다고 지시. 재시작한 검증용 AVD를 종료했고 더 실행하지 않음 |
| 이미지 체크섬/범위 보존·비밀값 검사 | PASS: 이미지8장 SHA256 일치, 스테이징32파일 비밀값/패턴 검사, 보호 기능·DB·권한·의존성 무수정 확인, diff --check 통과 |
| 실제 Cloud Auth/RLS/Realtime 및 Windows E2E | NOT RUN, 이번에는 live DB/외부 전송을 수행하지 않음 |
| 실제 FusedLocation 수신 / NaviHelper 새 route callback | NOT RUN, 기존 adapter 재사용 및 fixture 좌표 전달만 이번에 검증 |
| 실제 Rest 5분 알람 / 실차 | NOT RUN, 이번 UI 작업에서 타이머를 새로 생성하지 않음 |

SAF 선택기는 기존 Pleos DocumentsUI stub으로 여전히 BLOCKED다. 승인된 명시적 Downloads 내보내기와 기존 암호화 저장 대안은 유지했고 DOCX 기기 회귀에서 확인했다. 이미지 선택기 제한 역시 UI 변경으로 해결됐다고 주장하지 않는다.

홈 증거: [마지막 페이드 보완 전 실제 앱 화면](screenshots/premium-2026-11-07/VEH-01-home-before-final-fade.png). 활성 대기/장소가 없을 때만 캡처했으며 문서·초대·실제 위치는 포함하지 않았다. 캡처를 보고 상단 사진의 시작 경계를 네이비 페이드로 추가 보완했다. **최종 시각 확인은 사용자가 직접 수행한다.**

독립 `am start -W` 호출은 한 차례 wait timeout을 반환했다. 실제 MainActivity 자동 진입·화면 캡처는 통과했지만 이 결과로 cold-start 성능을 통과했다고 주장하지 않는다.

## 주요 변경 파일

- `MainActivity.kt`: typed route 및 현재 기능 연결, 저장 보호, 알람 요청·문서 보안 수명주기.
- `ui/premium/{StayAppShell,StayRoute,StayHomeScreen,StayDesign,StayVisuals,StayFeatureComponents}.kt`, `ui/theme/Theme.kt`.
- `parking/ui/ParkingScreen.kt`, `waiting/ui/WaitingScreen.kt`, `pickup/ui/PickupScreen.kt`.
- `realtime/{FileTransferScreen,RealtimeEntry,RealtimeLockedScreen}.kt`.
- `res/values/stay_strings.xml`, 승인된 `res/drawable-nodpi/` 이미지8개.
- `test/.../ui/StayNavigationTest.kt`.
- `androidTest/.../ui/{StayAppShellTest,ParkingPremiumTest,PremiumFeatureActionsTest,PremiumProductionEntryTest}.kt`.
- 이 보고서, UI 명세 갱신 및 이미지 manifest.

## 남은 차이·후속 확인

- Entertainment·일반 설정은 준비 중. Work는 DOCX 범위이며 PDF/원격 AirDesk는 제공하지 않는다.
- Rest/Study/Work 본문의 기능 화면은 현재 구현을 보존했다. 별도 상세 Premium 시안이 없는 화면의 전면 리디자인은 이번 완료 범위가 아니다.
- 석양 사진/모드 카드 소재는 새 디자인 선택으로 기존 Notion 원본 사진과 다르다. 색·구성은 사용자 요청 방향이며 실제 화면에서 크롭/사진 밝기·글꼴 크기를 추가 조정할 수 있다.
- 실제 서버 연결은 기존 backend/Realtime 설정과 키가 유효하고 서버가 실행 중이어야 한다. 홈의 사진·정적 장식은 서버 연결 상태를 가장하지 않는다.
- 실제 위치·내비 callback·Cloud paired transfer와 장시간 알람은 따로 실환경 인수가 필요하다.
- Push는 사용자 확인 후 별도로 수행한다.
