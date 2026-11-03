import type { Metadata } from "next";
import "./styles.css";

export const metadata: Metadata = { title: "Pleos Stay+ Pickup", description: "Pickup 위치·상태 공유" };
export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return <html lang="ko"><body>{children}</body></html>;
}
