# 담당자 검토 전달 검증 — 2026-11-14

## 기준과 변경 범위

- 시작 dev HEAD: `a59e52c59bc7df81735c223cd985f9b5fee1982f`.
- 확인한 원격 main: `123a6c8e8ba2a2b845b220da73bb82cec32ddd62`.
- 사용자가 두 저장소의 최신 dev 코드·규칙을 main에 반영하도록 승인했다. main의 과거 문서 이력은 병합으로 보존했다.
- main과 개발 가이드/OpenAPI 충돌은 사용자 확인 후 **최신 dev 내용 유지**로 해결했다. AGENTS·제품 정책·API 계약은 시작 dev와 동일하다.
- 이번 작업은 README·문서 목차·검토/실행 안내 갱신과 Debug APK 전달이다. 앱·Backend·Web·Windows 기능 소스, 확정 홈·로고·이미지·DB·RLS는 변경하지 않았다.
- 최종 커밋은 이 문서를 포함한 main의 Git 이력에서 확인한다. 로컬 APK 전달 폴더의 manifest에는 최종 전체 SHA와 APK SHA-256을 기록한다.

## 이번 실행 결과

| 항목 | 결과 | 근거 |
|---|---|---|
| Android Debug APK | PASS | assembleDebug 성공. 기존 동일 소스 작업은 UP-TO-DATE 재사용 |
| Android JVM | PASS / 1 SKIPPED | testDebugUnitTest 태스크를 --rerun으로 실제 재실행. 232개 중 231 통과, 실패·오류 0, Release 전용 Parking 검사 1 제외 |
| Backend 회귀 | PASS | 34/34 |
| Backend typecheck / build | PASS | TypeScript 검사와 dist 생성 |
| OpenAPI | PASS | 3.1 YAML·로컬 참조 검사 |
| Web 회귀 / typecheck | PASS | 16/16, TypeScript 검사 |
| Windows 로컬 통합 | PASS | 9개 결과 그룹. 양쪽 승인·C# HTTP·TXT/PDF/DOCX 원본/수정본·SHA-256·차량 새 DOCX·철회 차단 |
| APK 무결성·서명 | PASS | ZIP 검사, Android apksigner v2 서명 검증 |
| 비밀값 점검 | PASS | 현재 로컬 서버 비밀값과 지정 API 키가 tracked 파일·APK에 포함되지 않음. 실제 값은 출력하지 않음 |
| 앱·홈 보호 | PASS | 시작 dev 대비 Android 기능 소스·홈·에셋 및 Backend/Web/Windows 기능 소스 변경 없음 |
| 문서 상대 링크 / diff | PASS | 새 안내의 로컬 링크·git diff --check |

## 미실행·남은 검증

- NOT RUN: 에뮬레이터 실행·재시작·설치·계측·스크린샷, 실제 카메라·차량 SDK·오디오·내비 콜백. 사용자 성능 제약을 유지했다.
- NOT RUN: 새 Cloud 쓰기·DB 마이그레이션·RLS 변경·배포. 이번에는 격리된 로컬 테스트만 수행했다.
- NOT RUN: Web production rebuild. 실행 중인 웹 서비스 산출물을 보존했으며 웹 기능 소스는 변경하지 않았다.
- NOT RUN: 새 정식 서명 Release. 사용자는 담당자 검토·시연용 Debug를 선택했다.
- 이전 Windows 실제 연결·송수신은 사용자 확인이 있었으나 이번 작업에서 새 GUI 왕복은 하지 않았다.
- 문서 화면 간 PIN 유지 수정은 JVM 검증·기존 설치 기록이 있다. 사용자의 실제 Study/Work 화면 인수 확인은 아직 별도 항목이다.
- 기존 전체 production-entry UI 테스트의 Rest→Home 선택 표시 실패 기록은 [11/13 QA](2026-11-13-realtime-pickup-live-qa.md)에 남아 있다. 이번 작업에서 해당 계측 테스트를 재실행하거나 해결 완료로 변경하지 않았다.

## 전달본 성격

- applicationId: `com.pleosstay.app`; versionName `1.0`, versionCode `1`; minSdk28, targetSdk37.
- Debug 기본 API는 emulator→호스트의 Parking/Pickup3000, Realtime3012이다. APK만 설치해도 서버가 생기지는 않는다.
- Pickup 다른 휴대전화 접속에는 승인된 HTTPS 주소·실제 Auth 설정·지도 origin 허용이 필요하다. 임시 터널을 영구 서비스로 기록하지 않는다.
- 실제 서버 secret과 API provider 키는 APK·Git에 포함하지 않는다. Android용 Supabase publishable 설정은 기존 공개 클라이언트 설정이다.
- Work DOCX 공개 내보내기와 Realtime 수동 전송·PIN/안전 정책은 기존 그대로다.
- 일반 검토 문서는 [검토 가이드](../review/REVIEW_GUIDE.md)와 [실행 가이드](../review/LOCAL_SETUP.md)부터 읽는다.
