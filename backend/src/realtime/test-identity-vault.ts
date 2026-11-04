import {readFile,writeFile} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {createHash,randomBytes,createCipheriv,createDecipheriv,randomUUID} from 'node:crypto';
interface TestIdentity{uid:string;accessToken:string;refreshToken:string;expiresAt:number}
export async function testIdentityVault(secret:string){
 const path=join(tmpdir(),'stay-realtime-cloud-iadqoesbqamweseplvkr.enc');const key=createHash('sha256').update(secret).digest();
 let data:{runId:string;identities:Record<string,TestIdentity>}={runId:'TEST-Realtime-'+randomUUID(),identities:{}};
 try{const b=await readFile(path);const d=createDecipheriv('aes-256-gcm',key,b.subarray(0,12));d.setAuthTag(b.subarray(12,28));data=JSON.parse(Buffer.concat([d.update(b.subarray(28)),d.final()]).toString());}catch(e){if((e as NodeJS.ErrnoException).code!=='ENOENT')throw Error('TEST_VAULT_UNREADABLE_NO_NEW_ACCOUNTS');}
 return {data,save:async()=>{const iv=randomBytes(12),c=createCipheriv('aes-256-gcm',key,iv);const b=Buffer.concat([c.update(JSON.stringify(data)),c.final()]);await writeFile(path,Buffer.concat([iv,c.getAuthTag(),b]),{mode:0o600});}};
}
