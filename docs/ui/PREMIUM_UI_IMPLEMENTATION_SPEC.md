# Pleos Stay+ Premium UI — 구현 명세서 v1.0

- 작성일: 2026-11-05
- 기준 브랜치: \`dev\`
- 목적: 선택된 **A안 프리미엄 차량 홈 시안**의 디자인 시스템을 사용하여 PLEOS 차량용 Compose, Windows Stay+.exe(WPF), Pickup 웹(Next.js)을 단계적으로 구현·연결하고 에뮬레이터/실기에서 검증한다.
- 공식 시안 관리 위치: [Notion 프로젝트 명세서 > 10. UI 시안](https://app.notion.com/p/40fb74b2b905813e9840fb333a4c2ddc)
- 범위: **UI 및 프레젠테이션 계층 교체·구성 문서**. 이 명세 자체로 API/DB/SDK/배포 변경을 승인하지 않는다.
- 중요: 시안 PNG의 픽셀은 렌더링·배경 이미지의 참고용이지 실제 코드·로직·비밀값·QR·위치 데이터의 원천이 아니다.

## 1. 구현 우선순위와 완료 정의

1. 기존 주차장 검색·상세, Waiting Room 세션, Pickup QR/위치/상태, Realtime 수동 파일 전송을 **회귀 없이 보존**한다.
2. 첫 화면은 사용자 승인 프리미엄 A안. 새 UI에서 사이드 내비게이션을 통해 전체 기능으로 이동한다.
3. UI에서 실제로 동작하는 버튼만 활성화한다. 미구현 버튼은 숨기거나 **준비 중 / 지원 여부 확인 중**으로 분명히 표시한다.
4. 제작 단위는 공통 디자인 시스템 → 앱 셸·홈 → 기존 API 붙은 화면 → 모드 상세 → WPF/웹 스타일 통일 → 실제 실행 QA.
5. Android, Windows, 웹은 같은 브랜드 색·타이포그래피·상태 표현을 사용하되 각각의 플랫폼 입력·창 크기·접근성 규칙을 따른다.
6. UI 실물은 시안과 최대한 유사하게 구현하지만, 장치별 DPI, 시스템 바, OS 정책 및 에셋 때문에 픽셀 동일성을 보장하지 않는다. “99% 동일”이라는 수치를 품질 기준으로 삼지 않는다.

### 참고 소스의 우선순위
- 최근 사용자 결정 > \`AGENTS.md\` > \`DEVELOPMENT_GUIDE.md\` > 확정 Notion API 정책 > \`docs/api/openapi.yaml\` 및 실제 \`dev\` 코드 > UI 시안.
- 최신 기능 기준으로 Waiting은 **Android 로컬 Room**, Pickup은 **독립 Fastify API + Next.js 웹**, Realtime은 **수동 파일 전송 API**, Parking은 **Fastify 검색 API**다.
- 오래된 계획 문서에 있는 Waiting HTTP 서버, 자동 파일 동기화, 원격 AirDesk 스트리밍, 가상 키보드, 카메라 주변 안전 감시는 이 UI 작업만으로 완성하지 않는다.
- 코드는 \`dev\`를 기준으로 작성하되 구현 착수 때 HEAD/빌드 상태/Notion 변경일을 재확인한다. 충돌이 있으면 임의로 API를 변경하지 않는다.

## 2. 화면 목록과 Navigation ID

| ID | 플랫폼 | 화면 | 사이드 활성 메뉴 | 상태 |
|---|---|---|---|---|
| VEH-01 | PLEOS | 홈 대시보드 | HOME | 신규 UI |
| VEH-02 | PLEOS | 주차장·대기 장소 검색 | PARKING | 기존 Parking 연결 |
| VEH-03 | PLEOS | 장소 상세/내비 진입 | PARKING | 기존 Parking 연결 |
| VEH-04 | PLEOS | 대기 시작/진행/연장/종료/기록 | PARKING | 기존 Waiting 연결 |
| VEH-05 | PLEOS | Pickup 생성·QR·참가자 상태 | PICKUP | 기존 Pickup 연결 |
| VEH-06 | PLEOS | Realtime 연결·파일 목록·버전·다운로드 | FILES | 기존 Realtime 연결 |
| VEH-07 | PLEOS | 휴식 Rest | MODES | 새 기능/UI |
| VEH-08 | PLEOS | 공부 Study | MODES | 새 기능/UI |
| VEH-09 | PLEOS | 업무 Work | MODES | 신규 화면, 실제 문서 편집 별도 |
| VEH-10 | PLEOS | 엔터테인먼트 Entertainment | MODES | 새 기능/UI |
| VEH-11 | PLEOS | 정차 확인 불가/주행 제한 화면 | 선택 유지/차단 레이어 | 기존 safety 연동 |
| VEH-12 | PLEOS | 대기 종료 요약·기록 | PARKING | Waiting 조회 기반 |
| WIN-01 | Windows | 내 파일·버전 이력·업로드/다운로드 | FILES | 기존 WPF 연결 |
| WIN-02 | Windows | 차량 연결/복원/코드/승인 | VEHICLE | 기존 WPF 연결 |
| WIN-03 | Windows | 설정·개발자 설정 | SETTINGS | 기존 WPF 연결 |
| WEB-01 | Mobile Web | QR 스캔 후 공유 안내 | 없음 | Next.js |
| WEB-02 | Mobile Web | 초대/동의/별칭 입력 | 없음 | Next.js |
| WEB-03 | Mobile Web | 지도·출발·근접·합류 완료/취소 | 없음 | Next.js |
| WEB-04 | Mobile Web | 만료·회수·오류·종료 | 없음 | Next.js |

### Navigation 항목
- HOME: 홈
- PARKING: 주차·대기 (검색/상세/Waiting/히스토리)
- PICKUP: 픽업 공유 (QR·진행/종료)
- MODES: 대기 모드 (Rest/Study/Work/Entertainment)
- FILES: 파일 작업 (연결·버전)
- SETTINGS: 설정

**자동 활성 표시 규칙:** Route → NavigationGroup 매핑을 단일 함수/타입으로 정의한다. 새로운 화면이 생겨도 화면마다 \`activeMenu = ...\`를 하드코딩하지 않는다. 하위 화면은 상위 그룹의 보라색 상태를 유지한다. 뒤로 가기/복원/딥링크가 발생해도 실제 route와 표시를 동기화한다. Safety Lock은 현재 그룹을 유지하되 조작을 가린다.

## 3. 공통 디자인 시스템

### 3.1 브랜드 무드
- A안 시안의 **다크 네이비 + 절제된 일렉트릭 블루·퍼플 그라데이션 + 어두운 차량 사진**을 홈의 시각 마스터로 채택한다.
- 카드가 과도하게 겹치는 AI 컨셉 이미지처럼 보이지 않도록 그림자·테두리·텍스트 양을 줄인다. 배경 사진·그라데이션은 화면당 한두 핵심 영역에만 사용한다.
- 동일한 카드 높이 반복 대신 정보 우선순위에 따라 히어로/주요 작업/보조 정보로 명확한 위계를 만든다.
- 텍스트는 반드시 런타임 네이티브 텍스트로 표시한다. 이미지 안에 포함된 글자를 실제 버튼·데이터로 사용하지 않는다.

### 3.2 시작 토큰(실제 PNG 측정 후 조정 가능)
| 토큰 | 값 제안 | 사용 |
|---|---|---|
| \`bg.main\` | \`#081323\` | 전체 배경 |
| \`bg.rail\` | \`#0A172B\` | 사이드바 |
| \`bg.surface\` | \`#15263F\` | 카드 |
| \`bg.surfaceRaised\` | \`#1B3151\` | 선택·강조 카드 |
| \`text.primary\` | \`#F5F7FC\` | 본문 제목 |
| \`text.secondary\` | \`#ABBAD1\` | 설명/메타 |
| \`border.subtle\` | \`#304463\` | 카드 구분 |
| \`accent.blue\` | \`#75A7FF\` | 포커스/보조 |
| \`accent.purple\` | \`#7963D8\` | 활성 네비게이션 |
| \`status.success\` | \`#61DDB0\` | 성공 확인 시에만 |
| \`status.warning\` | \`#FFCB76\` | 조건부/주의 |
| \`status.error\` | \`#FF8B91\` | 실패 |
| \`radius.card\` | \`18–24dp\` | 차량 카드 |
| \`radius.button\` | \`12–18dp\` | 차량 버튼 |
| \`gap.base\` | \`8dp\` 눈금 | 패딩/간격 |
| \`rail.width\` | \`96–116dp\` 제안 | 가로 화면 사이드 |

색상/크기 값은 **스타팅 토큰**이지 확정된 화면 픽셀값이 아니다. 환경별 screenshot QA에서 조정하되 제품 전체에 일관 적용한다.

### 3.3 Compose 구성 요소
- \`StayAppShell\`: 안전한 앱 내부 영역, 레이아웃, 공통 상태 표시, 콘텐츠 슬롯.
- \`StayNavigationRail\`: 아이콘·라벨·선택 표시·포커스. 화면마다 복사하지 않는다.
- \`StayHero\`, \`StayCard\`, \`StayPrimaryButton\`, \`StaySecondaryButton\`, \`StayStatusPill\`, \`StayInfoRow\`, \`StayEmptyState\`, \`StayErrorPanel\`, \`StayLoadingSkeleton\`.
- \`StayConfirmationDialog\`: 연결 해제·데이터 삭제·픽업 취소 등 돌이키기 어려운 작업 확인.
- \`StaySafetyOverlay\`: 차량 상태/플랫폼 제한 변경 시 즉시 터치 차단.
- 공통 라벨은 resources에, 색/간격/타이포그래피는 theme에 모은다. 가능하면 Material3 기반으로 만든다.

### 3.4 차량 화면 크기/시스템 UI
- **예시 PNG의 상단 P·속도, 실제 차량 HVAC/공조, 하단 도크는 우리 앱이 소유한다고 가정하지 않는다.**
- PLEOS 공식 HMI/AAOS 시스템 바가 제공하는 영역을 임의로 덮거나 차량 신호를 추측해 렌더링하지 않는다. 앱이 사용 가능한 window bounds를 측정하고 그 안에 UI를 배치한다.
- 가로 화면 16:9 부근을 기본 디자인으로 삼되 Compose constraints에 따라 스크롤·2열→1열 전환·텍스트 잘림을 처리한다. \`dp\` 기반, 고정 픽셀 좌표 금지.
- 가로 화면에서 글자/터치 영역이 작아지지 않게 한다. 색상에만 의존하지 않고 아이콘+상태문구를 함께 제공한다.
- 사진/아이콘/로고는 실제 사용 허가가 확인된 파일만 사용한다. 생성형 시안의 차량·브랜드 일러스트를 정식 에셋이라고 가정하지 않는다.

## 4. 화면별 필수 데이터·동작

### VEH-01 홈
- 목적: 앱의 현재 상태를 바로 파악하고 주차 검색, 현재 대기, 픽업, 파일 작업, 4개 모드에 진입.
- 레이아웃: 왼쪽 Rail / 상단 브랜딩·히어로 / 현재 세션 및 안전 상태 / 4개 모드 카드 / 주차·픽업·파일 바로가기. 프리미엄 A안의 정보 위계 유지.
- 데이터: \`WaitingUiState.active\`, \`WaitingUiState.safety\`, \`PickupUiState.session\`, 실존하는 Realtime 연결 상태(접근 가능한 곳에 한함).
- 활성 Waiting이 없으면 '대기 세션 시작' 안내. 활성 있으면 경과 시간/장소(있을 경우) 표시. **실제 Waiting 데이터에 없는 '예상 대기 시간'을 추정하지 않는다.**
- 현재 위치·날씨·차량 이미지가 실제 데이터로 준비되지 않았으면 그 블록을 숨기거나 명확하게 정적 장식으로만 처리. 기어/속도/주차 가능 판정을 사진·가정으로 표시하지 않는다.
- 모드 4종은 미구현일 때 동작하는 것처럼 화면 전환만 해 완료로 주장하지 않는다.

### VEH-02 주차장 검색
- 구현 파일: \`parking/ui/ParkingScreen.kt\`, \`ParkingViewModel.kt\`; Backend \`GET /api/v1/parking/places\`.
- 현재 상태: \`ParkingUiState\`에 위도·경도/반경/예정시간/purpose/freeOnly, 로딩·리스트·선택 장소·데이터 신선도·오류·내비 상태가 있음.
- 실제 가능한 필터: 주변 반경, 예정 시간, 목적, 무료 여부. 결과는 \`ParkingPlace\` 모델의 이름·유형·거리·요금 상태·허용 활동·운영시간·출처를 사용.
- **목적지 텍스트 검색, 지도 렌더링, 공영 필터, 요금 '확정' 주장, 현재 빈자리 수는 현재 MVP 완료 기능이 아니다.** 구현하지 않은 검색창은 비활성/제거/별도 승인 필요. 외부 지도 SDK·지오코딩은 승인 전 추가하지 않는다.
- 빈 결과/미지원 위치/권한 거부/오래된 데이터/서버 오류를 각각 표현한다. \`dataFreshness\`가 STALE면 눈에 보이게 안내.

### VEH-03 주차장 상세
- API: \`GET /api/v1/parking/places/{placeId}\`; 요금 추정은 현재 별도 estimate 경로/도메인 정책에 따라 처리.
- 표시: 이름, 주소, 좌표 기반 거리(있을 때), 운영시간, parking type, 규정/이용 조건, estimatedFee, feeEstimateStatus, source/sourceUpdatedAt.
- \`UNKNOWN\` 요금을 0원으로 표시하지 않는다. 운영시간 부재는 '정보 없음'. \`eligibility=CONDITIONAL/UNKNOWN\`은 적법 확정 문구 금지.
- 선택 시 '내비로 이동'은 기존 \`NavigationGateway\` 호출. '이 장소로 대기 시작'은 선택한 \`WaitingPlace(id,name)\`를 Waiting으로 전달한 뒤 안전 게이트 확인 후 시작.
- 지도 이미지는 별도 지도 SDK 없이 구현하려면 정적 비상호작용 배경이어야 하며, 실제 위치와 일치한다고 오인시키지 않는다.

### VEH-04 Waiting 세션·기록
- 구현: \`WaitingScreen.kt\` + \`WaitingViewModel.kt\` + 기존 \`LocalWaitingRepository/RoomWaitingStore\`. 신규 HTTP Waiting API 추가 금지.
- 사용자 기능: 5~720분 시작(기본 15/30/60/120분), 연장(기존 예정 종료 시각 기준; 총 예정 720분 한도), 경과/남은/초과, 수동 종료, 저장 기록·재실행 복구.
- \`WaitingTime.calculate\`의 표시 규칙 사용. 예정 시간을 초과해도 ACTIVE 유지. 실제 이동 확인 시 DRIVING 종료는 기존 monitor가 담당.
- 안전 판단: \`PARKED_CONFIRMED\` 수준이 확인되고 restriction 없는 경우만 조작 허용. UNKNOWN/주행/권한 거부/조회 실패/화면 비활성에서 잠금.
- '안전 모니터링'은 차량 속도·기어·UxRestrictions 기반 **주행/상태 잠금**으로만 표현; 후방 접근/보행자 센서 감지 기능으로 묘사 금지.
- 모드 변경 필드는 현재 \`WaitingSession\`에 없다. 홈/모드 UI 상태와 Waiting 세션 DB를 암묵적으로 결합하지 않는다.

### VEH-05 Pickup QR / 진행
- 구현: \`PickupScreen.kt\`, \`PickupViewModel.kt\`, \`PickupRepository.kt\`, \`/api/v1/pickup/**\`.
- Waiting 없이 독립 Pickup 생성 가능. 로컬 Waiting UUID는 선택적 연결만 제공. 운전자 Supabase 익명 인증 JWT를 사용.
- QR 발급된 \`PickupInvitation.shareUrl\`을 실제 QR로 인코딩. 시안 속 가짜 QR을 배포용으로 사용 금지.
- 참가 가능 기간은 60분 정책; 새 링크 발급 때 이전 링크·참가자 권한 회수. 스캔은 운전자 휴대폰 '공유 안내'용이며 스캔 자체로 참가되지 않음.
- 참가자는 한 명; 참가자 상태 및 이벤트 revision, 위치 공유 동의 여부/마지막 갱신시각을 표현. GPS 오류가 이벤트 수신/상태 복구를 막지 않음.
- 차량 위치는 실제 동의·안전 조건 충족 시에만 갱신. '20m/30초 자동 합류 완료' 금지; 조건 충족해도 합류 후보 이벤트만. 정확도 없는 위치는 자동 근접 판정에 사용하지 않는다.
- 만료/취소/완료에서 민감 위치 삭제·초대 취소·화면 권한 철회 상태를 표현한다. TTS는 SDK_VERIFY 실제 검증 전 성공 배지 금지.

### VEH-06 차량 파일 전송 (Realtime)
- 구현: \`RealtimeEntry.kt\`, \`FileTransferScreen.kt\`, \`FileTransferViewModel.kt\`, \`RealtimeWorkBridge.kt\`; 서버 \`/api/v1/realtime\`.
- 파일 목록 \`GET /files\`, 버전 \`GET /files/{id}/versions\`, 파일 저장/다운로드, 업로드 중 진행률, 취소/재시도, 사용자 확인 기반 PIN 잠금, 연결 복원.
- 허용 TXT/PDF/DOCX, 최대 20 MiB. 원본/수정본은 서버 버전으로 명확히 구분. 자동 덮어쓰기/실시간 동기화/폴더 감시/원격 데스크톱/기본 내장 편집기 **미지원**.
- 기기 페어링: 8자리 코드·10분 만료·PC와 차량 양쪽 승인. 차량과 PC를 같은 익명 사용자로 가정하지 않는다.
- \`FileTransferState.allowed/unlocked/busy/files/versions/progress\`를 활용. \`allowed=false\` 또는 \`unlocked=false\`이면 파일명·본문·전송 도구를 노출하지 않는다.
- 디버그 Fake Work는 실제 문서 편집으로 광고 금지. Work 편집 호출에 필요한 \`content://\` URI와 revision 계약은 별도 개발·검증 대상.
- 미연결/페어링 대기/서버 실패/전송 실패/해시 불일치/파일 미지원/접근 거부를 구별.

### VEH-07 휴식 / VEH-08 공부 / VEH-09 업무 / VEH-10 엔터
- 네 화면 모두 A안과 동일한 header/rail/card/token을 사용하고 **현재 UI 또는 기능 구현 수준에 맞춰** 동작 가능한 것만 노출.
- REST: 휴식 타이머·차분한 테마·선택 오디오 등은 신규 로컬 기능 제안. 공조·시트 원격 제어는 SDK/권한 승인 전 제외.
- STUDY: 집중 타이머·할 일·메모는 LOCAL(나중에 Room 확정 가능)로 우선 설계. 학습 기록 Cloud 동기화는 별도 결정.
- WORK: \`RealtimeWorkBridge\` 기반 문서 전달/수정본 업로드 가능 범위를 명확히 표시. **AirDesk 원격 영상 스트리밍, 마우스·키보드 기능은 별도 기술 검증 전 비활성**. 문서 뷰어/편집기는 별도 기능.
- ENTERTAINMENT: 허용 미디어·앱 실행 정책을 확인한 경우만 실제 실행. 임의 타사 앱 제어/재생 및 주행 중 영상 표시는 금지.
- 각 모드의 활성 화면은 \`MODES\` sidebar가 선택되어야 하며, 차량 안전상태 불명 또는 운행 중에는 인터랙션을 차단.

### VEH-11 Safety Lock / VEH-12 종료
- Safety Lock은 다른 네트워크 응답보다 우선하여 즉시 화면 입력 차단. UNKNOWN/DRIVING/UNSUPPORTED와 사유를 사용자에게 분명히 설명.
- 종료 시 Waiting은 저장된 실제 종료 시각·사유·경과시간만 표시. Pickup 연결이 있으면 기존 연동 종료 처리를 활용하되 실패/재시도는 명확히 표시하고 성공으로 위장하지 않는다.
- 주행으로 인한 종료와 수동 종료를 구분. 차량의 실제 경로 복귀가 가능한지는 NaviHelper 검증/사용자 요청과 분리.

## 5. Windows Stay+.exe — 프리미엄 스타일 적용

### WIN-01 내 파일
- 기술: \`windows/StayPlus.Desktop/MainWindow.xaml\`, \`MainWindow.xaml.cs\`, \`MainWindow.Presentation.cs\`.
- 고유 WPF 창의 크기 조절/키보드 탭/스크린리더 지원. Android의 가로 화면 레이아웃을 그대로 복사하지 않고 브랜드 토큰/사이드바만 공유.
- 내 파일에서 업로드, 새로 고침, 목록, 지원 파일 형식, READY 버전 기록, 선택한 버전의 **별도 저장**, 중단 업로드 재시도/취소, 파일 삭제 확인 표시.
- 현재 디자인 목업의 동영상/이미지 파일 탭, 전체 버전 일괄 다운로드, 무제한 파일 크기, 자동동기화 등 실제 미지원 항목은 노출 금지.
- 연결이 없는 경우 파일 화면의 민감한 행과 업로드 버튼을 보호하고 '차량 연결'로 진입하도록 안내.

### WIN-02 차량 연결 / WIN-03 설정
- 연결 확인·복원, PC에서 코드 발급, 차량 발급 8자리 코드 입력, 상대 확인 후 쌍방 승인, 연결 해제를 보조 상태로 표시.
- 만료/잘못된 코드/승인 대기/이미 연결됨/복원 실패에 맞는 메시지 제공.
- 개발자 설정(서버 URL·테스트 토큰·publishable key)은 접힌 영역/개발 빌드로 격리. **service_role, 서버 secret을 클라이언트에 제공하지 않는다**.
- 새 컴퓨터에서 자동 연결하려면 배포용 Fastify HTTPS 주소 + 공개 Supabase 설정 공급이 필요함. 파일 EXE만 복사한 상태를 자동 연결 완료로 가정하지 않는다.

## 6. Pickup 모바일 웹 — 시안 연결

- 실제 경로: \`web/app/share/[token]/page.tsx\`(SharePanel), \`web/app/pickup/[token]/page.tsx\`(PickupPanel), \`web/app/pickup/session/page.tsx\`(복원).
- WEB-01: QR 스캔 직후 운전자 공유 안내 페이지. 링크 복사, 네이티브 공유(Web Share API), 만료 안내. **차량 위치 공개·자동 참가 금지**.
- WEB-02: 픽업 대상이 받은 링크 열고 동의/별칭 입력 후 참가. 위치 GPS 권한은 선택 사항이며 거부해도 수동 상태 전환 가능. 만료·재발급 회수는 접근 차단.
- WEB-03: 카카오맵 차량 위치 마커, 마지막 갱신 시각·데이터 신선도, '출발'→'곧 도착'→'합류 완료', '취소', 필요시 길찾기. 실제 Kakao SDK 설정과 API 제공 데이터 없는 항목을 정적 지도로 속이지 않음.
- WEB-04: 완료/취소/만료/위치 불명·권한 거부·서버 오류를 각각 독립 상태로 표현. 개인정보·이동 경로 기록을 새로 수집하지 않는다.
- 모바일 웹은 실제 브라우저 폭에서 터치·안전영역·작은 화면 스크롤·모바일 타이핑을 검증한다.

## 7. 구현 구조와 데이터 흐름

### 7.1 기존 Android 유지 범위
- \`android/app/src/main/java/com/pleosstay/app/MainActivity.kt\`: 현재 \`section\` 숫자 0/1/2/3으로 Parking/Waiting/Pickup/Realtime을 전환한다.
- UI 개편 시 공통 \`StayAppShell\`과 typed screen route를 도입하되 기존 \`parkingViewModel\`, \`waitingViewModel\`, \`pickupViewModel\`, \`RealtimeEntry\`의 생명주기/권한 요청/연계를 그대로 연결한다.
- Waiting safety gateway는 중복 생성/시작하지 않는다. \`requestVehicleInformation\`, \`requestNearbySearch\`, \`requestPickupStart\` 권한 흐름을 보존.
- \`waitingState.active\`와 \`pickupState.session\`의 기존 연동 종료 로직을 훼손하지 않는다.
- Compose UI 함수는 UiState를 그리며 ViewModel의 명시적 action을 호출한다. UI에서 직접 HTTP/DB/차량 SDK 호출 금지.

### 7.2 권장 신규 UI 전용 파일
- \`android/.../ui/design/StayColors.kt\`, \`StayTypography.kt\`, \`StayComponents.kt\`
- \`android/.../ui/navigation/StayRoute.kt\`, \`StayNavigationRail.kt\`
- \`android/.../ui/home/StayHomeScreen.kt\`
- \`android/.../ui/modes/ModeMenuScreen.kt\`, \`RestScreen.kt\`, \`StudyScreen.kt\`, \`WorkScreen.kt\`, \`EntertainmentScreen.kt\`
- 이름·위치는 제안이며 기존 package 구조와 충돌 확인 후 조정한다. 새 파일마다 책임을 분리하고 기존 모델 중복 정의 금지.

### 7.3 서버/API 원칙
- Parking의 기존 JSON/nullable/요금 추정/실시간 가용면수 미지원은 유지.
- Waiting을 이유 없이 HTTP 서버로 이전하지 않는다.
- Pickup은 독립 서버 세션이며 Waiting과 공통 소유자 세션으로 합치지 않는다.
- Realtime 파일 원본은 연결 ID에 묶인다. Waiting/Pickup의 하위 데이터처럼 다루지 않는다.
- 디자인 때문에 새 엔드포인트/DB 필드/권한/SDK를 자동 생성하지 않는다. UI에서 필요한 신규 기능은 **Gap 목록과 별도 승인 티켓**으로 구분.

## 8. 디자인 목업과 실제 코드 Gap 목록

| 시안 요소 | 현재 상태 | 권고 처리 |
|---|---|---|
| 상단 P/속도/HVAC/도크 | 플랫폼 시스템 UI일 수 있음 | 앱 소유 영역에서 제외, AAOS/Pleos 검증 |
| 날씨 카드/23°C | 실제 연동 불명 | 홈 초기 구현에서 제거 또는 정적 장식으로 명시 |
| 포토 카드의 차량·로고 | 생성 시안 소재 | 라이선스·실제 파일 검토 후 적용 |
| 목적지 텍스트 검색 | Parking MVP의 현재 차량 위치 검색과 다름 | 별도 지오코딩/검색 공급자 승인 전 비활성 |
| 지도 프리뷰/지도 길찾기 | Parking 앱 내부 지도가 현재 없음 | 허위 지도 금지, NaviHelper로 분리 |
| 주차 가능 면수/확정 요금 | 제공 데이터 없음/조건부 | UNKNOWN/추정/출처 구별 |
| AI 카메라 후방 감지 | 미구현/SDK 검증 필요 | 안전 상태 잠금 설명만 표시 |
| Work 원격 PC 조작·AirDesk | 현행 Realtime 범위 아님 | 수동 파일 전송으로 표기, 별도 검증 |
| 'Realtime 동기화 중' | 현재 수동 전송 | '수동 파일 전송/새로 고침'으로 변경 |
| PDF/DOCX 차량 편집 | 렌더/에디터 미검증 | 명시적 준비 중 또는 제외 |
| 타사 미디어 재생/앱 실행 | 차량 정책·SDK 미검증 | 지원 앱 확인 전 비활성 |
| 자동 차량 위치/픽업 ETA | 정확도/위치 허용 상태에 따라 다름 | 실제 API 값만 표현, 오류·마지막 갱신 표시 |
| 디자인 QR 블록 | 시안 이미지는 실제 토큰 아님 | \`shareUrl\`에서 진짜 QR 생성 |

## 9. 상태 전이와 시각 규칙

- 초기 로딩: 연속 skeleton / 로딩 안내를 제공하고 실제 데이터처럼 보이는 가짜 장소·파일·참가자 목록 금지.
- Empty: '검색 결과 없음', '활성 대기 없음', '픽업 참가자 없음', '연결된 PC 없음'을 구별하고 실행 가능한 다음 행동을 제시.
- Error: 네트워크/권한/입력/만료/인증/무결성/안전 잠금에 맞는 사용자 문구+복구 행동.
- 버튼: enabled/disabled/loading/focus/pressed 구분. 중복 POST·전송 버튼 빠른 연타 방지.
- 성공: 서버/저장소의 확정 응답 뒤에만 완료 표시.
- 안전: 실제 차량 데이터 확인 실패 시 기본 잠금. 미확인 P=안전 주차라고 단정하지 않음.
- 위치: 정보 없음/마지막 갱신 오래됨/권한 거부에서 현재 위치라고 표기 금지.
- 접근성: 충분한 터치 크기, 색 + 텍스트로 상태 표현, 키보드 포커스, TalkBack/Windows UIA, 대비·글꼴 확대 검증.
- 애니메이션: 선택된 사이드 표시/카드 전환에 짧고 절제된 모션. 차량 주행 중 시선을 끄는 장식 애니메이션 금지.

## 10. 구현 단계와 각 단계의 수용 기준

### Phase A — 디자인 시스템/홈(코드 변경 최초 단계)
- 공통 theme/rail/shell/home을 추가, 기존 MainActivity 4기능 라우팅을 보존.
- 상단/하단 시스템바를 스크린샷에서 그대로 그리지 않고 실제 앱 window에 맞춰 배치.
- 각 상위 메뉴 선택, 상세 화면 진입·뒤로 가기, 재생성(rotate/relaunch)에서 보라색 활성 표시 일치.
- Compose Preview와 에뮬레이터 실물 스크린샷을 Notion 홈 시안과 수동 비교.
- 빌드/테스트 결과가 없는 변경은 완료 선언하지 않는다.

### Phase B — 기존 Android API UI 연결
- Parking 검색/상세/내비, Waiting 세션/연장/종료, Pickup 시작/QR/참가자 변화, Realtime 페어링/파일 목록/버전/전송을 새 화면에 연결.
- 기존 API 결과와 버튼 액션 1:1 추적 표 작성. 각 API 요청의 loading/error/retry 상태 테스트.
- 안전 상태 UNKNOWN·DRIVING에서 필요한 부분이 잠기는지 확인. 주차장 데이터 STALE/UNKNOWN 표시 확인.

### Phase C — Windows/웹
- Windows WPF 파일 목록/페어링 화면에 공통 브랜드 토큰 적용, 기존 UI 이벤트 핸들러/DPAPI/전송 journal 보존.
- Next.js SharePanel/PickupPanel에서 3개 모바일 화면 스타일을 적용, 인증·동의·권한·라우팅·갱신 정책 보존.
- 실제 모바일 브라우저에서 QR 공유 → 링크 열기 → 참가 → 출발 → 도착 → 종료 기본 시나리오 검증.

### Phase D — 4개 모드
- Rest/Study/Work/Entertainment를 각 독립 계약·상태/오류/주행 잠금으로 구현.
- 추가한 기능마다 LOCAL/REST/SDK/STREAM 분류, 권한·저장·복구·실측 테스트 기록.
- 일부 기능이 미구현이면 별도 '준비 중' 상태를 유지하고 시연 가능한 핵심 흐름만 완성한다.

### Phase E — 회귀/시각 검수
- Android \`assembleDebug\`, unit test, 가능한 Pleos Connect Emulator 2.1.0 직접 실행.
- Web \`lint\`/build 및 실제 휴대폰 QR 진입, Windows WPF build 및 로컬/실Cloud 연결 테스트.
- 동작 비교: 홈→주차장 검색→상세→Waiting→픽업→파일 작업→종료, 에러/복귀/앱 재시작.
- 시안 대비 스크린샷 체크: 섹션 위계, 폰트/간격/배경/그라데이션, 사이드 활성, 클릭 영역, 데이터/상태/오류.
- 테스트 증거는 '로컬 Fake', '실제 Supabase Cloud', 'Pleos Emulator', '실차'를 분리. 실행하지 않은 것은 '미검증' 기록.

## 11. 에이전트 작업 범위/금지사항

1. 이 명세는 **개발 가이드 문서**다. Git에 등록하는 행위만으로 대규모 리팩터링·SDK 설치·API 변경·배포 승인을 받은 것은 아니다.
2. 작업 전 \`git status\`, 현재 브랜치, 수정 예정 파일, 변경 후 테스트를 사용자에게 제시. \`main\` force push/reset 금지.
3. 우선 UI 전용 파일과 필요한 최소 라우팅만 수정. 기존 Backend, SQL migration, 위치 수집, Auth/RLS, SDK permission, Gradle dependency를 무단 변경 금지.
4. 시안에 존재하지만 구현 불가/불명확한 요소는 blocker 또는 placeholder(명시적으로 구별)로 남기고 임의 성공 데이터 만들지 않는다.
5. 서버 URL·Supabase secret/service_role·실제 QR 토큰·차량/참가자 좌표를 UI/로그/공개 이미지에 넣지 않는다.
6. 기존 기술 스택 유지: Android Kotlin+Compose, Windows .NET WPF, Web Next.js, Backend Fastify+Supabase.
7. 변경 후 Git diff 및 빌드·실행 결과, 회귀 이슈, 남은 미구현/미검증 항목을 보고한다.

## 12. 이번 문서 등록 시점의 완료 범위

- [x] 선택된 프리미엄 홈 디자인 방향 결정
- [x] 화면 ID/플랫폼/사이드 활성 규칙/기존 API 연결 계약 정의
- [x] 시안 보관 Notion 10번 페이지 링크 정의
- [ ] 모든 화면별 PNG가 실제 Notion 페이지에 첨부되었는지 확인
- [ ] Compose 화면 재구현·에뮬레이터 비교
- [ ] Windows WPF 스타일 교체
- [ ] Pickup 웹 스타일 교체
- [ ] 모드 4종 실제 로직 구현
- [ ] 실환경 API E2E/차량 규정 검증

> **이 문서만으로 실행 결과를 주장하지 않는다.** Notion의 이미지가 아직 첨부되지 않았다면 구현자가 원본 시안의 정확한 이미지를 열 수 없으므로, 그림을 임의로 상상하지 않고 첨부 완료를 확인해야 한다.
