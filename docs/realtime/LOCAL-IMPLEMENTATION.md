# Realtime FileTransfer — 로컬 구현 및 인계

2026-11-04 기준. 자동 동기화가 아닌 수동 파일 전송 MVP다. 기존 Waiting Safety Gateway를 재사용하며 Work 편집기는 구현하지 않는다.

## 구현 범위
- Backend: 독립 Fastify `/api/v1/realtime` API, Anonymous Auth 토큰 검증, 양쪽 승인 페어링, 파일/버전 관리, SHA-256, TXT/PDF/DOCX 구조 검사, Supabase RPC/Private Storage 어댑터.
- Windows: .NET 10 WPF Stay+.exe, 선택 업로드/진행률/취소/복구, 파일·버전 목록, 덮어쓰기 없는 다운로드, 페어링·철회, DPAPI 세션/전송 기록. 연결 주소 수정 중에도 복구 기록은 실제 연결된 서버를 기준으로 한다.
- Android: Compose 목록/다운로드/진행률, Keystore 인증·전송 기록, PIN 잠금, 기존 Safety Gateway 차단, Fake Work 문서 선택과 수정본 업로드. 실제 에뮬레이터/차량 UI 왕복 검증은 제외했다.
- 원본마다 fileId, 전송마다 versionId와 idempotencyKey. 수정본은 baseVersionId를 보존하며 READY 확정 시 버전 번호를 부여한다. 동일 이름은 별도 원본으로 취급한다.

## 실행과 키의 역할
설정: `backend/src/realtime/.env.local` (Git 제외). 비밀값은 문서/로그/클라이언트에 복사하지 않는다.
- REALTIME_SUPABASE_URL: 승인된 프로젝트 `https://iadqoesbqamweseplvkr.supabase.co`.
- REALTIME_SUPABASE_PUBLIC_KEY: 공개 publishable key. Windows/Android의 익명 가입·토큰 갱신, Backend의 `/auth/v1/user` 검증, 테스트 Auth 요청에 필요하다. 파일 접근 권한을 대신하지 않는다.
- REALTIME_SUPABASE_SERVER_SECRET: Backend의 DB RPC·Storage 요청에만 사용. EXE/APK에 포함하지 않는다.
- REALTIME_PAIRING_SECRET: 서버 HMAC용 32바이트 암호학적 난수(hex 64자). 로컬에 생성해 설정했다. 재실행마다 바꾸지 않는다. 변경 시 미완료 연결 코드와 테스트 세션 보관함의 호환성에 영향을 준다.

backend 폴더에서 `node --import tsx src/realtime/supabase-local-server.ts` 실행. 명시적 REALTIME_SUPABASE_ENABLED=true 필요. 기본 127.0.0.1:3012이며 외부 배포·터널은 하지 않는다. 서버는 만료/삭제 객체 정리 작업을 포함하므로 승인된 프로젝트에만 실행한다.
Windows에서 로컬 Fake 인증을 해제하고 서버 주소, Supabase URL와 공개 키를 입력한다. Android Debug는 같은 서버에 10.0.2.2:3012로 접근할 수 있으나 이 환경에서는 실행 검증하지 않았다. 운영 환경에는 팀원의 HTTPS 서버 배포가 필요하다.

Cloud를 사용하지 않는 테스트는 local-server.ts를 사용한다. REALTIME_LOCAL_DEMO=true, 서로 다른 32자 이상 PC/CAR 토큰, 저장 경로를 지정한다. 기본 포트3010. 메타데이터는 체크포인트로 복원되며 단일 프로세스 개발용이다.

## HTTP 계약
Base `/api/v1/realtime`, Bearer 토큰. 오류 `{error:{code,message,requestId}}`.
- POST devices, GET devices/me
- POST pairings, POST pairings/claim, GET pairings/:id, POST pairings/:id/approve, DELETE pairings/:id
- GET connections/current, DELETE connections/:id
- GET files, GET files/:id/versions, DELETE files/:id
- POST uploads: connectionId, fileId?/baseVersionId?, fileName, mimeType, size, sha256, idempotencyKey
- PUT uploads/:id/content: application/octet-stream. POST uploads/:id/complete, GET/DELETE uploads/:id
- GET versions/:id/content: 서버 스트리밍, Content-Length와 X-Content-SHA256. Signed URL은 발급하지 않는다.

개별 인증 UID를 기기에 묶고 활성 연결을 매 요청 검증한다. RPC·테이블·Storage는 일반 클라이언트에 직접 허용하지 않는다. 제한은 20MiB, TXT/PDF/DOCX다. 구조 검사는 악성코드 검사나 문서 렌더링 검증이 아니다.

## Work 연결 계약
`RealtimeWorkBridge.kt`:
- WorkRevisionRequest(originalFileId, baseVersionId, sourceUri, fileName, mimeType, idempotencyKey)
- suspend stageRevision(request): StagedRevision
- suspend uploadRevision(staged, progress: (Long, Long) -> Unit): JSONObject (확정 Version)
Work는 stageRevision이 끝날 때까지 읽기 가능한 content:// URI 권한을 유지한다. Realtime은 앱 전용 저장소로 복사·크기/해시 검사 후 반환하므로 이후 원래 URI의 장기 권한에 의존하지 않는다. 재시도는 같은 key와 staged 파일을 사용한다. URI 분실·지원하지 않는 형식·크기 초과·안전 차단·인증 실패는 예외로 전달하며 성공으로 치환하지 않는다. 취소는 coroutine 취소를 전파한다. 실제 Work 화면 연결과 반환 파일 열기는 Work 담당과 통합 테스트해야 한다.

## 복구·보안의 범위
- PENDING/FAILED는 같은 요청으로 전체 재전송, VERIFYING은 확정 재시도, READY는 기존 결과 사용. 청크 이어받기는 없다.
- 서버 중단 당시 UPLOADING은 임의 재개하지 않는다. 취소 후 새 요청 또는 1시간 만료 처리. 로컬 staged 파일과 암호화 기록으로 사용자 재시도를 지원한다.
- 토큰 갱신 실패 시 새 익명 UID를 몰래 만들지 않는다. 자격 증명 분실은 명시적 재연결 대상이다.
- 다운로드는 임시 파일에서 해시 검사 후 완료하고 기존 파일을 덮어쓰지 않는다. 연결 철회 후 새 접근은 거절하지만 이미 다운로드한 파일을 원격 삭제하지 않는다.
- Android UNKNOWN/주행 상태는 차단한다. PIN은 앱 잠금이며 차량 OS 사용자 전환 통합을 대신하지 않는다.
- 파일 삭제는 논리 삭제 후 서버 정리. 자동 병합, 폴더 감시, 백그라운드 상시 동기화, OS 알림은 제외한다.

## 검증 결과와 명령
- Backend 26개 통과: 기존 Parking/Pickup, HTTP 왕복, 코드 추측 제한·만료·재사용, 문서 검사, 중단/중복 확정, 체크포인트 및 별도 프로세스 재시작.
- TypeScript noEmit 통과. `node --import tsx --test test/*.test.ts` (backend).
- Windows 빌드 0경고/0오류. `dotnet build windows/StayPlus.Desktop/StayPlus.Desktop.csproj --no-restore`. 출력 bin/Debug/net10.0-windows/Stay+.exe는 .NET 10 Desktop Runtime이 필요한 framework-dependent 빌드다.
- Windows 실제 C# 클라이언트 로컬 HTTP 테스트와 Auth 가짜 응답 테스트 통과. `node backend/test/run-windows-local.mjs`.
- Android assembleDebug 및 단위 테스트 32개 통과(기존27+안전2+세션3). 한국어 Windows 경로 환경은 JAVA_HOME=Android Studio/jbr, ANDROID_HOME=SDK 설정 후 Gradle에 `-Dorg.gradle.jvmargs=-Xmx2048m -Dfile.encoding=COMPAT` 옵션을 사용했다. 공통 Gradle 파일은 변경하지 않았다.
- 실제 Cloud: 익명 계정 3개, 양쪽 페어링 승인, TXT/PDF/DOCX 원본·수정본 6개, SHA-256, 동일 요청 중복 방지, 원본 보존, 제3자 차단, 새 서버 인스턴스 복원, 직접 테이블 접근 거절, 연결 철회 모두 통과.
- 별도 실제 프로세스 재시작 추가 검증 결과는 SUPABASE-REVIEW.md에 기록한다.

`verify-cloud.ts`는 명시적 REALTIME_CLOUD_TEST_APPROVED=true가 필요한 쓰기 테스트다. 기존 암호화 세션 보관함의 3계정을 재사용한다. REALTIME_PROCESS_SMOKE=true는 TXT만으로 별도 프로세스 재시작을 검사한다. 승인 없이 다른 프로젝트에서 실행하지 않는다. 테스트 데이터는 TEST-Realtime 표식으로 구분하고 자동 삭제하지 않는다.

## 변경 파일과 후속 작업
Backend src/realtime 및 test/realtime-*는 전송·영속·인증·검증 담당. Windows/StayPlus.Desktop과 StayPlus.LocalTests는 PC 앱/검증 담당. Android realtime 패키지와 테스트는 차량 UI·Work 계약 담당. supabase/drafts는 적용 기록/수정 SQL이며 기존 전체 migrations를 db push하지 않는다. 공통 수정은 승인된 MainActivity 최소 연결만 유지했다.
팀원은 Git 공유 전 .env.local/토큰/빌드 산출물 제외를 확인하고 독립 Realtime 파일들을 포함해야 한다. 이번 작업에서는 commit/push하지 않았다. 실제 Work 연결, 팀원 Android 에뮬레이터 UI·URI·Safety 전환 검증, 차량/PLEOS 검증, HTTPS Fastify 배포는 남는다. Windows UI 전체 왕복 조작도 별도 인수 테스트가 필요하다. 페이지네이션·사용량 할당량·악성코드 검사·대규모 만료 객체 정리는 후속 개선이다.
