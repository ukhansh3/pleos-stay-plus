import {config as loadEnv} from "dotenv";
import {fileURLToPath} from "node:url";
loadEnv({path:fileURLToPath(new URL(".env.local",import.meta.url))});
import { buildSupabaseRealtimeServer } from './supabase-server.js';
/** Explicit local Cloud connector. Start only after the database/application review is approved. */
if(process.env.REALTIME_SUPABASE_ENABLED!=='true')throw new Error('REALTIME_SUPABASE_ENABLED_REQUIRED');
function required(name:string){const value=process.env[name];if(!value)throw new Error(`${name}_REQUIRED`);return value;}
const {app,cleanup}=await buildSupabaseRealtimeServer({url:required('REALTIME_SUPABASE_URL'),publicKey:required('REALTIME_SUPABASE_PUBLIC_KEY'),serverSecret:required('REALTIME_SUPABASE_SERVER_SECRET'),pairingSecret:required('REALTIME_PAIRING_SECRET'),maxBytes:Number(process.env.REALTIME_MAX_BYTES??20971520)});
let running=false;
const timer=setInterval(async()=>{if(running)return;running=true;try{const result=await cleanup();if(result.failed)console.error('REALTIME_CLEANUP_INCOMPLETE',result.failed);}catch{console.error('REALTIME_CLEANUP_FAILED');}finally{running=false;}},60000);
timer.unref();app.addHook('onClose',async()=>{clearInterval(timer);});
const address=await app.listen({host:'127.0.0.1',port:Number(process.env.REALTIME_LOCAL_PORT??3012)});
console.log('LOCAL_READY '+address);
for(const signal of ['SIGINT','SIGTERM'] as const)process.once(signal,()=>{void app.close();});
