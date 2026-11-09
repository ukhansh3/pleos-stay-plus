# Pleos Stay+

Pleos Connect에서 주차 장소를 찾고, 대기 세션 중 휴식·공부·업무를 이어가는 차량용 앱입니다.

## 현재 제출 범위

- **Waiting**에서 대기 시작·연장·복원을 관리합니다. 휴식·공부·업무를 전환하거나 끝내도 대기 세션은 별도로 유지됩니다.
- **Rest**는 개인 프리셋, 타이머, 배경음과 음량 설정을 제공합니다. 지원 차량에서는 충전 대기 중 목표 충전율에 맞춰 휴식을 마칠 수 있습니다.
- **Study**는 학습 자료를 읽고 메모를 남기며, **Work**는 차량에서 문서를 열람·편집하고 저장합니다.
- **Parking**, **Pickup**, **Realtime**은 주차장 검색, 픽업 공유, 차량과 PC 간 수동 파일 전송을 지원합니다.

## 검증 범위

- 로컬 검증에서는 Android Debug 단위 테스트 193개 통과, Debug·AndroidTest·Release APK 빌드, Compose Preview 7개를 확인했습니다. 기기 계측 테스트는 미실행입니다.
- 실제 차량 신호와 오디오 경로, PLEOS 분할 창, 차량과 PC 사이의 실제 전송은 이번 개편에서 기기로 검증하지 않았습니다.

- [`backend/`](backend/README.md): Node.js + TypeScript + Fastify Parking API
- [`android/`](android/README.md): Kotlin + Jetpack Compose 차량 앱
- [`docs/api/openapi.yaml`](docs/api/openapi.yaml): OpenAPI 3.1 계약
- [`docs/verification/2026-11-02-parking-api.md`](docs/verification/2026-11-02-parking-api.md): Parking 구현·검증 기록

공통 개발·안전 규칙과 기존 결정 기록은 [`AGENTS.md`](AGENTS.md) 및 [`DEVELOPMENT_GUIDE.md`](DEVELOPMENT_GUIDE.md)를 따릅니다.
