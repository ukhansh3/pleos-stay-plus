import { SharePanel } from "../../../components/SharePanel";
export default async function SharePage({params}:{params:Promise<{token:string}>}) { const {token}=await params;
 if (!/^[A-Za-z0-9_-]{43}$/.test(token)) {
   return <main className="panel stack"><h1>픽업 링크를 열 수 없습니다</h1><p>운전자에게 새 공유 링크를 요청하세요.</p></main>;
 }
 return <SharePanel token={token}/>; }
