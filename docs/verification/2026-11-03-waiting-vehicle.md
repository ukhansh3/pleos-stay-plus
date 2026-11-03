# Waiting 차량 상태 연결 구현·검증 — 2026-11-03

## 결과

기존 UNKNOWN 고정 구현을 **공식 AAOS 기어·속도 속성 연결**로 교체했다. 공식 API 존재·용도와 컴파일/단위 테스트를 확인했으며, 실제 Pleos 기기 신호 수신은 아직 미검증이다. 지원·권한·신호가 확보되지 않으면 여전히 잠긴다. Fake 값을 제품에 넣어 잠금을 해제하지 않았다.

작업 시작: `dev`, `9f17b7dda8ad41d7ccc48f8ef71a91dd5426fac3`, 변경 없음. commit/push/merge 없음.

## 공식 근거와 선택

확인일: 2026-11-03. 기존 Vehicle 의존성 2.3.2, compileSdk 37, 대상 Pleos 에뮬레이터 Android 14/API 34.

- [Pleos SDK API 정책](https://document.pleos.ai/en/docs/pleos-only/vehicle-app-planning-guide/pleos-connect-sdk-api): 기어·속도 같은 표준 정보에는 AAOS Car API를 권장한다. 따라서 deprecated Pleos Gear 호출 대신 표준 API를 선택했다.
- [Pleos 호환성 안내](https://document.pleos.ai/en/docs/pleos-only/vehicle-app-planning-guide/api-compatibility-availability): 차종 이름을 추측하지 않고 실제 속성 지원과 접근 가능 여부를 검사한다.
- [Google VehiclePropertyIds](https://developer.android.com/reference/android/car/VehiclePropertyIds): `GEAR_SELECTION`은 선택 기어, `PERF_VEHICLE_SPEED`는 실제 속도(m/s, 후진 음수)다. 속도 0만으로 주차라고 판정하지 않는다. 각각 일반 권한 `android.car.permission.CAR_POWERTRAIN`, 실행 시 사용자 동의가 필요한 `android.car.permission.CAR_SPEED`를 사용한다.
- [CarPropertyManager](https://developer.android.com/reference/android/car/hardware/property/CarPropertyManager): `getPropertyList`, `registerCallback`, `getProperty`, `unregisterCallback`을 사용한다. 구독 결과와 `STATUS_AVAILABLE`을 검사한다. 블로킹 가능한 조회/구독/해제는 IO 작업에서 실행한다.
- [Android 14 공식 소스](https://android.googlesource.com/platform/packages/services/Car/+/refs/heads/android14-release/car-lib/src/android/car/hardware/property/CarPropertyManager.java): 위 공개 메서드의 존재를 확인했다. 최신 SDK의 register/unregister deprecation은 API 34 호환 때문에 제한적으로 허용했다. 숨겨진 API나 CarDrivingStateManager를 사용하지 않는다.
- [CarPropertyValue](https://developer.android.com/reference/android/car/hardware/CarPropertyValue): 속성 timestamp는 elapsedRealtimeNanos와 같은 기준이다. 이전 샘플·미래 timestamp·오류 상태를 안전 판정에 사용하지 않는다.
- [Pleos 주행 제한 가이드](https://document.pleos.ai/en/docs/pleos-only/vehicle-app-developer-guide/driving-restriction-development-guide): 기존 CarUxRestrictions 연결을 유지한다. 플랫폼 제한을 해제하거나 DO 메타데이터로 우회하지 않는다.

## 변경 파일

경로는 저장소 루트 기준이다.

| 파일 | 역할 |
|---|---|
| android/app/src/main/java/com/pleosstay/app/waiting/safety/PlatformWaitingSafetyGateway.kt | 공식 속성 연결, 권한/지원 확인, 구독·조회·해제, UX 제한 결합, 오류·연결 세대 검사 |
| android/app/src/main/java/com/pleosstay/app/waiting/safety/VehicleSafetySignals.kt (신규) | Android 없이 테스트하는 기어·속도·신선도 판정 |
| android/app/src/main/java/com/pleosstay/app/waiting/safety/WaitingDrivingMonitor.kt (신규) | 화면 수명과 분리된 주행 종료 이벤트 소비·저장 재시도 |
| android/app/src/main/java/com/pleosstay/app/waiting/safety/WaitingSafety.kt | 화면 활성 여부 전달 인터페이스와 초기 안내 |
| android/app/src/main/java/com/pleosstay/app/MainActivity.kt | 차량 속도 권한 요청, 앱 프로세스 단위 저장 담당 객체, 화면 복귀/숨김 처리 |
| android/app/src/main/AndroidManifest.xml | 기어·속도 읽기 권한 선언 |
| android/app/src/main/java/com/pleosstay/app/waiting/ui/WaitingViewModel.kt | 분리된 주행 저장 상태 구독; 화면 조작 차단에 반영 |
| android/app/src/main/java/com/pleosstay/app/waiting/ui/WaitingScreen.kt | 주행 저장 실패 및 차량 권한 재시도 안내 |
| android/app/src/test/java/com/pleosstay/app/waiting/VehicleSafetySignalsTest.kt (신규) | 차량 신호 판정 6개 테스트 |
| android/app/src/test/java/com/pleosstay/app/waiting/WaitingDrivingMonitorTest.kt (신규) | Activity/ViewModel 없이 종료·실패·재시도·중복 처리 |
| android/app/src/test/java/com/pleosstay/app/waiting/WaitingViewModelTest.kt | 분리된 오류 상태 검증에 맞춘 기존 테스트 수정 |
| docs/verification/2026-11-03-waiting-local.md | 과거 보고서임을 표시하고 이번 기록 연결 |
| docs/verification/2026-11-03-waiting-vehicle.md | 이번 구현·검증 및 남은 기기 검증 절차 |

기존 세션 모델·시간 정책·Repository·Room DB·Parking 구현·서버·OpenAPI·Gradle 의존성은 변경하지 않았다.

## 실제 실행 흐름

1. 앱 진입 시 Gateway는 UNKNOWN/차단으로 시작한다. 대기 관리 버튼에서 차량 속도 정보 권한을 요청한다. 거부하면 잠금을 유지한다.
2. Car 서비스 연결 후 기어와 속도가 실제 지원 목록에 있는지 확인하고 이벤트를 구독한다. 현재값도 IO 작업에서 재조회한다.
3. P 기어, 유효한 속도 0, 플랫폼 제한 없음, 화면 활성, 저장 복구 완료가 모두 충족돼야 Waiting 조작을 허용한다. Parking 장소/GPS 권한은 필요하지 않다.
4. D/R/N 기어만 확인되면 조작을 차단하지만 자동 종료하지 않는다. UX 제한만 발생하거나 화면이 백그라운드로 가도 자동 종료하지 않는다.
5. 유효하고 최근인 실제 속도가 양수 또는 음수로 확인되면 상태를 먼저 잠근 뒤 DrivingEventBuffer에 확인 시각을 전달한다. 수신 확인 시각이며 실제 출발 시각을 역산하지 않는다.
6. 앱 프로세스 소유 WaitingDrivingMonitor가 기존 Repository의 onDrivingConfirmed를 호출한다. 기존 복구 journal → 해당 세션 Room 종료 → journal 정리 순서를 사용한다. 성공할 때만 이벤트를 제거하고 실패하면 오류를 표시하며 1초 간격으로 재시도한다.
7. 화면이 숨겨져도 프로세스가 살아 있으면 구독과 종료 저장 담당 객체는 유지된다. 다시 화면에 진입하면 보관하던 주차 확인값을 무효화하고 새 정보로 재확인한다.
8. 프로세스 재시작 시 기존 Room 세션·종료 기록 및 journal 복구 흐름을 그대로 사용한다. 매초 타이머를 DB에 기록하지 않는다.

## 안전 판정의 세부 범위

- 속도 구독 요청 5Hz, 속성 상태 재조회 약 1초 간격. 3초 이상 신호가 오래되거나 기어 재확인이 끊기면 차단한다. 이는 앱의 보수적인 신선도 기준이며 차량의 안전 보증이 아니다. 실제 차종의 전달 주기 검증이 필요하다.
- 기어는 ONCHANGE 속성이므로 P를 오래 유지했다고 기어 자체 timestamp만으로 만료시키지 않는다. 최근 성공한 조회/이벤트 여부도 확인한다.
- 구독 실패, 권한 없음, 서비스 끊김, 비정상 속도(NaN/Infinity), 유효하지 않은 timestamp는 PARKED로 처리하지 않는다.
- 이전 연결의 콜백은 연결 세대 번호로 거부한다. 최신값보다 이전 timestamp인 콜백은 최신 상태를 덮어쓰지 않는다.
- 이미 확인된 이동 중 반복 속도 이벤트는 하나의 이동 관측으로 처리한다. 저장 자체도 기존 멱등 종료를 사용한다.
- 차량 데이터는 읽기만 한다. 속도·기어 원시값이나 GPS를 DB/로그에 지속 저장하지 않는다.

## 유지되는 최초 요구사항과 Notion 차이

Waiting은 Android 로컬 기능이다. HTTP Waiting API, Supabase 테이블/Realtime은 만들지 않았다. 기존 HTTP 계약을 삭제·수정하지 않았다. 예정시간 만료는 ACTIVE 유지, 총 예정시간 12시간 상한, 수동/주행 종료 구분, 로컬 기록·재실행 복구, 선택적인 Parking 장소, Pickup ETA로 자동 종료하지 않는 정책도 유지된다.

Vehicle SDK와 AAOS를 혼동하지 않는다. 이번 기어·속도 연결은 **Pleos 정책이 권장하는 Android Automotive 표준 API**를 이용한다. Pickup/Realtime 연결은 기존 로컬 세션 UUID·revision 경계를 유지하며 후속 서버 ID 매핑·동기화 정책은 미구현이다.

## 기기 검증 절차와 한계

- 이 PC에서 Pleos_Connect_v2.1.0 실행을 두 번 시도했다. 첫 실행은 adb offline, 스냅샷 없는 재시도도 정상 연결에 도달하지 못했다. 최종 adb 목록에는 사용 가능한 기기가 없다. emulator 37.2.12.0, API 34 Pleos 이미지, WHPX 인식 로그까지 확인했으며 부팅 실패 원인은 확정하지 않았다. AVD 데이터 초기화·루팅·권한 우회는 하지 않았다.
- 실제 기어·속도 지원, 런타임 권한 화면, P에서 UI 해제, 이동 시 자동 종료, 플랫폼 화면 차단, Room 기기 테스트는 이 실행 환경에서 미검증이다.
- 프로세스가 종료/중단돼 있던 동안의 주행 이력은 알 수 없다. 재실행 때 이미 P로 돌아왔다면 과거 출발 시각이나 종료 기록을 만들어내지 않는다. 기존 ACTIVE를 복구하고 주차가 다시 확인된 뒤 수동 정리할 수 있다. 프로세스 생존을 보장하는 서비스는 이번에 추가하지 않았다.
- 관측 이벤트가 journal에 기록되기 전에 프로세스가 사라지거나 독립 저장소가 모두 실패하면 완전한 복구를 보장하지 못한다. 이 한계는 실제 주행 시각을 임의로 생성하는 방식으로 숨기지 않는다.
- OS 알림·백그라운드 알람·TTS는 이번 범위에 없다.

팀원이 정상 실행되는 Pleos 환경에서 확인할 순서:

1. APK 설치 후 대기 관리에서 차량 정보 권한을 허용한다.
2. [Virtual Console 공식 가이드](https://document.pleos.ai/en/docs/pleos-only/app-test-guide/virtual-console-guide)의 Motion → Gear Selection에서 P를 선택한다. 실제 속도 속성이 유효한 0으로 들어오고 플랫폼 제한이 없을 때 시작·연장·수동 종료·재실행 복구를 확인한다.
3. D/R/N만 변경하고 속도 0일 때 잠금만 걸리며 세션은 ACTIVE인지 확인한다.
4. 실제 PERF_VEHICLE_SPEED를 바꿀 수 있는 검증된 테스트 환경에서 양/음의 속도를 전달하고 자동 종료 시각·DRIVING·중복 기록 없음·저장 재시도를 확인한다. Virtual Console 문서의 Cruise Target Speed는 실제 속도가 아니므로 대체 신호로 쓰지 않는다. 현재 가이드에서 실제 속도 조작 카드는 확인되지 않았다.
5. [Pleos 공식 ADB 가이드](https://document.pleos.ai/en/docs/pleos-only/vehicle-app-developer-guide/adb-command-reference-guide)의 `adb shell cmd hmg_car_service --service drivingstate driving_regulation on`으로 플랫폼 제한을 시험한다. 이는 실제 이동 신호가 아니므로 이 명령만으로 자동 종료되면 안 된다. 시험 후 `off`로 되돌린다.
6. 권한 거부/철회, 앱 숨김·복귀, 신호 끊김·갱신 지연에서 잠금과 기록 보존을 확인한다.

## 공부할 순서: Java 개념과 연결

1. VehicleSafetySignals: Java 클래스의 필드와 메서드처럼 이전 정보를 보관하고 입력에 따라 결과를 계산한다. Kotlin의 `Boolean?`/`Float?`는 정보가 없을 수 있다는 뜻이며 null을 안전으로 해석하지 않는다.
2. PlatformWaitingSafetyGateway: Gateway는 외부 시스템 접점을 묶은 객체다. Java 인터페이스 구현과 listener 콜백처럼 공식 차량 이벤트를 앱 상태로 번역한다. 코루틴은 대기 작업을 UI를 막지 않게 수행하는 방식이며 IO 작업과 화면 상태 변경을 분리한다.
3. WaitingDrivingMonitor: 화면과 별도 수명을 가진 이벤트 처리 담당 클래스다. Mutex는 Java synchronized처럼 동시에 두 종료 처리가 섞이지 않도록 하는 잠금이다. StateFlow는 최신 상태를 구독자에게 전달하는 통로다.
4. MainActivity → WaitingViewModel → WaitingScreen: 권한 승인 결과, 화면 수명, 표시 상태가 어떻게 이어지는지 읽는다. Room/Repository의 기존 세션 저장 코드는 그대로 이어서 읽으면 된다.

## 실제 실행한 검증 결과

최종 의미 변경 후 다음 명령을 실행했다. JDK는 Android Studio jbr, ANDROID_HOME은 D:/Android/SDK, GRADLE_USER_HOME은 D:/tmp/pleos-waiting-gradle-20261103을 사용했다.

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug assembleDebugAndroidTest lintDebug --console=plain --no-configuration-cache
```

- BUILD SUCCESSFUL. Android 단위 테스트 26개, 실패/오류 0개: Waiting 21개 + 기존 Parking 4개 + 기본 테스트 1개.
- 신규 7개는 P+속도 조건, 후진, UX/백그라운드 잠금, 오래된/미래/잘못된 신호, 신호 소실, 역순 이벤트, 화면 없는 종료 저장 재시도·중복을 검증한다. 테스트 차량 입력은 Fake이며 실제 차량 연결 성공 증거가 아니다.
- 앱 debug APK 및 androidTest APK 빌드 성공.
- lintDebug 최종 실행 성공: 0 errors, 29 warnings. 기존 라이브러리 버전 권고·스타일·리소스 등 경고가 남았다. Lifecycle 라이브러리의 일부 lint 검사 호환성 경고로 해당 검사가 생략됐으므로 완전한 정적 검증으로 표현하지 않는다.
- 중간에 프로세스 모니터 주입 관련 컴파일 오류와 이동된 오류 상태를 가리키던 테스트 단언 1개를 수정했다. 중간 lint 실행에서 FIR 내부 예외가 출력됐으나 최종 소스 고정 후 다시 실행한 lint에서는 해당 예외 없이 성공했다.
- `connectedDebugAndroidTest` 실제 실행: **실패 — No connected devices!** Room 기기 테스트는 실행되지 않았다. 차량 신호 수신/권한 UI/Compose 실제 조작/플랫폼 차단의 기기 검증도 남아 있다.
- git diff --check 통과. CRLF 변환 안내는 Git 설정에 따른 경고다.
- backend 소스·계약은 변경하지 않았고 이번 작업에서 backend 테스트는 재실행하지 않았다.

APK: android/app/build/outputs/apk/debug/app-debug.apk

## 에뮬레이터 재시도

Android Studio Device Manager에서 Pleos_Connect_v2.1.0을 Cold Boot로 직접 실행하고 차량 홈 화면 도달 여부를 확인한다. Wipe Data는 이번 절차에 포함하지 않는다.

```powershell
& "D:\Android\SDK\platform-tools\adb.exe" devices
```

기기 행이 `device` 상태면 Android Studio에서 앱 실행 및 기기 테스트를 다시 시도할 수 있다. `offline` 또는 빈 목록이면 아직 앱 테스트를 시작할 수 없다. 실패 원인을 앱 코드로 단정하거나 AVD 데이터를 초기화하지 않는다.

## 후속 부팅 재시도 결과

2026-11-03 추가 확인: 기존 AVD는 Windows AppHangB1 응답 없음으로 종료됐다. 원본 데이터 초기화 없이 D:/tmp/pleos-waiting-avd-20261103에 같은 Pleos 이미지의 별도 PleosWaitingVerify 기기를 구성했다. 해당 임시 기기는 ADB device 연결 및 sys.boot_completed=1에 도달했고 앱 APK와 androidTest APK 설치가 모두 Success였다. 따라서 앞선 No connected devices는 최초 시도의 결과이며 이후 부팅·설치는 성공했다. 경로와 새 데이터가 함께 바뀌었으므로 원인을 하나로 확정하지 않는다.

사용자가 실제 시뮬레이션을 팀원에게 맡기기로 하여 여기서 추가 검증을 중단했다. instrument 테스트, 앱 UI 실행, 실제 차량 속성 수신·P 잠금 해제·주행 종료는 실행/검증하지 않았다. 이번 작업의 임시 기기만 종료했고 원본 AVD와 앱 데이터는 삭제하지 않았다. Windows 화면 확인에는 computer-use 스킬을 사용했다. Git commit/push 없음.
