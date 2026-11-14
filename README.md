# Pleos Stay+

**차량에서 잠깐의 대기를 휴식·공부·업무로 이어주는 PLEOS Connect 앱입니다.**

주차 장소를 찾고, 대기시간을 관리하며, 픽업 상대에게 차량 위치를 공유합니다. PC에서 받은 자료는 차량에서 읽거나 DOCX로 편집하고, 사용자가 선택한 파일만 수동 전송합니다.

> 담당자 검토 기준은 `main`입니다. 최신 `dev`의 기능 코드·규칙을 보존해 통합했습니다.
> 제출 APK는 아래에서 내려받을 수 있습니다. [검토 가이드](docs/review/REVIEW_GUIDE.md)와 [Release 검증 결과](docs/verification/2026-11-14-submission-release.md)를 함께 확인해 주세요.

## 구현 범위

| 기능 | 현재 제공하는 흐름 | 확인할 제약 |
|---|---|---|
| Parking | 현재 위치 주변 검색, 반경·시간·목적·무료 필터, 상세·요금·출처, NaviHelper 요청 | 실시간 빈자리·목적지 이름 검색·앱 내부 지도는 제공하지 않습니다. |
| Waiting | 로컬 대기 시작·연장·종료·기록, 모드 진입 | 정차 확인이 안 되면 제한합니다. 선택 장소를 실제 도착 위치로 간주하지 않습니다. |
| Pickup | Waiting 없는 독립 시작, 60분 초대·QR·재발급, 1명 참가, 동의 기반 위치 공유·수동 완료 | 휴대전화 접근용 HTTPS 주소와 Supabase 설정이 필요합니다. |
| Realtime | 8자리 코드·양쪽 승인 PC 연결, TXT/PDF/DOCX 파일·버전 수동 송수신 | Windows Stay+ 앱과 Realtime 서버가 필요합니다. 자동 동기화가 아닙니다. |
| Rest | 프리셋, 타이머, 배경음·알람, 지원 시 EV 정보 | 실제 차량 오디오·차량 신호 지원은 기기별 검증이 필요합니다. |
| Study | PDF/DOCX/TXT 열람, 문서 옆 메모, 버전별 진도·복원 | 기존 Realtime 인증·자료함을 사용합니다. |
| Work | DOCX 본문·서식·표·이미지 편집, 암호화 초안, 실제 DOCX 내보내기 | PDF 고급 편집은 제공하지 않습니다. 공개 Downloads 저장은 확인 후 실행합니다. |

Entertainment는 팀 결정으로 제외됐습니다. **AirDesk는 별도 앱·저장소**이며 Stay+ Work에 통합된 기능으로 표시하지 않습니다.

## APK 파일 위치

**[Team_EX_PleosStayPlus_release.apk 내려받기](apk/Team_EX_PleosStayPlus_release.apk)**

- 저장소 경로: `apk/Team_EX_PleosStayPlus_release.apk`
- GitHub 파일 화면에서 **Download raw file**로 APK를 내려받으세요.
- 제출용 **서명된 Release** 빌드입니다. 기존 로컬 시연용 Debug APK와 구분합니다.
- 무결성 확인: [SHA-256](apk/Team_EX_PleosStayPlus_release.apk.sha256)

## 실행 방법

1. PLEOS Connect 환경을 실행하고 위 Release APK를 설치합니다.
2. 앱 목록에서 **Pleos Stay+**를 실행합니다. 정차 전용 기능은 차량의 정차 상태가 확인되어야 사용할 수 있습니다.
3. 인터넷 연결과 요청되는 위치 권한을 확인한 뒤 **주차·대기**에서 주변 장소를 검색합니다.
4. **픽업 공유**는 초대 QR을 발급하고 휴대전화 브라우저로 열어 참가 동의 후 사용합니다. 휴대전화에서 앱을 설치할 필요는 없습니다.
5. PC 자료를 사용하려면 Windows용 **Stay+**를 실행하고, 차량의 **파일 작업 → PC 연결하기**에서 발급한 8자리 코드를 입력한 뒤 **차량과 PC 양쪽에서 승인**합니다. 기존 PIN 확인은 유지됩니다.
6. 연결한 자료는 Study에서 열람하거나 Work에서 DOCX로 편집합니다. 파일 전송과 공개 Downloads 내보내기는 사용자가 직접 승인합니다.

제출 APK에는 아래 공개 HTTPS 주소가 설정되어 있습니다. **심사자가 로컬 Backend·웹 서버를 실행하거나 API 키를 입력할 필요는 없습니다.** PC 파일 송수신에는 별도 Windows Stay+ 프로그램이 필요합니다. **[Windows 프로그램 내려받기](https://github.com/ukhansh3/pleos-stay-plus/releases/download/submission-2026-11-14/StayPlus-Windows-x64.zip)** → 압축 해제 → `Stay+.exe` 실행으로 사용합니다. 서버 주소 입력이나 별도의 .NET 설치는 필요하지 않습니다. 설치 프로그램 형식이 아닌 휴대용 x64 실행 파일입니다. [전체 Release 파일](https://github.com/ukhansh3/pleos-stay-plus/releases/tag/submission-2026-11-14)에서 함께 받을 수 있습니다. Windows 소스·개발 빌드는 [로컬 실행 가이드](docs/review/LOCAL_SETUP.md)를 참고하세요.

이미 실행 중인 에뮬레이터가 한 대인 경우 설치·실행 명령은 다음과 같습니다. Android SDK의 `platform-tools`에서 실행하며, 이 명령은 에뮬레이터를 새로 켜지 않습니다.

```powershell
adb -e install -r "팀에서_내려받은_경로\Team_EX_PleosStayPlus_release.apk"
adb -e shell am start -n "com.pleosstay.app/.MainActivity"
```

기존 Debug 앱과 제출 APK는 서명이 달라 업데이트 설치가 거부될 수 있습니다. 이때 기존 데이터를 삭제하지 말고 별도 테스트 환경에서 설치하세요. 일반 휴대전화용 APK가 아닌 PLEOS 차량용 앱입니다.

## 테스트 환경 및 필요한 설정

| 항목 | 환경·조건 |
|---|---|
| 개발에 사용한 에뮬레이터 | **Pleos Connect Emulator v2.1.0 / Android API 34 / x86_64** |
| 전체 디스플레이 | **2560 × 1440** |
| Stay+ 앱 콘텐츠 영역 | **약 1682 × 1210 (약 1.39:1)**. 시스템 영역·밀도·창 제약에 따라 달라지며 dp 기반 레이아웃을 사용합니다. |
| Android 빌드 | Kotlin / Jetpack Compose, Android Studio JBR 25.0.4, compile/target SDK 37, min SDK 28 |
| 서버·웹 검증 환경 | Node.js 24.13.0, TypeScript, Fastify, Next.js, 기존 Supabase Auth·Storage·DB |
| PC 자료 송수신 | Windows x64 Stay+ 클라이언트, 양쪽 승인 페어링·PIN·인터넷 연결 |
| 권한·안전 | 위치 권한 및 위치 서비스. 정차 상태 UNKNOWN/주행 중에는 제한합니다. |

### 제출본 서버

| 역할 | 공개 HTTPS 주소 |
|---|---|
| Parking / Pickup API | https://stayplus-review-20261114-api.onrender.com |
| Realtime API | https://stayplus-review-20261114-realtime.onrender.com |
| Pickup 모바일 웹 / PC 연결 안내 | https://stayplus-review-20261114-web.onrender.com |

Render 무료 심사용 서비스와 기존 Supabase를 사용합니다. 미사용 시 서버가 휴면하므로 첫 요청에 시간이 걸릴 수 있습니다. 잠시 기다린 뒤 재시도해 주세요. 무료 사용량 한도에 도달하면 서비스가 중단될 수 있으며, 상시 가용성을 보장하지 않습니다. 결제수단·유료 인스턴스는 추가하지 않았습니다.

### 검증 범위

- **PASS:** Release 빌드·서명·정렬·APK 무결성, Android JVM 231 통과 / 1 제외, Backend 38/38, Web 16/16 및 타입 검사.
- **PASS:** 공개 서버의 실제 Parking 데이터 조회, Supabase 익명 인증, Pickup 초대·참가·재발급·수동 완료, Realtime 양쪽 승인·TXT 원본/수정본·신규 DOCX 업로드·다운로드·SHA-256·철회 차단.
- **NOT RUN:** 이번 Release APK의 새 에뮬레이터 설치·UI 조작·실차 GPS·NaviHelper·스피커·휴대전화 지도 표시. 사용자 요청으로 에뮬레이터를 실행하지 않았습니다.
- QA 임시 계정 정리의 DB 참조 제약 오류 등 상세 결과는 [Release 검증 기록](docs/verification/2026-11-14-submission-release.md)에 분리했습니다. API 검증을 실제 차량 UI 검증으로 간주하지 않습니다.

개발자가 직접 빌드할 때만 [로컬 설정 가이드](docs/review/LOCAL_SETUP.md)에 따라 서버 환경변수와 공개 클라이언트 설정을 준비합니다. 서버 secret·서명키는 Git과 APK에 포함하지 않습니다. [Compose Preview](docs/ui/COMPOSE_PREVIEW_GUIDE.md)로 UI를 확인할 수도 있습니다.

## 저장소 구조

```text
apk/                      서명된 제출 Release APK·SHA-256
android/                  PLEOS Kotlin / Jetpack Compose 앱
  app/src/main/java/com/pleosstay/app/
    MainActivity.kt       앱 진입과 화면 연결
    ui/                   Premium 디자인·내비게이션·화면 조합
    modes/                공통 모드 시작·전환
    waiting/              Room 대기 세션·차량 안전 게이트
    parking/  pickup/     주차 검색·픽업 차량 기능
    realtime/             PC 연결·PIN·파일 전송
    documents/ workspace/ 공부·업무 공통 자료함·인증 세션
    rest/ study/ work/    기능별 상태·저장소·화면
backend/                  Parking/Pickup Fastify API + 독립 Realtime 서버
web/                      Pickup 모바일 웹·공유 안내·PC 연결 안내
windows/                  Stay+ WPF 파일 전송 앱과 로컬 통합 테스트
supabase/                 SQL·RLS·적용 기록 — 실행 전 별도 검토
docs/                     계약·설계·UI·검증·담당자 안내
tools/                    격리된 문서 엔진 기술검증 도구
```

일반적인 기능 흐름은 **Compose 화면 → ViewModel/Controller → Repository/Gateway → 로컬 저장소·HTTP API·공식 SDK**입니다. 화면 디자인 변경과 API·인증·저장 정책 변경은 별도로 다룹니다.

## 코드와 문서 읽는 순서

1. [검토 가이드](docs/review/REVIEW_GUIDE.md): 기능별 시작 파일과 확인 시나리오
2. [API 안내](docs/api/README.md): HTTP·LOCAL·SDK의 실제 책임 구분
3. [공통 문서 계약](docs/contracts/DOCUMENT_WORKSPACE_CONTRACT.md): 파일·버전·PIN·초안 경계
4. [UI 구현 기준](docs/ui/PREMIUM_UI_IMPLEMENTATION_SPEC.md): 확정 홈과 Premium 컴포넌트
5. [문서 목차](docs/README.md): 상세 명세와 날짜별 검증 기록
6. [개발 규칙](AGENTS.md) · [제품·결정 이력](DEVELOPMENT_GUIDE.md)

## 빌드·테스트

```powershell
# 저장소 루트 / Node.js 20 이상
cd backend
npm ci
npm test
npm run typecheck
npm run validate:openapi

# Android SDK와 JAVA_HOME 설정 후, android 폴더에서
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug --max-workers=1
```

웹·Windows·전체 검증 명령은 [실행 가이드](docs/review/LOCAL_SETUP.md)를 참고하세요. 최신 결과와 미실행 항목은 [Release 검증 기록](docs/verification/2026-11-14-submission-release.md)에 분리했습니다. 빌드 성공은 실제 차량 SDK·내비·휴대전화 지도 동작 검증과 다릅니다.

## 안전·운영 경계

- 차량 상태 UNKNOWN/주행/권한 상실 시 정차 전용 기능을 차단합니다.
- PIN 확인은 같은 앱 전면 세션의 화면 이동에서 재사용하고, 백그라운드·명시적 잠금·사용자 변경 등에서는 잠급니다.
- Pickup QR은 위치 접근 권한 자체가 아닙니다. 참가 동의·인증·초대 유효성 확인이 필요합니다.
- Work 공개 내보내기와 Realtime 업로드는 각각 사용자가 직접 승인합니다.
- 서버 배포·DB 마이그레이션·과금 설정은 빌드·설치 과정에 포함하지 않습니다.
- `main`은 담당자 검토 기준, `dev`는 개발 기준입니다. 변경은 이력을 보존하며 통합합니다.
