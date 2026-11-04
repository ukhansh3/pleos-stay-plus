import { Readable, Transform } from "node:stream";
import { createHash } from "node:crypto";
import type { StorageGateway, StoredContent } from "./storage-gateway.js";
import { RealtimeError, requireCondition } from "./domain.js";
/** Server-only Storage REST adapter. Never instantiated by the local harness. */
export class SupabaseStorageGateway implements StorageGateway {
 constructor(private readonly baseUrl: string,private readonly secret: string,private readonly objectPath: (versionId:string)=>string,private readonly transport:typeof fetch=fetch){requireCondition(new URL(baseUrl).protocol==="https:",500,"HTTPS_REQUIRED");}
 private url(key:string){const path=this.objectPath(key);requireCondition(/^[0-9a-f-]{36}\/[0-9a-f-]{36}\/[0-9a-f-]{36}\/content$/.test(path),500,"INVALID_STORAGE_PATH");return `${this.baseUrl.replace(/\/$/,"")}/storage/v1/object/stay-realtime-files/${path}`;}
 async put(key:string,source:Readable,maxBytes:number):Promise<StoredContent>{
  let size=0;const hash=createHash("sha256");const meter=new Transform({transform(chunk:Buffer,_encoding,done){size+=chunk.length;if(size>maxBytes)return done(new RealtimeError(413,"FILE_TOO_LARGE"));hash.update(chunk);done(null,chunk);}});
  source.on("error",error=>meter.destroy(error));source.pipe(meter);
  try{
   const options:RequestInit & {duplex:string}={method:"POST",headers:{apikey:this.secret,...(this.secret.startsWith("eyJ")?{Authorization:`Bearer ${this.secret}`} : {}),"Content-Type":"application/octet-stream","x-upsert":"false"},body:Readable.toWeb(meter) as ReadableStream<Uint8Array>,duplex:"half",signal:AbortSignal.timeout(120000)};
   const response=await this.transport(this.url(key),options);if(!response.ok)throw new RealtimeError(502,"STORAGE_UPLOAD_FAILED");return{size,sha256:hash.digest("hex")};
  }finally{source.destroy();meter.destroy();}
 }
 async open(key:string):Promise<Readable>{const response=await this.transport(this.url(key),{headers:{apikey:this.secret,...(this.secret.startsWith("eyJ")?{Authorization:`Bearer ${this.secret}`} : {})},signal:AbortSignal.timeout(120000)});if(!response.ok||!response.body)throw new RealtimeError(404,"FILE_UNAVAILABLE");return Readable.fromWeb(response.body as Parameters<typeof Readable.fromWeb>[0]);}
 async remove(key:string){const response=await this.transport(`${this.baseUrl.replace(/\/$/,"")}/storage/v1/object/stay-realtime-files`,{method:"DELETE",headers:{apikey:this.secret,...(this.secret.startsWith("eyJ")?{Authorization:`Bearer ${this.secret}`} : {}),"Content-Type":"application/json"},body:JSON.stringify({prefixes:[this.objectPath(key)]}),signal:AbortSignal.timeout(10000)});if(!response.ok)throw new RealtimeError(502,"STORAGE_DELETE_FAILED");}
}
