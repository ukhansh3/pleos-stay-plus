# 에뮬레이터 없이 기능 UI 보기

실제 기능 화면 Compose를 재사용하는 정적 Preview입니다. 예시 데이터만 사용하며 서버·GPS·차량 SDK·PIN·문서 저장을 실행하지 않습니다. 앱에서 보이는 실제 결과나 기능 성공 화면의 증거가 아닙니다.

## Android Studio에서 열기

1. 프로젝트 `android`를 열고 Build Variant가 **debug**인지 확인합니다.
2. **Shift를 두 번** 눌러 아래 파일 중 하나를 검색해 엽니다.
3. 코드 편집창 우측 상단 **Split** 또는 **Design**을 선택합니다.
4. 빌드 요청이 나타나면 **Build & Refresh**를 누릅니다.

**Run / Run Preview / 기기 실행 버튼은 누르지 않습니다.** 이 기능은 IDE 안의 미리보기만을 위한 것입니다. 기기에서 실행하려 하면 실제 기능 대신 안내만 표시합니다.

전체를 보려면 **`AllScreensPreview.kt`**를 열고 Design에서 **Grid** 또는 **List** 모드를 선택하세요. 홈+기능8개, 총9개 화면을 함께 볼 수 있습니다. 화면이 작게 보이면 확대율/Fit을 조정하세요. 무거우면 **Focus** 모드로 원하는 화면만 선택하거나 아래 개별 Preview 파일을 여세요.

각 개별 파일에는 Preview가 하나씩 있어 필요할 때 한 화면만 렌더링할 수 있습니다.

| 파일 | 화면 |
|---|---|
| `ParkingPreview.kt` | 주차장 조건·목록·선택 상세 |
| `WaitingPreview.kt` | 대기 타이머·장소·연장·기록 |
| `PickupPreview.kt` | 예시 초대 QR·참가자 상태 |
| `RealtimePreview.kt` | PC 연결·파일·버전·선택 정보 |
| `RestPreview.kt` | 휴식 사진·타이머·설정 |
| `StudyPreview.kt` | 자료·문서·메모·진도 |
| `WorkLibraryPreview.kt` | 문서 자료함·초안·시간 |
| `WorkEditorPreview.kt` | DOCX 구조·본문·서식·표·이미지 |

파일 위치: `android/app/src/debug/java/com/pleosstay/app/preview/`.

기준 크기는 앱 콘텐츠 영역 **1682 x 1210dp**, API34, 한국어입니다. PLEOS 시스템 바·Driving View·HVAC를 그리거나 숨기지 않습니다. 기존 홈 Preview와 홈 소스는 수정하지 않았습니다.

## 확인 가능한 것과 한계

- 실제 Compose의 열 배치, 여백, 글자 크기, 이미지, 색상, 카드와 버튼 위치를 확인합니다.
- 표시된 장소·파일·참가자·QR·EV 수치는 모두 `예시`입니다. 실제 위치나 인증정보를 읽지 않습니다. QR은 example.invalid의 동작하지 않는 주소입니다.
- 기능·전송·저장·안전·PIN·권한·실차 검증은 수행하지 않습니다. Preview의 버튼으로 실제 기능을 시험하지 마세요.
- 정적 Preview의 렌더링, 폰트, 입력기와 파일 선택기는 실제 PLEOS와 다를 수 있습니다.
- 첫 Preview를 위한 Gradle 빌드·IDE 색인은 여전히 필요할 수 있습니다. 이것이 에뮬레이터 실행을 뜻하지는 않습니다.

## 구현 경계

Preview 엔트리·예시 상태·실패 차단형 포트는 `src/debug`에만 있습니다. release에는 포함하지 않습니다. 실제 실행 중인 앱은 `LocalInspectionMode=false`이므로 Preview 상태 provider를 무시하고 원래 ViewModel Flow를 계속 사용합니다. 모델 안의 안전·인증은 여전히 차단 상태이며 실제 SDK/Room/Keystore/HTTP 서버를 생성하지 않습니다.

공식 근거: [Android Compose Preview 문서](https://developer.android.com/develop/ui/compose/tooling/previews) — 크기 설정, LocalInspectionMode, 리소스를 아끼는 Focus 모드와 Preview 제약. 실제 IDE 렌더링 결과는 별도 확인 대상이며 컴파일 성공과 동일하지 않습니다.
