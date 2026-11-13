import assert from "node:assert/strict";
import {test} from "node:test";
import {pickupApi} from "../lib/api.js";

test("optional blank nickname is omitted rather than rejected by the real Pickup contract",async()=>{
  const original=globalThis.fetch;
  const requests:Array<Record<string,unknown>>=[];
  globalThis.fetch=async(_input,init)=>{requests.push(JSON.parse(String(init?.body)));return new Response(JSON.stringify({participantId:"synthetic",sessionId:"synthetic",status:"JOINED",revision:1}),{status:200});};
  try {
    await pickupApi.redeem("synthetic-test-access","synthetic-invitation",true,"  ");
    assert.equal(Object.hasOwn(requests[0],"displayName"),false);
    await pickupApi.redeem("synthetic-test-access","synthetic-invitation",true,"  테스트  ");
    assert.equal(requests[1].displayName,"테스트");
  }finally{globalThis.fetch=original;}
});
test("mobile close retries can reuse the same request key",async()=>{
  const original=globalThis.fetch;const keys:string[]=[];
  globalThis.fetch=async(_input,init)=>{keys.push(new Headers(init?.headers).get("Idempotency-Key")??"");return new Response("{}",{status:200});};
  try{
    await pickupApi.complete("synthetic-test-access","synthetic-session","synthetic-stable-key");
    await pickupApi.complete("synthetic-test-access","synthetic-session","synthetic-stable-key");
    assert.deepEqual(keys,["synthetic-stable-key","synthetic-stable-key"]);
  }finally{globalThis.fetch=original;}
});