import { RealtimeError, requireCondition } from "./domain.js";
/** Server-only RPC transport; actor UID must come from the verified access token. */
export class SupabaseRealtimeRepository {
 constructor(private readonly baseUrl:string,private readonly secret:string,private readonly transport:typeof fetch=fetch){requireCondition(new URL(baseUrl).protocol==="https:",500,"HTTPS_REQUIRED");}
 async command<T>(actorUid:string,operation:string,args:object={}):Promise<T>{
  let response:Response;
  try{response=await this.transport(new URL("/rest/v1/rpc/realtime_command",this.baseUrl),{method:"POST",headers:{apikey:this.secret,...(this.secret.startsWith("eyJ")?{Authorization:`Bearer ${this.secret}`} : {}),"Content-Type":"application/json"},body:JSON.stringify({actor_uid:actorUid,operation,args}),signal:AbortSignal.timeout(15000)});}catch{throw new RealtimeError(503,"DATABASE_UNAVAILABLE");}
  if(!response.ok){const body=await response.json().catch(()=>({})) as {message?:string};const code=body.message??"DATABASE_UNAVAILABLE";
   const allowed=["UNAUTHENTICATED","INVALID_DEVICE","DEVICE_NOT_REGISTERED","DEVICE_KIND_CONFLICT","ALREADY_CONNECTED","INVALID_PAIRING_CODE","INCOMPATIBLE_DEVICE","PAIRING_NOT_FOUND","PAIRING_NOT_ACTIVE","CONNECTION_UNAVAILABLE","IDEMPOTENCY_CONFLICT","FILE_UNAVAILABLE","BASE_VERSION_INVALID","UPLOAD_UNAVAILABLE","UPLOAD_STATE_CONFLICT","INTEGRITY_MISMATCH","ALREADY_COMPLETE","UPLOAD_NOT_VERIFIED"];
   throw new RealtimeError(code==="UNAUTHENTICATED"?401:!allowed.includes(code)?503:code.endsWith("UNAVAILABLE")||code.endsWith("NOT_FOUND")?404:409,allowed.includes(code)?code:"DATABASE_UNAVAILABLE");
  }return await response.json() as T;
 }
}
