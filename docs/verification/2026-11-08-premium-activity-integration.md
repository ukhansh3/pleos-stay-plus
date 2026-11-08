# 공통 활동 관리자 × Premium UI 통합 검증 기록 — 2026-11-08

이번 요청은 로컬 구현 및 자동 검증만 수행했다. commit/push, Cloud 변경, 서버 배포, 에뮬레이터·실기기 실행은 하지 않았다.

## 기준과 Git

- 작업 시작 HEAD: b287bd3a745fa6bc1549a528aaa045b5ba152681 (dev, 깨끗한 작업 트리).
- 시작 및 완료 전 fetch 결과 origin/dev: b287bd3a745fa6bc1549a528aaa045b5ba152681. 자동 병합·강제 덮어쓰기 없음.
- 로컬 HEAD: b287bd3a745fa6bc1549a528aaa045b5ba152681. 변경 파일은 미커밋 상태다.
- Notion 기준: 사용자가 제공한 D:/download/프로젝트 명세성 11.08 12.29.zip의 08 API·09 아키텍처·10 Premium UI 자료. 10번 문서의 “Entertainment를 Rest에 통합” 문구만 삭제한 버전을 최신으로 취급한다는 사용자 추가 지시를 적용했다. 새 원본 파일이나 온라인 Notion이 확인됐다고 주장하지 않는다. 원본 수정 없음.
- 루트·Study·Work AGENTS, DEVELOPMENT_GUIDE, 관련 README·UI 명세 및 기존 구현을 확인했다. 이번 사용자 명세가 MainActivity·Navigation·UI 통합 변경을 허용한다.

## 구현

- Activity 소유 PremiumActivityHost가 기존 Coordinator와 네 Adapter를 유지한다. 기존 전환 알고리즘, Waiting 생성·연장 로직과 게임·문서 엔진을 재구현하지 않았다.
- 홈·모드 메뉴·Waiting 진입, 각 화면의 시작·명시적 종료, 내부 홈과 Android Back이 하나의 UI 연결부를 사용한다. 명시적 종료 후 해당 모드의 설정 화면은 유지한다.
- 다른 활동은 기존 Coordinator의 확인 토큰으로만 전환한다. 승인 후 저장·종료 완료를 기다리고 시작 성공 후에만 화면을 이동한다. 취소·중복·만료·종료 실패를 처리한다. 만료 또는 안전 사전 검사 실패 후 재시도는 새 확인을 받는다.
- 내부 홈은 타이머를 종료하지 않는다. 활동 전환 후 기록에 종료된 활동 경로가 쌓이지 않게 했다. 기존 Premium 뒤로 가기 바는 수정하지 않았다.
- Study·Work는 같은 Realtime 연결과 기존 PIN 검증을 재사용한다. 준비 창은 기존 자료함 등록·페어링·PIN 화면을 사용한다. 준비·전환 중에도 실제 전경 상태와 권한을 검사하고 문서 화면에 FLAG_SECURE를 적용한다. 활성 문서 Owner는 다른 연결의 Owner로 바꾸지 않는다.
- Study 홈 이동은 이미 수락한 메모 초안을 기존 암호화 저장소에 보관하고 타이머를 유지한다. 메모 저장 실패 표시·저장 재시도·작업 복귀를 연결했다.
- Work는 기존 DOCX flush/retryRetention을 사용한다. 저장 실패 시 기존 점유를 유지하며 새 활동을 차단한다. 사용자 요청 없는 외부 DOCX 덮어쓰기·초안 삭제는 하지 않는다.
- Entertainment 홈 카드를 실제 독립 경로에 연결했다. 실제 CoordinatedEntertainmentAdmission, ActivityHost와 기존 Runtime 및 2048·퀴즈 화면을 재사용한다. 내부 홈 후 진행 상태를 보관하고 콘텐츠는 사용자가 직접 재개한다. 마이크·영상·추천 UI는 추가하지 않았다.
- 전경 상실은 문서 잠금 및 Entertainment 일시 정지를 수행한다. 내부 홈 이동과 시스템 홈을 구분하며 기존 Rest 알람 정책을 유지한다.

## 검증 결과

| 항목 | 결과 |
|---|---|
| Android JVM 전체 | 254 PASS / 실패 0 / 오류 0 / 건너뜀 0 |
| 신규 JVM | 기존 235개 대비 19개 추가: UI 연결 16개, Study 보관·재시도 2개, Rest 독립 동의 1개 |
| Backend | 28 PASS, 실패·건너뜀 0 (로컬 실행) |
| Debug APK | assembleDebug PASS |
| AndroidTest APK | assembleDebugAndroidTest PASS (기기 실행 결과가 아님) |
| 신규 Compose 기기 테스트 | 3개 작성·컴파일, 실행 NOT RUN |
| Preview | 전환·Study 오류·Work 복구·Entertainment 예시 4개 컴파일, IDE 렌더링 NOT RUN |
| git diff --check | PASS |
| Lint | 오류 3, 경고 46; 기존 대비 추가 0, 제거 0 |

실행 명령은 offline Gradle testDebugUnitTest assembleDebug assembleDebugAndroidTest lintDebug이며, Lint 기존 오류 때문에 전체 Gradle 명령은 실패로 종료된다. APK와 단위 테스트 개별 결과를 Lint 성공으로 혼동하지 않는다.

UI 연결 테스트는 실제 Coordinator에 테스트 Adapter를 연결해 네 활동 홈·동일 세션 복귀, 문서 준비 전 전환 금지, 확인·취소·저장 종료 대기, 빠른 입력, Study 종료 실패와 명시 재시도, Work 홈 저장 실패와 타이머 유지, 작업 복귀, 만료·안전 검사 후 새 승인, 전경 차단, 명시 종료, Waiting 선택, 복구 전용 재시도, Owner 교체 방지, 활동 Navigation 기록을 검증한다. Study·Rest·Work·Entertainment 기존 실제 서비스/엔진 회귀 테스트도 전체 실행에 포함됐다. 이것은 실제 앱의 15개 시나리오를 기기로 실행한 결과가 아니다.

## 기존 Lint 오류

1. AndroidKaraokeMicrophone.kt:142 — MissingPermission: Manifest RECORD_AUDIO 선언 없음. 이번 권한 UI·Manifest 범위 제외이며 억제하지 않았다.
2. PreviewSamples.kt:77 — ResourceType: openRawResource에 drawable 자원 사용.
3. DocxFileGateway.kt:39 — NewApi: minSdk 28에서 API 29 MediaStore.Downloads 필드 사용.

이번에 발견한 ActivityPanels의 StateFlow.value 직접 읽기 2건은 collectAsState로 수정했다. 최종 신규 문제 여부는 위 비교 결과를 기준으로 한다.

경고 분류:
- Aligned16KB: 3개
- DefaultLocale: 1개
- GradleDependency: 4개
- IconXmlAndPng: 2개
- LocalContextResourcesRead: 1개
- NewerVersionAvailable: 3개
- ObsoleteLintCustomCheck: 1개
- RedundantLabel: 1개
- UnusedResources: 10개
- UseKtx: 12개
- UseTomlInstead: 7개
- VisibleForTests: 1개

## 디자인 보존과 미완료

홈 변경은 Entertainment 콜백 전달만 포함한다. 차량·하늘·산 이미지, 네이비 페이드, 로고, 사이드바, 카드 크기·배치·타이포그래피, 색상 토큰과 리소스는 변경하지 않았다. 새 확인·복구 UI는 기존 FeaturePanel·PremiumAction·StayColors를 사용한다. 앱 크기 변화에는 너비 제한·스크롤·최소 버튼 크기를 사용했다.

에뮬레이터 없는 CLI 빌드로 Preview 소스를 컴파일했으며, Android Studio Preview 렌더링·스크린샷·텍스트 잘림·실제 터치 위치는 시각 검증하지 않았다. 실제 PLEOS 앱 실행, 차량 안전 신호·멀티윈도우·PIN·문서/게임 복귀·파일 선택·마이크는 검증 미완료다. 실제 마이크 권한 요청 및 RECORD_AUDIO 선언은 후속 단계다.

코드·자동 검증 결과와 실제 PLEOS 검증을 구분한다. 기존 Lint 3건은 후속 수정 대상이며, Preview 시각 확인과 기기 테스트도 남아 있다. GitHub 반영은 별도 승인 후 최신 dev와 다시 대조해야 한다.

Notion은 위 사용자 기준 외의 새 변경을 가정하지 않는다. 향후 API 인덱스·구현/검증 문서에 네 독립 활동 및 실제 완료/미완료 범위를 반영하면 된다. 과거 회고 일괄 수정 없음.

## 변경 파일

- `android/app/src/androidTest/java/com/pleosstay/app/ui/ActivityPanelsDeviceTest.kt`
- `android/app/src/debug/java/com/pleosstay/app/preview/ActivityIntegrationPreview.kt`
- `android/app/src/main/java/com/pleosstay/app/MainActivity.kt`
- `android/app/src/main/java/com/pleosstay/app/entertainment/EntertainmentActivityHost.kt`
- `android/app/src/main/java/com/pleosstay/app/entertainment/EntertainmentScreen.kt`
- `android/app/src/main/java/com/pleosstay/app/realtime/RealtimeEntry.kt`
- `android/app/src/main/java/com/pleosstay/app/rest/RestActivityAdapter.kt`
- `android/app/src/main/java/com/pleosstay/app/rest/RestController.kt`
- `android/app/src/main/java/com/pleosstay/app/rest/RestScreen.kt`
- `android/app/src/main/java/com/pleosstay/app/study/StudyScreen.kt`
- `android/app/src/main/java/com/pleosstay/app/study/StudyViewModel.kt`
- `android/app/src/main/java/com/pleosstay/app/ui/activity/ActivityPanels.kt`
- `android/app/src/main/java/com/pleosstay/app/ui/activity/ActivityUiActions.kt`
- `android/app/src/main/java/com/pleosstay/app/ui/activity/ActivityUiController.kt`
- `android/app/src/main/java/com/pleosstay/app/ui/activity/PremiumActivityHost.kt`
- `android/app/src/main/java/com/pleosstay/app/ui/activity/PremiumDocuments.kt`
- `android/app/src/main/java/com/pleosstay/app/ui/premium/StayHomeScreen.kt`
- `android/app/src/main/java/com/pleosstay/app/ui/premium/StayRoute.kt`
- `android/app/src/main/java/com/pleosstay/app/work/WorkScreen.kt`
- `android/app/src/test/java/com/pleosstay/app/rest/RestControllerTest.kt`
- `android/app/src/test/java/com/pleosstay/app/study/StudyViewModelTest.kt`
- `android/app/src/test/java/com/pleosstay/app/ui/ActivityUiControllerTest.kt`

이 검증 기록도 새로 추가했다. 원본·최종 파일 사본, SHA256 목록, 작업 diff, XML 및 실행 로그: D:/stay-plus-backup/pre-premium-ui-integration-20261108T064426290Z.
