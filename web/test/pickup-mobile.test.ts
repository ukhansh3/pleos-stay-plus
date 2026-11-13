import assert from "node:assert/strict";
import { test } from "node:test";
import { bindingMatchesInvitation, canSetParticipantStatus, locationAge, locationIsStale, parsePickupBinding, pickupErrorMessage, pickupStraightLineDistance } from "../lib/pickup-mobile.js";
import { watchPickupPosition } from "../lib/pickup-gps.js";
import type { PickupLocation } from "../lib/api";

const now = Date.parse("2026-11-13T01:00:00Z");
const location: PickupLocation = { latitude:37.28,longitude:127.04,accuracyMeters:5,observedAt:new Date(now).toISOString(),receivedAt:new Date(now).toISOString(),source:"REAL" };
test("replayed old observations are stale despite a recent server receipt", () => {
  assert.equal(locationIsStale(location,now),false);
  assert.equal(locationIsStale({...location,observedAt:new Date(now-60_000).toISOString()},now),true);
  assert.equal(locationIsStale({...location,latitude:NaN},now),true);
  assert.equal(locationIsStale(null,now),true);
  assert.equal(locationAge(location,now+20_000),"20초 전 갱신");
});
test("distance is a straight-line approximation and disappears for old or unavailable samples", () => {
  assert.equal(pickupStraightLineDistance(location,{...location,latitude:37.281},now),"약 110m");
  assert.equal(pickupStraightLineDistance(location,null,now),null);
  assert.equal(pickupStraightLineDistance(location,{...location,observedAt:new Date(now-60_000).toISOString()},now),null);
});
test("a new invitation never silently restores a binding to a different pickup", () => {
  const binding={participantId:"11111111-1111-4111-8111-111111111111",sessionId:"22222222-2222-4222-8222-222222222222",invitationFingerprint:"a".repeat(64)};
  assert.deepEqual(parsePickupBinding(JSON.stringify(binding)),binding);
  assert.equal(bindingMatchesInvitation(binding,"b".repeat(64)),false);
  assert.equal(bindingMatchesInvitation(binding,"a".repeat(64)),true);
  assert.equal(bindingMatchesInvitation(binding,null),true);
  assert.equal(bindingMatchesInvitation({...binding,invitationFingerprint:undefined},"a".repeat(64)),false);
  assert.equal(parsePickupBinding('{"participantId":"bad","sessionId":"bad"}'),null);
});
test("status actions never offer a backwards transition or repeat a completed one", () => {
  assert.equal(canSetParticipantStatus("JOINED","DEPARTED"),true);
  assert.equal(canSetParticipantStatus("DEPARTED","NEARBY"),true);
  assert.equal(canSetParticipantStatus("NEARBY","DEPARTED"),false);
  assert.equal(canSetParticipantStatus("ARRIVED","NEARBY"),false);
});
test("raw transport or SDK error details do not reach user copy", () => {
  assert.equal(pickupErrorMessage(new Error("HTTP500 raw internal details")),"연결하지 못했어요. 네트워크를 확인하고 다시 시도해 주세요.");
  assert.match(pickupErrorMessage({code:"INVITATION_INVALID"}),/만료/);
  assert.match(pickupErrorMessage({code:"PARTICIPANT_LIMIT_REACHED"}),/다른 참가자/);
});
function geolocationHarness() {
  let success: PositionCallback = () => {};
  let failure: PositionErrorCallback = () => {};
  const cleared:number[]=[];
  const reader={ watchPosition:(ok:PositionCallback,bad?:PositionErrorCallback|null)=>{success=ok;failure=bad??(()=>{});return 7;}, clearWatch:(id:number)=>cleared.push(id) };
  const position=(timestamp=now)=>({coords:{latitude:37.28,longitude:127.04,accuracy:5,altitude:null,altitudeAccuracy:null,heading:null,speed:null,toJSON:()=>({})},timestamp,toJSON:()=>({})}) as GeolocationPosition;
  return {reader,cleared,emit:(p=position())=>success(p),fail:(code:number)=>failure({code,message:"synthetic test",PERMISSION_DENIED:1,POSITION_UNAVAILABLE:2,TIMEOUT:3}),position};
}
test("stopping GPS cancels pending transmission and ignores every late position callback", async () => {
  const geo=geolocationHarness();let sends=0;let signal:AbortSignal|undefined;let messages=0;
  const stop=watchPickupPosition({geolocation:geo.reader,now:()=>now,send:async(_p,s)=>{sends++;signal=s;await new Promise<void>(resolve=>s.addEventListener("abort",()=>resolve()));},onMessage:()=>messages++,onStopped:()=>{},onSendError:()=>{}});
  geo.emit();assert.equal(sends,1);stop();assert.equal(signal?.aborted,true);assert.deepEqual(geo.cleared,[7]);geo.emit();await new Promise(resolve=>setTimeout(resolve,0));assert.equal(sends,1);assert.equal(messages,0);
});
test("GPS denial keeps location stopped rather than restarting automatically", () => {
  const geo=geolocationHarness();let stopped=0;let sent=0;
  watchPickupPosition({geolocation:geo.reader,send:async()=>{sent++;},onMessage:()=>{},onStopped:()=>stopped++,onSendError:()=>{}});
  geo.fail(1);geo.emit();assert.equal(stopped,1);assert.equal(sent,0);assert.deepEqual(geo.cleared,[7]);
});
test("GPS sharing limits successful updates to five-second intervals and rejects invalid data", async () => {
  const geo=geolocationHarness();let clock=now;let sent=0;
  const stop=watchPickupPosition({geolocation:geo.reader,now:()=>clock,send:async()=>{sent++;},onMessage:()=>{},onStopped:()=>{},onSendError:()=>{}});
  geo.emit({...geo.position(),coords:{...geo.position().coords,longitude:NaN}});assert.equal(sent,0);
  geo.emit();await new Promise(resolve=>setTimeout(resolve,0));clock+=4999;geo.emit();assert.equal(sent,1);clock++;geo.emit();await new Promise(resolve=>setTimeout(resolve,0));assert.equal(sent,2);stop();
});