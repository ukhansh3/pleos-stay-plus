# 2026-11-01 설계 검증 기록 — API / DB / SDK

> 범위: GitHub `dev` 브랜치에 저장된 `docs/api/openapi.yaml`, `docs/architecture/database-design.md`, `DEVELOPMENT_GUIDE.md`를 정적으로 비교 검토. **실제 API 서버, DB, 에뮬레이터 테스트 미실행.**

## 검증 요약
| 검증 | 결과 | 근거/제약 |
|---|---|---|
| OpenAPI 3.1 문서의 path 항목 | 10개 | 파일 정적 탐색 |
| OpenAPI HTTP operation | 11개 | 파일 정적 탐색 |
| component ref 참조 명칭 확인 | 27개, 미정의 0 | 이름 기반 검사, 완전한 OpenAPI 스키마 검증 아님 |
| YAML 인라인 중괄호 수 일치 | OK | 기초 구문 점검만 수행 |
| 인증·RLS | **BLOCKED: REVIEW_REQUIRED** | 운전자 auth provider, 참가자 토큰과 DB 권한 연결 미정 |
| DB 관계·트랜잭션 | **DESIGN ONLY** | 테이블 후보와 핵심 조건 정의; 실행 SQL 없음 |
| Pleos Vehicle / NaviHelper SDK | **DOC-ASSUMED** | 사용자의 결정에 따라 실제 실행 검증은 추후로 미룸 |
| API 실제 HTTP 테스트 | NOT RUN | 서버 미구현 |
| Supabase RLS 테스트 | NOT RUN | DB 및 프로젝트 미연결 |

## 보완 필요 (우선순위순)
### P0 — 인증 모델 확정 전 SQL 생성 금지
1. 차량 앱 운전자 인증을 Pleos 계정과 연동할지, Supabase 익명/회원 인증을 사용할지 **REVIEW_REQUIRED**.
2. 픽업 참여자가 받은 `limitedAccessToken`이 어느 발급자/검증기에서 인증되고, Supabase RLS에 어떤 claim 또는 사용자로 매핑되는지 **미설계**.
3. 임시 링크는 토큰 해시 저장, 사용 횟수 동시성, 만료/회수 시 참가자 권한 폐기 방법을 확정해야 한다.
4. 익명 인증 사용 시 앱 재설치, 다른 운전자 탑승, 계정 교체 시 소유 데이터 보호 전략 결정 필요.

### P0 — API 계약 보완
1. 초대 동의의 `consent`는 단순 boolean이 아니라 반드시 `true`여야 참여 허용. 거부 시 이벤트/DB 레코드 생성 금지.
2. `PATCH /waiting-sessions/{id}`에서 요청 시간이 `startedAt` 기준인지 현재시각 기준 연장인지 명확히 정의.
3. `POST /pickup/participants/{id}/status`는 중복 `clientEventId` 요청을 멱등 처리하고, 성공 시 `revision` 또는 정렬 가능한 이벤트 식별자 반환.
4. `WaitingSession` 응답에 선택한 주차장과 계획 분(min) 등 화면 구성 필드의 필요성을 검토.
5. 서버 응답 401/403/404/409/410/429/502와 `ApiError` 코드 표준화 필요.
6. `GET /waiting-sessions/{id}/events?afterRevision=` 같은 재연결 보정 API가 필요할지 확인.

### P1 — 주차장 데이터 공급자
1. Provider 선정 및 라이선스/요금/좌표계/호출 제한 **REVIEW_REQUIRED**.
2. `availability=null`은 정보 없음. 실시간 빈자리·가격·운영시간을 만들어내지 않는다.
3. 예상 요금은 시간 단위, 무료 시간, 1일 최대요금, 요금 갱신 시각 등 제공자별 규칙을 검증한 뒤 계산한다.
4. API 키는 서버 보관. 캐시 TTL과 provider 장애 시 처리 정책 검토.

### P1 — 모드 구현 책임
- Work: `RemoteDesktopGateway`, 스트리밍/입력 기술검증. 일반 REST로 화면 전송 금지.
- Study: 집중시간·메모·할 일 로컬 우선; 동기화 필요 시 API 추가.
- Entertainment: Android Media3/패키지 API; 타사 재생 제어는 권한 확인 후.
- Rest: 타이머/오디오 로컬 우선; HVAC/시트는 SDK 지원/권한 검증 전 보류.
- Safety: 공식 호환성 문서 기반 설계는 가능하나 **실제 P 상태 판독 완료라고 주장하지 않는다**.

## 권장 진행 순서
1. **11/01:** 위 계약/인증 설계 검토와 decision log 작성; OpenAPI validator 및 auth 결정 후 SQL 확정.
2. **11/02:** 공급자 확정 후 주차장 API 구현(실제 API 키·계정 없으면 공급자 인터페이스와 계약 테스트까지만).
3. SDK 실제 에뮬레이터 검증은 사용자 요청에 따라 후속 작업으로 보류.

## 추적 상태
- [x] GitHub `dev` OpenAPI·DB 문서 정적 읽기
- [x] API path/ref 기초 검사
- [x] 검토 필요 항목 식별
- [ ] 공식 OpenAPI 파서/린터로 파일 검증
- [ ] 운전자 인증 방식·게스트 토큰 설계 승인
- [ ] Provider/요금 정책 승인
- [ ] RLS SQL 및 실제 권한 테스트
- [ ] SDK 앱 실행 검증(보류)
