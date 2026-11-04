import { createHash,createHmac,randomInt } from "node:crypto";
import type { Readable } from "node:stream";
import type { Device,Connection } from "./domain.js";
import { requireCondition } from "./domain.js";
import type { LocalPairingService } from "./local-pairing-service.js";
import type { FileRecord,UploadRequest,Version } from "./local-transfer-service.js";
import type { PairingService,TransferService } from "./repository.js";
import type { StorageGateway } from "./storage-gateway.js";
import { SupabaseRealtimeRepository } from "./supabase-repository.js";
import { validateMetadata,validateDocument } from "./document-validator.js";
type Pairing=ReturnType<LocalPairingService["get"]>;
export class SupabasePairingService implements PairingService {
 constructor(private readonly db:SupabaseRealtimeRepository,private readonly secret:string,private readonly ttlSeconds=600){requireCondition(secret.length>=32,500,"INVALID_CONFIGURATION");}
 private digest(code:string){return createHmac("sha256",this.secret).update(code).digest("hex");}
 private async limit(uid:string,scope:string,subject:string){const result=await this.db.command<{allowed:boolean}>(uid,"limit",{scope,subject:this.digest(subject),limit:5});requireCondition(result.allowed,429,"RATE_LIMITED");}
 register(uid:string,kind:Device["kind"],name:string){return this.db.command<Device>(uid,"register",{kind,name});}
 device(uid:string){return this.db.command<Device>(uid,"device");}
 current(uid:string){return this.db.command<Connection|null>(uid,"current");}
 async issue(uid:string){await this.limit(uid,"issue",uid);const code=randomInt(0,100000000).toString().padStart(8,"0");const p=await this.db.command<{id:string;expiresAt:number}>(uid,"issue",{digest:this.digest(code),ttlSeconds:this.ttlSeconds});return{...p,code};}
 async claim(uid:string,code:string,ip:string){await this.limit(uid,"claim",uid);await this.limit(uid,"ip",ip);requireCondition(/^\d{8}$/.test(code),400,"INVALID_PAIRING_CODE");return this.db.command<Pairing>(uid,"claim",{digest:this.digest(code)});}
 get(uid:string,id:string){return this.db.command<Pairing>(uid,"pairing",{id});}
 approve(uid:string,id:string){return this.db.command<Pairing>(uid,"approve",{id});}
 async cancel(uid:string,id:string){await this.db.command(uid,"cancelPairing",{id});}
 authorize(uid:string,id:string){return this.db.command<Connection>(uid,"authorize",{id});}
 async revoke(uid:string,id:string){await this.db.command(uid,"revoke",{id});}
}
export class SupabaseTransferService implements TransferService {
 constructor(private readonly db:SupabaseRealtimeRepository,private readonly storage:StorageGateway,private readonly paths:Map<string,string>,private readonly maxBytes=20*1024*1024){}
 private remember(v:Version){this.paths.set(v.id,`${v.connectionId}/${v.fileId}/${v.id}/content`);return v;}
 async prepare(uid:string,input:UploadRequest){
  requireCondition(typeof input.fileName==="string"&&input.fileName.length>0&&input.fileName.length<=200&&!/[\\/\x00-\x1f]/.test(input.fileName),400,"INVALID_FILENAME");validateMetadata(input.fileName,input.mimeType);
  requireCondition(Number.isSafeInteger(input.size)&&input.size>0&&input.size<=this.maxBytes,413,"FILE_TOO_LARGE");
  requireCondition(/^[a-f0-9]{64}$/.test(input.sha256)&&/^[0-9a-f-]{36}$/i.test(input.idempotencyKey),400,"INVALID_INPUT");
  requireCondition(Boolean(input.fileId)===Boolean(input.baseVersionId),400,"BASE_VERSION_REQUIRED");
  const requestDigest=createHash("sha256").update(JSON.stringify([input.connectionId,input.fileId??null,input.baseVersionId??null,input.fileName,input.mimeType,input.size,input.sha256])).digest("hex");
  return this.remember(await this.db.command<Version>(uid,"prepare",{...input,requestDigest}));
 }
 async status(uid:string,id:string){return this.remember(await this.db.command<Version>(uid,"status",{id}));}
 async content(uid:string,id:string,source:Readable){
  const v=this.remember(await this.db.command<Version>(uid,"begin",{id}));
  try{
   await this.storage.remove(id);const stored=await this.storage.put(id,source,Math.min(v.size,this.maxBytes));
   requireCondition(stored.size===v.size&&stored.sha256===v.sha256,422,"INTEGRITY_MISMATCH");
   await validateDocument(await this.storage.open(id),v.fileName,v.mimeType,this.maxBytes);
   return this.remember(await this.db.command<Version>(uid,"verified",{id,...stored}));
  }catch(error){
   // A lost verified response must never delete a successfully committed object.
   // On an unknown DB outcome retain it for the terminal-state cleanup worker.
   const state=await this.db.command<Version>(uid,"fail",{id}).catch(()=>null);
   // Retain private FAILED bytes until the next exclusive begin or expiry cleanup.
   // Removing here would race a retry that already owns the same object path.
   void state;
   throw error;
  }
 }
 async complete(uid:string,id:string){return this.remember(await this.db.command<Version>(uid,"complete",{id}));}
 list(uid:string){return this.db.command<FileRecord[]>(uid,"list");}
 async listVersions(uid:string,id:string){const versions=await this.db.command<Version[]>(uid,"versions",{id});return versions.map(v=>this.remember(v));}
 async download(uid:string,id:string){const version=this.remember(await this.db.command<Version>(uid,"download",{id}));return{version,stream:await this.storage.open(id)};}
 async cleanup(){
  const candidates=await this.db.command<Version[]>("00000000-0000-0000-0000-000000000000","cleanup");
  const results=await Promise.allSettled(candidates.map(async v=>{this.remember(v);await this.storage.remove(v.id);}));
  return {attempted:results.length,failed:results.filter(r=>r.status==="rejected").length};
 }
 async cancel(uid:string,id:string){const v=this.remember(await this.db.command<Version>(uid,"cancel",{id}));await this.storage.remove(v.id);}
 async delete(uid:string,id:string){const versions=await this.listVersions(uid,id);await this.db.command(uid,"deleteFile",{id});for(const v of versions)await this.storage.remove(v.id);}
}
