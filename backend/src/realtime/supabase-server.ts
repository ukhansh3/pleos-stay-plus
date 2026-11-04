import Fastify from "fastify";
import { SupabaseIdentityVerifier } from "./auth.js";
import { SupabaseRealtimeRepository } from "./supabase-repository.js";
import { SupabaseStorageGateway } from "./supabase-storage-gateway.js";
import { SupabasePairingService,SupabaseTransferService } from "./supabase-services.js";
import { registerRealtimeRoutes } from "./routes.js";
/** Construction alone performs no I/O. Explicit deployment/configuration is a separate approval. */
export async function buildSupabaseRealtimeServer(config:{url:string;publicKey:string;serverSecret:string;pairingSecret:string;maxBytes?:number},transport:typeof fetch=fetch){
 const paths=new Map<string,string>();
 const db=new SupabaseRealtimeRepository(config.url,config.serverSecret,transport);
 const storage=new SupabaseStorageGateway(config.url,config.serverSecret,id=>{const p=paths.get(id);if(!p)throw new Error("UNKNOWN_STORAGE_PATH");return p;},transport);
 const pairing=new SupabasePairingService(db,config.pairingSecret);
 const transfer=new SupabaseTransferService(db,storage,paths,config.maxBytes);
 const app=Fastify({logger:false});
 await registerRealtimeRoutes(app,pairing,transfer,new SupabaseIdentityVerifier(config.url,config.publicKey,transport));
 // Hosting process schedules cleanup and records aggregate failures; never log document contents/tokens.
 return {app,cleanup:()=>transfer.cleanup()};
}
