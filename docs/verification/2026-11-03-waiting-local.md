# Waiting Android 로컬 MVP 구현·검증 — 2026-11-03

> 이 문서는 최초 로컬 MVP 구현 당시의 기록이다. 이후 UNKNOWN 고정 구현을 공식 AAOS 차량 속성 연결로 교체했다. 최신 상태와 미검증 범위는 [차량 상태 연결 검증 기록](2026-11-03-waiting-vehicle.md)을 따른다.

## 최초 구현 당시 결과와 중요한 제한

로컬 세션·시간 계산·연장·종료·기록·복구·Compose 화면을 작성했다. 주차 확인 신호와 실제 주행 신호는 검증되지 않았다. **현재 배포 코드의 차량 상태는 UNKNOWN이며 Waiting 시작·연장·종료·기록 화면은 잠겨 있다. 실제 차량에서 사용 가능한 Waiting 완료로 해석하면 안 된다.** 테스트용 주차/주행 데이터는 단위 테스트에만 존재한다.

시작 기준 Git: dev / a7966cf9c6d54944fc69ad9a9077e79d5d6485b1 / 변경 없음. commit·push·merge·배포·Supabase·기존 HTTP 계약 변경 없음.

## 1. 생성·수정 파일과 역할

Android 소스 기준: android/app/src/main/java/com/pleosstay/app/

| 파일 | 역할 |
|---|---|
| waiting/domain/WaitingModels.kt | 세션·장소·종료 원인·시간 정책·계산·Clock |
| waiting/data/WaitingRepository.kt | 저장 인터페이스, 시작·연장·종료·복구, 요청 직렬화, 주행 종료 재시도 |
| waiting/data/WaitingEntity.java | Room 행, 활성 슬롯 고유 제약 |
| waiting/data/WaitingDao.java | 생성·조회·버전 검사 연장·멱등 종료 SQL |
| waiting/data/WaitingDatabase.java | Room DB v1 |
| waiting/data/RoomWaitingStore.kt | Room과 Kotlin 모델 변환, 독립적인 종료 복구 저장소 |
| waiting/ui/WaitingViewModel.kt | 화면 상태·동작·초 단위 표시·복구·안전 이벤트 |
| waiting/ui/WaitingScreen.kt | 시작·연장·종료·기록·예정시간 안내 |
| waiting/safety/WaitingSafety.kt | 플랫폼과 분리한 차량 안전 상태·Gateway |
| waiting/safety/PlatformWaitingSafetyGateway.kt | 공식 CarUxRestrictions 현재값·콜백·연결 해제 |
| MainActivity.kt (수정) | 기존 Parking과 Waiting 전환, 장소 ID·이름 전달, 앱 프로세스 단위 의존 객체 |

그 밖의 수정: android/app/build.gradle.kts(Room 2.7.2 runtime/compiler, optional android.car), AndroidManifest.xml(optional car library; DO 선언 추가 없음), backup_rules.xml/data_extraction_rules.xml(Waiting DB·복구 정보만 백업/이전 제외), android/.gitignore(빌드 생성 .kotlin/ 제외).

테스트 신규: src/test/.../waiting/WaitingPolicyTest.kt, WaitingRepositoryTest.kt, WaitingViewModelTest.kt; src/androidTest/.../waiting/WaitingDatabaseTest.kt. 본 문서도 신규 파일이다. Parking 소스·기존 테스트·백엔드 코드·OpenAPI·공통 DTO는 수정하지 않았다.

## 2. 실제 코드 실행 흐름

MainActivity → WaitingScreen → WaitingViewModel → LocalWaitingRepository → RoomWaitingStore → DAO → waiting.db.

- 시작: 주차 확인 + UX 사용 허용 검사 → 입력 5~720분 검사 → 기존 활성 세션 확인 → UUID 발급 → DB 생성 성공 후 상태 전달.
- 이중 시작 방지: UI busy 처리, Repository Mutex, DB의 unique activeSlot=1 제약. 기존 세션을 덮어쓰지 않는다.
- 연장: 기존 plannedEndAt에 추가 분을 더한다. 총 예정시간은 시작 시각부터 최대 720분. revision 비교 후 증가.
- 시간 초과: 현재 시각과 예정 종료 시각의 차이로 표시한다. ACTIVE를 유지하고 자동 만료시키지 않는다.
- 수동 종료: 같은 행을 ENDED로 갱신하고 endedAt·MANUAL 기록. 반복 요청은 기존 종료 기록 유지.
- 시간 정책과 선택지: WaitingPolicy에서 관리. UI에 정책 값을 표시한다.

## 3. 저장·복구

Room은 SQLite 접근을 객체와 DAO 인터페이스로 관리하는 도구다. Java Entity/DAO/DB에 annotationProcessor를 적용하여 추가 KSP 플러그인 없이 구성했다. Kotlin 2.2.10 / 기존 AGP 9.5.0 / Gradle 9.6.0에서 컴파일·APK 생성 확인.

startedAt·plannedEndAt·endedAt은 UTC epoch 밀리초다. 재실행 시 DB를 조회하고 현재 시각과 비교해 경과·남은·초과 시간을 재계산한다. 화면 타이머는 매초 DB에 쓰지 않는다. ENDED 행을 ACTIVE로 바꾸는 복구 로직은 없다. 파괴적인 DB 재생성 fallback도 없다.

주행 자동 종료는 독립 SharedPreferences에 관측 시각을 먼저 기록하고 DB에 적용한다. DB 읽기 실패도 재시도할 수 있도록 먼저 관측 명령을 저장한다. 대상 세션 ID가 결정되면 그 ID로 고정해 후속 세션을 종료하지 않는다. 성공 전에는 복구 오류를 표시하고 조작을 막는다. 최초 관측 시각을 재시도 시각으로 바꾸지 않는다.

DB와 복구 저장소가 모두 쓰기 실패한 뒤 프로세스까지 종료되면 최초 관측 시각의 영구 보존을 보장할 수 없다. 오류를 숨기거나 과거 출발 시각을 추정하지 않는다. 기기 시계 변경은 실제 시간과의 불일치를 만들 수 있으며, 시작보다 이른 종료시각은 만들어 저장하지 않는다.

정확한 GPS 좌표는 모델·DB에 없다. 장소 ID·이름만 선택적으로 저장한다. 앱 삭제/데이터 지우기는 기록을 제거한다. Waiting 데이터는 클라우드 백업과 기기 이전에서 제외했다.

## 4. 안전 처리: 확인된 문서와 구현

공식 문서 확인일: 2026-11-03.

- Pleos 주행 규제 개발: https://document.pleos.ai/en/docs/pleos-only/vehicle-app-developer-guide/driving-restriction-development-guide
- Pleos 지역별 정책: https://document.pleos.ai/en/docs/pleos-only/vehicle-app-planning-guide/driving-restiction-policy
- Android UX 이벤트 의미: https://developer.android.com/reference/android/car/drivingstate/CarUxRestrictions
- Android 리스너: https://developer.android.com/reference/android/car/drivingstate/CarUxRestrictionsManager
- Android Car 연결: https://developer.android.com/reference/android/car/Car
- Vehicle SDK 도메인: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/intro
- Room 공식 릴리스: https://developer.android.com/jetpack/androidx/releases/room

플랫폼의 현재 UX 제한과 리스너를 사용한다. 제한·연결 실패·해제·백그라운드 전환 시 안전 허용 상태를 유지하지 않는다. 화면은 DB 저장을 기다리지 않고 먼저 차단한다. DO metadata를 선언하거나 플랫폼 제한을 우회하지 않는다. 비차량/해당 API 미지원 환경도 기본 차단한다.

CarUxRestrictions는 OEM·지역별 UI 제한이며 P 기어 또는 실제 주행 전환을 확정하는 신호로 사용하지 않는다. 제한이 없어도 주차가 확인되지 않으면 잠금이다. Vehicle SDK DrivingMode를 PRND로 간주하지 않는다. 미확인 SDK 메서드·권한·차량 제어 코드는 추가하지 않았다.

## 5. 주행 자동 종료 상태

**핵심 종료·중복 방지·저장 재시도 로직은 구현했지만, 실제 차량에 연결된 자동 종료 트리거는 없다.** Fake 이벤트로만 검증했다. Gateway에서 신뢰할 수 있는 DRIVING과 observedAt이 제공될 때만 자동 종료를 요청한다. DRIVING에 관측 시각이 없으면 잠금은 적용하되 종료는 보류한다.

기기·실차에서 공식 신호의 의미·권한·지원·신선도·구독 수명을 검증한 후 Gateway를 구현해야 한다. 백그라운드 상태에서의 실제 주행 감지 보장도 별도 검증 대상이다. onPause/onStop, UX 제한, 예정시간 만료, 픽업 ETA를 출발 신호로 사용하지 않는다.

대안: 기존 ACTIVE 기록을 보존·복구한다. 검증된 주차 확인이 가능해진 뒤 사용자가 기록을 확인하고 직접 종료하도록 한다. 관측하지 못한 과거 출발 시각은 복원하거나 추정하지 않는다.

## 6. 실행한 검증

- Gradle testDebugUnitTest: 17개 통과 (Waiting 12개, 기존 Parking 4개, 예제 1개).
- Gradle assembleDebug: 성공, app/build/outputs/apk/debug/app-debug.apk 생성.
- Gradle assembleDebugAndroidTest: 성공, 기기 테스트 APK 생성. 테스트 실행과는 구분.
- 기존 Parking 백엔드 npm test: 12개 통과. 잠금 파일의 의존성만 설치했고 코드·계약 수정 없음.
- git diff --check: 공백 오류 없음.
- adb devices: 연결된 기기 없음.

테스트 범위: 시간 경계·음수/0·연장 상한·초과 ACTIVE·중복 시작/종료·재구성 복구·쓰기/읽기 실패·주행 관측 시각 보존·시각 없는 이벤트 보류·지연 중복 이벤트가 새 세션에 영향 없음·UNKNOWN/주행 제한 차단·백그라운드만으로 종료 안 됨. 차량 테스트는 Fake이며 실차 성공 증거가 아니다.

첫 Gradle 실행은 APK 생성에 성공했으나 단위 테스트 실행기 GradleWorkerMain을 로드하지 못했다. 영문 임시 GRADLE_USER_HOME에서 재실행해 해결했다. 프로젝트의 Gradle 버전·기존 전역 설정은 바꾸지 않았다. 재현 환경:

```powershell
$env:JAVA_HOME = 'D:\Android\Android Studio\jbr'
$env:ANDROID_HOME = 'D:\Android\SDK'
$env:GRADLE_USER_HOME = 'D:\tmp\pleos-waiting-gradle-20261103'
.\gradlew.bat testDebugUnitTest assembleDebug assembleDebugAndroidTest --console=plain --no-configuration-cache
```

## 7. 미실행·미구현

Room DB 파일을 실제 기기에서 닫고 재개방하는 instrumentation 테스트는 작성·컴파일했지만 실행하지 못했다. 실제 프로세스 종료/기기 재시작·백업 제외·화면 배치·플랫폼 차단 콜백·실차 권한은 미검증이다. 테스트 보고서의 메모리 저장소 복구를 실제 Room 재실행 성공으로 표현하지 않는다.

OS 알림·알람음·TTS·정확한 백그라운드 알림, Pickup 서버·Realtime·실제 차량 자동 종료 트리거는 구현하지 않았다. 화면 안에서 예정시간 5분 이내·도달·초과 안내만 구현했다.

## 8. 기존 Notion/계약 차이와 팀 합의 사항

Notion/기존 OpenAPI는 Waiting HTTP CRUD·Supabase 소유권·서버 시간/공유 종료를 설명한다. 이번 구현은 Android 로컬 CRUD·기기 시각·앱 내부 기록이며 HTTP 계약 구현으로 취급하지 않는다. 기존 ACTIVE/ENDING/ENDED/EXPIRED 후보 중 로컬에서는 ACTIVE/ENDED만 사용하고 예정시간 초과로 EXPIRED 전환하지 않는다. 외부 DTO/SQL enum은 수정하지 않았다.

향후 합의할 것: 공식 주차/실제 주행 신호와 권한·지원 기준, 백그라운드 구독 정책, 개인정보 보존/삭제 UI, 서버 등록 시 로컬 UUID와 원격 ID 매핑, 로컬 revision과 서버 revision 분리, 오프라인 종료와 초대 회수·재전송·충돌 정책.

로컬 ID는 세션 생성 후 바뀌지 않는다. 향후 인증된 공유 등록 Adapter가 별도 로컬-ID/서버-ID 매핑을 관리하도록 한다. 가짜 ownerId·서버 ID·동기화 코드는 추가하지 않았다. Pickup ETA 5분 안내는 준비 알림이며 Waiting 자동 종료 트리거가 아니다.

## 9. Java와 연결한 학습 순서

1. WaitingModels.kt: data class는 Java 값 보관 클래스와 유사. val은 재대입 불가, Long?는 null 허용. copy로 새 값을 만든다.
2. WaitingPolicyTest.kt: 입력→결과를 확인하며 상태 전이와 시간 계산을 읽는다.
3. WaitingRepository.kt: interface와 생성자 주입은 Java 다형성과 연결된다. Mutex는 동시 요청의 임계 구역을 지킨다.
4. WaitingEntity/Dao/Database.java → RoomWaitingStore.kt: 익숙한 Java로 SQL 저장 경계를 먼저 이해한다.
5. WaitingViewModel.kt: suspend는 대기 중 실행을 중단할 수 있는 함수이며 매번 새 스레드를 만드는 기능이 아니다. StateFlow는 현재 상태와 변경을 전달한다.
6. WaitingScreen.kt → MainActivity.kt: Compose가 상태를 화면으로 표시하고 사용자 동작을 ViewModel에 전달하는 흐름을 읽는다.
7. WaitingSafety.kt → PlatformWaitingSafetyGateway.kt → WaitingViewModelTest.kt: 인터페이스를 통한 외부 연동 격리와 Fake 테스트의 차이를 확인한다.

## 추가 검토 수정 및 재현 검증

두 회귀 테스트를 먼저 추가하여 기존 코드에서 각각 실패함을 확인한 뒤 수정했다.

- savedExtensionReadFailureLocksUntilRefreshWithoutApplyingTwice: 연장은 DB에 성공했으나 뒤의 조회만 실패하는 상황. 저장 실패와 조회 실패를 구분하고 Repository·UI 모두 재조회 완료까지 변경을 차단한다. 재시도는 연장을 반복하지 않고 조회만 수행한다. 복구 후 30분+15분=45분, 추가 연장 없음 확인.
- rapidDrivingThenUnknownStillEndsAtObservationTime: 소비자가 실행되기 전에 DRIVING 직후 UNKNOWN으로 바뀌는 상황. 최신 차량 상태와 DrivingEventBuffer의 미처리 관측 목록을 분리했다. 관측은 Repository 처리 성공 후 acknowledge하며, 오류 시 목록에 유지해 재처리한다. 최초 확인시각으로 한 번만 종료되고 UNKNOWN 잠금도 유지됨을 확인.

미래의 검증된 차량 Gateway는 공식 주행 확인 시 drivingEvents.record(observedAt)를 호출해야 한다. 상태의 DRIVING 값만 변경하는 것으로 자동 종료하지 않는다. 목록은 프로세스 내 보관이며, 디스크 복구는 기존 Repository journal 책임이다. 디스크 기록 전 프로세스 종료까지 보장하는 장치는 아니며 실제 백그라운드 구독 수명·차량 신호 연동은 여전히 별도 검증 대상이다. 프로덕션 Gateway에는 가짜 이벤트 생성 경로를 넣지 않았다.

최종 실행: Android 단위 테스트 19개(Waiting 14 + 기존 5) 통과, assembleDebug 및 assembleDebugAndroidTest 성공, Parking 백엔드 12개 통과. adb devices에 연결 기기가 없어 Room instrumentation·실차 자동 종료·실제 화면 재현은 미실행이다. 위 재현은 JVM 테스트의 Fake 저장소·차량 이벤트를 사용했다. commit/push/API/Supabase 변경 없음.
