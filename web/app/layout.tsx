import type { Metadata, Viewport } from "next";
import "./styles.css";
import "./pickup-mobile.css";

export const viewport: Viewport = { width: "device-width", initialScale: 1, viewportFit: "cover", themeColor: "#091322" };
export const metadata: Metadata = { title: "Pleos Stay+ Pickup", description: "Pickup 위치·상태 공유" };
export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return <html lang="ko"><body>{children}</body></html>;
}
