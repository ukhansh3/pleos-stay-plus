# 문서 목차

처음 보는 담당자는 [검토 가이드](review/REVIEW_GUIDE.md)와 [설정·실행](review/LOCAL_SETUP.md)부터 확인하면 됩니다.

| 목적 | 문서 |
|---|---|
| 제출 Release·공개 서버 검증 | [2026-11-14 Release 검증](verification/2026-11-14-submission-release.md) |
| 이전 Debug 전달본의 테스트·제약 | [2026-11-14 검증 기록](verification/2026-11-14-review-handoff.md) |
| HTTP / LOCAL / SDK 구분 | [API 안내](api/README.md) · [모드 API 매트릭스](api/MODE_API_MATRIX.md) |
| Parking·Pickup 계약 | [OpenAPI](api/openapi.yaml) |
| 파일·버전·PIN·초안 계약 | [공통 문서 계약](contracts/DOCUMENT_WORKSPACE_CONTRACT.md) |
| Realtime API·Windows 연계 | [Realtime 로컬 구현](realtime/LOCAL-IMPLEMENTATION.md) |
| Study | [학습 기능 명세](features/STUDY_MVP_SPEC.md) |
| Work | [DOCX 편집기 명세](features/WORK_DOCX_EDITOR_SPEC.md) |
| Premium UI / Preview | [UI 기준](ui/PREMIUM_UI_IMPLEMENTATION_SPEC.md) · [Preview](ui/COMPOSE_PREVIEW_GUIDE.md) |
| DB·인증 검토 | [Supabase 안내](../supabase/README.md) |
| 개발·안전·승인 규칙 | [AGENTS](../AGENTS.md) · [DEVELOPMENT_GUIDE](../DEVELOPMENT_GUIDE.md) |

## 최신 수정 근거

- [문서 진입·PIN 세션](verification/2026-11-14-document-entry-pin-session.md)
- [Windows 양쪽 페어링 승인](verification/2026-11-14-windows-pairing-approval-fix.md)
- [Pickup 종료·취소·재발급 QA](verification/2026-11-13-pickup-qa-fixes.md)
- [Pickup 모바일 UI](verification/2026-11-13-pickup-mobile-premium.md)
- [Realtime·Pickup 실제 연결 QA](verification/2026-11-13-realtime-pickup-live-qa.md)

## 과거 문서를 읽을 때

날짜가 지난 문서에는 당시의 미구현 범위와 실패 기록이 남아 있습니다. 과거 계획을 현재 지원 기능으로 해석하지 말고, 이후 사용자 결정·최신 소스·검증 기록을 함께 확인하세요.

특히 초기 Waiting HTTP 초안과 Work PDF 편집 목표는 현재 실행 경로를 뜻하지 않습니다. Waiting은 Android 로컬이며, 현재 Work 편집은 DOCX입니다. Realtime 경로는 기존 서버 소스와 API 안내를 기준으로 확인합니다.
