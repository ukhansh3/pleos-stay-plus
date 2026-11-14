# Pleos Stay+

**차량에서 잠깐의 대기를 휴식·공부·업무로 이어주는 PLEOS Connect 앱입니다.**

주차 장소를 찾고, 대기시간을 관리하며, 픽업 상대에게 차량 위치를 공유합니다. PC에서 받은 자료는 차량에서 읽거나 DOCX로 편집하고, 사용자가 선택한 파일만 수동 전송합니다.

> 담당자 검토 기준은 `main`입니다. 최신 `dev`의 기능 코드·규칙을 보존해 통합했습니다.
> 처음 보신다면 [검토 가이드](docs/review/REVIEW_GUIDE.md) → [설정·실행](docs/review/LOCAL_SETUP.md) → [검증 결과](docs/verification/2026-11-14-review-handoff.md) 순서로 읽어 주세요.

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

## 빠르게 확인하기

- **APK 검토:** 담당자 검토·시연용은 Debug 빌드입니다. APK 설치만으로 서버나 인증 설정이 만들어지지는 않습니다.
- **로컬 시연:** Parking/Pickup `3000`, Pickup 웹 `3001`, Supabase Realtime 어댑터 `3012`를 사용합니다. Android 에뮬레이터는 PC의 loopback에 `10.0.2.2`로 접근합니다.
- **다른 휴대전화:** `localhost` 대신 승인된 공개 HTTPS 웹 주소가 필요합니다. 임시 QA 주소는 영구 배포 주소가 아닙니다.
- **UI만 확인:** [Compose Preview 안내](docs/ui/COMPOSE_PREVIEW_GUIDE.md)를 참고하세요. 에뮬레이터를 자동 실행하지 않습니다.

자세한 명령과 환경변수 역할은 [설정·실행 가이드](docs/review/LOCAL_SETUP.md)에 있습니다. 실제 비밀키·토큰·서명키·개인 파일은 Git에 포함하지 않습니다.

## 저장소 구조

```text
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

웹·Windows·전체 검증 명령은 [실행 가이드](docs/review/LOCAL_SETUP.md)를 참고하세요. 최신 결과와 미실행 항목은 [이번 검증 기록](docs/verification/2026-11-14-review-handoff.md)에 분리했습니다. 빌드 성공은 실제 차량 SDK·내비·휴대전화 지도 동작 검증과 다릅니다.

## 안전·운영 경계

- 차량 상태 UNKNOWN/주행/권한 상실 시 정차 전용 기능을 차단합니다.
- PIN 확인은 같은 앱 전면 세션의 화면 이동에서 재사용하고, 백그라운드·명시적 잠금·사용자 변경 등에서는 잠급니다.
- Pickup QR은 위치 접근 권한 자체가 아닙니다. 참가 동의·인증·초대 유효성 확인이 필요합니다.
- Work 공개 내보내기와 Realtime 업로드는 각각 사용자가 직접 승인합니다.
- 서버 배포·DB 마이그레이션·과금 설정은 빌드·설치 과정에 포함하지 않습니다.
- `main`은 담당자 검토 기준, `dev`는 개발 기준입니다. 변경은 이력을 보존하며 통합합니다.
