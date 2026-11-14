# 2026-11-14 Study/Work 진입 및 공유 PIN 세션 수정

시작 HEAD: dev 252a23e. 사용자 확인: Windows 연결 및 파일 전송 성공 후, Study/Work 진입에서 PIN 재입력과 Files 복귀가 발생했다.

## 원인

MainActivity가 RealtimeEntry.foreground에 실제 Activity 전면 상태와 내부 화면 경로를 함께 전달했다. Files에서 Home/Modes로 이동하면 foreground=false가 되어 FileTransferViewModel.lock이 이미 확인한 PIN을 지웠다. 또한 문서 모드 준비 화면이 PIN 화면 본문을 숨기고 Files 열기 버튼만 제공했으며, 문서 목록은 모드 진입만으로 자동 조회하지 않았다.

## 수정

- PIN 세션은 실제 앱 전면 상태에 연결하고, 내부 화면 이동만으로 잠그지 않는다. PIN 해제를 영구 저장하거나 자동으로 새 권한을 부여하지 않는다.
- 문서 화면을 벗어나면 기존 문서 렌더러·임시 미리보기·편집 내용을 차폐하는 처리는 유지한다.
- Study/Work 진입 때 현재 연결과 두 Workspace 권한을 다시 확인한 뒤 자동 진행한다. 이미 검증된 PIN이면 재입력이나 Files 경유를 요구하지 않는다.
- 실제 PIN/연결 준비가 필요하면 요청한 모드의 준비 화면 안에서 기존 PIN/PC 연결 컴포넌트를 표시한다.
- Study/Work 화면 진입 후 기존 ViewModel로 자료 목록을 자동 조회한다. 파일·버전 선택과 해시 검증은 기존 Gateway를 재사용한다.
- 앱 실제 백그라운드·주행/UNKNOWN·명시적 잠금·사용자/연결 변경·접근 철회·프로세스 재시작의 기존 차단을 유지한다.
- ModeStartManager/활동 전환 정책, Waiting, Study 메모/진도 저장, Work 편집 엔진, Realtime 서버/인증 계약은 변경하지 않았다.

최신 사용자 UX 지시는 DEVELOPMENT_GUIDE 및 DOCUMENT_WORKSPACE_CONTRACT에 기록했다. Notion Study/Work/공통 모드 회고를 조회했으며, 과거 문서보다 최신 사용자 결정을 우선했다. Notion 문서는 수정하지 않았다.

## 검증

- PASS: Android JVM 232개 중 231 통과, 1 skipped, 실패/오류 0.
- PASS: assembleDebug 및 compileDebugAndroidTestKotlin. 계측 테스트 코드를 컴파일한 것이며 기기에서 계측 테스트를 실행한 결과는 아니다.
- PASS: 신규 회귀 테스트 4개. 같은 전면 세션의 PIN 유지, 실제 백그라운드/명시적 잠금/UNKNOWN 후 자동 잠금 해제 금지, 요청한 Study/Work로 준비 후 진행, 기존 검증 상태에서도 신선한 권한 확인 대기, 미검증 세션 진입 차단을 포함한다.
- PASS: git diff --check.
- PASS: 기존 에뮬레이터 사용자에 APK를 install -r로 갱신하고 MainActivity 재실행. 에뮬레이터·서버·Windows EXE 재시작 없음.
- PENDING: 사용자의 실제 Files→Home→Study/Work 동선, 추가 PIN 없음, 문서 목록/선택 확인. 앱 업데이트로 프로세스가 새로 시작했으므로 최초 1회 PIN 확인은 필요하다.
- NOT RUN: 이번 변경에서 새 파일 업로드/DB 변경, 백그라운드/주행/사용자 전환의 실차 계측. 실제 사용자 문서 내용을 자동 열람하거나 PIN을 대신 입력하지 않았다.

## 영향 파일

- MainActivity.kt: 실제 앱 foreground와 내부 route 분리, PIN/연결 준비를 요청한 모드 안에 표시.
- realtime/RealtimeEntry.kt, FileTransferViewModel.kt: 실제 foreground 상실에만 기존 잠금 적용; 자동 unlock 없음.
- ui/activity/PremiumActivityHost.kt, ActivityUiController.kt, PremiumDocuments.kt: 권한 재확인·자동 준비·자료 목록 조회.
- FileTransferSafetyTest.kt, ActivityUiControllerTest.kt: 위 회귀 검증.
- DEVELOPMENT_GUIDE.md, docs/contracts/DOCUMENT_WORKSPACE_CONTRACT.md 및 본 검증 기록.

홈 디자인·원본 사용자 문서·PIN 값·서버 키·DB/RLS 변경 없음. 이번 기록 작성 시 Git push하지 않았다.
## 설치 후 관측

설치 후 Realtime 서버에서 Android의 devices/me·connections/current·files 요청이 HTTP 200으로 처리되는 것을 확인했다. 최근 대상 앱 AndroidRuntime 크래시는 관측되지 않았다. 사용자의 PIN 입력 횟수와 두 모드의 실제 목록/선택 성공 여부는 이 HTTP 로그만으로 확정하지 않는다.
