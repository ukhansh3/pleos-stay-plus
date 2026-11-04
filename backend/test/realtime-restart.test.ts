import test from 'node:test';
import assert from 'node:assert/strict';
import {spawn,type ChildProcess} from 'node:child_process';
import {mkdtemp,rm} from 'node:fs/promises';
import {join} from 'node:path';
import {tmpdir} from 'node:os';
import {fileURLToPath} from 'node:url';
import {randomUUID,createHash} from 'node:crypto';
test('standalone process restart preserves pairing, file metadata and idempotency',{timeout:60000},async()=>{
 const root=await mkdtemp(join(tmpdir(),'stay-process-'));const pc=randomUUID(),car=randomUUID();let child:ChildProcess|undefined;
 const cwd=fileURLToPath(new URL('../',import.meta.url));
 async function start(){
  const processChild=spawn(process.execPath,['--import','tsx','src/realtime/local-server.ts'],{cwd,env:{...process.env,NODE_ENV:'test',REALTIME_LOCAL_DEMO:'true',REALTIME_LOCAL_PC_TOKEN:pc,REALTIME_LOCAL_CAR_TOKEN:car,REALTIME_LOCAL_STORAGE:root,REALTIME_LOCAL_PORT:'0'},stdio:['ignore','pipe','pipe'],windowsHide:true});child=processChild;
  return await new Promise<string>((resolve,reject)=>{let output='';const timer=setTimeout(()=>reject(new Error('LOCAL_SERVER_START_TIMEOUT')),20000);processChild.once('exit',()=>{clearTimeout(timer);reject(new Error('LOCAL_SERVER_EXIT'));});processChild.stdout!.on('data',chunk=>{output+=chunk;const m=/LOCAL_READY (http:\/\/127\.0\.0\.1:\d+)/.exec(output);if(m){clearTimeout(timer);resolve(m[1]!+'/api/v1/realtime');}});});
 }
 async function stop(){if(!child || child.exitCode!==null)return;const p=child;await new Promise<void>(resolve=>{p.once('exit',()=>resolve());p.kill();});child=undefined;}
 let base='';
 async function call(token:string,method:string,path:string,body?:object|string){const response=await fetch(base+path,{method,headers:{Authorization:`Bearer ${token}`,...(body===undefined?{}:{'Content-Type':typeof body==='string'?'application/octet-stream':'application/json'})},...(body===undefined?{}:{body:typeof body==='string'?body:JSON.stringify(body)})});assert.ok(response.ok,`${method} ${path}: ${response.status}`);return response;}
 try{
  base=await start();await call(pc,'POST','/devices',{kind:'WINDOWS',name:'restart test'});await call(car,'POST','/devices',{kind:'VEHICLE',name:'restart peer'});
  const pair=await(await call(pc,'POST','/pairings')).json();await call(car,'POST','/pairings/claim',{code:pair.code});await call(pc,'POST',`/pairings/${pair.id}/approve`);const linked=await(await call(car,'POST',`/pairings/${pair.id}/approve`)).json();
  const text='restart-safe';const input={connectionId:linked.connectionId,fileName:'restart.txt',mimeType:'text/plain',size:text.length,sha256:createHash('sha256').update(text).digest('hex'),idempotencyKey:randomUUID()};
  const v=await(await call(pc,'POST','/uploads',input)).json();await call(pc,'PUT',`/uploads/${v.id}/content`,text);await call(pc,'POST',`/uploads/${v.id}/complete`);
  await stop();base=await start();assert.equal((await(await call(pc,'GET','/connections/current')).json()).connection.id,linked.connectionId);
  assert.equal((await(await call(pc,'POST','/uploads',input)).json()).id,v.id);assert.equal(await(await call(car,'GET',`/versions/${v.id}/content`)).text(),text);
  await call(car,'DELETE',`/connections/${linked.connectionId}`);await stop();base=await start();assert.equal((await(await call(pc,'GET','/connections/current')).json()).connection,null);
 }finally{await stop();await rm(root,{recursive:true,force:true});}
});
