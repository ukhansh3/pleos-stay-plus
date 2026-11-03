# AGENTS.md — Pleos Stay+ Coding Agent Instructions

> 적용 대상: 레포 루트 및 모든 하위 디렉터리. **개발 중 운영 지침** (2026-11-02).  
> Android 프로젝트 생성·에뮬레이터 앱 빌드는 확인됨. 앱 실행 검증과 Pleos SDK 실제 메서드 호출 검증은 별개. 변경·배포 권한은 사용자 지시 범위에 한정한다.

## 1. 미션과 문서 우선순위
Pleos Stay+는 차량 대기 장소 탐색, 대기시간·차량상태 관리, 픽업 웹 연결, 업무/여가/휴식 모드, 안전한 운행 복귀를 지원한다.

구현 전에 다음 순서대로 확인한다.
1. 최신 사용자 지시(명시적 승인·범위·제약)
2. `DEVELOPMENT_GUIDE.md`의 제품·안전·API·의사결정 내용
3. 확정된 ADR / OpenAPI / 검증 기록
4. 공식 Pleos SDK 문서와 실제 프로젝트 소스·빌드 결과

문서 간 충돌은 코드로 조용히 해결하지 않는다. 충돌 내용을 설명하고 사용자 결정을 받는다. 공식 SDK 사실 관계는 실제 문서·동작을 기준으로 수정 제안한다.

## 2. 강제 규칙 — 임의 판단 금지
- **모호한 요구·제품 정책·벤더 선택·보안/인증 설정·유료 서비스·API 승인·디자인 방향은 사용자에게 질문한다.**
- 사용자가 결정하지 않은 DEC-XX 항목은 임의로 확정하지 않는다.
- 존재 여부를 확인하지 못한 Pleos SDK 클래스/메서드/permission/Gradle 좌표를 **만들어내지 않는다**.
- Android Automotive 일반 API와 Pleos SDK API를 혼동하지 않는다.
- 현재 프로젝트/SDK에서 검증하지 않은 기능을 '동작 완료'라고 선언하지 않는다.
- 사용자가 승인하지 않은 레포 생성, GitHub push, Notion 수정, 외부 배포, DB 삭제·초기화, 공개 범위 변경, 과금 서비스 활성화는 하지 않는다.
- 숨겨진 키·토큰·개인정보를 파일, 로그, PR, 이슈, 스크린샷에 노출하지 않는다.
- 수정 전 영향 범위와 기존 동작을 확인하며, 무관한 코드/문서를 대규모 변경하지 않는다.

## 3. 안전 우선 정책 (절대 준수)
- 정차 상태를 확인할 수 없는 경우 업무/영상 UI 잠금. **UNKNOWN은 PARKED가 아니다.**
- SDK 실패·권한 거부·리스너 해제·프로세스 복구 시 안전 게이트를 기본 차단한다.
- 주행 전환시 원격 화면 및 입력 이벤트 즉시 차단한다. 서버/PC 응답을 기다리지 않는다.
- 주행 중 HMI/Android Automotive UX 제한을 우회하는 코드를 작성하지 않는다.
- '불법 주정차 없음', '추돌 안전 보장' 같은 검증 불가 문구 사용 금지.
- 실제 차량 제어(공조·도어·시트 포함)는 별도 승인 및 공식 지원·권한 검증 전까지 작업하지 않는다.

## 4. 허용 도구·기술 원칙
- Android: Kotlin, Jetpack Compose, ViewModel, Repository, Coroutines/Flow.
- Pleos: 공개 SDK 문서로 확인 가능한 최소 권한 API만 통합. 도메인별 Capability → Get/Callback 순서. 콜백 실패·해제 처리.
- Web: Next.js, TypeScript, 접근성·폼 검증·오류 표시.
- Backend: Parking 검색은 Node.js + TypeScript + Fastify를 11/02 구현 기준으로 사용. Waiting/Pickup은 Supabase 익명 인증 + Postgres/RLS/Realtime을 MVP 결정으로 사용하되 서비스 연결·키 설정·배포는 별도 검증/승인을 따른다.
- Remote PC: Sunshine/Moonlight 후보. 라이선스·배포·보안·AAOS 호환성 검증 전 커스텀 스트리밍 엔진 개발 금지.
- 모든 외부 연동은 Gateway/Adapter로 격리. Fake/Unsupported는 테스트 또는 명시적 데모 모드에만.
- 라이브러리 버전은 실제 Maven/npm에서 확인 가능한 고정/잠금 정책 적용. 최신 버전이라는 이유로 자동 업데이트하지 않음.

## 5. 작업 시작 전 체크리스트
1. 요구사항 ID(F-XX), API-XX, DEC-XX와 직접 연관된 문서를 열어 확인.
2. 기술 검증이 필요한 경우 공식 출처 URL·SDK 버전·확인 일자 기록.
3. 구현 범위/비범위, 건드릴 파일, UI 상태, API 계약, 권한을 요약.
4. 미결정 사항이 결과를 바꾸면 **코딩 전 질문**. 답이 없으면 위험한 가정을 실행하지 말고 안전한 경계/정적 문서까지만 처리.
5. 기존 git 상태, 관련 파일, 빌드 스크립트, 프로젝트 규칙을 확인.

## 6. 작업 단위와 구현 표준
- 한 작업에는 명확한 사용자 시나리오와 완료 조건이 있어야 한다.
- 화면/UI → ViewModel → UseCase → Repository/Gateway → 네트워크/SDK 순으로 책임 분리.
- UI에는 인증/차량 SDK/SQL을 직접 삽입하지 않는다.
- API DTO와 도메인 모델을 분리한다. 서버 에러·타임아웃·재시도·중복 요청을 다룬다.
- 상태 전이는 domain 계층에서 검증하고 UI/DB 중 하나에만 흩어놓지 않는다.
- 네트워크 비동기 작업에 취소·스코프·타임아웃·스레드 규칙을 적용.
- 기본적으로 단일 책임의 작고 테스트 가능한 함수. 불필요한 추상화 및 추측 구현 금지.
- 임의의 UI 더미를 실데이터처럼 제시하지 않는다. 테스트 데이터는 명확히 라벨링.

## 7. SDK/플랫폼 검증 규칙
- API Reference에서 이름/인자/반환형/권한을 확인하고 소스 URL로 기록.
- Vehicle SDK는 callback 결과와 실제 capability를 확인. `DrivingMode`를 PRND 기어 정보로 추론 금지.
- `Vehicle.initialize()` 후 작업하고, 콜백 완료/리스너 정리 후 `release()`.
- NaviHelper는 초기화·리스너 등록·요청·해제 라이프사이클 준수.
- 공식 SDK가 제공하더라도 에뮬레이터와 실제 차량의 지원 상태가 다를 수 있다.
- 자동차 앱 UX 제한과 라이선스 문제는 우회하지 않고 사용자 결정 필요 사항으로 표시.

## 8. 서버·데이터·보안
- RLS 없는 공개 테이블에 개인 데이터 저장 금지.
- `service_role`, 서드파티 API secret, 초대 원문 토큰을 Git/클라이언트 번들에 넣지 않는다.
- 이메일·위치정보·이동 상태·로그는 최소화하고 수집/보존 정책 확정 전 프로덕션 수집 금지.
- 초대 URL 만료·취소·재전송·도난·다른 세션 조회 공격을 테스트.
- 데이터베이스 destructive migration, 백필, 권한 변경에는 목적·영향·복구 방안 제시 후 승인.
- 사용자 동의 없는 GPS 장기 추적·마이크 백그라운드 녹음 금지.

## 9. 테스트 및 진실한 보고
실행할 수 있는 환경이면 변경마다 관련 단위/통합 테스트, Android/웹 빌드, 가능한 에뮬레이터 E2E를 수행한다. 테스트가 불가능하면 이유를 적고 성공이라고 표현하지 않는다.

매번 확인:
- 정상/빈 결과/로딩/네트워크 장애/권한 거부
- 주행 전환 및 UNKNOWN 상태에서 화면 잠금
- 세션 종료·재시도·복원·중복 호출
- 픽업 웹 만료·권한·상태 역행
- 민감값이 로그/빌드 산출물에 남지 않는지

## 10. Git 작업 규칙
- 기본 브랜치·레포 공개/비공개·PR 정책은 사용자 결정 전 미확정.
- 사용자가 개발을 시작하도록 요청하면 기능별 브랜치를 제안하고 의미 있는 단위의 변경을 커밋.
- 자동으로 `main`에 강제 푸시하거나 `--force`, `reset --hard`, 광범위 삭제 작업 금지.
- 이슈·PR에는 요구사항 ID, 테스트 증거, 위험 및 후속 결정을 기록.
- 새 외부 의존성은 라이선스·크기·보안·호환성 근거를 포함해 승인 요청.

## 11. 에이전트 응답 형식
### 작업 시작 전에
- 목표 및 관련 F/API/DEC ID
- 구현 경계와 영향 파일
- 공식 문서/SDK 검증 여부
- 미결정 사항 질문

### 완료 후
- 변경 파일
- 구현 내용
- 실행한 테스트와 실제 결과
- 실행하지 못한 테스트와 이유
- 검증된 지원 범위/제약
- 남은 문제 및 사용자 결정 필요 사항

## 12. 결정 보류·에스컬레이션
대표적인 확인 대상: 지도·주차장 공급자, 인증 방식, 초대 링크 수명, 정확한 차량 신호 지원, 원격 PC 통합 수준, Pleos UX 정책, 실제 배포 목표, 프로젝트 일정. `DEVELOPMENT_GUIDE.md`의 DEC-01~12를 참고. 사용자에게 질문할 때는 **선택지별 장단점과 안전한 기본값**을 설명하되 답을 받기 전에 기본값을 확정해 코드로 박지 않는다.

## 13. 현재 지시(2026-11-02)
**11/02 Parking API 구현 착수 승인.** Kotlin/Compose Android 프로젝트가 생성되어 Pleos Connect Emulator v2.1.0에서 기본 앱 실행, Gradle debug 빌드 성공을 사용자 화면에서 확인했다. Vehicle/NaviHelper 의존성의 Gradle 빌드 성공과 실제 차량 SDK API 동작은 구분한다. 현재 작업은 Parking API 구현과 관련 문서/계약 동기화 범위에 한정된다. 외부 서비스 배포·유료 기능·DB destructive 작업·주요 제품 정책 임의 결정은 승인되지 않았다. GitHub main/dev 문서 변경은 사용자가 별도로 승인했다. 다른 모듈은 일자별 후속 승인 범위에 맞춰 구현한다.


## 14. 모드별 책임·API-first 작업 규칙 (v0.3)
- `DEVELOPMENT_GUIDE.md` 14~18장을 반드시 선독한다. Work/Study/Entertainment/Rest를 단일 `mode` 필드 구현으로 완료 처리하지 않는다.
- `WORK-`, `STUDY-`, `ENT-`, `REST-`, `PARK-`, `WAIT-`, `PICK-`, `VEH-`, `LIFE-` 기능 ID의 입력·출력·상태·실패 처리·테스트를 각각 추적한다.
- `/api/v1/waiting-sessions`가 canonical이며 `/api/v1/sessions` 같은 비승인 별칭을 만들지 않는다.
- 실제 HTTP API와 Android LOCAL, Pleos SDK, Android Media, 원격 STREAM 프로토콜을 구분한다. 내부 메서드마다 REST API를 만들지 않는다.
- 계약 우선: OpenAPI 3.1 초안 → 사용자 승인 → 계약 테스트 → 구현. UI는 typed client와 mock server를 사용해 병렬 진행 가능.
- 차량 상태 SDK, NaviHelper, PC 스트리밍은 11/01 통합 게이트에서 실제 확인을 시도한다. 실패 기능을 가짜 성공으로 치환하지 않는다.
- 차량이 주행 중이거나 상태 UNKNOWN이면 업무·영상·휴식 UI를 잠근다. 원격 입력 차단은 서버 요청 성공을 기다리지 않는다.
- 타사 미디어 제어, 원격 PC 내장, 공조·시트 제어, GPS 동의·추적 등은 결정 미완료 상태에서 구현 금지.
- 영속/민감 데이터 수집, 초대 만료·보존, 인증 공급자는 승인 전 기본값으로 고정하지 않는다.
- 병렬 에이전트는 파일 ownership 경계를 따르고 공통 DTO/API를 독단적으로 수정하지 않는다.
- 변경 보고에 `검증됨/미검증/검토 필요`를 명시하며, 시연에서 실제 작동하지 않는 기능은 작동한다고 주장하지 않는다.

## 15. 공식 Pleos 플랫폼·SDK 준수 (2026-11-02)
- 앱 개발은 현대차그룹 공모전 개발 가이드, Pleos 공식 SDK 문서, 해당 SDK의 권한·지원 범위, Android Automotive UX 및 주행 제한 정책을 준수한다. 일반 Android/Kotlin/Compose와 서버 구현은 허용되나 **차량 시스템에 대한 접근은 공식 문서에서 허용한 API만** 사용한다.
- 비공개/숨겨진 API, 리플렉션·시스템 서비스 우회, 비공식 차량 CAN/센서 접근, 권한 상승, 서명/루팅 우회, 주행 중 HMI 제한 무력화 및 검증 없는 코드 생성 금지.
- SDK 함수 호출 전 공식 문서의 클래스·시그니처·권한·버전 및 emulator/실차 지원 차이를 검증하고 출처와 결과를 기록한다. 공식 문서 확인만으로 동작 완료를 선언하지 않는다.
- 미지원 기능은 SDK_VERIFY 또는 UNSUPPORTED로 표시하고 정상 동작인 것처럼 mock/placeholder를 배포하지 않는다. 규정 해석이 불명확하거나 구현이 이를 벗어날 수 있으면 REVIEW_REQUIRED로 두고 사용자에게 먼저 확인한다.
- Parking 장소 추천은 합법적 이용 근거가 확인된 대상에 한정한다. 직접 선택한 도로변 대기 세션은 Waiting이 담당한다. Waiting Safety Monitor는 Waiting 모듈 내 Android 로컬에 두며 외부 서버 응답에 의존하지 않고 차량 이동 시 UI를 잠근다.
- 오늘 기준 Parking 문서: https://app.notion.com/p/40cb74b2b90581cfb9f2e20d22428008 · 계약은 docs/api/openapi.yaml과 일치시킨다.

## 일정 업데이트 (2026-11-02)
- 11/01: 설계·SDK/원격 PC 사전검증·DB·인증 기반을 하루에 통합하는 목표. 수행하지 못한 검증은 반드시 미검증으로 표시.
- 11/02: Parking API부터 개발 착수, 이후 Waiting → Pickup → Realtime → Work → Study → Entertainment → Rest → Frontend 통합 순으로 진행.
- 세부 날짜와 완료 기준은 DEVELOPMENT_GUIDE.md 17장을 우선한다.

## 16. Pickup 구현 지시 (2026-11-03)

- Issue #5~#8의 Pickup 개발이 승인되었다. 최신 Pickup 노션 명세와 `DEVELOPMENT_GUIDE.md` 21장, OpenAPI v0.3을 우선한다.
- Pickup은 Waiting 없이 독립 실행한다. Waiting은 기존 Android Room/Safety Monitor를 유지하며 서버 Pickup 세션의 필수 부모로 만들지 않는다.
- 운전자와 참가자는 각각 Supabase Anonymous Auth JWT를 자동 획득한다. 기능이 비활성화된 환경은 `BLOCKED`로 보고하고 인증 우회나 공통 고정 토큰을 만들지 않는다.
- 운전자 Android/에뮬레이터 인증은 로그인 UI 없이 별도 Supabase Anonymous Auth 세션을 사용한다. Pleos 플랫폼 로그인이 Pickup API 인증을 대신한다고 가정하지 않는다.
- Supabase migration/RLS SQL은 저장소에 작성하되 사용자 별도 승인 없이 Cloud 프로젝트에 적용하지 않는다.
- Android 익명 인증은 공식 Supabase Auth REST 경계로 구현하고, QR은 ZXing Core를 사용한다. 확인되지 않은 Pleos TTS API는 호출하지 않고 `SDK_VERIFY`로 남긴다.
- Web은 Kakao Maps JavaScript SDK를 사용하되 실제 키는 `.env.local`에만 둔다. 허용 도메인·활성화·쿼터 확인 전 실제 지도 검증 완료를 선언하지 않는다.
- Backend, Android, Web은 독립 작업 브랜치와 파일 ownership을 사용한다. 공통 OpenAPI·개발 가이드·통합 QA 문서는 총괄 에이전트만 수정한다.
- 기존 Parking·Waiting 회귀를 검증하고, main 병합·외부 배포·유료 기능 활성화·Supabase Cloud migration 실행은 하지 않는다.
- 활성 Pickup은 참가자 1명, 신규 참가 링크 60분, 재발급 시 기존 참가자 접근 철회, 최신 위치만 저장, 종료 시 위치 삭제를 강제한다. QR 스캔만으로 위치 접근을 승인하지 않으며 참가 동의와 UID 바인딩을 별도로 확인한다.
