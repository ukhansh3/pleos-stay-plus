"use client";
import { useState } from "react";
export function PickupSimulator(){const[lat,setLat]=useState("37.2636");const[lng,setLng]=useState("127.0286");return <main className="panel stack"><h1>DEV Pickup Simulator</h1><p className="error">SIMULATED 테스트 전용 · 실제 위치와 혼합 금지</p><label>위도<input value={lat} onChange={e=>setLat(e.target.value)}/></label><label>경도<input value={lng} onChange={e=>setLng(e.target.value)}/></label><p>가상 위치: {lat}, {lng}</p><p>서버의 별도 QA 자격증명 API가 승인되기 전에는 좌표를 전송하지 않습니다.</p></main>}
