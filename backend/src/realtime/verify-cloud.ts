import {testIdentityVault} from './test-identity-vault.js';
import {config as loadEnv} from 'dotenv';
import {fileURLToPath} from 'node:url';
import {readFile,writeFile} from 'node:fs/promises';
import {join} from 'node:path';
import {tmpdir} from 'node:os';
import {randomUUID,createHash} from 'node:crypto';
import {spawn,type ChildProcess} from 'node:child_process';
loadEnv({path:fileURLToPath(new URL('.env.local',import.meta.url))});
if(process.env.REALTIME_CLOUD_TEST_APPROVED!=='true')throw Error('CLOUD_TEST_APPROVAL_REQUIRED');
function required(name:string){const value=process.env[name];if(!value)throw Error(`${name}_REQUIRED`);return value;}
const config={url:required('REALTIME_SUPABASE_URL'),publicKey:required('REALTIME_SUPABASE_PUBLIC_KEY'),serverSecret:required('REALTIME_SUPABASE_SERVER_SECRET'),pairingSecret:required('REALTIME_PAIRING_SECRET')};
if(new URL(config.url).hostname!=='iadqoesbqamweseplvkr.supabase.co')throw Error('APPROVED_PROJECT_MISMATCH');
const vault=await testIdentityVault(config.pairingSecret);const runId=vault.data.runId;const reportPath=join(tmpdir(),runId+(process.env.REALTIME_PROCESS_SMOKE==='true'?'-process':'-cloud')+'.json');
const report:{runId:string;accounts:{kind:string;uid:string}[];versions:string[];passed:string[];error?:string}={runId,accounts:[],versions:[],passed:[]};
async function save(){await writeFile(reportPath,JSON.stringify(report,null,2));}
async function identity(kind:string){
 const saved=vault.data.identities[kind];
 if(saved&&saved.expiresAt>Date.now()/1000+60){report.accounts.push({kind,uid:saved.uid});return saved.accessToken;}
 const path=saved?'/auth/v1/token?grant_type=refresh_token':'/auth/v1/signup';
 const r=await fetch(config.url+path,{method:'POST',headers:{apikey:config.publicKey,'Content-Type':'application/json'},body:JSON.stringify(saved?{refresh_token:saved.refreshToken}:{data:{test_run:runId,test_device:kind}}),signal:AbortSignal.timeout(20000)});
 if(!r.ok)throw Error('AUTH_EXCHANGE_'+r.status);const j=await r.json() as {access_token:string;refresh_token:string;expires_in:number;user:{id:string}};
 if(!j.access_token||!j.user?.id|| (saved&&saved.uid!==j.user.id))throw Error('INVALID_AUTH_RESPONSE');
 vault.data.identities[kind]={uid:j.user.id,accessToken:j.access_token,refreshToken:j.refresh_token,expiresAt:Date.now()/1000+j.expires_in};await vault.save();
 report.accounts.push({kind,uid:j.user.id});await save();return j.access_token;
}
let child:ChildProcess|undefined;let address='';
async function start(){
 const p=spawn(process.execPath,['--import','tsx',fileURLToPath(new URL('supabase-local-server.ts',import.meta.url))],{env:{...process.env,REALTIME_LOCAL_PORT:'0'},stdio:['ignore','pipe','pipe'],windowsHide:true});child=p;
 address=await new Promise<string>((resolve,reject)=>{let out='';const timer=setTimeout(()=>reject(Error('SERVER_START_TIMEOUT')),20000);p.once('exit',()=>{clearTimeout(timer);reject(Error('SERVER_EXIT'));});p.stdout!.on('data',chunk=>{out+=chunk;const m=/LOCAL_READY (http:\/\/127\.0\.0\.1:\d+)/.exec(out);if(m){clearTimeout(timer);resolve(m[1]!);}});});
}
async function stop(){const p=child;if(!p||p.exitCode!==null)return;await new Promise<void>(resolve=>{p.once('exit',()=>resolve());p.kill();});child=undefined;}
async function request(token:string,method:string,path:string,body?:object|Buffer){return fetch(address+'/api/v1/realtime'+path,{method,headers:{Authorization:`Bearer ${token}`,...(body===undefined?{}:{'Content-Type':Buffer.isBuffer(body)?'application/octet-stream':'application/json'})},...(body===undefined?{}:{body:Buffer.isBuffer(body)?new Uint8Array(body):JSON.stringify(body)}),signal:AbortSignal.timeout(120000)});}
async function json(token:string,method:string,path:string,body?:object|Buffer){const r=await request(token,method,path,body);if(!r.ok){const j=await r.json().catch(()=>null) as {error?:{code?:string}}|null;throw Error(`${method} ${path}: ${r.status} ${j?.error?.code??''}`);}return r.status===204?null:await r.json();}
try {
 const pc=await identity('PC'),car=await identity('CAR'),outside=await identity('OUTSIDE');report.passed.push('three_distinct_anonymous_accounts');
 await start();for(const [token,kind] of [[pc,'WINDOWS'],[car,'VEHICLE'],[outside,'VEHICLE']])await json(token!,'POST','/devices',{kind,name:runId});
 const existing=await json(pc,'GET','/connections/current');let connectionId:string;
 if(existing.connection){connectionId=existing.connection.id;}else{const pair=await json(pc,'POST','/pairings');await json(car,'POST','/pairings/claim',{code:pair.code});await json(pc,'POST',`/pairings/${pair.id}/approve`);connectionId=(await json(car,'POST',`/pairings/${pair.id}/approve`)).connectionId;}
 report.passed.push('pairing_two_approvals');
 let retainedId='',retainedSha='';
 for(const extension of (process.env.REALTIME_PROCESS_SMOKE==='true'?['txt']:['txt','pdf','docx'])){
  const original=await readFile(new URL(`../../test/fixtures/document.${extension}`,import.meta.url));
  const mimeType=extension==='txt'?'text/plain':extension==='pdf'?'application/pdf':'application/vnd.openxmlformats-officedocument.wordprocessingml.document';
  let fileId:string|undefined,baseVersionId:string|undefined;
  for(let version=1;version<=2;version++){
   const bytes=extension==='txt'&&version===2?Buffer.concat([original,Buffer.from('\nFake Work revision\n')]):original;
   const sha256=createHash('sha256').update(bytes).digest('hex');const token=version===1?pc:car;
   const input={connectionId,fileName:`${runId}-v${version}.${extension}`,mimeType,size:bytes.length,sha256,idempotencyKey:randomUUID(),...(fileId?{fileId,baseVersionId}:{})};
   const v=await json(token,'POST','/uploads',input);report.versions.push(v.id);await save();
   await json(token,'PUT',`/uploads/${v.id}/content`,bytes);const done=await json(token,'POST',`/uploads/${v.id}/complete`);if(done.versionNo!==version)throw Error('VERSION_SEQUENCE');
   if((await json(token,'POST','/uploads',input)).id!==v.id)throw Error('IDEMPOTENCY_FAILED');
   const downloaded=await request(version===1?car:pc,'GET',`/versions/${v.id}/content`);if(!downloaded.ok||createHash('sha256').update(Buffer.from(await downloaded.arrayBuffer())).digest('hex')!==sha256)throw Error('INTEGRITY_FAILED');
   if((await request(outside,'GET',`/versions/${v.id}/content`)).status!==404)throw Error('OUTSIDER_ACCESS');
   if(version===1){retainedId=v.id;retainedSha=sha256;}fileId=v.fileId;baseVersionId=v.id;
  }
  const originalAgain=await request(car,'GET',`/versions/${retainedId}/content`);if(createHash('sha256').update(Buffer.from(await originalAgain.arrayBuffer())).digest('hex')!==retainedSha)throw Error('ORIGINAL_CHANGED');
  report.passed.push(`${extension}_roundtrip_versions_hash_isolation`);await save();
 }
 await stop();await start();if((await json(pc,'GET','/connections/current')).connection.id!==connectionId)throw Error('RESTART_CONNECTION_LOST');
 const restored=await request(car,'GET',`/versions/${retainedId}/content`);if(!restored.ok||createHash('sha256').update(Buffer.from(await restored.arrayBuffer())).digest('hex')!==retainedSha)throw Error('RESTART_FILE_LOST');
 report.passed.push('fresh_server_process_persistence');
 const direct=await fetch(config.url+'/rest/v1/realtime_files?select=id',{headers:{apikey:config.publicKey,Authorization:`Bearer ${outside}`}});if(direct.ok)throw Error('DIRECT_TABLE_ACCESS');report.passed.push('direct_table_access_denied');
 await json(pc,'DELETE',`/connections/${connectionId}`);if((await request(car,'GET',`/versions/${retainedId}/content`)).status!==404)throw Error('REVOKED_ACCESS');report.passed.push('revocation_denied');
 await save();console.log(JSON.stringify({runId,passed:report.passed,reportPath}));
}catch(e){report.error=e instanceof Error?e.message:'UNKNOWN_TEST_FAILURE';await save();console.error(JSON.stringify({runId,error:report.error,reportPath}));process.exitCode=1;}
finally{await stop();}
