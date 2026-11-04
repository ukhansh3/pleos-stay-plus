import { LocalCheckpoint } from "./local-checkpoint.js";
import { join } from "node:path";
import Fastify from "fastify";
import { randomBytes } from "node:crypto";
import { LocalIdentityVerifier } from "./auth.js";
import { LocalPairingService } from "./local-pairing-service.js";
import { LocalTransferService } from "./local-transfer-service.js";
import { LocalStorageGateway } from "./storage-gateway.js";
import { registerRealtimeRoutes } from "./routes.js";
// Explicit standalone local harness; never imported by the production entry point.
if (process.env.NODE_ENV === "production" || process.env.REALTIME_LOCAL_DEMO !== "true") throw new Error("Set REALTIME_LOCAL_DEMO=true in a non-production environment");
const pc = process.env.REALTIME_LOCAL_PC_TOKEN;
const car = process.env.REALTIME_LOCAL_CAR_TOKEN;
if (!pc || !car || pc.length < 32 || car.length < 32 || pc === car) throw new Error("Two distinct local credentials (32+ characters) are required");
const app = Fastify({ logger: false });
const pairing = new LocalPairingService(process.env.REALTIME_LOCAL_PAIRING_SECRET ?? pc);
const transfer = new LocalTransferService(pairing, new LocalStorageGateway(process.env.REALTIME_LOCAL_STORAGE ?? ".realtime-local"), Number(process.env.REALTIME_MAX_BYTES ?? 20*1024*1024));
const checkpoint=new LocalCheckpoint(join(process.env.REALTIME_LOCAL_STORAGE ?? ".realtime-local","metadata.json"),pairing,transfer);
checkpoint.load(); await transfer.cleanup(); checkpoint.save();
app.addHook("onSend",async (_request,_reply,payload)=>{checkpoint.save();return payload;});
await registerRealtimeRoutes(app, pairing, transfer, new LocalIdentityVerifier(new Map([[pc, "local-pc"], [car, "local-car"]])));
const address=await app.listen({ host: "127.0.0.1", port: Number(process.env.REALTIME_LOCAL_PORT ?? 3010) });
console.log(`LOCAL_READY ${address}`);

