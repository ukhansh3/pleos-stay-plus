# Pleos Stay+ — Development Guide

> 상태: **구현 중 통합 설계 v0.5** · 2026-11-02  
> GitHub 초기 문서 게시 및 Kotlin/Compose Android 프로젝트 생성·Pleos 에뮬레이터 기본 앱 실행·Debug Gradle 빌드 성공을 확인했다. Pleos Vehicle/NaviHelper 실제 SDK 함수 동작과 서버 API 연동은 아직 미검증.  
> 제품명: **Pleos Stay+**. 개발 목표 기간은 **2026-11-01~11-13(13일)**. 날짜별 계획은 목표이며 실행 착수 승인이 아니다.

## 0. 이 문서의 목적·읽는 순서

이 문서는 코딩 에이전트가 추측 없이 개발할 수 있도록 **제품 규칙, 책임 경계, 데이터 계약, UX, 실패 처리, 보안, 검증 기준**을 정한다. 시작하기 전에 `AGENTS.md`를 읽고 이 문서의 `결정 대기`를 확인한다. 미확정인 항목은 적절히 인터페이스만 마련하고, 사용자의 승인을 받아 확정한다.

**요구사항 상태**
- `CONFIRMED`: 제품 방향에서 합의한 요구. 플랫폼에서 실제 가능한지는 별도 검증 필요.
- `PROPOSED`: 기술 설계안. 구현 전에 사용자 승인 필요.
- `SDK_VERIFY`: 공식 문서·권한·에뮬레이터 확인 전 사용 불가.
- `DEFERRED`: 후속 연구/확장. 현재 구현 지시 아님.

**기능 우선순위**: P0 (사용자 흐름/안전), P1 (핵심 경험), P2 (선택), Research (장기). 세부 모드별 필수 범위와 일정은 14~18장에서 계획으로 제안하며, 미승인 사항은 `REVIEW_REQUIRED`로 둔다.

## 1. 프로젝트 정의

### 1.1 한 문장 설명
차량에 머무는 시간을 안전하게 관리하고, 픽업 상대와 연결하며, 정차한 차 안을 업무·여가·휴식 공간으로 전환하는 Pleos Connect 차량용 앱.

### 1.2 핵심 가치
1. **장소**: 목적지 주변에서 주차장과 적법성이 확인된 단기 정차·승하차 장소 후보 탐색. 사용자가 추천되지 않은 위치를 선택할 수도 있다.
2. **상태**: 대기시간·예상 비용·차량 상태 및 알림 관리.
3. **연결**: 웹 초대 링크로 픽업 대상의 출발/도착 상황 공유.
4. **경험**: Work/Entertainment/Rest 모드로 대기시간 활용.
5. **복귀**: 차량 이동 준비 시 비운전용 기능 안전 차단과 세션 종료.

### 1.3 주요 사용자 흐름 (CONFIRMED)
`HOME → 장소 검색 또는 직접 선택한 위치 → 장소 확인/내비(선택) → 대기 시작 → 대기 대시보드 → [픽업 연결 / Work / Study / Entertainment / Rest] → 대기 종료 → 주행 복귀`.
- 사용자는 대기 중 언제든 다른 모드로 변경할 수 있다.
- 현재 대기 세션은 한 번에 최대 하나(단일 차량/운전자 기준, PROPOSED).
- 외부 알림, 화면 회전, 앱 재시작 후에도 세션 복구 방안을 둔다.
- 실제로 안전한 정차 상태가 확인되지 않으면 업무·엔터테인먼트 입력을 허용하지 않는다.

### 1.4 비목표 (DEFERRED)
투사형 홀로그램 키보드, 손동작 가상 마우스, 외부 센서 없는 후방 접근차 탐지, 도로변 불법주정차 자동 판정, 완전한 공조/시트 자동제어, ANC, 무인 자동 픽업, 무단 위치 추적, 웹 브라우저 백그라운드 GPS 보장, 자체 원격 데스크톱 코덱/프로토콜 구현.

## 2. 플랫폼/환경 및 공식 확인 사항

### 2.1 현재 확인한 로컬 환경
- Windows PC에서 Android Studio 2026.2.2 설치.
- Android SDK API 34 및 Pleos Connect v2.1.0 AVD 설치.
- Windows Hypervisor Platform 활성화 후 Pleos 가상 차량 기본 UI 표시 확인.
- **Android Kotlin/Compose 프로젝트 생성 완료** (`android/`, applicationId `com.pleosstay.app`, minSdk 28, compileSdk/targetSdk 37). Pleos Connect v2.1.0에서 기본 앱 실행 및 Gradle `assembleDebug` 성공 확인. Vehicle/NaviHelper 의존성 구성 빌드는 성공했지만 실제 SDK 메서드·권한·차량 신호 호출은 미검증.
- Git 사용 및 GitHub `dev` 푸시 확인. Node.js/VS Code/Python의 현재 실행·버전은 별도 확인 필요.

### 2.2 기본 빌드 기준
공모전 개발 가이드는 **Android API 28 이상, Gradle 8.0 이상, Java 또는 Kotlin**을 제시한다. Kotlin + Jetpack Compose를 권장한다. 앱의 `compileSdk`, `targetSdk`, AGP, JDK, Compose 버전은 프로젝트 생성 시 **공식 샘플과 현재 템플릿 호환성을 확인해 고정**한다. SDK API 34 에뮬레이터를 쓴다는 사실만으로 compileSdk/targetSdk도 34로 고정하지 않는다. 공식 Vehicle SDK Sample App의 예는 JDK 17, Gradle 8.10.2, AGP 8.8.2, Kotlin 2.0.0, compile/target 35이지만 **앱에서 반드시 동일 버전을 써야 한다는 뜻은 아님**.

### 2.3 공식 Pleos SDK 현황 (2026-11-01 확인)
| SDK | 공식 문서 기준 버전 | 용도 | 우리 프로젝트의 현재 판정 |
|---|---:|---|---|
| Vehicle | 2.3.2 | 차량 정보/상태/일부 제어 | `SDK_VERIFY`: capability·permission·실제 신호 테스트 필요 |
| NaviHelper | 3.0.5 | 목적지·경유지·길 안내 | `SDK_VERIFY`: 앱 설치·내비 서비스 동작 확인 |
| FusedLocation | 1.1.3 (한국어 릴리즈 노트) | 위치 | `SDK_VERIFY`: 실제 사용 가능 여부, 계정 권한 확인 |
| TextToSpeech | 3.3.1 (릴리즈 노트) | 음성 안내 | 선택 기능, 별도 검증 |
| SpeechToText | 3.3.1 (릴리즈 노트) | 음성 입력 | 선택 기능, 별도 검증 |

> **주의:** 공식 문서의 언어별 릴리즈 노트가 서로 다르게 노출되는 경우가 있다. 구체 버전은 사용하는 계정의 포털·Maven·샘플을 기준으로 다시 확인한다. 기능 존재, 해당 차량 capability, 권한 승인, 에뮬레이터 서비스 동작, 실제 배포 허용은 서로 별개다.

### 2.4 Vehicle SDK 기술 원칙
- `Vehicle` 엔트리 포인트를 `initialize()`로 초기화하고 `getDoor()`/`getBrake()` 같은 도메인 접근자를 이용한다. 사용 종료 시 `release()`.
- Capability / Get / Set / Callback은 다른 작업이다. `checkXxxCapabilities` 결과와 최소 필요 권한을 먼저 확인한다.
- IPC 기반 비동기 콜백(`onComplete`/`onFailed`)이므로 메인 스레드를 차단하지 않고, 완료 전 객체를 해제하지 않는다.
- 정확한 클래스·함수 시그니처 및 Permission 문자열은 API Reference 확인 전 작성 금지.
- SDK가 제공하는 `DrivingMode`가 곧 PRND 기어 상태라는 가정 금지. **실제 P 상태를 읽을 수 있는 공식 API가 확인되지 않으면 안전 잠금을 해제하지 않는다.**
- 후방 객체 감지, HVAC/Seat 제어, 창문 개폐를 차량 SDK에서 할 수 있다고 임의 추정하지 않는다.

공식 확인된 Vehicle Maven 구성 (`settings.gradle.kts`, 프로젝트 생성 후):
```kotlin
// dependencyResolutionManagement.repositories 내부
maven { url = uri("https://nexus-playground.pleos.ai/repository/maven-releases/") }
```
```kotlin
// Android 앱 모듈 build.gradle.kts의 dependencies 내부
implementation("ai.pleos.playground:Vehicle:2.3.2")
```
> 위 코드는 **Vehicle SDK만** 공식 설정 예시로 확인된 것이다. 다른 SDK의 Maven 좌표는 공식 문서 확인 전 작성 금지.

### 2.5 NaviHelper SDK 기술 원칙
`NaviHelper` 생성 → `initialize()` → `addListener()` → 목적지 요청 → 결과/상태 콜백 → `removeListener()` → `release()` 순서. 네비게이션 앱 상태와 구현에 따라 이벤트가 다를 수 있다. 정확한 목적지 객체, 좌표 기준계, 콜백 및 권한은 실제 레퍼런스 확인 후 채운다. 실패 시 사용자에게 위치를 지도/주소로 안내하는 대체 UX를 둔다.

### 2.6 에뮬레이터 실험 프로토콜
1. Pleos AVD **Cold Boot** 설정과 실행 재현.
2. 해당 AVD에 공식 Vehicle Sample 설치·기능 목록 검사.
3. 동일 APK에서 지원값과 unsupported/permission-denied 결과 기록.
4. 필요 시 Virtual Console로 차량 시뮬레이션 신호 변경; 구체 명령/포트는 공식 문서에서 확인 후 등록.
5. 로그에 SDK 버전·에뮬레이터 버전·사용 가능 영역·callback 타이밍·실패 코드를 보존(개인정보/비밀 제외).
6. 정상 작동하는 모의 상태와 실제 SDK 신호를 UI에 혼용하지 않는다.

## 3. 시스템 구조 (PROPOSED)

```text
┌──────────────────────┐       HTTPS         ┌────────────────────────┐
│ Pleos Android App    │◄────────────────────►│ Application Backend    │
│ Kotlin + Compose     │                      │ Supabase/Edge/Next API │
│ - Park/Wait/Modes    │                      │ - sessions/invitations │
│ - Safety Gate        │                      │ - pickup events        │
│ - Vehicle Adapter    │                      └──────────┬─────────────┘
│ - Navigation Adapter │                                 │ Realtime
│ - Remote PC Adapter  │                      ┌──────────▼─────────────┐
└──────┬─────────┬─────┘                      │ Passenger Web          │
       │ SDK     │ encrypted stream          │ Next.js + TypeScript   │
  Pleos Service  └──────────────────────┐    └────────────────────────┘
                                       ▼
                               Windows PC host
                               (Sunshine 등)

External parking/map providers → backend provider adapters → normalized ParkingPlace
```

**원칙:** 차량 SDK, 대기 비즈니스 규칙, 원격 PC 프로토콜, 백엔드 호출은 각각 분리된 어댑터를 둔다. Pleos SDK 미지원 또는 테스트 중에는 명시적인 Mock/Unsupported 모드로 분리한다.

### 3.1 제안 저장소 구조
```text
pleos-stay-plus/
├── AGENTS.md
├── DEVELOPMENT_GUIDE.md
├── README.md
├── .gitignore
├── .env.example
├── docs/
│   ├── ADR/                       # 승인한 결정 기록
│   ├── API_OPENAPI.yaml           # 서버 계약 확정 후 생성
│   ├── API_VERIFICATION.md        # SDK 지원 확인 결과
│   ├── SECURITY.md
│   ├── TEST_MATRIX.md
│   └── DESIGN_DECISIONS.md
├── android/
│   └── app/src/main/...           # 추후 Android Studio 생성
├── web/
│   └── ...                        # Next.js 픽업 웹
├── supabase/
│   ├── migrations/
│   ├── functions/
│   └── seed.sql                   # 가상 테스트 데이터만
└── .github/
    ├── workflows/
    └── pull_request_template.md
```
폴더명과 단일/멀티 레포 선택은 사용자 승인 후 확정한다. 비어 있는 폴더를 위해 의미 없는 애플리케이션 코드를 생성하지 않는다.

### 3.2 Android 계층
- `ui`: Compose 화면, 상태 표시, 접근성, 버튼.
- `presentation`: ViewModel, UiState, 오류·이벤트를 단방향 데이터 흐름으로 처리.
- `domain`: UseCase, 대기 상태 머신, 예상요금 계산, 주행 안전 규칙.
- `data`: 저장소 구현, 캐시, 서버 DTO 매핑.
- `integrations/vehicle`: VehicleSdkGateway, CapabilityMap, permission 처리.
- `integrations/navigation`: NavigationGateway.
- `integrations/remote`: RemotePcGateway (지원 여부 확인 전 추상화만).
- `core`: clock/time, error, dispatcher, logging, connectivity.

Android 앱 컴포넌트/권한/manifest/foreground-service는 Google 자동차 UX 제한과 Pleos 규정 검증 후 결정. 임의의 `QUERY_ALL_PACKAGES`, 광범위 위치·마이크·저장소 권한 추가 금지.

### 3.3 웹·서버 계층
- 웹: 픽업 링크 랜딩 → 사용자 동의 → 세션 참가 → 출발/근접/도착 상태 클릭 → 세션 종료 알림.
- 백엔드: 로그인·주차장 데이터 프록시·초대 발급·검증·상태 변경·정리·구독 권한.
- DB: 최소 개인정보, UUID 기본키, UTC timestamp, 명시적 상태 전이, RLS.
- 인증 방식은 `Supabase Auth` 또는 별도 계정 연동 후보이며 **미확정**. 익명 가입/Google/Kakao 등 사용자 승인 전 설치 금지.
- Supabase Edge Functions와 Next.js API Routes 중 어느 경로를 선택할지는 배포·보안·운영 조건으로 결정.

## 4. 세부 기능 요구사항과 완료 기준

### F-01 주차장 검색 [P1 · PROPOSED]
**입력:** 목적지(텍스트/좌표), 탐색 반경, 대기 예정시간, 최대요금, 선호 시설.  
**표시:** 이름, 주소, 거리(측정 가능할 때), 운영시간, 요금 출처, 데이터 갱신일, 출처 링크.  
**흐름:** 검색 → 결과/빈 결과/오류 → 상세 → 선택 → NaviHelper 연동 또는 지도 대체 → 대기 시작.  
**검증:** 검색 없을 때, 데이터 부분 누락, 시간표 예외, 유료/무료 혼합, 잘못된 좌표·가격.  
**주의:** '여기 주차 가능'을 법적 보장으로 표현하지 않는다. **주차면 실시간 가용성은 제공처가 확인될 때만**.

### F-02 대기 세션 [P0 · CONFIRMED]
- `start`: 장소/시작시각/예정시간/모드를 서버 또는 로컬에 기록.
- `extend`: 종료 예정시각 변경; 유효하지 않은 시간은 거절.
- `switchMode`: 활성 세션에서 모드 전환; 안전 제한 적용.
- `end`: 종료 중 반복 호출에도 안전하게 한 번만 종료(idempotent).
- `recover`: 앱 종료·에뮬레이터 재부팅 후 유효 세션 복원, 오래된 것은 만료 처리.
- 클라이언트 시간이 바뀌어도 만료 판단이 불일치하지 않도록 서버 시간 기준/오프라인 계산 정책을 정함.
- UI에 elapsed/remain, 선택 장소, 요금(확정/예상/알 수 없음 구분), 픽업 상태 표시.

### F-03 차량 상태와 안전 잠금 [P0 · SDK_VERIFY]
**정책:** 주차 안전 조건이 확인된 경우에만 비운전용 앱 화면 허용. 출발 징후·상태 조회 실패·권한 소실·SDK 해제 시 곧바로 보수적 차단.  
**상태:** `PARKED_CONFIRMED | DRIVING | UNKNOWN | UNSUPPORTED`.  
**주의:** 이 상태는 앱 내부 정규화 모델이며 공식 SDK enum이 아니다. 실제 기어/속도 등의 신호가 읽히는지 확인되지 않았다. Android Automotive 자체 UX restrictions도 존중해야 한다.

| 차량 상태 | Work/Entertainment | Rest 상호작용 | 기본 안내 |
|---|---|---|---|
| PARKED_CONFIRMED | 허용 가능(UX 규정 검증 후) | 허용 가능 | 정차 모드 |
| DRIVING | 차단 | 운전자 산만 조작 차단 | 운행 중 사용 불가 |
| UNKNOWN | 차단 | 주의 상태, 위험 조작 차단 | 차량 상태 확인 불가 |
| UNSUPPORTED | 차단 | 위험 조작 차단 | 지원되지 않는 기능 |

**검증:** 안전 상태 변화 즉시 잠금, 다중 콜백 순서 역전, SDK 예외, 프로세스 복구, 백그라운드 복귀, 화면 터치 차단 여부.

### F-04 픽업 초대 및 웹 연결 [P1 · CONFIRMED]
- 운전자가 현재 세션의 링크를 발급하고 OS 공유 기능으로 전달.
- 링크는 URL 안전 토큰(원문 DB 저장 금지), 만료·취소 가능.
- 상대는 이름(또는 별칭, 선택) 및 공유 안내 동의 후 참가.
- 수동 상태 버튼 `출발 / 곧 도착 / 도착`이 기본. 백그라운드 GPS 자동 수집에 의존하지 않음.
- 이벤트는 승인된 세션의 운전자에게만 보인다. 중복 요청/네트워크 재전송에도 상태 역행이 일어나지 않게 함.
- 종료 시 토큰 사용 중지·Realtime 구독 제거·상태 동기화.
- 개인정보 처리·보존기간은 결정 후 고지. 정확한 현재 위치 공유 여부는 사용자가 별도 확정해야 함.

### F-05 Work Mode: 원격 Windows PC [P1 · SDK_VERIFY]
- 목표: Windows 호스트와 차량의 Pleos 화면이 연결되고, 화면·터치 클릭·스크롤·텍스트 입력이 가능한지 검증.
- 후보: Sunshine 호스트 + Moonlight Android 클라이언트(또는 허용된 방식의 통합). **Moonlight를 타 앱에 쉽게 임베드할 수 있다는 뜻은 아님**. 라이선스, 차량 UX 정책, 배포 가능성, 터치 입력, 네트워크 도달성 확인 필요.
- 안전 잠금 시 우선 입력/화면 차단. 원격 세션 종료·호스트 화면 잠금 동작은 실험으로 확정.
- 호스트 재연결/오프라인, 암호화·페어링, 접근 권한, 화면 스트림 타임아웃, 모바일 테더링 비용 고려.
- 원격 PC 콘텐츠나 로그인 비밀번호를 서버로 전달하지 않음.
- 별도 서드파티 앱 실행 방식과 자체 앱 내장 방식은 **서로 다른 구현안**으로 분리하여 사용자가 선택.

### F-06 Entertainment [P2 · PROPOSED]
- 설치된 허용 앱으로 이동하는 진입 화면 제공.
- 내비게이션/오디오 우선권·차량 앱 런처 정책·패키지 가시성·주행 중 사용 제한 확인.
- 모든 기존 앱 실행·재생 제어 지원을 약속하지 않는다.

### F-07 Rest [P1 · PROPOSED]
- 타이머 10/15/20분(사용자 선택), 종료 알림, 조도 낮춘 테마, 오디오(직접 제공 또는 이용 허락받은 자료).
- 공조/시트 자동 변경은 SDK 권한 및 실제 차종에서 확인될 때만 별도 기능으로 추가.
- 차량 장시간 공회전·배터리·주정차 안전에 대한 과장 없는 안내.

### F-08 운행 복귀 [P0 · CONFIRMED]
- 사용자 종료 또는 주행 전환에 따라 세션 종료 플로우 진입.
- 즉시 업무/영상 입력 비활성화, 원격 PC 세션 잠금 요청(실패해도 차량 화면 잠금 유지), 픽업 링크 취소, 서버 종료 처리, 요약 표시.
- 네트워크 오류 시 종료 명령 재시도 큐와 상태 불일치 복구 규칙 필요.
- 앱에서 차량 기어 또는 운전을 제어하지 않음.

## 5. UX 화면 명세 (PROPOSED)

| 화면 ID | 화면 | 주요 상태·동작 |
|---|---|---|
| UI-00 | Onboarding/권한 | 서비스 설명, 최소권한 요청, SDK 미지원 고지 |
| UI-01 | Home | 목적지 검색, 최근 대기, 새 세션, 차량 상태 |
| UI-02 | Parking Search | 목록/지도, 필터, 로딩·빈 결과·재시도 |
| UI-03 | Parking Detail | 위치, 운영정보·요금·출처, 내비 연결 |
| UI-04 | Waiting Dashboard | 타이머, 요금, 픽업 이벤트, 모드 선택, 종료 |
| UI-05 | Pickup Share | 초대 생성, 링크 복사/공유, 참가 상태·만료 |
| UI-06 | Work Mode | 원격 상태, 재연결, 화면·입력, 즉시 잠금 |
| UI-07 | Entertainment | 허용 앱 바로가기, 경고, 픽업 알림 |
| UI-08 | Rest | 카운트다운, 음성/알림, 종료 |
| UI-09 | Summary | 총 대기시간, 완료 상태, 데이터 삭제 안내 |
| UI-10 | Safety Lock | 운행 중/상태 불명 화면, 입력 차단 |
| WEB-01 | Invitation Landing | 세션 확인/만료/동의 |
| WEB-02 | Pickup Status | 상태 버튼과 합류 위치, 종료 |

**접근성:** 큰 터치 영역, 높은 명암비, 텍스트 확대, 색상 외 추가 상태 표식, 터치 집중 복구. 실제 차량 HMI·심사 기준 검증 전 모바일 UI를 그대로 옮기지 않는다.

## 6. 자체 백엔드 API 계약 초안 (PROPOSED / 미구현)

> 다음 API는 **Pleos 공식 SDK API가 아니라 자체 제작할 서버 API**의 제안이다. 실제 URL·파라미터·인증은 확정 전. 구현 전 OpenAPI 3.1 스키마로 승격해야 한다.

### 6.1 공통 규칙
- Base path: `/api/v1` (제안). 모든 통신 HTTPS.
- 데이터는 UTF-8 JSON, 시간은 UTC ISO 8601, 내부 PK는 UUID.
- 에러 계약 예: `{"error":{"code":"INVITATION_EXPIRED","message":"...","requestId":"..."}}`.
- HTTP 상태: 200/201/204 성공, 400 검증 실패, 401 미인증, 403 권한 부족, 404 없음, 409 상태 충돌, 410 만료, 422 유효성 문제, 429 요청 제한, 5xx 서버 오류.
- idempotency가 중요한 POST에는 `Idempotency-Key` 또는 요청 ID와 서버 unique 제약 사용.
- 클라이언트가 보낸 `driverId`를 무조건 신뢰하지 않고 인증 토큰 주체와 일치하는지 검사.
- 오류에는 비밀 토큰/차량 식별·정확한 위치를 그대로 기록하지 않음.

### 6.2 엔드포인트
| ID | Method | Path | Auth | 설명 |
|---|---|---|---|---|
| API-01 | GET | `/parking/places` | 정책 결정 | 주차장 검색: `lat,lng,radiusMeters,plannedMinutes` |
| API-02 | GET | `/parking/places/{placeId}` | 정책 결정 | 주차장 상세/출처 |
| API-03 | POST | `/waiting-sessions` | 운전자 | 새 대기 세션 |
| API-04 | GET | `/waiting-sessions/{sessionId}` | 소유자 | 상태/시간/모드 |
| API-05 | PATCH | `/waiting-sessions/{sessionId}` | 소유자 | 예정 종료·모드 변경 |
| API-06 | POST | `/waiting-sessions/{sessionId}/end` | 소유자 | 멱등 종료 |
| API-07 | POST | `/waiting-sessions/{sessionId}/invitations` | 소유자 | 1회/만료형 링크 발급 |
| API-08 | POST | `/pickup/invitations/redeem` | 임시 토큰 | 약관 동의 후 참가 |
| API-09 | POST | `/pickup/participants/{participantId}/status` | 참가자 | 상태 전송 |
| API-10 | DELETE | `/waiting-sessions/{sessionId}/invitations/{id}` | 소유자 | 초대 취소 |

### 6.3 예시 DTO
`POST /waiting-sessions`
```json
{
  "parkingPlaceId": "43b94b20-df31-4d62-98bd-e0f32b30c702",
  "plannedMinutes": 30,
  "mode": "REST"
}
```
201 응답:
```json
{
  "id": "c91b4106-b531-428d-9239-5c2f8d347201",
  "status": "ACTIVE",
  "mode": "REST",
  "startedAt": "2026-11-01T02:00:00Z",
  "expiresAt": "2026-11-01T02:30:00Z",
  "revision": 1
}
```
`POST /pickup/participants/{participantId}/status`:
```json
{ "status": "DEPARTED", "clientEventId": "d4383c3b-62b6-42d3-8cba-56942fa75f87" }
```
응답 예:
```json
{ "accepted": true, "status": "DEPARTED", "revision": 2 }
```
정확한 요청/응답 타입, 필수 필드, nullable, 정밀도, 페이지네이션, 정렬, ETag, API 버저닝은 추가 승인 후 OpenAPI로 확정.

### 6.4 클라이언트 도메인 모델
```text
ParkingPlace(id, provider, providerId, title, address, lat, lng,
             openingHours?, pricePolicy?, lastVerifiedAt?, availability?)
WaitingSession(id, ownerId, parkingPlaceId?, startedAt, expiresAt,
               endedAt?, mode, state, revision)
PickupInvitation(id, sessionId, tokenHash, expiresAt, revokedAt?, maxUses)
PickupParticipant(id, sessionId, displayName?, state, lastChangedAt)
SessionEvent(id, sessionId, eventType, createdAt, data, revision)
VehicleSafetyState(value, source, observedAt, freshnessMs)
```
상기 `?`는 선택 필드 표기이며 특정 프로그래밍 언어 문법을 지정한 것이 아님.

### 6.5 상태 전이 규칙
```text
WaitingSession: ACTIVE → ENDING → ENDED
                     └──────────→ EXPIRED
PickupParticipant: INVITED → JOINED → DEPARTED → NEARBY → ARRIVED
                       └────────→ CANCELLED
```
- **제안:** 상태는 전진만 가능하며, UI에서 정정해야 할 경우 별도 재설정 이벤트를 정의.
- `revision`을 증가시켜 지연/중복 이벤트 반영을 방지.
- `ENDED/EXPIRED` 이후 모든 변경 요청은 409/410 정책에 따름.
- Realtime은 진실의 원천이 아니며 DB 확정 결과를 동기화하는 수단이다.

### 6.6 Supabase DB/RLS 설계
테이블 후보: `parking_places_cache`, `waiting_sessions`, `pickup_invitations`, `pickup_participants`, `session_events`.  
권장 인덱스: `waiting_sessions(owner_id, state)`, `pickup_invitations(token_hash)`, `pickup_participants(session_id)`, `session_events(session_id, revision)`.
- 모든 소유자 읽기·쓰기에 RLS와 서버 권한 검사 적용.
- 웹 초대 참가자는 전용 제한 토큰으로 **해당 세션의 필요한 필드만** 접근.
- Supabase `service_role` 키는 서버 전용; Android/브라우저 번들에 절대 삽입하지 않는다.
- 토큰은 해시 저장, 서명·만료·회수 검증. 초대 문자열을 로그/분석에 수집하지 않는다.
- DELETE 정책(물리 삭제 vs 익명화), 데이터 보존기간은 결정 대기.
- 외부 주차 공급자 API 키는 Edge Functions/서버에서 안전하게 관리.

### 6.7 이벤트/동기화
`session.started`, `session.updated`, `session.ended`, `invitation.created`, `invitation.revoked`, `pickup.joined`, `pickup.status_changed`, `safety.locked` 등 이벤트명을 제안.  
모든 이벤트: `eventId`, `sessionId`, `type`, `revision`, `occurredAt`, 최소화된 `payload`. 재연결 시 마지막 revision으로 상태 재조회.

## 7. 외부 API·지도·주차 데이터 (SDK_VERIFY)
- Parking 1차 데이터 원천은 **공공데이터포털 전국주차장정보표준데이터 우선 사용**으로 결정(2026-11-02). 구체 엔드포인트·실제 응답 필드·키 발급/승인·약관·갱신 정책은 REVIEW_REQUIRED. 민간·지자체 확장 공급자는 검증 후 추가. 검색 백엔드는 Node.js + TypeScript + Fastify로 구현하고, 대기/픽업 세션의 Supabase와 책임을 구분한다.
- `ParkingProvider.search(query)` / `details(providerId)` 추상화와 정규화 레이어를 설계.
- 통화 단위(KRW), 시간당/구간별 요금, 기본무료시간, 24시간 운영 예외, 휴무를 명시적으로 모델링.
- 장소 데이터에 출처·갱신시각·확인 수준 표기. 오프라인 캐시는 낡은 데이터를 명시.
- 주차장 요금은 추정일 뿐이고 '실제 청구 금액 보장'이 아님.

## 8. 원격 PC 기술 검증 기준 (SDK_VERIFY)
1. Sunshine(호스트), Moonlight 계열(클라이언트) 조합의 Android Automotive 실행 가능 여부 확인.
2. 차량 HMI/앱 정책상 내장 화면 스트리밍과 다른 앱 실행 중 무엇이 허용되는지 확인.
3. 네트워크 방식: 같은 Wi-Fi, 테더링, 외부망 연결 중 사용자 선택. 외부망 개방 방식은 보안 평가 전 설정 금지.
4. 입력: 터치 좌표 매핑, 클릭, 스크롤, 시스템 키보드, IME, 한글 입력, 해상도 변경.
5. 성능: 화면 갱신률/지연/패킷 손실/재연결 측정. 숫자 목표는 사전 실험 후 결정.
6. 보안: 페어링·암호화·인증·화면 잠금, 네트워크 노출, 자격증명 보존, 라이선스.
7. 차량 주행 전환/안전 상태 불명 시 **스트리밍 화면 및 입력 즉시 차단**.
8. 가상 키보드·제스처 마우스는 별도 연구 문서에서만 다룸.

## 9. 운영·보안·개인정보
- 목적·최소 수집, 이용자 동의, 철회, 만료/삭제 기능을 구현 전 정의.
- 정확한 GPS 이동 이력 수집 금지(명시적 요구·동의·보안 검토 없는 한).
- 앱·웹 로그인 실패/토큰 탈취/Replay/권한 확대/RLS 우회 대비.
- 초대 링크 프리뷰/스크린샷/Referer로 토큰 누출 위험을 검토.
- 브라우저에 참가 상태를 보여주되 운전자 계정 식별자·다른 참가자 정보 노출 금지.
- 저장 데이터 백업과 삭제 방식, 접근 로그 보존 정책 결정.
- 장애 중 안전 게이트는 서버/네트워크에 의존하지 않는다.
- 서버 로그에서 PII·위치좌표·토큰·민감 오디오를 기본적으로 제거.

## 10. 테스트 계획·품질 게이트

### 10.1 최소 테스트 매트릭스
| ID | 시나리오 | 기대 결과 |
|---|---|---|
| T-01 | 지원되는 차량 상태에서 안전 정차 확인 | 제한된 UI 접근 가능 |
| T-02 | 운행 전환 이벤트 | 비운전 UI 및 입력 즉시 차단 |
| T-03 | SDK 상태 UNKNOWN/UNSUPPORTED | 기본 차단, 명시적 설명 |
| T-04 | 주차장 검색 실패/빈 목록 | 재시도/대체 안내 |
| T-05 | 주차 운영·가격 누락 | 데이터 없음, 요금 조작 금지 |
| T-06 | 세션 종료 중복 요청 | 한 번만 종료, 일관된 응답 |
| T-07 | 앱 강제 종료·재실행 | 세션 정합성 복구 |
| T-08 | 초대 만료·취소 링크 접속 | 접근 거부 및 만료 안내 |
| T-09 | 픽업 버튼 연타·네트워크 재시도 | 상태 중복/역행 없음 |
| T-10 | 참가자 임의 세션 조회 | 접근 거부 |
| T-11 | 원격 PC 오프라인 | 오류 안내, 재연결 제공 |
| T-12 | 원격 화면 중 차량 이동 | 즉시 잠금, 입력 누출 없음 |
| T-13 | 위치/알림 권한 거부 | 기능별 대체 동작 |
| T-14 | Realtime 구독 끊김 | 재연결 후 최신 상태 조회 |
| T-15 | 외부 주차 데이터 좌표 불량 | 정규화/유효성 검증 실패 처리 |

### 10.2 단계별 완료 정의
- **계약**: 요구 ID, 입력·출력, 거절/실패 예외, 안전 정책, UI 상태 명시.
- **구현**: 변경 범위 분리, 컴파일, 에러/로깅 포함.
- **검증**: 자동 테스트 + Pleos 에뮬레이터 수동 확인, 날짜·버전·결과 기록.
- **문서**: README·API 변경·미지원/제한을 최신화.
- **보고**: 무엇이 실제 작동하고 무엇이 목업인지 명시.

### 10.3 제안 빌드/테스트 명령 (프로젝트 생성 후 검증)
```powershell
# android/ 디렉터리 (wrapper 준비 후)
.\gradlew.bat assembleDebug
.\gradlew.bat testDebugUnitTest

# web/ 디렉터리 (package.json 스크립트 확정 후)
npm ci
npm run lint
npm run build
```
위 명령은 계획된 기본 검증 예시이며 아직 실제로 실행하거나 성공을 확인하지 않았다.

## 11. 개발 의사결정 목록 (사용자 승인 필요)

| ID | 결정 | 선택 예시 / 영향 |
|---|---|---|
| DEC-01 | 1차 필수 기능 범위 | 주차·픽업·모드·원격 PC의 시연 우선순위 |
| DEC-02 | 팀/레포 운영 | 단일 레포 vs 다중 레포, 공개/비공개 |
| DEC-03 | 지도·주차 제공자 | 카카오/네이버/공공데이터 등 실제 약관·키 |
| DEC-04 | 운전자 인증 | 로그인 없음/익명/소셜 로그인 |
| DEC-05 | 픽업 상태 공유 | 버튼만/위치 전송/합류 장소 표시 범위 |
| DEC-06 | 초대 정책 | 만료시간·참가자 수·재발급·보존기간 |
| DEC-07 | 원격 PC 통합 | 별도 Moonlight 앱 vs 자체 UI 통합 여부 |
| DEC-08 | 지원 환경 | 에뮬레이터만 vs 실제 Pleos 차량 검증 |
| DEC-09 | UI 화면·브랜드 | 컬러·자동차 HMI 스타일·운행 중 정책 |
| DEC-10 | 일정/마감 계획 | **11/01~11/13 개발 완료 목표, 세부 일정은 제안** |
| DEC-11 | 제출 범위 | 소스·빌드·기획서·영상·목업 구분 |
| DEC-12 | 서버 호스팅 | 배포환경·개인정보·비용 및 서비스 키 관리 |

결정 전에는 `TODO(DEC-XX)` 및 어댑터 경계로 유지. 사용자 확인 없이 특정 벤더를 사실상 확정하는 코드를 작성하지 않는다.

## 12. 구현 진행 방식 (11/01~11/13 목표)
1. 사용자와 DEC-01~12를 협의해 미확정 결정과 리스크를 기록하고, 승인된 기능부터 구현한다.
2. SDK API·권한과 Pleos 차량 HMI 지원부터 기술 검증한다.
3. 백엔드 API/OpenAPI·DB/RLS를 확정한다.
4. 각 기능은 **세로 단위(화면 → 서비스 → DB → 테스트)**로 구현한다.
5. 매 PR에 테스트 결과, 구현되지 않은 범위, 안전 조건을 남긴다.
6. 최종 제출 요건·앱 심사·보안 점검은 공모전 최신 공지를 재확인한다.

## 13. 공식 참조 URL 및 확인 일자
- 확인: 2026-11-01
- [현대차그룹 공모전 개발 환경](https://appdevchallenge.auto-hmg.io/guide/development)
- [공모전 FAQ](https://appdevchallenge.auto-hmg.io/faq)
- [Vehicle SDK Introduction](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/Vehicle/intro)
- [Vehicle SDK Maven setup](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/Vehicle/project-setup)
- [Vehicle SDK API capability](https://document.pleos.ai/en/api-reference/connect-sdk-pleos/Vehicle/check-capability)
- [Vehicle SDK Sample App](https://document.pleos.ai/docs/pleos-only/tutorial/vehicle-sdk)
- [NaviHelper Introduction](https://document.pleos.ai/en/api-reference/connect-sdk-pleos/NaviHelper/intro)
- [Pleos SDK 릴리즈 노트](https://document.pleos.ai/docs/pleos-only/release-note/sdk-261015)

> 외부 서비스(Supabase, Sunshine, Moonlight, Next.js)의 상세 버전·라이선스·공식 API 자료는 사용 제품/배포 방법을 확정한 뒤 추가 검증한다. 이 문서는 공식 Pleos API의 전체 메서드 명세를 대체하지 않는다.


## 14. 기능/API 전체 지도 — v0.3 (REVIEW_REQUIRED)

> **중요:** 아래 숫자는 HTTP 라우트 수가 아니라 작업 단위다. 모든 기능에 서버 API를 만들지 않는다. `REST`=서버 엔드포인트, `LOCAL`=Android 내부 도메인/저장소, `SDK`=공식 Pleos/Android API 어댑터, `STREAM`=원격 데스크톱 클라이언트, `REALTIME`=DB 이벤트 구독. 항목은 **설계 후보이며 미구현**. 6장의 기존 엔드포인트 이름을 우선 canonical로 사용한다.

### 14.1 영역별 구현 책임 및 우선순위
| 모듈 | 핵심 목표 | 구현 수단 | 기본 우선순위 | 게이트 |
|---|---|---|---|---|
| Parking | 실제 출처의 주차장 찾기 | REST + ParkingProvider + SDK NaviHelper | P0 | 지도/주차 데이터 공급자 선정 |
| Waiting | 대기 시작/시간/세션 종료 | REST + LOCAL | P0 | 인증·시간 정책 |
| Pickup | 초대 링크와 상대 상태 알림 | REST + REALTIME + 웹 | P0 | 링크 보안·동의 |
| Vehicle Safety | 정차 확인·주행 차단 | SDK + LOCAL | P0 | 차량 신호·배포 정책 검증 |
| Work | PC 화면·마우스·키보드 | STREAM + LOCAL + 선택 REST | P1, **일정 리스크 최상** | AAOS·라이선스·입력/스트리밍 가능성 |
| Study | 집중 시간·할 일·메모 | LOCAL + 선택 REST | P1 | 동기화 범위 |
| Entertainment | 콘텐츠 선택·재생·앱 실행 | LOCAL + Android Media API | P1 | 타사 앱 제어 권한 |
| Rest | 타이머·오디오·테마 | LOCAL + Media3 | P1 | 백그라운드 알림 및 오디오 |
| Lifecycle | 모드 전환·앱 복귀·정리 | LOCAL + REST | P0 | 주행 전환 즉시 잠금 |

### 14.2 기능 상세 계약 — Work
| ID | 작업 | 명세/입출력 | 담당 | 실패 처리/검증 |
|---|---|---|---|---|
| WORK-01 | PC 등록·목록 | `RemoteDevice(id,label,paired,online,lastSeenAt?)`; 등록 방식 미정 | LOCAL/선택 REST | 미등록/오프라인 표시 |
| WORK-02 | 연결 인증 | 사용자가 선택한 PC와 승인/페어링; 자격증명 로컬 안전 저장 정책 **검토 필요** | STREAM | 인증 거부·만료 |
| WORK-03 | 스트림 시작 | deviceId + qualityProfile → Connecting/Connected/Error | STREAM | 연결 제한시간, 재시도 취소 |
| WORK-04 | 영상 출력 | Remote Surface 렌더링, 화면 비율/회전/오디오 | STREAM | 프레임 정지·검은 화면 오류 |
| WORK-05 | 마우스 | 클릭·드래그·우클릭·스크롤, 좌표 매핑 | STREAM | 좌표 벗어남·입력 누출 차단 |
| WORK-06 | 키보드 | 소프트 키보드·IME 구성·특수키 정책 | STREAM | 비밀번호 로그 금지, 입력 포커스 |
| WORK-07 | 화질 설정 | 해상도/FPS/비트레이트 프로필 | LOCAL/STREAM | 미지원 값 fallback |
| WORK-08 | 세션 재접속 | 네트워크/화면 복귀 후 재연결 | LOCAL/STREAM | 주행 중 자동 재연결 절대 금지 |
| WORK-09 | 연결 해제·잠금 | 입력 즉시 차단 → 스트림 정리; PC 잠금 명령은 별도 검증 | LOCAL/STREAM | 서버 응답 없이 즉시 화면 가림 |
| WORK-10 | 피드백 UI | 연결 상태·지연·에러·기기 상태 | LOCAL | 수치 측정불가면 표시하지 않음 |

**운영 원칙:** `/remote-devices`와 `/remote-sessions` 같은 서버 라우트는 **기기간 장기 등록/동기화가 결정된 경우에만** 도입한다. 단일 차량–PC 페어링이면 서버 없이 구현한다. 원격 데스크톱 화면/입력을 Supabase REST나 DB로 릴레이하지 않는다. Moonlight/Sunshine Android 사용/내장/재배포 가능 여부, AAOS 앱 제한 및 입력 캡처를 **11/01에 우선 실험하고 미해결 시 즉시 검토 목록에 기록**하고 기능 구현 범위를 확정한다. 카메라·홀로그램 키보드는 후속 연구.

### 14.3 기능 상세 계약 — Study
| ID | 작업 | 계약 제안 | 담당 | 예외 |
|---|---|---|---|---|
| STUDY-01 | 학습 세션 시작 | `{waitingSessionId,subject?,goalMinutes}` → `studySessionId` | LOCAL / 동기화시 REST | 주차 상태 확인 |
| STUDY-02 | 집중 타이머 | start/pause/resume/complete; 경과시간은 monotonic clock 기반 | LOCAL | 앱 재실행 시 wall-clock 보정 |
| STUDY-03 | 학습 할 일 | create/list/check/delete task | LOCAL / 선택 REST | 오프라인 처리 |
| STUDY-04 | 메모 | note CRUD, 텍스트 우선 | LOCAL / 선택 REST | 동시 수정·저장 충돌 |
| STUDY-05 | 학습 자료 | URL 북마크 또는 PC에서 열기; 문서 동기화는 제외 | LOCAL / STREAM | 외부 링크 안전성 |
| STUDY-06 | 학습 요약 | 실제 집중시간·완료한 목표 수 | LOCAL / 선택 REST | 중복 완료 방지 |
| STUDY-07 | 픽업 인터럽트 | 일시 중단/알림 표시/복귀 | LOCAL | 타이머 상태 복원 |

**선택 서버 엔드포인트 (확정 전 도입 금지):** `POST /study-sessions`, `PATCH /study-sessions/{id}`, `GET /study-sessions/{id}`, `GET/POST /study-sessions/{id}/notes`, `GET/POST/PATCH /study-sessions/{id}/tasks`. 첫 단계는 Room 저장 권장(제안).

### 14.4 기능 상세 계약 — Entertainment
| ID | 작업 | 구체 동작 | 담당 | 제약 |
|---|---|---|---|---|
| ENT-01 | 콘텐츠 홈 | 미디어 종류(오디오·앱 진입점)와 최근 선택 | LOCAL | 외부 추천 API 불필요 |
| ENT-02 | 설치 앱 조회 | allowlist + PackageManager queries 검증 | Android SDK | 보이지 않는 패키지는 표시 안 함 |
| ENT-03 | 외부 앱 실행 | 지원/허용된 앱만 Intent로 실행 | Android SDK | 불가 시 안내; 앱 종료 강제 금지 |
| ENT-04 | 자체 오디오 재생 | play/pause/seek/next | Media3 | 오디오 포커스 정책 |
| ENT-05 | 타사 미디어 상태 | MediaSessionManager 사용 가능 여부 확인 | Android SDK | 접근 권한 없는 앱 제어 금지 |
| ENT-06 | 타사 재생 제어 | 세션 권한·플랫폼 제약이 충족된 경우만 | Android SDK | 무조건 구현하겠다고 가정 금지 |
| ENT-07 | 음량 | 앱 스트림 음량 제어, 차내 전체 음량 정책 검토 | Android SDK | 차량 전역 음량 조작 가정 금지 |
| ENT-08 | 픽업 알림 | 현재 화면에서 알림 오버레이·복귀 행동 | LOCAL | 타사 앱 위 오버레이 권한 별도 검토 |
| ENT-09 | 종료 정리 | 앱 내부 재생 정리 및 백그라운드 해제 | LOCAL | 외부 앱 종료 강제 금지 |

**백엔드:** 자체 콘텐츠 카탈로그가 필요해진 경우만 `GET /media/catalog`을 설계. 지금은 서버 API 필수 아님. YouTube/OTT를 무단 내장 재생하거나 제어하지 않는다.

### 14.5 기능 상세 계약 — Rest
| ID | 작업 | 입력·결과 | 담당 | 실패·제약 |
|---|---|---|---|---|
| REST-01 | 휴식 시작 | durationMinutes, soundId?, darkTheme → restSession | LOCAL | 안전상태 미확인 차단 |
| REST-02 | 타이머 제어 | start/pause/resume/cancel | LOCAL | 앱 중단/시계 변경 |
| REST-03 | 오디오 목록 | 앱에 포함한 사용 허가 오디오 목록 | LOCAL | 저작권 검증 |
| REST-04 | 오디오 재생 | play/pause/loop/volume | Media3 | 오디오 포커스/통화 |
| REST-05 | 화면 테마 | 앱 내 화면 어둡게·밝기 조절 | LOCAL | 차량 전역 밝기 제어 금지 |
| REST-06 | 완료 알림 | 종료 알림/픽업 이벤트 표시 | LOCAL | 알림 권한 거부 대체 |
| REST-07 | 복원 | 화면 복귀·앱 재실행 시 일관된 상태 | LOCAL | 중복 종료 방지 |
| REST-08 | 공조 프리셋 | 현재 Vehicle SDK/권한/심사 확인 전 제외 | SDK_VERIFY | 제어 기능 미지원 처리 |
| REST-09 | 시트 프리셋 | 현재 Vehicle SDK/권한/심사 확인 전 제외 | SDK_VERIFY | 제어 기능 미지원 처리 |

**백엔드:** 휴식 타이머는 오프라인 작동해야 하므로 서버 필수 아님. 휴식 통계가 필요할 때만 `rest_sessions` 저장과 선택 REST 도입.

### 14.6 기능 상세 계약 — 공통
| ID | 세부 기능 | 경계 | 확인할 조건 |
|---|---|---|---|
| PARK-01 | 주변 주차장 조회·거리 정렬 | REST / ParkingProvider | 데이터 출처, 좌표계 |
| PARK-02 | 상세·영업시간·요금 | REST | 최신성, 정보 누락 |
| PARK-03 | 요금 추산 | 서버 또는 클라이언트 순수 함수 | 무료구간·일 최대요금 |
| PARK-04 | 즐겨찾기 | LOCAL 기본 | 계정 간 동기화시 REST |
| PARK-05 | 내비 설정 | NaviHelper SDK | 권한·콜백·실패 |
| WAIT-01 | 세션 생성 | REST | 사용자의 중복 활성 세션 |
| WAIT-02 | 상태 조회·재개 | REST | 세션 만료/권한 |
| WAIT-03 | 시간 변경 | REST | expired 이후 충돌 |
| WAIT-04 | 모드 전환 | LOCAL + 선택 PATCH 세션 | 안전 게이트 |
| WAIT-05 | 종료·만료 | REST | 링크 회수/재시도 |
| WAIT-06 | 지난 대기 조회 | REST 선택 | 개인정보 보존 |
| PICK-01 | 링크 발급 | REST | 임의 UUID URL만으로 인증 금지 |
| PICK-02 | 참가·동의 | REST + WEB | 만료·철회·동의 |
| PICK-03 | 출발/근접/도착 상태 | REST | 유효 상태 전이·중복 |
| PICK-04 | 합류 장소 공유 | REST | 공유 위치 정확도와 동의 |
| PICK-05 | 초대 무효화 | REST | 즉시 토큰 폐기 |
| PICK-06 | 실시간 알림 | REALTIME | 끊김 후 재조회 |
| VEH-01 | SDK 초기화 | Vehicle SDK | 권한·초기화 콜백 |
| VEH-02 | 정차/주행 판정 | Vehicle SDK + LOCAL | **P 확인 신호의 실제 존재 미검증** |
| VEH-03 | capability 검사 | Vehicle SDK | 차종별 지원 |
| VEH-04 | 차량 상태 구독 | Vehicle SDK | 신규/해제/오류 |
| VEH-05 | 안전 차단 | LOCAL | UNKNOWN 포함 fail-closed |
| LIFE-01 | 모드 시작 | LOCAL | 단일 활성 모드 상태 |
| LIFE-02 | 모드 변경 | LOCAL | 원격 리소스 정리 |
| LIFE-03 | 화면 백그라운드 복귀 | LOCAL | 상태·권한 재확인 |
| LIFE-04 | 주행 전환 | LOCAL | 즉시 스트림/터치 차단 |
| LIFE-05 | 종료 요약 | LOCAL + REST 선택 | 세션 멱등 정리 |

## 15. 실제 HTTP API 계약·DTO·권한 — 설계안 v0.3

> canonical base `/api/v1`. **다음은 서버에 실제 구현할 후보만**이다. 6장의 API-01~10 이름은 그대로 유지한다. 프론트엔드가 참조할 OpenAPI 3.1 생성 시 이 표가 우선하며, 동일 기능에 `/sessions` 등의 별칭을 추가하지 않는다. 경로는 확정 전 `REVIEW_REQUIRED`.

| ID | Method | Path (base 제외) | 요청 | 응답 | 권한 | 우선 |
|---|---|---|---|---|---|---|
| API-01 | GET | `/parking/places` | lat,lng,radiusMeters,plannedMinutes,limit?,cursor? | `items:[ParkingPlace]`, nextCursor? | 정책 결정 | P0 |
| API-02 | GET | `/parking/places/{placeId}` | path:placeId | ParkingPlaceDetail | 정책 결정 | P0 |
| API-11 | GET | `/parking/places/{placeId}/estimate` | plannedMinutes | amountKrw?,assumptions[],confidence | 정책 결정 | P1 |
| API-03 | POST | `/waiting-sessions` | parkingPlaceId?,plannedMinutes,mode? | WaitingSession (201) | 운전자 | P0 |
| API-04 | GET | `/waiting-sessions/{sessionId}` | sessionId | WaitingSession | 소유자 | P0 |
| API-05 | PATCH | `/waiting-sessions/{sessionId}` | expectedRevision, plannedMinutes?,mode? | WaitingSession | 소유자 | P0 |
| API-06 | POST | `/waiting-sessions/{sessionId}/end` | expectedRevision?,reason | WaitingSession | 소유자 | P0 |
| API-12 | GET | `/waiting-sessions/history` | cursor?,limit? | items[],nextCursor? | 소유자 | P2 |
| API-07 | POST | `/waiting-sessions/{sessionId}/invitations` | expiresInMinutes?,maxUses? | invitationId,shareUrl,expiresAt | 소유자 | P0 |
| API-08 | POST | `/pickup/invitations/redeem` | token,consent,displayName? | participantId, limitedAccessToken,joinPlace | 초대 토큰 | P0 |
| API-09 | POST | `/pickup/participants/{participantId}/status` | status,clientEventId | accepted,status,revision | 해당 참가자 | P0 |
| API-10 | DELETE | `/waiting-sessions/{sessionId}/invitations/{id}` | invitationId | 204 | 소유자 | P0 |
| API-13 | GET | `/pickup/participants/{participantId}` | participantId | 제한된 PickupParticipant DTO | 소유자/참가자 | P1 |
| API-14 | GET | `/waiting-sessions/{sessionId}/events` | afterRevision? | events[],latestRevision | 소유자 | P1 |
| API-15 | GET | `/me/preferences` | - | preferences | 인증 필요 | P2 |
| API-16 | PATCH | `/me/preferences` | patch | preferences | 인증 필요 | P2 |

**선택 서버 API**: 학습 동기화(`/study-sessions` 계열), 원격 PC 기기 등록(`/remote-devices`), 휴식 통계(`/rest-sessions`), 자체 미디어 카탈로그(`/media/catalog`)는 **범위 미확정**이므로 우선 `LOCAL` 계약만 만들고 REST 서버를 자동 생성하지 않는다.

### 15.1 표준 DTO 제안 (OpenAPI로 이관 대상)
- `ParkingPlace`: id, provider, externalId, name, lat, lng, address?, openingHours?, tariff?, verifiedAt?, availability? (`null`은 정보 없음).
- `WaitingSession`: id, ownerId(서버만), placeId?, status(ACTIVE/ENDING/ENDED/EXPIRED), activeMode(NONE/WORK/STUDY/ENTERTAINMENT/REST), startedAt, plannedEndAt, endedAt?, revision.
- `PickupParticipant`: id, status(JOINED/DEPARTED/NEARBY/ARRIVED/CANCELLED), updatedAt, displayName?.
- `SessionEvent`: id, sessionId, type, revision, occurredAt, payload(최소화).
- `RestSession`: id(local), waitingSessionId, startedAt, durationSeconds, remainingSeconds, soundId?, status.
- `StudySession`: id(local), waitingSessionId, subject?, focusSeconds, status; `StudyTask`, `StudyNote`.
- `RemoteDevice`: id(local), displayName, pairingState, connectionState, capabilities?.
- `ApiError`: `{error:{code,message,requestId,details?}}`; 서버 로그에는 민감 정보 제외.

### 15.2 유효성 및 상태 전이
- plannedMinutes: 허용 범위 **검토 필요**(제안 5~240), 미래 종료시각은 서버에서 계산.
- 주차장 좌표: 위도 -90~90, 경도 -180~180; 반경 상한과 요금 정책 **검토 필요**.
- 단일 활성 대기 세션 정책: **검토 필요**; 채택 시 owner별 DB 제약/트랜잭션.
- 모드: `NONE → WORK/STUDY/ENTERTAINMENT/REST → NONE`; 전환 시 이전 모드 리소스 정리.
- 차량 상태 UNKNOWN/MOVING에서 WORK/STUDY/ENTERTAINMENT/REST 진입 거부. 실제 차량 시그널 확인 전에는 에뮬레이터 안전 시뮬레이션만 표시.
- 픽업 상태: JOINED → DEPARTED → NEARBY → ARRIVED, 취소 가능; 'NEARBY' 자동 GPS 판정 없음.
- 모든 status 변경은 optimistic revision 혹은 명시적 eventId로 중복 처리.
- 만료/종료된 초대는 410, 타인의 세션 접근은 403 또는 정보 노출 최소화를 위해 404 선택(**검토 필요**).
- `Idempotency-Key`를 POST /waiting-sessions, /end, /invitations에 도입하는 방안 **검토 필요**.
- 시간은 UTC ISO8601; 로컬 타이머는 monotonic clock, 재실행 뒤 서버 시간으로 교정.

### 15.3 DB 스키마와 권한 초안
| 테이블 | 핵심 컬럼 | 제약·인덱스 |
|---|---|---|
| waiting_sessions | id, owner_id, place_id, status, mode, started_at, planned_end_at, ended_at, revision | owner+status partial index, revision |
| pickup_invitations | id, session_id, token_hash, expires_at, revoked_at, max_uses, used_count | unique token_hash |
| pickup_participants | id, invitation_id, session_id, display_name, status, updated_at | session_id index |
| session_events | id, session_id, revision, type, occurred_at, payload | unique(session_id, revision) |
| parking_places_cache | id, provider, source_id, payload, cached_at, expires_at | unique(provider,source_id) |
| user_preferences | owner_id, settings, updated_at | PK(owner_id), 선택 |

- 참가자 제한 액세스 토큰은 서버에서 검증하고 SQL RLS/서버 핸들러에서 **해당 참가자·세션의 허용 필드만** 제공.
- Realtime 클라이언트의 채널 접근권한(RLS/private channel)도 반드시 테스트.
- 외부 주차 공급자 API 키·Supabase service_role은 서버 비밀 환경변수.
- 테이블/컬럼 및 인증 제공자는 **검토 필요**. 마이그레이션 전 사용자 승인.

### 15.4 플로우별 API 호출 순서
**주차/대기**
1. `GET /parking/places` → 목록.
2. `GET /parking/places/{id}` + (필요 시) `/estimate`.
3. `NaviHelper` 어댑터에서 목적지 요청(로컬 SDK, HTTP 아님).
4. 사용자 대기 시작 → `POST /waiting-sessions`.
5. `GET /waiting-sessions/{id}`로 복구 및 구독 시작.

**픽업**
1. `POST /waiting-sessions/{id}/invitations` → 공유 URL.
2. 게스트 웹에서 개인정보 안내·동의 → `POST /pickup/invitations/redeem`.
3. `POST /pickup/participants/{id}/status` → DB 확정 → Realtime 통지.
4. 차량 앱은 Realtime 이벤트 수신 뒤 revision 검증/필요하면 재조회.
5. `DELETE invitation` 또는 `POST session/end` 시 참가 토큰 권한 제거.

**모드**
1. 사용자 모드 선택 → Android `SafetyGate.assertParked()` 검사.
2. `ModeCoordinator`가 이전 모드 자원 정리 후 대상 모드 서비스 시작.
3. STUDY/REST: Room, 타이머/오디오; ENTERTAINMENT: Media3/앱 런처; WORK: RemoteDesktopGateway.
4. 서버에는 필요할 때 `PATCH /waiting-sessions/{id}`로 `activeMode`만 동기화. **모드 세부 입력은 HTTP로 전송하지 않음.**
5. 이동 전환 → 로컬 차단 → 모드 종료·원격 입력 차단 → 서버 종료 전송.

## 16. 소스 구조·인터페이스·병렬 에이전트 경계 (PROPOSED)

```text
pleos-stay-plus/
├─ AGENTS.md
├─ DEVELOPMENT_GUIDE.md
├─ docs/
│  ├─ openapi.yaml              # 승인된 HTTP 계약 생성 후
│  ├─ decisions/ADR-*.md
│  ├─ integrations/pleos-sdk.md
│  └─ verification/*.md
├─ android/
│  └─ app/src/main/java/.../
│     ├─ core/safety/           # SafetyGate, VehicleSafetyState
│     ├─ core/network/          # 인증·API 공통
│     ├─ core/navigation/       # 앱 화면/세션 전환
│     ├─ feature/parking/
│     ├─ feature/waiting/
│     ├─ feature/pickup/
│     ├─ feature/work/
│     ├─ feature/study/
│     ├─ feature/entertainment/
│     ├─ feature/rest/
│     └─ integration/{pleos,remote}/
├─ web/                          # Next.js 게스트 링크 웹
├─ supabase/{migrations,functions}/
└─ .github/workflows/
```

### 핵심 클라이언트 계약 (실제 SDK 메서드 이름 아님)
```kotlin
interface SafetyGate {
    val state: StateFlow<VehicleSafetyState>
    fun canEnter(mode: StayMode): Boolean
    fun lockImmediately(reason: String)
}
interface ModeCoordinator {
    suspend fun enter(mode: StayMode): Result<Unit>
    suspend fun exitCurrent(reason: String)
    val active: StateFlow<StayMode>
}
interface RemoteDesktopGateway {
    val connection: StateFlow<RemoteConnectionState>
    suspend fun pair(device: RemoteDevice): Result<Unit>
    suspend fun connect(deviceId: String): Result<Unit>
    suspend fun disconnect()
    suspend fun setQuality(profile: StreamQuality)
    fun sendPointer(event: RemotePointerEvent)
    fun sendKeyboard(event: RemoteKeyboardEvent)
    fun blockInput()
}
interface ParkingRepository {
    suspend fun search(query: ParkingQuery): Result<List<ParkingPlace>>
    suspend fun detail(placeId: String): Result<ParkingPlaceDetail>
}
interface WaitingRepository {
    suspend fun start(request: StartWaitingRequest): Result<WaitingSession>
    suspend fun update(id: String, request: UpdateWaitingRequest): Result<WaitingSession>
    suspend fun end(id: String): Result<WaitingSession>
    suspend fun get(id: String): Result<WaitingSession>
}
```
이 코드는 *독자 설계*로, 패키지 선언·실제 SDK 시그니처·구현이 아니다.

### 에이전트 병렬 작업 규칙
- 계약 책임자(Agent 0): `docs/openapi.yaml`, 공통 DTO, ADR 관리. 다른 에이전트가 계약을 일방 수정하지 않음.
- 차량 Agent A: `android/integration/pleos`, `core/safety`; 실제 SDK 검증 먼저.
- 서버 Agent B: `supabase`, DB·RLS·API.
- Android Agent C: 각 `android/feature/*`, mock repository로 초기 UI 작업.
- 픽업 웹 Agent D: `web`, 승인된 DTO/오류계약 사용.
- 원격/모드 Agent E: `integration/remote`, work/study/entertainment/rest; 안전 게이트 준수.
- 공유 설정/빌드파일 변경은 파일 소유권 합의 후 진행. **서로 같은 파일에 동시 수정 금지**.
- 병렬 개발은 계약 테스트(JSON 샘플·OpenAPI 검증)와 mock server로 먼저 연결한 뒤 실제 API로 교체.

## 17. 2026-11-01~11-13 개발 마스터 플랜 (목표·제안)

> **일정의 날짜는 사용자 지정 목표다.** 세부 날짜별 일감/완료 기준은 초안이며 사용자 검토 후 확정한다. 특정 날짜의 미완료를 허위 완료 처리하지 않는다. 핵심 위험(정차 신호, 원격 PC)은 백엔드보다 빨리 테스트한다.

| 날짜 | 주요 작업 | 구체 산출물/완료 기준 | 병렬 |
|---|---|---|---|
| **11/01 일** | **설계·SDK 기술검증·DB/인증 기반 통합** | 기능 ID·OpenAPI 초안·DTO·ERD·RLS 정책 및 마이그레이션 초안·인증/소유권 테스트·Pleos Vehicle/Navi 신호 확인·Sunshine/Moonlight 기초 연결 실험. **검증되지 않은 항목은 REVIEW_REQUIRED로 기록**. 실제 프로젝트 생성/테스트는 사용자 승인 후 수행 | 계약 + SDK + Backend + Remote |
| **11/02 월** | **Parking API 개발** | 주차 데이터 공급자 선정·연결, 주차장 목록/상세·요금 계산, 출처/갱신시각/누락값 처리, 계약 테스트 | Backend + Data |
| **11/03 화** | Waiting API 개발 | 대기 시작·조회·시간 변경·모드 변경·종료, 유효성·중복요청·복구 테스트 | Backend + Android Mock |
| **11/04 수** | Pickup API 개발 | 초대 토큰 발급·동의·참여·출발/도착·만료/회수, 권한 및 보안 테스트 | Backend + Web |
| **11/05 목** | Realtime 및 기본 통합 | 구독·재접속·중복 이벤트, 픽업 웹 최소 화면, Android ↔ 서버 최소 연동 | Backend + Web + Android |
| **11/06 금** | Work Mode 핵심 개발 | PC 페어링·화면 스트리밍·터치 마우스·키보드·연결해제 구현(11/01 기술검증 결과에 따름) | Remote + Android |
| **11/07 토** | Study Mode 상세 구현 | 집중 타이머·할 일·메모·학습 세션·픽업 인터럽트·복원 | Android |
| **11/08 일** | Entertainment Mode 구현 | 지원 앱 진입점·자체 오디오·미디어 상태·픽업 알림; 타사 제어는 권한 확인 시만 | Android |
| **11/09 월** | Rest Mode 및 Lifecycle 구현 | 휴식 타이머·음원·화면 테마·모드 전환·주행 차단·종료 자원 정리 | Android |
| **11/10 화** | 프론트엔드 본격 통합 | Compose 주차/대기/픽업/4개 모드 UI + Next.js 픽업 웹 연결·상태 화면 | Android + Web |
| **11/11 수** | 전체 플로우 E2E | 주차장 선택→대기→픽업→모드→복귀; 세션·실시간·안전 정책 통합 | 전체 |
| **11/12 목** | 안정화·QA | 권한·네트워크·중복요청·기어 전환·원격 PC 오프라인·RLS·테스트 리포트 | 전체 |
| **11/13 금** | 개발 완료·시연 검증 | APK·웹·시연 흐름 검증, 문서/로그/남은 이슈 동결, 실제/조건부/미구현 구분 | 전체 |

### 중간 게이트 (필수 검토 시점)
- **G0 11/01**: API/DB/인증 기반과 Pleos SDK·원격 PC 조기 기술검증을 같은 날 수행하는 목표. 승인·환경 문제로 실행하지 못한 항목은 미완료/검토 필요로 명시.
- **G1 11/02**: Parking API 실제 공급자·인증·요금 데이터 검증. SDK/원격 PC의 11/01 미해결 리스크는 병렬로 이어가며 대안은 사용자에게 보고.
- **G2 11/05**: Parking·Waiting·Pickup API + 픽업 웹 상태 갱신 최소 E2E 성공. 실패 시 확장을 멈추고 계약/권한 수정.
- **G3 11/11**: 운행 전환 차단 및 데이터 접근 보안 통과. 실패 시 시연 범위 축소를 사용자에게 요청.
- **G4 11/13**: 앱 전체 시연 재현, 실패 기능 목록 공개 및 문서 일치.

### API-first 운영의 의미
1. 11/01에 **OpenAPI 계약 전체를 정의**한다(실제 서버 16개 후보 + 로컬/SDK/STREAM 계약).
2. 11/01에 인증/DB 기반까지 처리하는 목표를 세우며, 11/02부터 Parking API를 구현한다. Android 최소 연결 화면은 SDK 검증을 위해 선행할 수 있다.
3. 11/06~09에 각 모드의 실제 작동을 만들고, 11/10에 프론트엔드 화면을 집중 통합한다.
4. 11/13까지 **API만 많고 실제 서비스 연결이 없는 상태**를 피한다.

### 오늘(11/01) 바로 처리 가능한 선행 작업과 보류
- 문서 설계·API 목록·DB ERD·권한 매트릭스·OpenAPI 초안: 즉시 가능.
- 외부 키·계정·유료 지도 공급자 설정: 승인 및 자격증명 필요.
- 프로젝트 생성·저장소 초기화·GitHub/Notion 게시: **사용자가 별도 승인하기 전 하지 않음**.
- SDK 실제 테스트는 **Android 프로젝트 생성 및 실행 허가 이후** 가능.
- 호스팅·배포, 데이터 자동 삭제는 보존 정책 승인 이후.

## 18. 결정 대기 목록 및 구현 준비 체크리스트

| 결정 ID | 선택/검증 필요 항목 | 에이전트 작업 중 임시 처리 |
|---|---|---|
| DEC-01 | WORK/ STUDY/ ENT/ REST에서 실제 제출 필수 범위 | 모듈별 Gateway 계약만 확정, 임의 기능 약속 금지 |
| DEC-03 | 주차장/지도 API 공급자 및 API key | ParkingProvider mock만 |
| DEC-04 | 운전자 로그인(익명/회원/기기 인증) | 인증 abstraction·RLS 문서만 |
| DEC-05 | 픽업 상대의 실제 GPS 공유 여부 | 버튼 상태·합류 장소만 설계 |
| DEC-06 | 초대 만료(제안 30분), 사용횟수, 동의문구, 저장기간 | 상수 하드코딩 금지 |
| DEC-07 | Moonlight 앱 실행 vs 클라이언트 내장 | RemoteDesktopGateway 인터페이스와 실험 계획 |
| DEC-08 | 실제 차량 vs 에뮬레이터만 | 검증 결과에는 실행 환경 표기 |
| DEC-09 | AAOS/Pleos HMI UX·외부 앱 허용 정책 | 기본 주행 중 비운전 UI 차단 |
| DEC-11 | 제출 가능 원격 PC 영상/저작권/패키지 | 기술 검증과 제품 기능 구분 |
| DEC-12 | 서버 주소·도메인·배포·요금 | 로컬/테스트 환경만 제안 |
| DEC-13 | 학습 메모·할 일의 다중기기 동기화 | Android LOCAL 기본 |
| DEC-14 | PC 페어링/보안 연결/암호화·재접속 | 민감값 저장 금지 및 실험 중 별도 승인 |
| DEC-15 | 자체 오디오 라이선스와 지원 앱 목록 | 안전한 예제/공개 사용 허가 자료 검토 |
| DEC-16 | 차내 정확한 정차 판정 API 및 신선도 기준 | UNKNOWN이면 잠금 유지 |
| DEC-17 | 목표 플랫폼 호환성과 원격 앱 배포 정책 | 공식 문서·실기 테스트 전 조건부 |

### 개발 준비 상태 (현재 기록)
- [x] Android Studio 설치/AVD 기본 UI 구동
- [ ] Android 프로젝트 생성 및 실제 SDK 빌드
- [ ] 안전 관련 상태 수신과 주행 전환 재현
- [ ] OpenAPI 및 DTO를 사용자와 확정
- [ ] Supabase 계정/환경/권한 설정 승인
- [ ] 지도·주차 공급자 확정
- [ ] 원격 PC 기술 검증 결과 기록
- [ ] 개발용 Git 저장소 생성 승인
- [ ] 11/01~11/13 범위 확정

## 19. Parking API v0.2 / Waiting 책임 경계 (2026-11-02, CONFIRMED)
- 오늘 Parking은 **장소 검색·추천·상세·요금 추정·데이터 출처·NaviHelper 좌표 전달**을 담당한다. 서버 경로는 `GET /api/v1/parking/places`, `GET /api/v1/parking/places/{placeId}`, `GET /api/v1/parking/places/{placeId}/estimate`.
- 장소 유형: `PARKING_LOT`, `ON_STREET_PARKING`, `PICKUP_ZONE`, `TIME_LIMITED_ZONE`, `PUBLIC_REST_AREA`. MVP 실데이터 대상은 공영·민영/정식 노상주차장; 나머지는 공식 이용허용 데이터가 확보될 때 활성화하며 그전에는 어댑터 경계만 생성.
- 검색 필터: lat/lng, radiusMeters, plannedMinutes, purpose, freeOnly, limit. 반환: places, total, dataFreshness; 장소별 type, allowedUse, eligibility, source, estimatedFee, feeEstimateStatus, liveAvailability. 상세한 타입·nullable·예시는 `docs/api/openapi.yaml` 및 [Parking 노션 명세](https://app.notion.com/p/40cb74b2b90581cfb9f2e20d22428008)에서 관리.
- 데이터가 없으면 UNKNOWN/null로 반환한다. 현장 표지와 법적 제한보다 추천 점수를 우선하지 않는다. 불법이지만 단속이 약한 일반 갓길 추천 금지.
- Waiting API는 **추천 장소 없이 직접 선택한 위치에서도 대기 세션 시작 가능**. Waiting Safety Monitor는 Waiting 모듈 내 Android 로컬에서 수행하며 Pleos SDK 실제 지원 여부를 검증한다. Parking 서버는 차량 상태 감시/정차 적법성 보증을 담당하지 않는다.
- Fastify Parking 서버와 Android 앱은 계약 일치/오류/캐시 신선도/실제 데이터 수신을 테스트한다. Supabase 별도 서비스와 같은 서버를 강제로 합치지 않는다.

## 20. 공식 SDK·공모전 플랫폼 규정 준수 (필수)
- 개발은 현대차그룹 공모전 가이드, 공식 Pleos Connect SDK API Reference, 공식 권한/호환성 및 Android Automotive UX·주행 중 표시 제한을 준수한다. **공식 지원된 API로 차량 시스템에 접근**한다. 일반 Android/Compose/백엔드 사용 자체는 금지하지 않는다.
- 숨겨진/비공개 API, 리플렉션·루팅·서명 권한 우회, 차량 CAN/센서 직접 접근, 제한을 해제하기 위한 해킹/오버레이는 금지한다.
- 외부 SDK, 공개 라이브러리, 플랫폼 API 사용 시 기능의 공식 지원·배포/라이선스 허용·권한을 확인한다. 공식 문서 확인 → 컴파일 → 에뮬레이터 실제 동작 검증 순서로 결과를 분리 기록한다.
- 검증되지 않은 Vehicle/Navigation/Surrounding API는 `SDK_VERIFY`로 남긴다. 주변 감시 센서 데이터 접근 불가 시 `UNAVAILABLE`로 표시하고 동작을 가장하지 않는다.
- 정책 위반 가능성 또는 자료 부족 시 자의적 우회 구현 없이 `REVIEW_REQUIRED`로 사용자에게 확인한다. 차량 이동/상태 UNKNOWN이면 정차 전용 UI는 로컬에서 즉시 차단한다.
