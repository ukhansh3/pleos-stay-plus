# Realtime 간편 PC 연결 — 2026-11-11

## 기준과 실제 사용자 흐름

시작 HEAD: `cb342c2` (`dev`). 최신 원격은 `dfcb375`; 이전 QA 수정 로컬 커밋 1개를 보존한 상태에서 작업했다. 원격을 덮어쓰거나 과거 버전으로 되돌리지 않았다.

1. 차량 파일 작업에서 **PC 연결하기**.
2. 기존 자료함 PIN 설정/확인. 성공하면 입력 포커스와 소프트 키보드를 정리한다.
3. 기존 Realtime 인증 → 기기 등록 → 현재 연결 확인. 연결이 없으면 기존 POST /pairings에서 **실제 8자리 코드**를 받는다.
4. PC Windows Stay+의 **차량 연결** 탭에서 코드 입력 → **연결 요청**.
5. 차량에서 PC 이름 확인 → **이 PC 승인**, PC에서 **이 차량 승인**.
6. 서버가 활성 connection을 반환한 이후에만 파일 목록과 전송을 제공한다. 기존 연결이면 새 코드를 만들지 않고 복원한다.

서버 주소/토큰/공개 키는 일반 사용자 흐름에 없다. QR 안내는 승인된 `REALTIME_PAIRING_PAGE_URL`이 있을 때만 제공하며, 코드·인증 토큰을 안내 URL에 넣지 않는다. 웹 페이지만으로 PC 파일 전송이 되는 것처럼 표현하지 않는다.

## 구현

- `RealtimeEntry`: 설정 복구/실제 클라이언트/PIN과 렌더링 경계는 유지하고 첫 진입을 간편 연결 화면으로 분리했다. 시작 의도는 메모리에만 보관한다. 설정이 없거나 잘못되면 오류·재시도를 표시하며 코드나 성공 상태를 생성하지 않는다.
- `RealtimeConnectScreen`: 일반 화면에는 PC 연결하기/재시도만 제공한다. 주소/키/토큰 입력은 별도의 Debug 개발자 화면으로 분리했다. 실제 설정 저장은 기존 Keystore 암호화 파일을 재사용한다. 편집 중 값은 취소 시 기존 설정을 바꾸지 않는다.
- `MainActivity`, `StaySettingsScreen`: Debug의 **설정 → PC 연결 설정**에서만 개발자 화면에 접근한다. 진행 중 문서 활동의 연결은 바꾸지 못한다. 다른 화면으로 이동하면 개발자 화면 플래그를 닫는다. 홈 렌더러·이미지·로고·레이아웃은 변경하지 않았다.
- `FileTransferViewModel.openConnection`: PIN 확인 이후 기존 등록/복원/코드 발급을 이어 실행한다. 화면 재진입·취소·만료만으로 코드를 자동 재발급하지 않는다. 수동 재발급은 기존 버튼/API를 사용한다. 양쪽 승인·코드 8자리·기존 만료 및 요청 제한은 서버 정책 그대로다.
- `FileTransferScreen`: 초기 진입을 위 흐름에 연결했다. 역방향 PC 발급 코드 테스트 UI는 기본 숨김이며 API/메서드는 유지한다. 사용자 전환, 수동 DOCX 전송, 파일/버전·해시·권한 검사는 그대로다.
- Android Debug만 문서화된 실제 인증 서버 `http://10.0.2.2:3012/api/v1/realtime/`를 기본값으로 사용한다. 고정 QA 토큰을 자동 생성/주입하지 않고 기존 Supabase URL/공개 키와 실제 인증을 사용한다. Release는 임의의 기본 주소 없이 승인된 HTTPS 배포 주소가 필요하다.
- Windows: 첫 화면을 차량 연결로 두었다. 환경변수 기본값이 빈 빌드 메타데이터로 덮어써지던 버그를 수정했다. Debug만 실제 인증 서버의 loopback HTTP 기본값을 사용하고 Release는 HTTPS만 허용한다. 개발자 설정·고정 토큰 모드는 Release에서 차단한다.
- Windows `Deployment.local.props`: 기존 Android의 승인된 **공개 인증 설정만** Git 제외 로컬 빌드 파일에 보존했다. 서버 secret/HMAC/access token은 이 파일에 복사하지 않았다. `Deployment.local.props.example`에는 예시만 있으며 빌드 환경의 명시적 값이 로컬 기본값보다 우선한다.

## 설정과 미완료 환경

- 실제 프로젝트: `C:/Users/ukhan/pleos-stay-plus`.
- Android Debug APK와 Windows Debug EXE를 빌드했다. 설치/실행하지 않았다. Windows의 공개 인증 기본값은 로컬 빌드 설정에 들어 있어 매번 UI에서 입력할 필요가 없다.
- `backend/src/realtime/.env.local`은 현재 없다. 기존 실제 서버에는 `REALTIME_SUPABASE_ENABLED`, URL, 공개 키, 서버 전용 secret, `REALTIME_PAIRING_SECRET`가 필요하다. 원래 Parking/Pickup 서버(3000), Next.js(3001) 실행만으로 Realtime(3012)이 켜지지 않는다.
- 서버 전용 설정/새 HMAC 키 생성과 실제 Cloud 실행·쓰기는 이번 코드 변경만으로 임의 수행하지 않았다. 기존 승인 프로젝트의 설정을 재사용하더라도 새로운 서버 비밀키 파일 준비에는 별도 확인을 받는다.
- 승인된 공개 HTTPS 서비스 주소/안내 페이지는 아직 없으므로 외부망 차량↔PC 연결과 안내 QR은 BLOCKED다. 새 서버/도메인/배포/DB/RLS/공모전 SDK를 도입하지 않았다.
- 기존 `web/next-env.d.ts` 변경은 이 작업과 무관하여 보존하고 커밋에서 제외한다.

## 검증

| 검증 | 결과 |
| --- | --- |
| Android 전체 JVM 테스트 | PASS 217개, 예상된 Release 전용 Parking 1개 Debug에서 제외(총218), 실패0. |
| Debug APK | PASS `:app:assembleDebug`. |
| Android 계측 APK | PASS `:app:assembleDebugAndroidTest` 빌드만. 새 일반 연결 화면/재시도/기술 입력 부재 테스트는 실제 실행 NOT RUN. |
| Release Kotlin | PASS `:app:compileReleaseKotlin`. Release 전용 JVM task가 없는 현 구성은 이전 QA 보고서의 제한 그대로다. |
| Windows Debug/Release | PASS .NET WPF 빌드, 각각 오류0/경고0. GUI는 실행하지 않았다. |
| Windows 기본값/접속 정책 | PASS 실제 C# 호스트 테스트: 환경변수 보존·빌드 기본값 fallback, Debug loopback, Release HTTP 거부. |
| 실제 로컬 HTTP + C# 클라이언트 | PASS 차량 역할 코드 발급 → 명명된 PC 코드 입력 → 한쪽 승인만으로 파일 권한 없음 → 양쪽 승인 후 연결/전송. TXT/PDF/DOCX 원본·수정본·SHA-256·중복 요청·차량 DOCX 최초 업로드·PC 다운로드/XML 재열기·철회 차단. |
| Backend 회귀 | PASS30개. 승인·코드 만료/재사용/추측 제한·파일 권한·기존 Parking/Pickup 포함. |
| 실제 PLEOS 터치/PIN/키보드/코드 화면 | NOT RUN. 사용자의 에뮬레이터 제한을 지켜 실행/재시작/설치하지 않았다. |
| 실제 Supabase Auth + Windows GUI + 차량 앱 왕복 | BLOCKED 현재 실제 서버 설정 미완료. 로컬 HTTP 테스트의 별도 테스트 인증을 실제 Cloud 검증으로 확대하지 않는다. |
| 보호 영역 | 홈/이미지/로고/차량 SDK/Waiting/ModeStartManager/Study 저장/DOCX 엔진/Backend API/DB 변경 없음. |

## 참조

- [Realtime Notion 명세](https://app.notion.com/p/40cb74b2b90581e4865cdea39157d799): 11/05 문서의 8자리·10분·양쪽 승인·별도 Windows 프로그램·서버 설정 인계 조건을 확인했다. 더 최신 사용자 지시와 실제 dev 구현을 우선했다.
- [기존 서버 및 로컬 구성](../realtime/LOCAL-IMPLEMENTATION.md), [기존 간편 연결 QA](2026-11-09-qa-pc-onboarding.md), [전경 상태 QA 수정](2026-11-11-qa-foreground-and-connection-readiness.md).
- 공식 Compose [SoftwareKeyboardController](https://developer.android.com/reference/kotlin/androidx/compose/ui/platform/SoftwareKeyboardController), [FocusManager](https://developer.android.com/reference/kotlin/androidx/compose/ui/focus/FocusManager). 키보드 요청은 플랫폼에 종속되므로 실제 PLEOS 표시 성공을 주장하지 않는다.
