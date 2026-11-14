# 제출 Release 검증 — 2026-11-14

## 기준·변경 범위

- 기능 소스 및 배포 커밋: `a7762db648a33a0478f0413888c8a673fd56b4c3`.
- 이전 로컬 Debug 전달은 [별도 기록](2026-11-14-review-handoff.md)이며 이 제출 APK와 구분합니다.
- 사용자가 전용 Release 서명키 생성, Render 무료 심사용 배포, 기존 Supabase 재사용, main/dev 정리와 업로드를 승인했습니다.
- 앱 기능·확정 홈·이미지·SDK·API 계약·인증 정책·DB/RLS는 변경하지 않았습니다. 별도 hosted 서버 진입점과 설정 검증, 서명 도구만 추가했습니다.
- 제출 정리의 최종 SHA는 이 문서를 포함한 Git 이력과 접수 메일에서 고정합니다. APK 빌드 기능 소스는 위 SHA입니다.

## APK

- `apk/Team_EX_PleosStayPlus_release.apk`, 전용 로컬 Release 인증서로 서명.
- applicationId `com.pleosstay.app`, versionName `1.0`, versionCode `1`, min SDK28 / target SDK37.
- SHA-256: `7de017a3b45dbdfa957aae15406383074d0b18f69fa30029bc0953ea50c0cdc1`.
- 인증서 SHA-256: `01b48ec78ad53b6afe89e3d4271d711419af0220486fd975c25edbc55ef87d46`.
- APK에 서버 HTTPS URL과 Supabase 공개 publishable 설정만 포함합니다. API 공급자 secret·서버 secret·Render API key·개인 서명키/비밀번호는 포함하지 않습니다.
- Release manifest에 debuggable/testOnly/cleartext 허용 플래그가 없음을 확인했습니다. Release 정식 가드·기존 PIN·안전 정책을 유지합니다.

## 실제 실행 결과

| 검사 | 결과 | 근거·한계 |
|---|---|---|
| Android Release | PASS | assembleRelease, R8, lintVital, packageRelease 성공 |
| Android JVM | PASS / 1 SKIPPED | 232개 중 231 통과, 실패·오류 0, Release 전용 검사 1 제외 |
| Release 서명·정렬 | PASS | apksigner verify 및 zipalign 16KiB 정렬 검사, ZIP CRC 검사 |
| 서버 비밀값·URL 점검 | PASS | 실제 로컬 비밀값을 출력하지 않고 APK 엔트리 검사, HTTPS 대상 포함 확인 |
| Backend 회귀·타입·명세 | PASS | 38/38, tsc, OpenAPI 3.1 참조 검사 |
| Web 회귀·타입 | PASS | 16/16 및 tsc |
| Render 배포 | PASS | Singapore Free 3개 서비스가 기능 소스 SHA로 LIVE. Web은 새 허용 도메인용 키로 재빌드 |
| 공개 HTTP 접근·차단 | PASS | health·웹·connect·복구 페이지, 미인증401, simulator404, .env404 등 8항목 |
| 실제 Parking 공급자 | PASS | 공공 테스트 좌표 기준 실제 API200, FRESH, 총16곳 / 요청3곳 반환. 실제 차량 위치가 아님 |
| Pickup 미인증 | PASS | 보호 snapshot401 |
| Supabase 인증 통합 | PASS | 실제 익명 Auth 기반 격리된 QA 주체. Mock 인증 사용 안 함 |
| 독립 Pickup | PASS | 생성·재시도, 공개 HTTPS 초대·60분 유효시간, 실제 웹 proxy 참가·상태·외부 계정 차단, 재발급 철회, 수동 완료 |
| Realtime 페어링 | PASS | 차량 8자리 코드, 차량·PC 양쪽 승인 이전 연결 차단 |
| 실제 Storage·파일 버전 | PASS | TXT 원본/수정본, 신규 차량 DOCX, PC 역할 API 다운로드·동일 바이트·SHA-256, 외부 계정 차단, 연결 철회 후 접근 차단 |
| Kakao SDK HTTP·번들 | PASS | 허용된 공개 웹 origin에서 SDK200, 참가 페이지와 JS8개 정상, 배포 키가 실제 served bundle에 존재 |
| Windows 전달본 빌드 | PASS | Windows x64 self-contained Release publish 성공, 공개 서버·Auth 설정 포함. GUI는 미실행 |
| QA 임시 Auth 계정 삭제 | FAIL | Realtime 기기와 연결된 임시 익명 사용자 삭제가 DB FK `23503`으로 실패. 마지막 실행의 3계정과 앞선 반복 QA의 일부 메타데이터가 남음. Pickup 전용 계정 삭제·테스트 Storage 파일 삭제는 성공 |

실제 클라우드 테스트는 비기밀 TXT/DOCX fixture만 사용했습니다. 차량·참가자의 GPS를 수집·전송하지 않았고 시뮬레이터 경로도 사용하지 않았습니다. 초대·연결은 종료/철회하고 테스트 파일은 삭제했습니다. 임시 계정 정리를 위해 DB 제약·RLS를 바꾸거나 기존 사용자 데이터를 삭제하지 않았습니다. 이 정리는 별도 검토가 필요합니다.

초기 QA 스크립트의 DELETE204와 철회 후 빈 목록200에 대한 기대값 오류는 실제 계약에 맞춰 수정한 뒤 다시 실행했습니다. 철회 후 실제 버전 다운로드404도 확인했습니다.

## 미실행·제약

- NOT RUN: 새 Release 설치·실행·Compose 조작·스크린샷·계측·실차 GPS/차량 신호·내비 콜백·차량 오디오. 에뮬레이터를 자동으로 켜지 않았습니다.
- NOT RUN: Windows GUI에서 새 전달 EXE로 실제 사용자의 수동 조작 및 이 Release 앱과의 화면 왕복. API 기반 실제 업/다운로드와 구분합니다.
- NOT RUN: 휴대전화 지도 실제 렌더링·GPS. SDK200은 실제 지도 화면을 검증한 결과가 아닙니다.
- `testReleaseUnitTest` 태스크는 현재 Android 구성에 없으므로 해당 스위트를 실행한 것으로 보고하지 않습니다.
- 무료 서비스 휴면·월 사용량 제한·공공데이터 초기 로딩 지연이 있습니다. 새 카드·유료 플랜·DB·Cloudflare 서비스를 추가하지 않았습니다.
- 기존 Rest→Home 선택 표시 계측 실패 이력은 앞선 검증 기록에 남아 있으며 이번에 재검증·해결했다고 주장하지 않습니다.

## 제출·설치

README의 APK 링크·실행 방법·PLEOS v2.1.0 API34·전체2560×1440/앱약1682×1210 규격을 확인합니다. 기존 Debug와 Release 서명이 다르므로 데이터 삭제 없이 별도 테스트 기기에 설치하세요. 서버용 키·Release 서명키는 저장소·제출 압축에 넣지 않습니다.
