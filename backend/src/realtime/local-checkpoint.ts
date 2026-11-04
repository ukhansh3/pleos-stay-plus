import { mkdirSync, readFileSync, writeFileSync, renameSync, existsSync, openSync, closeSync, fsyncSync } from "node:fs";
import { dirname } from "node:path";
import type { LocalPairingService } from "./local-pairing-service.js";
import type { LocalTransferService } from "./local-transfer-service.js";
/** Single-process local harness checkpoint. Not a substitute for the Supabase transaction adapter. */
export class LocalCheckpoint {
 constructor(private readonly path:string,private readonly pairing:LocalPairingService,private readonly transfer:LocalTransferService){}
 load(){if(!existsSync(this.path))return;const state=JSON.parse(readFileSync(this.path,"utf8"));if(state.version!==1)throw new Error("CHECKPOINT_VERSION_UNSUPPORTED");this.pairing.restore(state.pairing);this.transfer.restore(state.transfer);}
 save(){mkdirSync(dirname(this.path),{recursive:true});const temp=this.path+".tmp";writeFileSync(temp,JSON.stringify({version:1,pairing:this.pairing.snapshot(),transfer:this.transfer.snapshot()}),{mode:0o600});const fd=openSync(temp,"r+");try{fsyncSync(fd);}finally{closeSync(fd);}// Windows scanners can briefly hold the destination; preserve the temp on failure.
 for(let attempt=0;;attempt++){try{renameSync(temp,this.path);break;}catch(error){if(attempt>=5 || !["EPERM","EBUSY","EACCES"].includes((error as NodeJS.ErrnoException).code ?? ""))throw error;Atomics.wait(new Int32Array(new SharedArrayBuffer(4)),0,0,20);}}
}
}
