import { SharePanel } from "../../../components/SharePanel";
export default async function SharePage({params}:{params:Promise<{token:string}>}) { const {token}=await params; return <SharePanel token={token}/>; }
