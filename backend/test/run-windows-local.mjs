import {spawn} from 'node:child_process';
import {mkdtemp,rm} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {fileURLToPath} from 'node:url';
const root=await mkdtemp(join(tmpdir(),'stay-windows-http-'));
const backend=fileURLToPath(new URL('../',import.meta.url));
const project=fileURLToPath(new URL('../../windows/StayPlus.LocalTests/StayPlus.LocalTests.csproj',import.meta.url));
const fixtures=fileURLToPath(new URL('./fixtures',import.meta.url));
const server=spawn(process.execPath,['--import','tsx','src/realtime/local-server.ts'],{cwd:backend,windowsHide:true,env:{...process.env,NODE_ENV:'test',REALTIME_LOCAL_DEMO:'true',REALTIME_LOCAL_PC_TOKEN:'local-integration-pc-token-000000000000',REALTIME_LOCAL_CAR_TOKEN:'local-integration-car-token-00000000000',REALTIME_LOCAL_PORT:'0',REALTIME_LOCAL_STORAGE:root},stdio:['ignore','pipe','pipe']});
try{
 const address=await new Promise((resolve,reject)=>{let output='';const timeout=setTimeout(()=>reject(Error('LOCAL_SERVER_START_TIMEOUT')),20000);server.once('exit',()=>{clearTimeout(timeout);reject(Error('LOCAL_SERVER_EXIT'));});server.stdout.on('data',chunk=>{output+=chunk;const m=/LOCAL_READY (http:\/\/127\.0\.0\.1:\d+)/.exec(output);if(m){clearTimeout(timeout);resolve(m[1]);}});});
 const result=spawn('dotnet',['run','--project',project,'--',address+'/api/v1/realtime/',fixtures],{windowsHide:true,stdio:'inherit'});
 const code=await new Promise((resolve,reject)=>{result.once('error',reject);result.once('exit',resolve);});if(code!==0)throw Error('WINDOWS_INTEGRATION_FAILED');
}finally{if(server.exitCode===null)await new Promise(resolve=>{server.once('exit',resolve);server.kill();});await rm(root,{recursive:true,force:true});}
