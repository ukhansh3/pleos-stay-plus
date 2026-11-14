# Stay+ Windows 파일 전송 프로그램

**[Windows x64 ZIP 내려받기](https://github.com/ukhansh3/pleos-stay-plus/releases/download/submission-2026-11-14/StayPlus-Windows-x64.zip)** · [전체 Release](https://github.com/ukhansh3/pleos-stay-plus/releases/tag/submission-2026-11-14)

1. 압축을 풀고 `Stay+.exe`를 실행합니다. 별도 .NET 설치나 로컬 서버 실행은 필요하지 않습니다.
2. 차량의 **파일 작업 → PC 연결하기**에서 발급한 8자리 코드를 입력합니다.
3. 차량과 PC 양쪽에서 기기를 확인하고 승인합니다.
4. TXT/PDF/DOCX 원본 또는 새 버전을 선택해 수동 송수신합니다. 업로드 완료와 PC 다운로드 완료는 별개입니다.

이 패키지는 Windows x64 self-contained Release입니다. 별도 설치 프로그램이나 Windows 코드 서명 인증서는 포함하지 않습니다. 실행 전 Release의 SHA-256과 출처를 확인하세요. 서버가 휴면 중이면 잠시 기다린 뒤 재시도합니다.

- [앱·APK·환경 안내](../README.md)
- [소스 빌드·개발 설정](../docs/review/LOCAL_SETUP.md)
- [검증 결과와 미실행 항목](../docs/verification/2026-11-14-submission-release.md)

`StayPlus.Desktop/`는 WPF 클라이언트, `StayPlus.LocalTests/`는 로컬 테스트입니다. 소스·전송·인증 계약은 기존 구현을 그대로 사용합니다. 서버 secret과 개인 서명키는 포함하지 않습니다.
