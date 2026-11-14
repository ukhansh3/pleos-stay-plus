# 담당자 검토 가이드

## 검토 기준

이번 `main`은 최신 `dev`의 기능 코드와 개발 규칙을 보존한 기준입니다. 구조를 설명하기 위해 소스 폴더를 대규모로 옮기거나 API를 바꾸지 않았습니다. 확정 홈·로고·이미지와 기존 사용자 데이터 정책도 유지합니다.

## 1. 앱 전체 흐름

1. [MainActivity](../../android/app/src/main/java/com/pleosstay/app/MainActivity.kt)에서 앱 초기화·화면 연결을 확인합니다.
2. [Premium UI](../../android/app/src/main/java/com/pleosstay/app/ui/premium/)에서 공통 셸·내비게이션·화면 표현을 확인합니다.
3. [ActivityUiController](../../android/app/src/main/java/com/pleosstay/app/ui/activity/ActivityUiController.kt)와 [modes](../../android/app/src/main/java/com/pleosstay/app/modes/)에서 시작·전환·복구 흐름을 확인합니다.
4. [waiting/safety](../../android/app/src/main/java/com/pleosstay/app/waiting/safety/)에서 정차 확인과 UNKNOWN 차단을 확인합니다.

UI는 상태를 보여주고 명령을 전달합니다. 인증·저장·SDK 접근은 해당 기능의 ViewModel/Controller·Repository/Gateway에서 처리합니다. 화면에 표시됐다는 이유만으로 서버 저장이나 차량 SDK 동작 성공으로 간주하지 않습니다.

## 2. 기능별 시작점

| 확인할 기능 | Android 시작점 | 서버·다른 클라이언트 | 검토할 동작 |
|---|---|---|---|
| Parking | [parking](../../android/app/src/main/java/com/pleosstay/app/parking/) | [Parking service](../../backend/src/service.ts) · [provider](../../backend/src/provider.ts) | 위치 출처·필터·요금 UNKNOWN·상세·길 안내 |
| Waiting | [waiting](../../android/app/src/main/java/com/pleosstay/app/waiting/) | Android Room / LOCAL | 화면 이동과 종료 구분·복구·안전 |
| Pickup | [pickup](../../android/app/src/main/java/com/pleosstay/app/pickup/) | [backend/src/pickup](../../backend/src/pickup/) · [web](../../web/) | 독립 세션·1명 참가·재발급·종료 후 접근 차단 |
| Realtime | [realtime](../../android/app/src/main/java/com/pleosstay/app/realtime/) | [서버](../../backend/src/realtime/) · [Windows](../../windows/StayPlus.Desktop/) | 코드·양쪽 승인·PIN·전송·버전·SHA-256 |
| Rest | [rest](../../android/app/src/main/java/com/pleosstay/app/rest/) | Android LOCAL / 공식 SDK 어댑터 | 프리셋·타이머·포커스 상실·알람 |
| Study | [study](../../android/app/src/main/java/com/pleosstay/app/study/) | [documents](../../android/app/src/main/java/com/pleosstay/app/documents/) | 실제 자료 열람·옆 메모·버전별 진도 |
| Work | [work](../../android/app/src/main/java/com/pleosstay/app/work/) | 기존 자료함 / 로컬 OOXML | 문단·서식·표·이미지·초안·DOCX 내보내기 |

공통 문서 인증과 PIN 유지 경계는 [workspace](../../android/app/src/main/java/com/pleosstay/app/workspace/) 및 [RealtimeEntry](../../android/app/src/main/java/com/pleosstay/app/realtime/RealtimeEntry.kt)를 함께 봅니다. Work DOCX 내보내기 이후의 PC 전달은 Realtime 화면의 명시적 수동 전송입니다.

## 3. 계약과 구현 대조

- [OpenAPI](../api/openapi.yaml)는 Parking/Pickup 계약입니다. 초기 Waiting HTTP 초안은 현재 서버에 자동으로 존재하지 않습니다.
- Realtime의 실제 경로·접근 통제는 [routes.ts](../../backend/src/realtime/routes.ts)와 관련 테스트를 확인합니다.
- Study/Work 내부 동작은 HTTP API가 아니라 [LOCAL 및 기존 Realtime 계약](../api/MODE_API_MATRIX.md)을 사용합니다.
- SDK 호출은 해당 Gateway와 [SDK 검증 목록](../verification/pleos-sdk-checklist.md)을 대조합니다.

## 4. 추천 시연 순서

1. 정차 상태를 확인한 뒤 Parking 검색·장소 선택·Waiting 시작
2. Waiting에서 Rest 또는 Study/Work 진입 — 모드 종료와 Waiting 종료 구분
3. Realtime에서 PC 연결 코드 발급 → Windows 입력 → 양쪽 승인 → 실제 자료 업로드·다운로드
4. 같은 전면 세션에서 Study/Work로 이동 → 문서 선택·메모 또는 DOCX 편집
5. DOCX 내보내기 확인 → Realtime에서 차량 파일 선택·전송 승인 → PC에서 수동 다운로드
6. Pickup 독립 시작 → 실제 QR → 휴대전화 참가 동의 → 위치·상태 → 수동 완료
7. 잠금·백그라운드·UNKNOWN·연결 철회에서 문서·전송·위치 공유 차단 확인

공개 웹 주소나 인증 설정이 없는 경우 해당 단계는 BLOCKED입니다. 서버 미연결을 빈 자료함 성공 상태로 평가하지 않습니다.

## 5. 테스트와 증거

- [Android JVM 테스트](../../android/app/src/test/)와 [계측 테스트](../../android/app/src/androidTest/)
- [Backend 테스트](../../backend/test/) · [Web 테스트](../../web/test/) · [Windows 로컬 테스트](../../windows/StayPlus.LocalTests/)
- [이번 검증 결과](../verification/2026-11-14-review-handoff.md): 실행 결과와 NOT RUN 구분
- [문서 엔진 PoC](../../tools/docx-engine-poc/README.md): 앱 구현과 격리된 기술검증의 차이

실제 차량 신호·스피커 출력·NaviHelper 수락·모바일 지도 허용 도메인은 자동 테스트만으로 검증되지 않습니다.
