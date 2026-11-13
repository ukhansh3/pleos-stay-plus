import { SharePanel } from "../../../components/SharePanel";
import { PickupEndScreen } from "../../../components/PickupPanel";
export default async function SharePage({ params }: { params: Promise<{ token: string }> }) {
  const { token } = await params;
  if (!/^[A-Za-z0-9_-]{43}$/.test(token)) return <PickupEndScreen end={{ kind:"ended", message:"초대 링크가 올바르지 않아요. 차량에서 새 QR을 받아 주세요." }} />;
  return <SharePanel token={token} />;
}