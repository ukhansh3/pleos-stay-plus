"use client";
import { useEffect, useRef, useState } from "react";
import type { PickupLocation } from "../lib/api";

declare global { interface Window { kakao?: any } }
export function KakaoPickupMap({vehicle,participant}:{vehicle:PickupLocation;participant:PickupLocation|null}) {
  const target=useRef<HTMLDivElement>(null); const [error,setError]=useState("");
  useEffect(()=>{
    const key=process.env.NEXT_PUBLIC_KAKAO_MAP_JS_KEY;
    if(!key){setError("Kakao Maps 키가 설정되지 않았습니다");return;}
    const render=()=>window.kakao.maps.load(()=>{
      if(!target.current)return; const center=new window.kakao.maps.LatLng(vehicle.latitude,vehicle.longitude);
      const map=new window.kakao.maps.Map(target.current,{center,level:4}); new window.kakao.maps.Marker({map,position:center,title:"차량"});
      if(participant)new window.kakao.maps.Marker({map,position:new window.kakao.maps.LatLng(participant.latitude,participant.longitude),title:"내 위치"});
    });
    if(window.kakao?.maps){render();return;}
    const script=document.createElement("script");script.src=`https://dapi.kakao.com/v2/maps/sdk.js?appkey=${encodeURIComponent(key)}&autoload=false`;script.async=true;script.onload=render;script.onerror=()=>setError("Kakao Maps를 불러오지 못했습니다");document.head.appendChild(script);
    return()=>{script.remove();};
  },[vehicle.latitude,vehicle.longitude,participant?.latitude,participant?.longitude]);
  return <>{error&&<p className="error">{error}</p>}<div ref={target} className="map" aria-label="Pickup 위치 지도"/></>;
}
