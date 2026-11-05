# Stay+ Windows 파일 전송 UI 리소스 검토

검토일 2026-11-05. 대상은 Windows에 설치된 .NET 10 Desktop Runtime을 사용하는 framework-dependent WPF 데스크톱 앱이다. 공모전 제출, 소스 공개, Windows용 앱 배포 시 UI와 함께 전달되는 리소스를 기준으로 확인했다.

| 사용 항목 | 출처와 사용 방식 | 배포 조건·고지 처리 |
| --- | --- | --- |
| Segoe UI, Malgun Gothic(맑은 고딕) | Windows 설치 글꼴을 WPF FontFamily 이름으로 참조해 텍스트를 화면에 그린다. | Windows 글꼴은 해당 Windows의 라이선스에 따라 사용한다. 설치된 시스템 글꼴을 앱에서 화면에 쓰는 경우를 Microsoft FAQ에서 확인했다. 글꼴 파일, 웹폰트, 자체 임베딩 폰트를 패키지에 포함하지 않는다. [Microsoft 글꼴 재배포 FAQ](https://learn.microsoft.com/en-us/typography/fonts/font-faq) |
| 파일 보관함 문서 그림과 + 기호 | 이번 화면의 XAML Border·Path·Ellipse·Canvas로 직접 구성한 문서 모양과 일반 텍스트 기호. | 외부 아이콘 세트·SVG·이미지 원본이 없다. 이 그림에 대한 제3자 고지 추가 대상 없음. |
| Stay+ 텍스트 로고와 화면 색상 | 프로젝트 이름을 일반 텍스트로 표시한다. 청록색 강조, 연회색 배경, 카드와 버튼 배치를 프로젝트 XAML로 작성했다. | 현대자동차·PLEOS·Microsoft 공식 로고, Fluent 아이콘이나 공식 제품 스크린샷을 포함하지 않는다. 이 검토 범위는 UI에 사용한 리소스이며 Stay+ 명칭의 상표 등록 가능성이나 공모전 전체 제출 자격에 대한 권리 조사는 별도다. |
| WPF 기본 컨트롤·파일 대화상자 | 기존 .NET WPF와 OS 기본 대화상자를 호출한다. 버튼·탭 모서리와 색상은 직접 작성한 템플릿으로 지정한다. | WPF 소스는 .NET Foundation 및 기여자의 MIT 라이선스로 상업적 사용·재배포를 허용하며 사본·상당 부분을 배포하면 저작권·허가 고지를 유지해야 한다. 현재 배포 방식에서는 WPF 소스나 런타임을 앱에 복사하지 않고 설치된 런타임을 사용한다. [dotnet/wpf 공식 라이선스](https://github.com/dotnet/wpf/blob/main/LICENSE.TXT) |
| 추가 UI 라이브러리·스톡 이미지·외부 폰트·아이콘 패키지 | 사용하지 않음. | 신규 패키지·복사한 외부 리소스에 따른 추가 고지 없음. |

## 배포 시 확인 범위

- 현재 UI 변경에는 별도 라이선스 파일을 동봉해야 하는 신규 제3자 리소스가 없다. 외부 UI 라이브러리 설치와 글꼴 재배포가 필요하지 않다.
- 배포 방식을 self-contained 또는 설치 프로그램에 런타임을 동봉하는 방식으로 바꾸면 실제 포함되는 런타임의 LICENSE 및 ThirdPartyNotices 등 고지를 해당 배포물에서 다시 확인한다.
- 공모전용 소개 이미지나 홍보물에 추후 공식 로고·차량 사진·외부 에셋을 추가할 경우 각 자산의 이용 허가를 별도로 확인한다.

## 화면 배포 설정

배포 담당자가 첫 실행 전에 공개 설정을 제공하면 사용자는 설정 탭을 열지 않고 차량 연결 화면에서 **연결 확인 · 복원**을 누를 수 있다. 기존 사용자에게는 저장된 연결 프로필이 우선한다.

| 환경 변수 | 배포 담당자가 제공하는 값 |
| --- | --- |
| STAYPLUS_DESKTOP_API_URL | 전송 서버의 전체 API 기본 주소. 예시 형식은 `https://<host>/api/v1/realtime/`이며 끝의 슬래시까지 포함한다. |
| STAYPLUS_DESKTOP_AUTH_URL | 해당 프로젝트의 HTTPS Supabase 주소 |
| STAYPLUS_DESKTOP_PUBLISHABLE_KEY | 클라이언트용 publishable key 또는 기존 anon 공개 키 |

운영 서버 미배포 환경에서는 담당자가 연결 정보를 먼저 준비해야 한다. Fake 인증은 개발자 설정에서 선택한 루프백 테스트에만 사용한다. 서버 비밀키는 Windows 앱의 설정 항목이나 배포 리소스로 제공하지 않는다.

