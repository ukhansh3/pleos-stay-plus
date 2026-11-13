# 2026-11-13 Pickup QA 결함 5건 수정·검증

대상: ukhansh3/pleos-stay-plus, dev, 실제 PC 폴더 C:\Users\ukhan\pleos-stay-plus.
시작 HEAD: 1dfa5b9324305679b6e1ef42634db5d8bedaf269. 원격 fetch 결과 0 behind / 기존 로컬 6개 ahead에서 시작했다.

## 수정 결과

| 문제 | 변경 | 로컬 검증 |
| --- | --- | --- |
| 참가자 CANCELLED 뒤 위치 접근 유지 | 메모리 저장소와 Supabase 어댑터 모두 기존 closeSession 종료 경로 사용. Supabase는 기존 CLOSE_SESSION RPC의 잠금·종료 트리거·접근 철회·좌표 삭제를 재사용. clientEventId를 종료 멱등 키로 전달 | 취소 후 guest snapshot/events 404, 소유자 세션 CANCELLED, 메모리 양측 위치 삭제, 재시도 동일 응답, 타인 요청 차단 |
| 안전 상실 후 늦은 첫 GPS 전송 | 첫 요청부터 locationJob에 포함. 지연 응답 전후 취소·현재 세션·안전 상태 검사. 종료 버튼을 누르면 서버 응답 전에 위치 작업 중단. API 실패가 UNKNOWN 차폐를 풀지 않도록 유지 | 첫/반복 GPS 지연, DRIVING/UNKNOWN, 늦은 비협조적 응답, 종료 응답 대기, 생성 실패 후 차폐 테스트 |
| null/빈 문자열 좌표의 (0,0) 저장 | latitude/longitude/accuracyMeters/confidence를 JSON number로 검증하고 강제 숫자 변환 제거 | 차량/참가자 두 위치 API에서 잘못된 타입 400, 정상 숫자와 nullable 선택 필드 200 |
| 완료 후 새 Pickup 시작 불가 | 종료 상태에서 새 시작 버튼 제공. 신규 시작 시 이전 QR·위치 표시·이벤트 상태·키·동기화 작업 정리. 세션 세대로 이전 응답 차단 | 완료/취소/연결 Waiting 종료 후 새 독립 Pickup, 중복 시작 방지 |
| 재발급 응답 유실 시 이전 QR 잔존 | 재발급 시작 즉시 이전 QR 비활성화. 실패 후 이전 QR 복원하지 않음. 같은 멱등 키로 재시도 | 대기 중/실패 후 QR 없음, 재시도 동일 키, 새 QR 수신 |

HTTP 경로·요청/응답 필드·기존 60분 초대·1인 참가 정책은 유지했다. OpenAPI에는 CANCELLED의 종료 의미를 명확히 적었다.

## 테스트

- PASS — Backend 전체 npm test: 34/34 (신규 3개 포함).
- PASS — Backend typecheck, OpenAPI YAML/로컬 참조 검사, build.
- PASS — 로컬 HTTP 응답 계약 19건. 잘못된 좌표 재현 결과 400/저장 안 됨. 취소 후 guest snapshot 404/위치 응답 없음.
- PASS — Android 전체 JVM: 228개 중 227 통과, 1 skipped, 실패/오류 0. PickupViewModelTest 14개 통과(신규 7개).
- PASS — Android assembleDebug. 실제 Kotlin 재컴파일·단위 테스트·APK 패키징 성공.
- PASS — Web 전체 npm test 16/16, typecheck.
- PASS — git diff --check. 환경 파일 Git ignore 확인 및 outgoing 텍스트의 지정 비밀키 패턴 검사.
- NOT RUN — 실제 Supabase RPC/RLS/Realtime 재검증. Supabase 어댑터는 모의 HTTP로 호출 내용·멱등 키·사용자 scope를 검증했고, 실제 Cloud 데이터는 수정하지 않았다.
- NOT RUN — PLEOS 에뮬레이터/실차/휴대폰 GPS/Windows GUI. 사용자 요청에 따라 에뮬레이터 실행·재시작 없음.
- NOT RUN — 이번 수정에서 Web production build는 실행 중 서비스 산출물 보호를 위해 생략. 웹 제품 소스 변경 없음.

## 변경 파일

- backend/src/pickup/routes.ts
- backend/src/pickup/memory-repository.ts
- backend/src/pickup/supabase-repository.ts
- backend/test/pickup.test.ts
- android/app/src/main/java/com/pleosstay/app/pickup/ui/PickupViewModel.kt
- android/app/src/main/java/com/pleosstay/app/pickup/ui/PickupScreen.kt
- android/app/src/test/java/com/pleosstay/app/pickup/PickupViewModelTest.kt
- docs/api/openapi.yaml
- 본 검증 기록

새 DB migration/RLS/권한·SDK·의존성·홈 디자인·Parking/Waiting/Work/Study/Realtime 제품 소스 변경 없음. 기존 승인된 종료 트리거가 설치된 DB를 전제로 한다. 이번에 다루지 않은 기능까지 전체 서비스 검증 완료로 간주하지 않는다.

기존 전체 감사 초안과 Next가 생성한 web/AGENTS.md는 로컬에 보존하고 이번 커밋에서 제외한다. 실제 .env·비밀키·로컬 QA 캐시는 Git에 포함하지 않는다.