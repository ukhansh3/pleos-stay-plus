import { notFound } from "next/navigation";
import { headers } from "next/headers";
import { PickupSimulator } from "../../../components/PickupSimulator";

function isLoopbackHost(host: string | null): boolean {
  if (!host) return false;
  const hostname = host.startsWith("[") ? host.slice(1, host.indexOf("]")) : host.split(":")[0];
  return hostname === "localhost" || hostname === "127.0.0.1" || hostname === "::1";
}

export default async function SimulatorPage() {
  const host = (await headers()).get("host");
  if (process.env.ENABLE_PICKUP_SIMULATOR !== "true" || process.env.NODE_ENV === "production" || !isLoopbackHost(host)) notFound();
  return <PickupSimulator />;
}
