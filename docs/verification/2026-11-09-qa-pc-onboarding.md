# Pleos Stay+ 사용성 QA 후속과 PC 간편 연결

2026-11-09. 최초 작업 기준은 `5a9fd96`이다. 팀이 Entertainment를 제거한 `5b66be8`을 받아 해당 버전을 최종 부모로 삼았다. 기존 Entertainment 파일·화면·리소스 삭제와 앱 복원 시 오래된 화면 경로를 정리하는 로직을 유지했다.

| 기능 | QA 문제와 확인한 원인 | 수정 | 검증 및 남은 일 |
| --- | --- | --- | --- |
| Parking | SDK가 전달한 좌표는 지리적으로 유효하면 국내 공급 범위 밖이어도 검색에 쓰였다. 보고된 경도는 국내 공급 범위 밖이다. 좌표를 실제로 공급한 에뮬레이터 설정·캐시·신호는 현장에서 재현하지 않았다. | 국내 공급 범위 밖의 위치를 조회 전에 걸러 이전 위치와 결과를 지운다. 권한·위치 설정·수신 실패 안내와 Debug 입력을 분리한다. 조건 변경은 버튼을 다시 눌러 적용하고 이때 키보드를 닫는다. 상세에서도 검색 시 계산된 요금을 표시한다. 0으로 채워진 일일권 필드 때문에 유료 기본·추가 요금 계산이 막히던 케이스를 처리한다. | PASS JVM 위치·필터 및 서버 요금 회귀. NOT RUN 실제 차량·에뮬레이터 위치 원인, 공개 공급자 실데이터 비율. |
| Pickup | 설정이 비면 서버가 localhost 공유 주소를 발급했다. 모바일의 localhost는 해당 모바일 기기를 뜻한다. 공유 페이지의 브라우저 주소가 서버 렌더링과 달라질 여지도 있었다. | 승인된 HTTPS 웹 원점이 비었거나 잘못되면 초대 저장 전에 503을 반환한다. 차량은 기존 로컬 링크를 QR로 표시하지 않는다. 모바일 웹은 링크 형식·백엔드 설정 오류를 안내하고, 같은 출처 프록시·인증·동의·만료·종료 계약을 유지한다. | PASS 초대 설정·만료·철회·종료 API와 로컬 Next.js 페이지. BLOCKED 공개 QR 스캔과 타 기기 참가. |
| Realtime | 기존 앱은 서버 설정 화면을 거쳐 등록하고, 양쪽에서 수동으로 상태를 새로 고쳐야 했다. | 빌드에 서비스 기본값을 주입한다. 차량 코드 발급과 Windows 코드 입력, 양쪽 승인, 만료·취소·서버 오류·연결 해제 상태를 연결했다. 파일 목록과 이전 전송 경로를 재사용한다. | PASS 실제 Fastify와 Windows C# 클라이언트의 승인·파일 왕복. BLOCKED 외부 네트워크의 차량과 Windows GUI 왕복. |
| Study / Work | 연결 상태가 모호하면 문서 자료함을 검증하기 어렵다. | 같은 Realtime 연결·인증·PIN과 자료함 조회를 사용한다. Realtime 화면에서 PC 미연결·서버 실패·파일 없음이 구분된다. DOCX 수동 전송과 원본·버전·해시 정책을 유지한다. | PASS Android JVM 회귀와 로컬 TXT/PDF/DOCX 전송 및 DOCX XML 재열기. NOT RUN 차량 Study 읽기·Work 편집과 전송의 전체 UI 흐름. |
| Waiting / Rest | 화면 이탈 후 타이머 복귀가 눈에 띄지 않고 선택 장소를 도착 지점으로 오해할 수 있었다. | 기존 내비게이션·안전 게이트로 진행 중인 대기·휴식 바로가기를 제공한다. Waiting 예정 시간을 넘기면 전경에서 안내한다. 선택 장소와 도착 여부를 구분하고 활성 Rest 프리셋을 표시한다. | PASS 기존 타이머·화면 이동·오디오 포커스 JVM 회귀. NOT RUN 차량 오디오·Suspend와 백그라운드 Waiting 알림. 전경 안내만 구현했다. |

위치 소스는 [PLEOS FusedLocation](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/FusedLocation/intro)이며 마지막 알려진 위치와 업데이트 콜백을 사용하는 기존 게이트웨이를 유지했다. 요금 규칙은 [전국주차장정보표준데이터](https://www.data.go.kr/data/15012896/standard.do)의 기본·추가 단위 요금을 사용한다. 복합 할인·일일권·미기재 조건은 추정하지 않는다. 백그라운드 정확한 시각에 알림을 보장하려면 플랫폼 및 권한 조건 확인이 필요하다. [Android 알람 가이드](https://developer.android.com/develop/background-work/services/alarms)

## Realtime 사용자 흐름

1. 정차 상태에서 차량 앱 **파일 작업**을 열고 기존 자료함 PIN을 설정하거나 확인한다. **PC 연결하기**에서 8자리 코드와 남은 시간이 표시된다.
2. QR을 스캔하면 웹의 **Stay+ PC 연결** 안내가 열린다. 주소를 PC로 보낼 수 있다. Windows Stay+ 프로그램이 이미 있다면 차량 화면의 코드를 곧바로 입력할 수 있다.
3. Windows Stay+의 **차량 연결**에서 8자리 코드를 입력하고 **연결 요청**을 누른다. 앱은 배포 기본값으로 서버에 인증하고 기기를 등록한다.
4. 차량에서 요청한 PC 이름을 확인해 승인하고 Windows에서도 차량을 승인한다. 두 승인이 끝난 뒤 서버의 활성 연결과 자료함 조회를 확인한다. 예전에 만든 연결에 기기 이름 정보가 없으면 앱은 일반 연결 상태를 표시한다.
5. Work의 공개 내보내기를 완료한 파일은 **차량 파일 보내기**에서 선택·승인한다. Windows에서 목록을 새로고침해 해당 버전을 수동 다운로드한다.

QR에는 공개 안내 페이지 주소만 들어간다. 연결 코드와 인증 정보는 URL에 넣지 않는다. 웹 안내 페이지에서 파일 접근 권한을 부여하지 않으며 PC 프로그램에서 코드를 입력한다. 코드가 만료되면 차량에서 재발급한다. 서버 요청 오류와 서버가 확인한 차량·PC의 페어링 상태는 별개로 표시한다. PC 프로그램의 현재 실행 여부를 측정하는 기능은 없다.

## 배포 시 설정

- Android 빌드 환경 또는 개발자 `android/local.properties`에 `REALTIME_API_BASE_URL`을 HTTPS Fastify의 `/api/v1/realtime/` 주소로, `REALTIME_PAIRING_PAGE_URL`을 승인된 Next.js의 `/connect` 주소로 설정한다. Realtime 인증 프로젝트가 별도면 `REALTIME_SUPABASE_URL`과 `REALTIME_SUPABASE_PUBLIC_KEY`도 지정한다. 비어 있으면 앱의 기존 Supabase 공개 설정을 사용한다.
- Windows 빌드에 동일한 `REALTIME_API_BASE_URL`, `REALTIME_SUPABASE_URL`, `REALTIME_SUPABASE_PUBLIC_KEY`를 주입한다. 새 기본값에는 Supabase publishable key만 사용한다. 서버 전용 키는 서버에만 둔다. 기존 Windows 설정은 복구할 수 있으며 수동 주소·로컬 테스트 설정은 개발자 영역에 있다.
- Pickup Backend에는 `PUBLIC_WEB_BASE_URL`로 별도 승인된 HTTPS 웹 원점을 넣는다. Next.js 프로덕션에는 `PICKUP_BACKEND_URL`로 Fastify의 `/api/v1/` 주소를 명시한다. Next.js와 Fastify를 한 호스트에서 구동할 경우 명시적 loopback 프록시 주소도 허용한다. 브라우저에 배포하는 것은 기존 Supabase 공개 설정과 지도 JS 설정이다.
- 기존 Realtime Fastify가 PC와 차량 양쪽에서 접근 가능한 HTTPS 주소여야 한다. 기존 Supabase 인증, HMAC 코드, 짧은 만료·시도 제한, 양쪽 승인, Private Storage와 파일별 권한을 사용한다. 새 WebSocket, DB 스키마, 앱 인증 정책을 추가하지 않았다. 이번 작업에서는 공개 주소·호스팅을 정하지 않았다.

## 검증 기록

- **PASS** Backend 30개, TypeScript·빌드·OpenAPI. Pickup과 Parking 기존 회귀 포함.
- **PASS** Next.js 6개, 타입 검사 및 프로덕션 빌드. 로컬 HTTP에서 `/connect`, 링크 형식에 맞는 공유 안내, 잘못된 Pickup 링크 안내, 인증 누락과 백엔드 미설정 응답을 확인했다.
- **PASS** Windows 빌드 0오류·0경고. 격리된 루프백 Fastify에 실제 C# 클라이언트와 차량 역할의 테스트 클라이언트를 붙였다. 코드 클레임과 PC 이름, 한쪽 승인 시 연결 없음·파일 미노출, 양쪽 승인, TXT/PDF/DOCX 원본·수정본, 최초 차량 DOCX 업로드·Windows 수동 다운로드·SHA-256·XML 재열기, 연결 철회를 확인했다.
- **PASS** 팀 삭제 커밋을 반영한 최종 Android Debug·계측 테스트 APK 빌드와 Release Kotlin 컴파일. Debug JVM 196개 통과와 Release Parking 7개 통과. 두 빌드에서 반대 변형 전용 테스트 1개씩 제외.
- **BLOCKED** 공개 HTTPS 주소와 외부 배포가 필요한 기기간 연결·Pickup 외부 참여. Supabase 운영 데이터와 운영 인증은 사용하지 않았다.
- **NOT RUN** 요청대로 에뮬레이터를 실행하지 않았다. 차량과 Windows GUI를 직접 조작하는 계측 테스트, 실제 QR 스캔, 차량 위치·키보드·오디오·Suspend는 기기에서 확인해야 한다.

변경 파일은 이 보고서가 포함된 커밋에 기록된다. 주요 코드 경로는 Android의 `parking/ui`, `pickup/ui`, `realtime`, `ui/premium/StayAppShell.kt`, `waiting/ui/WaitingScreen.kt`, `rest/RestScreen.kt`, Backend의 `provider.ts`와 `pickup/service.ts`, Next.js의 `app/connect`·Pickup 웹 경로, Windows의 `StayPlus.Desktop` 및 `StayPlus.LocalTests`다.
