# Rest 전경 알람 구현·검증 기록 — 2026-11-07

## 범위와 Git 기준

- 저장소: D:\Android\Projects\pleos-stay-plus
- 브랜치/HEAD: dev / 8494280 (공통 모드 시작 관리자 커밋).
- 기존 Rest 미커밋 변경 위에 추가 구현했다. commit/push, 브랜치 변경, Cloud 쓰기, 배포 없음.
- 기존 루트 AGENTS.md, DEVELOPMENT_GUIDE.md, Android README와 Rest/Waiting/ModeStartManager/Safety 코드 및 테스트를 확인했다. 관련 하위 AGENTS.md는 검색되지 않았다.

## 해결한 문제와 동작

PLEOS API 34에서 SCHEDULE_EXACT_ALARM 권한이 없고 ACTION_REQUEST_SCHEDULE_EXACT_ALARM 처리 Activity도 없어 기존 Rest가 시작되지 않았다.

- SYSTEM_EXACT: 기존 setExactAndAllowWhileIdle + PendingIntent 예약·취소·알림음 경로 유지.
- FOREGROUND_ONLY: 사용자의 제한 안내 동의 후 앱 실행 중 Controller 타이머와 직접 MediaPlayer 알람 사용. 알림 채널이나 정확한 알람 권한에 의존하지 않는다.
- UNAVAILABLE: 음원/음량 등 준비 불가 사유를 표시하고 시작하지 않는다.
- 동의 전에 Waiting 생성·연장하지 않는다. 동의 후 기존 ModeStartManager를 그대로 호출한다.
- 종료 시각을 매번 현재 시각과 비교한다. Rest 연장 시 새 조건 검증 실패는 기존 종료 시각과 유효한 알람을 보존한다.
- 실제 알람 플레이어는 USAGE_ALARM, transient 오디오 포커스, SOFT/WAKE 실제 음원, 반복 재생과 사용자 정지를 사용한다. 포커스 상실·재생 오류는 정지하고 UI에 알린다.
- Stay+ 내부 탭 이동은 세션 유지. 외부 앱/홈 전환은 전경 알람 제한과 출력 중단. 복귀 시 미래 시각이면 다시 검증하고, 이미 지난 시각이면 종료 상태로 복구하되 뒤늦게 울리지 않는다.
- API 29 이상에서는 onTopResumedActivityChanged를 사용한다. PLEOS 다중 화면에서 onResume/onPause만으로는 활성 앱 전환을 구분할 수 없기 때문이다. API 28은 onResume/onPause를 사용한다.
- 기존 차량 안전 판정을 재사용한다. 주행/UNKNOWN에서 알람을 임의로 재생하지 않는다.
- EV 목표 이벤트도 선택한 알람 방식을 사용하며 목표·시간 알람 중복 억제를 유지한다.

## 이번 후속 작업의 파일과 함수

경로 기준: android/app/src/main/java/com/pleosstay/app/

| 파일 | 변경 이유 / 함수 |
|---|---|
| rest/RestAlarm.kt | RestAlerts.mode/foregroundProblem/playback 추가; RestAlarm.schedule/cancel/sound/silence에서 실행 방식 분리 |
| rest/RestAlarmPlayer.kt (신규) | play/stop/problem, 실제 알람 재생·오디오 포커스·오류·출력 상태 |
| rest/RestController.kt | start/acceptForegroundAlarm/dismissAlarmConsent/startPrepared, setForeground/restoreSession, checkAlarmAndSafety/canRun/ring, 기존 타이머·EV 연결 |
| rest/RestModels.kt | RestAlarmMode, RestPlayback, RestSession.alarmMode |
| rest/RestStore.kt | 실행 방식 저장; 기존 기록에 필드가 없으면 SYSTEM_EXACT로 해석 |
| rest/RestScreen.kt | 제한 안내와 동의/뒤로가기, 진행 중 제한 표시; 열 수 없는 특별 권한 버튼 숨김 |
| MainActivity.kt | Rest 전경 상태를 실제 Activity 생명주기에 연결; 공용 Safety Gateway 정책은 변경하지 않음 |

테스트:
- src/test/.../rest/RestControllerTest.kt: 전경 동의·취소·중복·종료·정지·연장 실패·복귀·저장 복구·EV·오디오 실패 추가.
- src/androidTest/.../rest/RestStorageTest.kt: 실행 방식 실제 파일 저장과 구버전 호환성.
- src/androidTest/.../rest/RestForegroundE2eTest.kt: 실제 5분 UI 시나리오와 두 음원의 활성 오디오 출력·정지.
- src/androidTest/.../rest/RestForegroundLifecycleTest.kt: 실제 Android 홈 입력·앱 복귀·Waiting 유지.

RestAudio, RestEvGateway, Waiting 저장소, ModeStartManager, Pickup, Realtime는 이번 알람 후속 작업에서 수정하지 않았다.
Manifest와 Safety/ModeStartManager에 이미 보이던 변경은 이전 Rest 개발 변경이며 보존했다.

## 빌드·자동 테스트

환경:
- JAVA_HOME=D:\Android\Android Studio\jbr
- ANDROID_HOME=D:\Android\SDK
- GRADLE_USER_HOME=D:\tmp\pleos-waiting-gradle-20261103

Android 실행 명령:
```powershell
.\gradlew.bat --offline --console=plain --no-configuration-cache '-Dorg.gradle.jvmargs=-Xmx2048m -Dfile.encoding=COMPAT' testDebugUnitTest assembleDebug assembleDebugAndroidTest
```

확인된 결과:
- Debug APK 및 AndroidTest APK 빌드 성공.
- Android 단위 테스트 78개, 실패 0 (Rest 23, ModeStartManager 15, Parking 4, Pickup 9, Realtime 5, Waiting/안전 21, 예제 1).
- Backend: node --import tsx --test 'test/*.test.ts' → 28개 통과, 실패 0.
- git diff --check 통과. Windows 줄바꿈 변환 경고는 존재.

## 에뮬레이터 증거와 재시험

- 기존 PleosWaitingVerify AVD를 -read-only -no-snapshot으로 임시 실행.
- PLEOS 실제 앱 사용자는 10번. 표준 CAR_SPEED 런타임 조회 권한만 해당 테스트 사용자에 허용.
- 정확한 알람 권한은 강제 부여하지 않았다.
- 두 음원의 MediaPlayer 재생, AudioManager 활성 USAGE_ALARM 출력, 정지 후 활성 출력 제거를 검증.
- 최초 5분 실시간 UI 시나리오 통과: 파워냅 선택 → 사용자 설정 5분 → 안내 취소 시 Waiting 불변 → 다시 동의 → Rest/Waiting 시작 → 내부 홈 이동·복귀 → 시간 종료 → 직접 알람 → 알람 정지 → Waiting 불변.
- 해당 실행의 예정 시각 1794013933259, 활성 출력 관측 시각 1794013934282: 약 1.023초 뒤. 이는 그 실행의 관측값이며 정밀도 보장이 아니다.
- 기본 파워냅 프리셋 20분과 기기 시각은 변경하지 않았다.
- 실제 스피커 청음 또는 호스트 루프백 파형은 확인하지 않았다. Android 재생/출력 활성 확인과 물리 음향 출력은 구분한다.

발생했던 문제:
1. 기본 Gradle 캐시에는 TextToSpeech 의존성이 없어 offline 실패 → 기존 검증 캐시로 재실행 성공.
2. 계측 코드의 비공개 isActive 참조 컴파일 오류 → 공개 getActivePlaybackConfigurations 목록으로 수정.
3. 차량 속도 조회 권한이 사용자 0에만 있어 최초 UI 안전 확인 timeout → 실제 사용자 10에 런타임 권한 적용.
4. UI 진단과 겹친 실행에서 계측 프로세스가 한 차례 종료됨 → 단독 5분 시나리오 재시험 성공.
5. 홈 이탈 테스트가 onPause/공용 안전 상태 변화만 가정해 실패 → Rest의 top-resumed 상태를 사용하는 코드로 보완. 최종 재시험 결과는 아래에 기록한다.

## 지원 범위와 남은 검증

- FOREGROUND_ONLY는 Stay+가 활성 화면인 동안만 지원한다. 프로세스 종료·기기 절전·차량 전원 종료 후 알람 보장 없음.
- 앱이 비활성인 동안 지난 종료 시각은 복귀 시 조용히 완료 처리한다.
- SYSTEM_EXACT의 실제 OS 예약 전달은 권한이 있는 별도 환경에서 추가 검증이 필요하다. 이번 PLEOS 테스트는 권한 없는 경로이다.
- EV 목표 알람 로직은 단위 테스트의 Fake 배터리 이벤트로 검증했다. 실제 차량 충전율 변화·목표 도달은 별도 검증 대상이다.
- 실차 오디오 라우팅·통화/안내와의 포커스 충돌·차량 Suspend 복귀는 실차 검증 필요.
- PLEOS Suspend에서는 일반 오디오를 중단하고 포커스를 해제해야 한다. 차량 전원이 꺼진 뒤 기상 알람을 보장하지 않는다.

직접 청음 확인:
1. Debug APK 실행 → 휴식 → 파워냅 → 이번 설정만 5분.
2. 앱 실행 중 알람 안내에 동의하고 Stay+를 활성 화면으로 유지.
3. 종료 시 PC/에뮬레이터 출력 장치에서 실제 소리를 확인.
4. 알람 정지 → 소리 중단 및 Waiting 유지 확인.

공식 근거:
- https://developer.android.com/develop/background-work/services/alarms
- https://developer.android.com/reference/android/media/AudioManager
- https://developer.android.com/develop/ui/views/layout/support-multi-window-mode
- https://document.pleos.ai/en/docs/pleos-only/vehicle-app-developer-guide/audio-focus-development-guide/audio-focus-development
- https://document.pleos.ai/en/docs/pleos-only/vehicle-app-developer-guide/audio-focus-development-guide/suspend-mode-audio-focus

## 최종 재시험 상태

완료:
- top-resumed 보완 후 RestForegroundLifecycleTest 단독 재시험 통과: OK (1 test), 106.063초.
- 실제 Android 홈 입력 후 Rest 제한 안내 및 무음 상태, 앱 재진입 후 같은 종료 시각·세션 유지, 수동 종료 후 Waiting 불변을 확인했다.
- RestLifecycle 로그에서 true → false → true 전환을 확인했다.
- 이미 통과한 5분 시나리오와 나머지 회귀 테스트는 사용자 지시에 따라 반복하지 않았다.
- 서로 다른 계측 테스트 총 8개에 대해 성공 결과를 확보했다: 기존 예제 1, Waiting Room 1, Rest 저장 3, 실제 오디오 출력 1, 5분 UI 시나리오 1, 홈 복귀 1. 하나의 최종 일괄 실행 결과가 아니라, 기존 통과 결과와 마지막 보완의 집중 재시험 결과를 합친 것이다.
- 마지막 코드의 Debug/AndroidTest 빌드 성공 및 단위 테스트 78개 성공 결과 확인. Backend 28개 성공.
- 코드 구현 완료 / 로컬 빌드·자동 테스트 완료 / PLEOS 앱 흐름 및 Android 오디오 활성·정지 검증 완료를 구분한다.
- 스피커의 물리 음향 출력, 실차, 권한이 있는 기기의 시스템 정확한 알람 전달은 미검증이다.

재현용 계측 명령 (테스트 AVD 전용):
```powershell
# 실제 사용자를 먼저 조회하고 CAR_SPEED를 표준 런타임 권한으로 허용한다.
adb shell am get-current-user
adb shell pm grant --user 10 com.pleosstay.app android.car.permission.CAR_SPEED

# 최초 5분 핵심 시나리오: 기본 프리셋과 시스템 시각을 변경하지 않는다.
adb shell am instrument --user 10 -w -r -e restE2E true -e class com.pleosstay.app.rest.RestForegroundE2eTest com.pleosstay.app.test/androidx.test.runner.AndroidJUnitRunner

# 최종 생명주기 보완만 집중 검증
adb shell am instrument --user 10 -w -r -e restE2E true -e class com.pleosstay.app.rest.RestForegroundLifecycleTest com.pleosstay.app.test/androidx.test.runner.AndroidJUnitRunner
```

이 명령은 실제 테스트용 앱의 Rest/Waiting 기록을 생성·연장할 수 있다. 개인 실사용 데이터가 있는 기기에는 그대로 실행하지 않는다.

