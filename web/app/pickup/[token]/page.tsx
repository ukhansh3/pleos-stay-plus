import { PickupPanel } from "../../../components/PickupPanel";
export default async function PickupPage({params}:{params:Promise<{token:string}>}) { const {token}=await params; return <PickupPanel invitationToken={token}/>; }
