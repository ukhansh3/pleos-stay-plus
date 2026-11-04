import test from "node:test";
import assert from "node:assert/strict";
import { mkdtemp, rm } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { createHash, randomUUID } from "node:crypto";
import Fastify from "fastify";
import { LocalPairingService } from "../src/realtime/local-pairing-service.js";
import { LocalTransferService } from "../src/realtime/local-transfer-service.js";
import { LocalStorageGateway } from "../src/realtime/storage-gateway.js";
import { LocalIdentityVerifier } from "../src/realtime/auth.js";
import { registerRealtimeRoutes } from "../src/realtime/routes.js";
test("real HTTP: pair, PC original, car revision, immutable downloads, isolation and revocation", async () => {
 const root = await mkdtemp(join(tmpdir(), "stay-realtime-")); const app = Fastify();
 const p = new LocalPairingService("x".repeat(32)); const t = new LocalTransferService(p, new LocalStorageGateway(root), 1024);
 await registerRealtimeRoutes(app,p,t,new LocalIdentityVerifier(new Map([["pc-token","pc"],["car-token","car"],["other-token","other"]])));
 const base = await app.listen({ host:"127.0.0.1", port:0 });
 async function request(who: string, method: string, path: string, body?: unknown, raw=false) {
  const response = await fetch(`${base}/api/v1/realtime${path}`, { method, headers:{ Authorization:`Bearer ${who}-token`, ...(body === undefined ? {} : {"Content-Type":raw?"application/octet-stream":"application/json"}) }, ...(body === undefined ? {} : {body:raw ? String(body) : JSON.stringify(body)}) });
  return response;
 }
 try {
  for(const [who,kind] of [["pc","WINDOWS"],["car","VEHICLE"],["other","VEHICLE"]]) assert.equal((await request(who!,"POST","/devices",{kind,name:who})).status,200);
  const pair = await (await request("pc","POST","/pairings")).json();
  assert.equal((await request("car","POST","/pairings/claim",{code:pair.code})).status,200);
  await request("pc","POST",`/pairings/${pair.id}/approve`); const connected=await (await request("car","POST",`/pairings/${pair.id}/approve`)).json();
  const connectionId=connected.connectionId;
  async function upload(who:string, text:string, fileId?:string, baseVersionId?:string) {
   const input={connectionId,fileName:"test.txt",mimeType:"text/plain",size:Buffer.byteLength(text),sha256:createHash("sha256").update(text).digest("hex"),idempotencyKey:randomUUID(),...(fileId?{fileId,baseVersionId}:{})};
   const prepared=await (await request(who,"POST","/uploads",input)).json();
   assert.equal((await (await request(who,"POST","/uploads",input)).json()).id,prepared.id);
   assert.equal((await request(who,"PUT",`/uploads/${prepared.id}/content`,text,true)).status,200);
   assert.equal((await request(who,"GET",`/versions/${prepared.id}/content`)).status,404);
   return await (await request(who,"POST",`/uploads/${prepared.id}/complete`)).json();
  }
  const v1=await upload("pc","original"); const v2=await upload("car","edited",v1.fileId,v1.id);
  assert.equal(v1.versionNo,1); assert.equal(v2.versionNo,2); assert.equal(v2.baseVersionId,v1.id);
  assert.equal(await (await request("car","GET",`/versions/${v1.id}/content`)).text(),"original");
  assert.equal(await (await request("pc","GET",`/versions/${v2.id}/content`)).text(),"edited");
  assert.equal((await request("other","GET",`/versions/${v2.id}/content`)).status,404);
  const invalid=await request("pc","POST","/uploads",{connectionId,fileName:"../bad.txt"}); assert.equal(invalid.status,400);
  assert.equal((await request("car","DELETE",`/connections/${connectionId}`)).status,204);
  assert.equal((await request("pc","GET",`/versions/${v1.id}/content`)).status,404);
 } finally { await app.close(); await rm(root,{recursive:true,force:true}); }
});
