# PLEOS Android 앱

> 제출용 서명 Release APK와 공개 서버 실행 안내: [저장소 README](../README.md). 최신 결과: [Release 검증](../docs/verification/2026-11-14-submission-release.md). 아래 Debug/로컬 설정은 개발용입니다.

Kotlin·Jetpack Compose 기반 차량 앱입니다. 홈과 Premium Navigation Rail에서 Parking, Waiting, Pickup, Realtime, Rest, Study, Work로 이동합니다.

## 실행·검토

- [저장소 전체 소개](../README.md)
- [담당자 검토 순서](../docs/review/REVIEW_GUIDE.md)
- [서버·인증·Android 설정](../docs/review/LOCAL_SETUP.md)
- [에뮬레이터 없는 Compose Preview](../docs/ui/COMPOSE_PREVIEW_GUIDE.md)
- [현재 빌드·검증 결과](../docs/verification/2026-11-14-review-handoff.md)

## 빌드

Android SDK와 JDK를 준비한 후 이 폴더에서 실행합니다.

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug --max-workers=1
```

출력은 `app/build/outputs/apk/debug/app-debug.apk`입니다. Debug APK는 로컬 검토·시연용이며, 이 명령은 에뮬레이터나 서버를 켜지 않습니다.

## 연결 설정

| 항목 | Debug 기본 대상 / 설정 |
|---|---|
| Parking / Pickup API | `http://10.0.2.2:3000/api/v1/` |
| Realtime API | `http://10.0.2.2:3012/api/v1/realtime/` |
| Supabase Auth | 무시된 `local.properties` 등의 `SUPABASE_URL`, `SUPABASE_PUBLISHABLE_KEY` |
| 별도 서버 | Gradle의 `PARKING_API_BASE_URL`, `PICKUP_API_BASE_URL`, `REALTIME_API_BASE_URL` 설정 |

서버 secret은 Android에 넣지 않습니다. 공개 URL·publishable key는 권한을 대신하지 않으며 서버 인증·소유권·PIN·안전 검사를 유지합니다. Release에는 로컬 HTTP 연결을 허용하지 않습니다.

## 구조

`MainActivity`는 진입과 화면을 연결합니다. `ui/premium`은 공통 표현, 기능별 ViewModel/Controller는 명령·상태, Repository/Gateway는 저장·API·공식 SDK를 담당합니다.

- `parking/`: 현재 위치·검색·상세·NaviHelper. 수동 좌표는 Debug만 허용합니다.
- `waiting/`, `modes/`: Room 대기 세션·안전 게이트·공통 모드 시작과 전환
- `pickup/`: 독립 Pickup·실제 QR·동의 기반 위치·참가자 동기화
- `realtime/`: 코드·양쪽 승인·PIN·자료 수동 전송
- `documents/`, `workspace/`: Study/Work 공통 자료·권한·파일 버전
- `rest/`, `study/`, `work/`: 휴식, 읽기·메모·진도, DOCX 편집·초안·내보내기

## 안전과 검증 경계

주행·UNKNOWN·권한 상실 시 정차 전용 화면을 차단합니다. 확인된 PIN은 같은 앱 전면 세션의 내부 화면 이동에서 재사용하며, 백그라운드·잠금·사용자 변경 등에서는 잠급니다.

Pleos FusedLocation 1.1.3, NaviHelper 3.0.5, TextToSpeech 3.3.1을 기존 Gateway로 사용합니다. FusedLocation의 confidence를 미터 정확도로 바꾸지 않으며 정확도를 확인할 수 없으면 자동 근접 판정을 하지 않습니다. 내비 경로 수락·콜백, 실제 차량 신호와 TTS 출력은 컴파일 결과와 구분해 보고합니다.

[11/02 초기 SDK 검증](../docs/verification/2026-11-02-parking-api.md)은 당시의 관측입니다. 이후 수정·환경 결과는 [검증 목차](../docs/README.md)를 함께 확인하세요.
