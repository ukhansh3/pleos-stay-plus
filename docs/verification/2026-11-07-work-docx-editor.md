# Work 자체 DOCX 편집기 검증 — 2026-11-07

## 기준과 결정

- 작업 저장소: `C:\Users\ukhan\pleos-stay-plus`, 브랜치 `dev`.
- 시작 HEAD: `a5719d47f766de10dde4f15208ef4b23e25497fa`. 최종 커밋 해시는 이 보고서를 포함한 dev 커밋 및 최종 응답으로 확인한다.
- `main` 기준 해시: `0ea77f3394b74cd873055b2c4358893ed6e95653`; 이번 작업에서 변경하지 않는다.
- 최신 사용자 지시가 이전 PDF+DOCX+업로드 계획보다 우선한다. **PDF 편집·Realtime 업로드는 제외**한다. 기존 서버/API/DB/RLS/ModeStartManager/Study/Rest 기능은 재구현하지 않는다.
- 사용자 승인: 기존 인증된 Work 프로필에 로컬/새 DOCX 초안 보관; 암호화 저장 후 연결 철회/사용자 전환 시 잠금·보관 및 명시적 삭제; POI/API34 PoC 후 **자체 OOXML 선택**; **명시적 앱 내 확인 후** 공개 `Download/StayPlus` 내보내기.
- root/work AGENTS, DEVELOPMENT_GUIDE, Work/문서 계약, 기존 엔진·Study/Work 검증 기록과 현재 코드를 확인했다. 이전 Aspose ART VerifyError를 우회하지 않았다.

## 엔진 선정과 실제 PoC

[Microsoft WordprocessingML 구조](https://learn.microsoft.com/en-us/office/open-xml/word/structure-of-a-wordprocessingml-document)는 본문·문단·run·텍스트 및 별도 패키지 파트를 정의한다. 이번 구현은 그 구조의 지원 부분만 수정하고 나머지 파트를 보존하는 Kotlin/Java 엔진이다. Open XML SDK의 .NET API를 Android API로 가장하거나 새 Pleos SDK를 만들지 않았다.

| 후보 | API34 실제 결과 | 선택 판단 |
| --- | --- | --- |
| Apache POI XWPF 5.4.1 | PASS: 생성, 한글/문자 서식, 기존 표 셀, 이미지 교체, 저장·재열기. 최초 소형 합성 문서 probe 10,921ms | 일반 JVM 결과를 근거로 가정하지 않고 별도 Android 앱에서 실행했다. 사용자 최종 선택에서 제외. |
| 자체 OOXML 초기 probe | PASS: 생성, 기존 한글 범위/서식 수정, 표 행·열·셀, 이미지 교체/추가/크기, 미변경 파트 보존, 내보내기·재열기. 최초 probe 1,248ms, 관측 heap delta 2,669,296bytes | 사용자 승인 후 본 앱 구현. 신규 제3자 편집 라이브러리 없음. |

이 수치는 단일 소형 fixture·초기 실행 관측값이며 공정한 성능 비교, 대형 문서 메모리 상한 또는 모든 Word 문서 호환성을 입증하지 않는다. POI는 [Apache-2.0](https://poi.apache.org/legal.html)이며 XMLBeans 등 전이 의존성이 있다. 격리된 도구 프로젝트에만 사용하고 본 앱 Gradle에는 추가하지 않았다. 도구 APK는 배포 대상이 아니며 채택·배포 시 license/NOTICE와 전이 라이선스를 별도 보존·검토해야 한다.

재현 코드: `tools/docx-engine-poc/`. 그 안의 엔진 사본은 초기 PoC 스냅샷이다. 최종 검증은 본 앱의 현재 엔진을 사용한다.

## 구현 구조

```text
MainActivity → RealtimeEntry의 기존 PIN gate
              └ ProtectedWorkWorkspace → WorkScreen → WorkViewModel
                                                    └ DocxEditorController
                                                       ├ DocxDocumentEngine / OoxmlDocxEngine
                                                       ├ WorkDraftRepository → 기존 암호화 record store
                                                       └ DocxFileGateway → SAF / private FileProvider / 승인된 Downloads
```

- 상태/ActivityResult 등록은 PIN 화면 바깥에서 구성할 수 있지만, **실제 Work 화면을 반환한 composable은 기존 RealtimeLockedScreen 안에서만 호출**한다. PIN 미검증/foreground 상실 시 표시되지 않는 것을 실제 기존 PIN 저장·검증 UI로 시험했다.
- UI는 ZIP/XML, SQL, 인증·차량 SDK를 직접 조작하지 않는다. 엔진은 문서 편집/직렬화, Controller는 명령·직렬 실행·이력·저장, ViewModel은 모드·접근·오류 상태를 담당한다.
- Work는 기존 ModeStartManager의 Ready일 때만 시작하며 종료해도 Waiting/페어링을 종료하지 않는다. 다른 모드 정책은 수정하지 않는다.
- 같은 인증 사용자·연결의 로컬 문서를 구분한다. 원격 문서는 기존 정확한 file/version 권한·해시 검증 경로를 추가로 재사용한다. 새 로컬 문서에 가짜 서버 file/version ID를 만들지 않는다.

## 지원 범위

| ID | 구현 | 제약 |
| --- | --- | --- |
| DOCX-01 | 빈 문서·한글 본문, 문단 추가/삭제, 실제 DOCX 생성 | 제목도 일반 문단으로 입력. 입력란 줄바꿈은 같은 문단의 line break이고 별도 문단은 추가 버튼 사용. |
| DOCX-02 | 기존 run 구조 기반 범위 편집, 이웃 run 속성·미수정 패키지 보존 | 링크/책갈피/인라인 개체가 섞인 문단의 직접 텍스트 편집 제한. 전체 문자열로 평탄화 재생성하지 않음. |
| DOCX-03 | 글꼴명, 크기, 굵게/기울임/밑줄, RGB, 좌/중/우 정렬 | 실제 OOXML 직접 속성 반영. 기기 미설치 글꼴은 미리보기 대체 글꼴이며 글꼴 임베딩/정확한 Word 레이아웃은 미지원. |
| DOCX-04 | 단순 표 생성, 행·열 추가, 셀 텍스트·배경 | 병합/중첩/불규칙 그리드는 읽기 전용. 일부 writer의 누락 tblGrid는 검증된 직사각형 셀 수로만 보완하며 원래 없던 열 너비를 추정하지 않음. |
| DOCX-05 | PNG/JPEG 삽입·copy-on-write 교체·크기, 관계·ContentType 유지 | 새 이미지 4MiB/16MP 제한. 실제 Pleos에서는 시스템 이미지 선택 UI가 없어 사용자 파일 선택 경로는 BLOCKED. 엔진·Controller의 실제 이미지 변경/출력은 기기 및 독립 파서로 검증. |
| DOCX-06 | 실행 취소/다시 실행, 변경마다 암호화 원자적 초안 저장, 재실행 복원 | 이력 20개/32MiB, 이력은 메모리에만 보관. 재시작 후에는 최신 문서 snapshot 복원. |

미지원 구조를 Word와 동일하게 렌더링한다고 주장하지 않는다. 서명·매크로·보호·알려진 변경 추적/필드/콘텐츠 컨트롤/수식·임베디드/복잡한 표는 제한 또는 읽기 전용이다. 머리글/바닥글·스타일·기타 미수정 파트는 유지하지만 그 전용 편집 UI는 없다. 아직 시험하지 않은 모든 복합 구조를 안전하게 처리한다고 보장하지 않는다. 전체 상용 Word 호환 corpus와 추가 구조 검출은 후속 과제다.

## 저장과 안전

- 저장 버튼은 기존 Android Keystore/AES-GCM/AtomicFile 기반 앱 전용 초안을 저장한다. 로컬 기록은 `docx:` namespace로 기존 원본 보관 기록과 구분한다.
- 다른 이름으로 저장: 실제 picker가 있으면 ACTION_CREATE_DOCUMENT; 없으면 공개 Downloads 확인창 또는 앱 전용 임시 파일을 제시한다.
- 이 Pleos 이미지에서 실제 해결된 컴포넌트는 `com.android.car.frameworkpackagestubs/.Stubs$DocumentsUIStubWithResult`. intent resolve 성공만으로 정상 SAF라고 처리하지 않는다. 파일 관리자 설치나 제한 우회는 하지 않았다.
- [Android 공유 저장소](https://developer.android.com/training/data-storage/shared/media#toggle-pending-status)의 MediaStore/IS_PENDING으로 새 파일만 작성한다. 크기·SHA-256·ZIP/XML/엔진 재열기 검증 후 공개한다. 실패하면 이번 요청이 만든 URI만 정리하며 정상 초안/이전 파일은 유지한다.
- 공개 저장 확인에는 앱 밖 접근 및 잠금/앱 삭제 후 잔존을 안내한다. 취소로는 파일을 작성하지 않는다. 공개 파일은 사용자 승인에 따라 잠금 후에도 남는다.
- private 임시 파일은 [FileProvider](https://developer.android.com/reference/androidx/core/content/FileProvider)의 좁은 cache 경로만 사용하고 자동 URI grant는 없다. 잠금/화면 종료 시 파일·grant 정리. 암호화 초안은 유지한다.
- DRIVING/UNKNOWN/PIN 거부/foreground 상실/타이머 만료 시 내용·이력 차폐, IO 취소·출력 권한 재확인. 이미 수락된 변경의 암호화 원자적 보관만 마무리할 수 있다. 접근 권한을 새로 부여하는 경로는 아니다.
- 문서 화면의 기존 FLAG_SECURE를 유지한다. 실제 문서/비밀값 로그·스크린샷은 만들지 않았다.
- XML DTD/entity/외부 fetch 차단, ZIP 경로·중복·크기/팽창량·XML 깊이/노드 제한. 잘못된 입력은 마지막 정상 초안을 대체하지 않는다.
- 프로세스가 공개 파일 게시 중 종료되면 provider의 미게시 pending 행이 남을 수 있다. 이전 정상 파일/초안은 유지되지만 비정상 종료 후 pending 행 정리의 장시간 검증은 NOT RUN.

## 실제 검증 결과

| 항목 | 상태 | 증거 / 한계 |
| --- | --- | --- |
| Debug APK / AndroidTest APK 빌드 | PASS | `:app:assembleDebug :app:assembleDebugAndroidTest`, Android Studio JBR, 최종 빌드 성공 |
| Android 전체 JVM 테스트 | PASS | 125개, failures=0/errors=0. 기존 모드·Work·Study·Parking/Waiting/Pickup/Realtime 테스트 포함 |
| Backend 회귀 | PASS | `npm test` 28개. 서버 소스/계약 변경 없음 |
| Pleos API34 통합 instrumentation | PASS 13 / BLOCKED 1 | 최종 suite 87.034초, runner OK (14 tests). PIN1 + DOCX Compose1 + 외부 DOCX1 + 안전 ViewModel1 + Study3 + 실제 암호화 store1 + Realtime loopback4 + Waiting DB1 = 13 PASS. 실제 SAF1은 assumption skip이며 PASS로 합산하지 않음 |
| 프로세스 실제 종료 후 초안 복원 | PASS | 별도 seed (2.553초) → am force-stop --user 10 → verify (2.186초). 실제 암호화 파일에서 한글/표 복원 후 테스트 기록 정리. 하나의 시나리오를 두 instrumentation invocation으로 검증 |
| 최종 앱 DOCX 독립 파서 | PASS | python-docx/zipfile/ElementTree/Pillow: ZIP/XML, 한글·범위 서식·글꼴/크기/색/정렬, 표/속성 순서, 이미지 픽셀·크기, 외부 writer 원본의 미변경 7파트 byte-identical |
| 시스템 SAF·로컬 문서/이미지 picker | BLOCKED | 실제 시스템 DocumentsUI stub. 정상 저장/선택 UI 없음. 해결 컴포넌트와 skip 이유 확인 |
| 명시적 Downloads 내보내기 | PASS | 실제 Compose 확인창 취소/승인, MediaStore 새 DOCX, 해시/구조/재열기. 최종 suite에서도 재검증 |
| 한글 입력 | PASS (Compose 입력 연결) | 실제 TextField 입력·범위 선택·서식. 물리 키보드/실제 IME 조합 입력까지 확인한 것은 아님 |
| Word/LibreOffice 시각적 렌더·광범위 corpus | NOT RUN | 독립 파서 검증은 실제 Word 앱 페이지 배치/서식 상속 재현을 증명하지 않음 |
| 실 페어링 Windows→Cloud→차량 자료 열기 | NOT RUN | 기존 API·다운로드/버전/해시 어댑터 재사용 및 loopback 회귀. 실 사용자 인증/서버 업로드를 새로 수행하지 않음 |
| PDF 편집 / Realtime 업로드 | 범위 제외 | 구현/자동 전송 없음 |

기기 테스트의 인증·차량 상태 입력은 **androidTest 전용** gateway이며 실제 Controller/ViewModel/Compose/Room/Keystore/provider를 사용한다. 실제 차량 신호·실 계정 Cloud·전체 ModeStart/PIN/다운로드를 하나의 실서비스 시나리오로 통과했다고 주장하지 않는다. 기존 PIN gate 자체는 별도 실제 UI 검증이다.

초기 검증 실패를 숨기지 않는다: 누락 tblGrid로 독립 python-docx 표 접근이 실패한 뒤 직사각형 구조 보완 및 회귀 테스트를 추가했다. 실제 SAF 저장 버튼 부재도 정상 성공으로 대체하지 않고 BLOCKED로 남겼다. 최종 결과는 수정 후 재실행 값을 기준으로 한다.

최종 합성 출력(로컬 ignored `tools/docx-engine-poc/output/main/`):
- `edited.docx`: 3,019bytes, SHA256 `470ed7857ccc7a9917583f77aafc8a62fda0889b3631f1b3a8fe32d167a89864`.
- `external-edited.docx`: 3,185bytes, SHA256 `fe7c27cea93f92b5b2a127c85d2dd546d41f3a61790bec705df53139a8619d3d`.
- `independent-verification.json`: 검사 항목·해시·PASS 증거. 시험용 문서만 사용했으며 실행할 때 UUID/ZIP 시간으로 해시는 달라질 수 있음.

## 변경 파일

### 본 앱
- `android/app/src/main/AndroidManifest.xml`
- `android/app/src/main/java/com/pleosstay/app/MainActivity.kt`
- `android/app/src/main/java/com/pleosstay/app/realtime/RealtimeEntry.kt`
- `android/app/src/main/java/com/pleosstay/app/work/WorkDraftRepository.kt`
- `android/app/src/main/java/com/pleosstay/app/work/WorkModels.kt`
- `android/app/src/main/java/com/pleosstay/app/work/WorkScreen.kt`
- `android/app/src/main/java/com/pleosstay/app/work/WorkViewModel.kt`
- `android/app/src/main/java/com/pleosstay/app/work/DocxDraft.kt`
- `android/app/src/main/java/com/pleosstay/app/work/docx/DocxDocumentEngine.kt`
- `android/app/src/main/java/com/pleosstay/app/work/docx/DocxEditorController.kt`
- `android/app/src/main/java/com/pleosstay/app/work/docx/DocxEditorPanel.kt`
- `android/app/src/main/java/com/pleosstay/app/work/docx/DocxFileGateway.kt`
- `android/app/src/main/java/com/pleosstay/app/work/docx/DocxPickerHost.kt`
- `android/app/src/main/java/com/pleosstay/app/workspace/ProtectedWorkWorkspace.kt`
- `android/app/src/main/res/xml/work_docx_paths.xml`

### 테스트
- `android/app/src/test/java/com/pleosstay/app/work/DocxEngineTest.kt`
- `android/app/src/androidTest/java/com/pleosstay/app/work/DocxEditorDeviceTest.kt`
- `android/app/src/androidTest/java/com/pleosstay/app/work/DocxExternalDocumentDeviceTest.kt`
- `android/app/src/androidTest/java/com/pleosstay/app/work/DocxProcessDeviceTest.kt`
- `android/app/src/androidTest/java/com/pleosstay/app/work/DocxSafPickerDeviceTest.kt`
- `android/app/src/androidTest/java/com/pleosstay/app/work/DocxSafetyBindingDeviceTest.kt`
- `android/app/src/androidTest/java/com/pleosstay/app/work/WorkPinGateDeviceTest.kt`
- `android/app/src/androidTest/assets/work/poi-generated-fixture.docx`, 같은 경로의 `README.md` (합성 fixture 출처)
- `tools/docx-engine-poc/{.gitignore,README.md,build.gradle.kts,gradle.properties,settings.gradle.kts,verify_main_exports.py}`
- `tools/docx-engine-poc/app/{build.gradle.kts,src/main/AndroidManifest.xml}`
- `tools/docx-engine-poc/app/src/main/java/com/pleosstay/docxprobe/{PoiProbe.java,OoxmlProbe.kt}`
- `tools/docx-engine-poc/app/src/main/java/com/pleosstay/app/work/docx/DocxDocumentEngine.kt` (초기 PoC 사본)

### 계약·기록
- `DEVELOPMENT_GUIDE.md`
- `docs/contracts/DOCUMENT_WORKSPACE_CONTRACT.md`
- `docs/features/WORK_DOCUMENT_MVP_SPEC.md`
- `docs/features/WORK_DOCX_EDITOR_SPEC.md`
- 이 검증 보고서

## 직접 실행 순서와 후속 작업

1. 기존 Realtime 연결·PIN 및 정차 상태 확인 → Work 진입 → 업무 타이머 시작.
2. 새 DOCX 또는 기존 자료함 DOCX 선택 / 보관 초안 복원.
3. 문단 입력·선택 범위 서식, 표·셀 변경, 기존 이미지 크기 변경. 이 이미지에서는 새 이미지 파일 선택은 BLOCKED.
4. 저장은 암호화 초안. 다른 이름으로 저장 → 공개 저장 설명 확인 → `Download/StayPlus` 저장 또는 앱 전용 임시 파일.
5. 내보낸 DOCX 재열기로 내용 확인. 앱 종료 후 같은 인증 프로필로 초안 목록 복원.
6. 공개 Downloads 파일의 수동 PC 추출/향후 수동 전송은 가능하지만 이번 작업에서 업로드하지 않는다.

완전한 실사용 완료를 위해 남은 항목: 실제 파일/이미지 picker 제공 환경 또는 별도 승인된 선택 UX, 실제 한국어 IME, 실 페어링 문서 다운로드, Windows Word 재열기/시각적 비교, 복합 문서 corpus·대용량 메모리/중단 실패 주입. 새 외부 의존성/배포/DB 변경 없이 현 단계에서 확인된 기능과 플랫폼 차단을 구분한다.
