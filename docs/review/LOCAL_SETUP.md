# 로컬 설정·실행

## 필요한 환경

- Android SDK 및 프로젝트가 사용하는 플랫폼 패키지, JDK 17 이상(현재 PC는 Android Studio 번들 JBR 사용)
- Node.js 20 이상 / npm
- Windows Stay+ 앱 빌드: .NET 10 SDK, 실행: 해당 .NET Desktop Runtime
- 승인된 공공데이터 API 키 및 Supabase 프로젝트 설정

에뮬레이터나 Android Studio를 자동 실행하는 명령은 포함하지 않습니다. 기존 로컬 설정 파일이 있다면 예제로 덮어쓰지 마세요.

## 환경변수 파일

| 위치 | 역할 | Git |
|---|---|---|
| `backend/.env` | Parking 키, Pickup 서버 인증·저장·공개 웹 주소 | 제외 |
| `backend/src/realtime/.env.local` | Realtime Supabase 서버 설정·HMAC 비밀키 | 제외 |
| `web/.env.local` | 웹 공개 키, 서버 전용 Pickup 프록시 대상 | 제외 |
| `android/local.properties` | SDK 경로, Android용 Supabase URL·publishable key | 제외 |

서버 secret/service-role/HMAC 값은 Android·웹 공개 변수·Git에 넣지 않습니다. `NEXT_PUBLIC_*`는 브라우저에 공개됩니다. 예제에는 실제 비밀값을 넣지 않습니다.

## Parking / Pickup — 포트 3000

저장소 루트에서:

```powershell
cd backend
# 최초 설정일 때만 .env.example을 .env로 복사하고 로컬에서 값 입력
npm ci
npm run dev
```

빌드 실행은 `npm run build` 후 `npm start`입니다. 두 방식 모두 `backend/.env`를 읽으며, 이미 지정한 프로세스 환경변수가 우선합니다. [Backend 세부 설정](../../backend/README.md)을 참고하세요.

Pickup 실데이터 저장에는 기존 승인 프로젝트의 스키마/RPC·Anonymous Auth 설정이 필요합니다. **저장소를 받았다는 이유로 SQL 전체를 자동 적용하지 마세요.** 이미 적용된 버전은 [Supabase 문서](../../supabase/README.md)와 검증 기록을 대조합니다.

## Pickup 웹 — 포트 3001

별도 터미널:

```powershell
cd web
# 최초 설정일 때만 .env.example을 .env.local로 복사
npm ci
npm run dev
```

`PICKUP_BACKEND_URL=http://127.0.0.1:3000/api/v1`는 Next.js 서버 전용입니다. 다른 휴대전화가 접속하려면 승인된 HTTPS 웹 주소를 `PUBLIC_WEB_BASE_URL`에 설정해야 합니다. localhost 링크는 공유용 QR로 발급하지 않습니다.

Kakao 지도는 발급된 JavaScript 키, API 활성화, **접속하는 정확한 웹 origin** 등록이 필요합니다. 터널 주소가 바뀌면 이전 등록이 새 주소를 허용하지 않습니다. 이 가이드에서는 배포나 터널 설치를 수행하지 않습니다.

## Realtime — 포트 3012

별도 터미널, `backend` 폴더에서:

```powershell
node --import tsx src/realtime/supabase-local-server.ts
```

기존 승인 프로젝트의 `backend/src/realtime/.env.local` 설정과 `REALTIME_SUPABASE_ENABLED=true`가 필요합니다. 이 어댑터에는 기존 만료 데이터 정리 작업이 있으므로 대상 프로젝트와 운영 권한을 확인한 뒤 실행하세요. [Realtime 실행·보안 계약](../realtime/LOCAL-IMPLEMENTATION.md)을 참고합니다.

Windows 앱:

```powershell
dotnet build windows/StayPlus.Desktop/StayPlus.Desktop.csproj
& ".\windows\StayPlus.Desktop\bin\Debug\net10.0-windows\Stay+.exe"
```

공개 배포 환경이 미설정인 검토본은 로컬 서버 준비가 필요합니다. Windows 설정에는 같은 서버 주소와 공개 Auth 설정을 사용합니다. 서버 비밀키는 입력하지 않습니다. 차량 앱의 **PC 연결하기 → 8자리 코드 → PC 입력 → 양쪽 승인** 후 실제 연결이 확정됩니다.

`3002`는 이 흐름에 필요하지 않습니다. `3010`은 격리된 로컬 테스트 어댑터용이며 실제 Supabase 어댑터 `3012`와 구분합니다.

## Android Debug APK

```powershell
cd android
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug --max-workers=1
```

출력: `android/app/build/outputs/apk/debug/app-debug.apk`

기본 Debug 대상은 Parking/Pickup `http://10.0.2.2:3000/api/v1/`, Realtime `http://10.0.2.2:3012/api/v1/realtime/`입니다. Supabase URL·publishable key를 무시된 `android/local.properties` 등에 설정합니다. 실제 기기와 외부 네트워크에는 승인된 HTTPS 대상이 필요합니다. 이 APK는 로컬 시연용이며 정식 서명 Release가 아닙니다.

## 자동 검증

```powershell
# backend
npm test
npm run typecheck
npm run build
npm run validate:openapi
node test/run-windows-local.mjs

# web
npm test
npm run typecheck
npm run build

# android
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug --max-workers=1
```

Cloud 쓰기 검증은 별도 승인 대상입니다. 서버·APK 빌드와 자동 테스트는 실제 PLEOS 신호·오디오·내비 수락·휴대전화 지도 성공을 대신하지 않습니다.
