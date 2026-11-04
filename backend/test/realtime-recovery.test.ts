import test from 'node:test';
import assert from 'node:assert/strict';
import { Readable } from 'node:stream';
import { mkdtemp,rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { createHash,randomUUID } from 'node:crypto';
import { validateDocument } from '../src/realtime/document-validator.js';
import { LocalPairingService } from '../src/realtime/local-pairing-service.js';
import { LocalTransferService } from '../src/realtime/local-transfer-service.js';
import { LocalStorageGateway } from '../src/realtime/storage-gateway.js';
import { LocalCheckpoint } from '../src/realtime/local-checkpoint.js';
import { SupabaseIdentityVerifier } from '../src/realtime/auth.js';
import { SupabaseRealtimeRepository } from '../src/realtime/supabase-repository.js';
import { SupabaseTransferService } from '../src/realtime/supabase-services.js';
const pdf=Buffer.from('%PDF-1.7\n1 0 obj\n<< /Type /Catalog >>\nendobj\n%%EOF\n');
test('TXT/PDF validation rejects invalid UTF8, binary masquerading and MIME mismatch',async()=>{
 await validateDocument(Readable.from(pdf),'a.pdf','application/pdf',20000);
 await validateDocument(Readable.from(Buffer.from('한글')),'a.txt','text/plain',20000);
 await assert.rejects(validateDocument(Readable.from(Buffer.from('not pdf')),'a.pdf','application/pdf',100));
 await assert.rejects(validateDocument(Readable.from(Buffer.from([0xff,0])),'a.txt','text/plain',100));
 await assert.rejects(validateDocument(Readable.from(pdf),'a.txt','application/pdf',100));
});
test('checkpoint restart, interrupted upload retry, duplicate commit and PDF revision',async()=>{
 const root=await mkdtemp(join(tmpdir(),'stay-recovery-'));
 try{
  const p=new LocalPairingService('x'.repeat(32));p.register('pc','WINDOWS','pc');p.register('car','VEHICLE','car');const pair=p.issue('pc');p.claim('car',pair.code,'127.0.0.1');p.approve('pc',pair.id);const connectionId=p.approve('car',pair.id).connectionId!;
  let t=new LocalTransferService(p,new LocalStorageGateway(root));let checkpoint=new LocalCheckpoint(join(root,'metadata.json'),p,t);
  let base:string|undefined,file:string|undefined;
  for(const data of [pdf,Buffer.concat([pdf,Buffer.from('\n')])]){
   const input={connectionId,fileName:'a.pdf',mimeType:'application/pdf',size:data.length,sha256:createHash('sha256').update(data).digest('hex'),idempotencyKey:randomUUID(),...(file?{fileId:file,baseVersionId:base!}:{})};
   const v=t.prepare('pc',input);checkpoint.save();await assert.rejects(t.content('pc',v.id,Readable.from(Buffer.from('broken'))));checkpoint.save();
   t=new LocalTransferService(p,new LocalStorageGateway(root));checkpoint=new LocalCheckpoint(join(root,'metadata.json'),p,t);checkpoint.load();
   assert.equal(t.prepare('pc',input).id,v.id);await t.content('pc',v.id,Readable.from(data));const completed=t.complete('pc',v.id);assert.equal(t.complete('pc',v.id).versionNo,completed.versionNo);
   const chunks:Buffer[]=[];for await(const chunk of (await t.download('car',v.id)).stream)chunks.push(Buffer.from(chunk));assert.deepEqual(Buffer.concat(chunks),data);file=v.fileId;base=v.id;
  }
  assert.equal(t.listVersions('car',file!).length,2);p.revoke('pc',connectionId);await assert.rejects(t.download('car',base!));
 }finally{await rm(root,{recursive:true,force:true});}
});
test('Supabase auth distinguishes invalid identity from transient failure',async()=>{
 const transport=(status:number,body:object)=>(async()=>new Response(JSON.stringify(body),{status})) as typeof fetch;
 assert.equal(await new SupabaseIdentityVerifier('https://fake.invalid','public',transport(200,{id:'uid'})).verify('jwt'),'uid');
 await assert.rejects(new SupabaseIdentityVerifier('https://fake.invalid','public',transport(401,{})).verify('jwt'),/UNAUTHENTICATED/);
 await assert.rejects(new SupabaseIdentityVerifier('https://fake.invalid','public',transport(503,{})).verify('jwt'),/AUTH_UNAVAILABLE/);
});
test('RPC verified-response loss never removes a verified object',async()=>{
 const v={id:randomUUID(),connectionId:randomUUID(),fileId:randomUUID(),fileName:'a.txt',mimeType:'text/plain',size:2,sha256:createHash('sha256').update('ok').digest('hex'),state:'UPLOADING'};
 let removed=0;const transport=(async(_url,options)=>{const body=JSON.parse(String(options?.body));if(body.operation==='verified'){v.state='VERIFYING';throw new Error('response lost');}return new Response(JSON.stringify(v));}) as typeof fetch;
 const db=new SupabaseRealtimeRepository('https://fake.invalid','server-secret',transport);
 const storage={remove:async()=>{removed++;},put:async()=>({size:2,sha256:v.sha256}),open:async()=>Readable.from('ok')};
 const t=new SupabaseTransferService(db,storage,new Map());await assert.rejects(t.content('uid',v.id,Readable.from('ok')));assert.equal(removed,1);
});

test('DOCX compressed fixture and damaged ZIP are screened',async()=>{
 const {readFile}=await import('node:fs/promises');
 const data=await readFile(new URL('./fixtures/document.docx',import.meta.url));
 const mime='application/vnd.openxmlformats-officedocument.wordprocessingml.document';
 await validateDocument(Readable.from(data),'document.docx',mime,20000);
 await assert.rejects(validateDocument(Readable.from(data.subarray(0,data.length-10)),'document.docx',mime,20000));
});
