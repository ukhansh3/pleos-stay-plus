import { CopyConnectAddress } from "../../components/CopyConnectAddress";

export default function ConnectPage() {
  return <main className="panel stack">
    <h1>Stay+ PC 연결</h1>
    <p>Windows Stay+ 프로그램에서 차량에 표시된 8자리 코드를 입력하세요.</p>
    <ol className="stack">
      <li>PC에서 팀이 제공한 Stay+ 프로그램을 실행합니다. 프로그램이 없으면 관리자에게 설치 파일을 요청하세요.</li>
      <li><strong>차량 연결</strong>을 열고 차량의 8자리 코드를 입력합니다.</li>
      <li>PC와 차량에서 상대 기기를 확인하고 각각 승인합니다.</li>
    </ol>
    <p>이 페이지를 휴대전화에서 열었다면 주소를 PC로 보내도 됩니다. 코드는 Windows 프로그램에서 입력합니다.</p>
    <CopyConnectAddress/>
  </main>;
}
