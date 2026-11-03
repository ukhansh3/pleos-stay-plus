"use client";
import { useCallback, useEffect, useState } from "react";
import { pickupApi, type ParticipantStatus, type PickupSnapshot } from "../lib/api";
import { isStale, shouldApplyRevision } from "../lib/pickup-state";
import { pickupSupabase } from "../lib/supabase";
import { KakaoPickupMap } from "./KakaoPickupMap";

export function PickupPanel({invitationToken}:{invitationToken:string}) {
  const [consent,setConsent]=useState(false); const [name,setName]=useState(""); const [busy,setBusy]=useState(false);
  const [error,setError]=useState(""); const [access,setAccess]=useState(""); const [participantId,setParticipantId]=useState(""); const [sessionId,setSessionId]=useState("");
  const [snapshot,setSnapshot]=useState<PickupSnapshot|null>(null); const [gpsMessage,setGpsMessage]=useState("GPS는 선택 사항입니다.");

  const refresh=useCallback(async(token=access,id=sessionId)=>{if(!token||!id)return;try{const next=await pickupApi.snapshot(token,id);setSnapshot(current=>!current||shouldApplyRevision(current.revision,next.revision)?next:current);}catch(e){setError((e as Error).message);}},[access,sessionId]);
  useEffect(()=>{
    const raw=sessionStorage.getItem("pickup-binding"); if(!raw)return;
    let binding:{participantId:string;sessionId:string}; try{binding=JSON.parse(raw);}catch{sessionStorage.removeItem("pickup-binding");return;}
    void pickupSupabase().auth.getSession().then(async({data:{session}})=>{
      if(!session){sessionStorage.removeItem("pickup-binding");return;}
      setAccess(session.access_token);setParticipantId(binding.participantId);setSessionId(binding.sessionId);
      try{setSnapshot(await pickupApi.snapshot(session.access_token,binding.sessionId));}catch(e){sessionStorage.removeItem("pickup-binding");setError((e as Error).message);}
    });
  },[]);
  async function join(){
    if(!consent){setError("정보 제공 동의가 필요합니다.");return;} setBusy(true);setError("");
    try{
      const supabase=pickupSupabase(); let {data:{session}}=await supabase.auth.getSession();
      if(!session){const result=await supabase.auth.signInAnonymously();if(result.error)throw result.error;session=result.data.session;}
      if(!session)throw new Error("Supabase 익명 인증이 비활성화되어 있습니다");
      const joined=await pickupApi.redeem(session.access_token,invitationToken,true,name.trim());
      setAccess(session.access_token);setParticipantId(joined.participantId);setSessionId(joined.sessionId);sessionStorage.setItem("pickup-binding",JSON.stringify({participantId:joined.participantId,sessionId:joined.sessionId}));history.replaceState(null,"",`/pickup/session`);
      const first=await pickupApi.snapshot(session.access_token,joined.sessionId);setSnapshot(first);
    }catch(e){setError((e as Error).message);}finally{setBusy(false);}
  }
  useEffect(()=>{
    if(!access||!sessionId)return; const supabase=pickupSupabase();
    const channel=supabase.channel(`pickup:${sessionId}`).on("postgres_changes",{event:"*",schema:"public",table:"pickup_events",filter:`session_id=eq.${sessionId}`},()=>void refresh()).subscribe();
    const poll=setInterval(()=>void refresh(),15_000);
    return()=>{clearInterval(poll);void supabase.removeChannel(channel);};
  },[access,sessionId,refresh]);
  async function status(value:ParticipantStatus){if(!access||!participantId)return;setBusy(true);try{await pickupApi.status(access,participantId,value);await refresh();}catch(e){setError((e as Error).message);}finally{setBusy(false);}}
  function shareGps(){
    if(!navigator.geolocation){setGpsMessage("브라우저 GPS를 지원하지 않습니다. 수동 상태 기능은 계속 사용할 수 있습니다.");return;}
    navigator.geolocation.getCurrentPosition(async position=>{try{await pickupApi.location(access,participantId,position);setGpsMessage("내 위치를 갱신했습니다.");await refresh();}catch(e){setGpsMessage((e as Error).message);}},error=>setGpsMessage(error.code===error.PERMISSION_DENIED?"GPS 권한을 거부했습니다. 차량 지도와 수동 상태는 계속 사용할 수 있습니다.":"GPS 위치를 받지 못했습니다."),{enableHighAccuracy:true,timeout:10_000,maximumAge:5_000});
  }
  async function close(kind:"complete"|"cancel"){if(!access||!sessionId)return;setBusy(true);try{if(kind==="complete")await pickupApi.complete(access,sessionId);else await pickupApi.cancel(access,sessionId);sessionStorage.removeItem("pickup-binding");setSnapshot(null);setAccess("");setError("Pickup 연결이 종료되었습니다.");}catch(e){setError((e as Error).message);}finally{setBusy(false);}}

  if(!snapshot)return <main className="panel stack"><h1>Pickup 참가</h1><p>차량의 최신 위치와 픽업 상태를 공유받습니다. 위치 이력은 저장하지 않으며 내 GPS 제공은 선택 사항입니다.</p><label><input type="checkbox" checked={consent} onChange={e=>setConsent(e.target.checked)}/> 정보 제공 및 Pickup 참가에 동의합니다.</label><label>별명(선택)<input value={name} maxLength={60} onChange={e=>setName(e.target.value)} placeholder="픽업 대상"/></label><button onClick={join} disabled={busy}>{busy?"확인 중…":"참가하기"}</button>{error&&<p className="error">{error}</p>}</main>;
  const vehicle=snapshot.vehicleLocation; const stale=isStale(vehicle?.receivedAt??null); const mapUrl=vehicle?`https://map.kakao.com/link/to/${encodeURIComponent("Pickup 차량")},${vehicle.latitude},${vehicle.longitude}`:"#";
  return <main className="panel stack"><h1>Pickup 상태</h1><p>상태: {snapshot.session.participant?.status??snapshot.session.status}</p>{snapshot.proximityCandidate&&<p className="notice">차량과 가까운 위치가 감지되었습니다. 실제 합류는 직접 확인해 주세요.</p>}{vehicle?<><KakaoPickupMap vehicle={vehicle} participant={snapshot.participantLocation}/><p className={stale?"error":""}>차량 위치 갱신: {vehicle.receivedAt}{stale?" · 오래된 위치":""}</p><a className="action" href={mapUrl} target="_blank" rel="noreferrer">Kakao 길찾기 열기</a></>:<p className="error">차량 위치를 아직 받지 못했습니다.</p>}<button className="secondary" onClick={shareGps}>내 GPS 위치 공유</button><p>{gpsMessage}</p><div className="row"><button onClick={()=>status("DEPARTED")} disabled={busy}>출발</button><button onClick={()=>status("NEARBY")} disabled={busy}>곧 도착</button></div><div className="row"><button onClick={()=>close("complete")} disabled={busy}>합류 완료</button><button className="danger" onClick={()=>close("cancel")} disabled={busy}>픽업 취소</button></div>{error&&<p className="error">{error}</p>}</main>;
}
