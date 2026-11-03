import { notFound } from "next/navigation";
import { PickupSimulator } from "../../../components/PickupSimulator";
export default function SimulatorPage(){if(process.env.ENABLE_PICKUP_SIMULATOR!=="true"||process.env.NODE_ENV==="production")notFound();return <PickupSimulator/>;}
