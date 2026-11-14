# 2026-11-14 Windows 차량 승인 버튼 처리 수정

시작 HEAD: dev / 6c8708bd00f145dde306fe7d3edd900692a65f4a.

## 재현 원인

Windows에서 ‘이 차량 승인’을 누르면 MainWindow.Run이 먼저 Pages.IsEnabled=false로 설정한다. Approve는 서버 페어링 정보를 가져와 DescribePair를 호출한 뒤 ApproveVehicle.IsEnabled를 승인 가능 여부로 검사했다. WPF는 부모가 비활성화돼 있으면 자식의 IsEnabled에 true를 설정해도 실제 값은 false이므로 승인 확인 팝업과 POST approve 전에 반환했다.

사용자 화면은 양쪽 버튼을 누른 상황이었지만, 확인 시점 서버에는 승인 POST가 한 번만 기록돼 있었다. 이는 서버 연결 완료와 다른 상태이며 사용자 조작 실수로 판정하지 않는다.

## 변경

- PairingApproval.CanApprove: 서버의 CLAIMED 상태, 차량/PC 식별 정보, 승인 목록으로 승인 전제조건을 판단한다.
- 화면 버튼 표시와 승인 핸들러가 동일한 판정 함수를 사용하며, 핸들러는 WPF의 실제 IsEnabled 값을 권한 판단에 사용하지 않는다.
- 기존 차량명 확인 팝업 및 명시적 사용자 승인, 서버의 양쪽 승인·인증·파일 접근 통제는 유지한다.
- 이미 CONNECTED인 서버 응답은 연결/파일 목록을 다시 조회한다.

변경 파일:

- windows/StayPlus.Desktop/PairingApproval.cs
- windows/StayPlus.Desktop/MainWindow.xaml.cs
- windows/StayPlus.Desktop/MainWindow.Presentation.cs
- windows/StayPlus.LocalTests/Program.cs
- windows/StayPlus.LocalTests/StayPlus.LocalTests.csproj
- 본 기록

## 검증

- PASS: 실제 WPF StackPanel/Button 객체에서 부모 busy 시 자식 IsEnabled=false 재현. 수정 판정은 같은 상황에서 true를 유지한다.
- PASS: OPEN/CONNECTED/EXPIRED/CANCELLED, PC 중복 승인, 차량 누락, 승인 목록 누락, null 응답은 승인 불가.
- PASS: Windows 로컬 테스트 9개 출력 그룹. 배포 설정/전송 제한, WPF 회귀, Auth 갱신, 실제 C# HTTP 클라이언트 양쪽 승인·파일 접근 차단, TXT/PDF/DOCX 원본/수정본/해시/멱등, 차량 DOCX→PC 다운로드 및 재열기, 철회 후 접근 거부.
- PASS: Windows Debug/Release 빌드(경고·오류 0).
- PASS: git diff --check.
- 실제 사용자 EXE는 기존 Debug 경로에서 갱신·재실행했다. 기존 서버·에뮬레이터와 저장된 인증·사용자 파일을 보존했다.
- NOT RUN/PENDING: 실제 사용자와 Cloud 서버의 최종 양쪽 승인 완료. 재실행 후 새 코드로 사용자 수동 재확인을 요청했다. 로컬 테스트를 실제 연결 완료로 보고하지 않는다.

새 패키지/운영 SDK/서버/DB/RLS/인증 정책 변경 없음. 테스트 프로젝트는 앱이 이미 사용하는 WPF 프레임워크로 UI 속성 회귀를 검사한다. 자동 승인·테스트 토큰으로 사용자 연결을 우회하지 않았다. 수정 당시 Git push는 하지 않았다.
## 수정 EXE 재실행 후 실제 서버 관측

수정 EXE가 실행된 후 기존 사용자 Realtime 서버(3012)에서 새 승인 POST 2건이 HTTP 200으로 처리됐고, 파일 목록 GET도 2건 HTTP 200으로 처리됐다. 요청 Content-Type 표시는 Android 쪽 application/json과 Windows 쪽 NONE을 구분해 확인했다. 별도 로컬 테스트 서버 요청은 이 로그에 포함되지 않는다.

따라서 실제 Cloud 연결에서 승인 요청·연결 권한을 가진 파일 목록 조회까지 진행됨을 확인했다. 양쪽 UI 연결 완료 표시, 실제 사용자 파일 업로드/다운로드는 사용자 확인 전까지 미확인으로 유지한다. 자동으로 승인 버튼이나 확인 팝업을 누르지 않았다.

## 사용자 후속 확인

2026-11-14 사용자가 수정 EXE에서 연결이 정상적으로 되고 파일 전송도 정상 동작한다고 확인했다. 이는 사용자 실제 UI/전송 확인이며, 에이전트가 실사용 파일 내용을 열람하거나 그 파일의 해시를 별도로 검증했다는 뜻은 아니다.
