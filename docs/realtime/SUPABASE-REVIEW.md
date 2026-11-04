# Supabase Realtime 적용 검토 — 2026-11-04

## 실제 조회 결과 (변경 없음)
대상: `pleos-stay-plus` / `iadqoesbqamweseplvkr`, ACTIVE_HEALTHY, PostgreSQL 17.
프로젝트 목록에서 이름·ID를 특정한 뒤 메타데이터 SELECT와 조회 전용 도구만 사용했다.

- public 테이블: pickup_sessions, pickup_invitations, pickup_participants, pickup_current_locations, pickup_events, pickup_request_idempotency. 모두 RLS 활성.
- authenticated: sessions/participants/current_locations/events에 SELECT만. anon: public 테이블 권한 없음. service_role: 기존 Pickup 테이블의 관리 권한.
- RLS: 위 4개 조회 테이블에서 owner 또는 활성 participant를 검사. invitation·idempotency에는 클라이언트 정책 없음.
- 함수: cleanup_closed_pickup, is_pickup_owner, is_active_pickup_participant, pickup_command. 정의만 읽었으며 실행하지 않았다.
- publication: Pickup 조회 테이블 4개가 supabase_realtime에 포함됨.
- realtime_* 테이블·함수 없음. waiting_sessions 없음.
- Storage 버킷 0개, storage 스키마 RLS 정책 0개. storage.objects RLS 활성. 기본 테이블 GRANT가 있어도 허용 정책이 없으므로 일반 클라이언트 접근은 차단됨.
- Migration 목록은 빈 배열. `supabase_migrations.schema_migrations`도 없음. README의 과거 ‘적용 완료’와 DB 객체 존재는 일치하지만 Migration 이력으로 적용을 증명할 수 없음.
- 공개 GET /auth/v1/settings: disable_signup=false. 이 응답은 Anonymous Auth, CAPTCHA, 세부 rate limit 설정을 제공하지 않아 미확인. 계정 생성으로 확인하지 않았음.

## 기존 SQL과 차이·충돌
`20261101000000_initial_waiting_schema.sql`은 pickup_invitations/pickup_participants를 waiting_sessions에 연결한다. 실제 DB는 독립 pickup_sessions 구조다. 초기 초안과 기존 Pickup SQL을 재실행하지 않는다. Migration 이력이 비어 있다는 이유로 db push/reset/pull 자동 적용을 하지 않는다.
Realtime 초안은 새 이름만 생성하고 기존 Pickup FK/함수/정책/publication을 참조하거나 수정하지 않는다. Auth는 공통 서비스이지만 신규 기기 UID만 사용하는 구조다. 테스트 계정 생성은 공통 Auth 사용량·제한에 영향을 줄 수 있다.

## 승인 요청 대상
실행 파일: `supabase/drafts/realtime-apply-reviewed.sql` (schema 초안 + RPC 초안 합본).
Migration 도구를 쓴다면 새 이름 `realtime_file_transfer_mvp` 한 건만 적용하며, Migration 이력 저장소 생성도 이 작업의 부수 효과다. 기존 Migration 이력의 임의 보정은 하지 않는다.

1. 새 테이블 6개: realtime_devices, realtime_pairing_requests, realtime_connections, realtime_files, realtime_file_versions, realtime_rate_limits.
2. 각 테이블의 FK/unique/check/index, RLS 활성, PUBLIC/anon/authenticated 권한 철회, service_role CRUD 부여.
3. 새 함수 realtime_version_json, realtime_command. SECURITY INVOKER, service_role만 실행. 페어링/승인/버전 확정은 하나의 트랜잭션에서 직렬화한다.
4. 새 Private Bucket stay-realtime-files, 제한 20 MiB, 서버 전송 MIME application/octet-stream. 실제 TXT/PDF/DOCX 내용 검증은 서버에서 수행.
5. 기존 Storage 정책 변경 없음. 현재 허용 정책이 없으므로 클라이언트는 파일을 직접 읽거나 쓰지 못한다. 서버의 인증·연결 검사를 거친 스트리밍만 사용하며 signed URL은 발급하지 않는다.
6. 별도 승인된 검증 범위: 테스트용 익명 계정 3개(PC/차량/제3자), TEST-Realtime 표식의 신규 Realtime 기기·연결·파일·버전, 합성 TXT/PDF/DOCX 업로드/다운로드 및 연결 철회. 기존 Pickup 데이터는 테스트에 사용하지 않는다. Auth 설정은 변경하지 않는다.

## 보호·실패 복구
- 적용 직전 같은 대상/객체 부재 및 Pickup 스키마·RLS·publication을 재조회한다. 대상 객체가 생겼으면 덮어쓰지 않고 중단한다.
- 합본을 원자적 Migration으로 적용한다. 오류면 해당 신규 변경 전체를 rollback하고 원인을 보고한다. 기존 SQL 재실행으로 복구하지 않는다.
- 성공 후 장애가 있으면 Realtime 실행부를 정지하고 신규 데이터·객체를 유지한다. 삭제/drop 또는 Auth 계정 정리는 별도 승인 전 실행하지 않는다.
- Storage 업로드는 DB 트랜잭션 밖이다. READY는 업로드·해시·형식 검증 후에만 확정한다. 중단/응답 유실은 동일 idempotency key로 상태 조회·확정 재시도한다.
- 서버 중단 당시 UPLOADING은 자동 재개하지 않는다. 만료(1시간) 또는 명시적 취소 후 새 요청으로 복구한다. 정리 작업은 terminal/삭제 객체만 대상으로 하고 진행 중 객체는 만료까지 기다린다.
- apply 이후 권한·테이블·함수·Bucket 설정을 다시 조회하고 Pickup 정의 변경 여부를 비교한다. 실제 Pickup 쓰기 E2E는 이 승인 범위에 포함하지 않는다.

## 적용 전 필요한 입력
서버 전용 REALTIME_SUPABASE_SERVER_SECRET과 32자 이상 REALTIME_PAIRING_SECRET은 로컬 프로세스 환경변수에만 주입한다. 채팅·소스·Git에 넣지 않는다. 공개 URL/key만 클라이언트에 입력한다.
실제 DB 변경과 테스트 계정/파일 생성은 아직 승인되지 않았고 실행하지 않았다. SQL의 PostgreSQL 실행 검증 역시 아직 하지 않았다. 로컬 psql/Docker/Supabase CLI를 발견하지 못했다.

## 승인 후 실제 적용 및 검증 (2026-11-04)
위의 '미승인/미적용' 문장은 최초 검토 시점의 기록이다. 이후 사용자 명시적 승인으로 다음을 적용했다.
- 20261104073617 realtime_file_transfer_mvp: 승인 합본 SHA256 9049E46D25FFBE1E2D92B9A701ACEF11934AE3725B78F110E01E32E81397A3A6.
- 20261104122800 realtime_pairing_interval_fix: DB 42703(column secs does not exist) 원인을 확인하고 별도 승인 후 realtime_command의 make_interval(secs=...)를 secs => ...로 수정. 검토 SQL SHA256 ec31865d23ba9ef25ff48f45688ba26800324eea6b5ae4cbfc603f82ad4e93a5. 테이블/권한 변경 없음.
- 여섯 Realtime 테이블 RLS 활성, Private Bucket public=false/20MiB 확인. realtime_command는 anon/authenticated 실행 불가, service_role만 실행 가능.
- 실제 익명 인증과 기기 등록 성공. 공개 키와 서버 키 연동 확인. PAIRING_SECRET은 안전한 난수로 로컬 설정하며 값은 출력하지 않았다.
- Cloud TXT/PDF/DOCX 원본·수정본 6개 READY와 Storage 객체 6개 확인. 각 왕복 SHA-256/원본 보존/버전/중복 요청/제3자 차단/서버 인스턴스 복원/직접 테이블 접근 차단/연결 철회 통과.
- 테스트 run: TEST-Realtime-24f68845-2a3c-4e58-b40e-59bfcb70d8fe. 계정 3개와 테스트 데이터는 보존 중이며 삭제는 별도 승인 대상이다.
- Pickup 함수 정의 fingerprint(함수명순, 빈 구분자 MD5) 111f14f3df8cf79dc20f3efc43b601c9: 변경 전후 동일. Pickup SELECT 정책 4개와 publication 대상 4개 유지. 기존 Pickup 데이터는 읽기/쓰기 테스트에 사용하지 않았다. 실제 Pickup Cloud E2E 성공을 의미하지 않는다.
- Advisor는 Realtime의 RLS 정책 없음(INFO)을 보고했으나 서버 전용 접근 설계상 의도된 차단이다. 기존 Pickup SECURITY DEFINER 실행 권한, 익명 사용자 허용 정책, 비밀번호 유출 보호 설정 관련 경고는 별도 검토 대상으로 남기고 변경하지 않았다. 참고: https://supabase.com/docs/guides/database/database-linter?lint=0028_anon_security_definer_function_executable

### 별도 프로세스 복원 검증 완료
REALTIME_PROCESS_SMOKE=true로 실제 Fastify 자식 프로세스를 종료하고 새 프로세스를 실행했다. 같은 익명 계정 3개를 재사용했으며 TXT 원본/수정본 2개 추가 저장, 연결 복원, 파일 SHA-256 복원, 제3자 차단, 연결 철회 모두 통과했다. 새 계정은 추가하지 않았다.
보고서: D:/temp/TEST-Realtime-24f68845-2a3c-4e58-b40e-59bfcb70d8fe-process.json.
전체 형식 테스트 보고서: D:/temp/TEST-Realtime-24f68845-2a3c-4e58-b40e-59bfcb70d8fe.json.
적용 SQL은 최초 승인 합본과 별도 문법 수정본을 순서대로 기록한다. 최초 합본의 hash를 유지하기 위해 수정 내용을 기존 승인 파일에 덮어쓰지 않았다. 기존 waiting/pickup migrations를 통째로 재실행하지 않는다.
