"use client";
import { useMemo, useState } from "react";

export function SharePanel({token}:{token:string}) {
  const [message,setMessage]=useState("");
  const link=useMemo(()=>typeof location==="undefined"?"":`${location.origin}/pickup/${encodeURIComponent(token)}`,[token]);
  async function share(){
    try{
      if(navigator.share) await navigator.share({title:"Pleos Stay+ Pickup",text:"Pickup 위치와 상태를 확인해 주세요.",url:link});
      else { await navigator.clipboard.writeText(link); setMessage("링크를 복사했습니다."); }
    }catch(error){ if((error as DOMException).name!=="AbortError") setMessage("공유하지 못했습니다. 링크 복사를 사용해 주세요."); }
  }
  async function copy(){ try{await navigator.clipboard.writeText(link);setMessage("링크를 복사했습니다.");}catch{setMessage("복사 권한이 없어 직접 선택해 주세요.");} }
  return <main className="panel stack"><h1>Pickup 링크 공유</h1><p className="notice">이 화면은 운전자용 공유 안내입니다. 차량 위치를 표시하거나 Pickup 참가를 자동 실행하지 않습니다.</p><input readOnly value={link} aria-label="Pickup 참가 링크"/><div className="row"><button onClick={share}>공유하기</button><button className="secondary" onClick={copy}>링크 복사</button></div>{message&&<p>{message}</p>}</main>;
}
