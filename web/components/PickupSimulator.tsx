"use client";

import { createClient, type SupabaseClient } from "@supabase/supabase-js";
import { useEffect, useRef, useState } from "react";
import { pickupApi, pickupSimulatorApi } from "../lib/api";
import { KakaoSimulatorMap } from "./KakaoSimulatorMap";

type Point = { latitude: number; longitude: number };
type SimulatorSession = { driverToken: string; participantToken: string; sessionId: string; participantId: string };
const vehicleInitial = { latitude: 37.2636, longitude: 127.0286 };
const participantInitial = { latitude: 37.2660, longitude: 127.0310 };

function anonymousClient(): SupabaseClient {
  const url = process.env.NEXT_PUBLIC_SUPABASE_URL;
  const key = process.env.NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY;
  if (!url || !key) throw new Error("Supabase 공개 설정이 없습니다");
  return createClient(url, key, { auth: { persistSession: false, autoRefreshToken: false, detectSessionInUrl: false } });
}

function invitationToken(shareUrl: string): string {
  const token = new URL(shareUrl).pathname.split("/").filter(Boolean).at(-1);
  if (!token) throw new Error("초대 토큰을 확인할 수 없습니다");
  return decodeURIComponent(token);
}

export function PickupSimulator() {
  const [vehicle, setVehicle] = useState<Point>(vehicleInitial);
  const [participant, setParticipant] = useState<Point>(participantInitial);
  const [accuracy, setAccuracy] = useState(5);
  const [vehicleLoss, setVehicleLoss] = useState(false);
  const [participantLoss, setParticipantLoss] = useState(false);
  const [speed, setSpeed] = useState(10);
  const [running, setRunning] = useState(false);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState("개발 전용 세션을 생성해 주세요.");
  const [session, setSession] = useState<SimulatorSession | null>(null);
  const [revision, setRevision] = useState(0);
  const [proximityCandidate, setProximityCandidate] = useState(false);
  const simulatedTime = useRef(Date.now());
  const pathStep = useRef(0);

  async function createTestSession() {
    setBusy(true);
    setMessage("익명 테스트 사용자와 Pickup 세션을 생성하고 있습니다…");
    try {
      const driver = anonymousClient();
      const guest = anonymousClient();
      const driverAuth = await driver.auth.signInAnonymously();
      if (driverAuth.error) throw driverAuth.error;
      const guestAuth = await guest.auth.signInAnonymously();
      if (guestAuth.error) throw guestAuth.error;
      const driverToken = driverAuth.data.session?.access_token;
      const participantToken = guestAuth.data.session?.access_token;
      if (!driverToken || !participantToken) throw new Error("Supabase 익명 인증이 비활성화되어 있습니다");
      const created = await pickupApi.create(driverToken);
      const invited = await pickupApi.invite(driverToken, created.id);
      const joined = await pickupApi.redeem(participantToken, invitationToken(invited.shareUrl), true, "SIMULATED 참가자");
      setSession({ driverToken, participantToken, sessionId: created.id, participantId: joined.participantId });
      setRevision(joined.revision);
      simulatedTime.current = Date.now();
      pathStep.current = 0;
      setMessage("SIMULATED 세션 준비 완료. 위치 전송 또는 자동 경로를 실행할 수 있습니다.");
    } catch (cause) { setMessage((cause as Error).message); }
    finally { setBusy(false); }
  }

  async function transmit(nextVehicle = vehicle, nextParticipant = participant, advanceMs = 0) {
    if (!session) { setMessage("먼저 개발 전용 세션을 생성해 주세요."); return; }
    simulatedTime.current += advanceMs;
    const now = new Date(simulatedTime.current).toISOString();
    const location = (point: Point) => ({ ...point, accuracyMeters: accuracy, observedAt: now, simulatedNow: now });
    try {
      const results = await Promise.all([
        vehicleLoss ? null : pickupSimulatorApi.vehicleLocation(session.driverToken, session.sessionId, location(nextVehicle)),
        participantLoss ? null : pickupSimulatorApi.participantLocation(session.participantToken, session.participantId, location(nextParticipant)),
      ]);
      const latest = [...results].reverse().find((value): value is NonNullable<typeof value> => value !== null);
      if (latest) { setRevision(latest.revision); setProximityCandidate(latest.proximityCandidate); }
      setMessage(vehicleLoss || participantLoss ? "신호 손실 상태를 반영했습니다. 끊긴 위치는 전송하지 않았습니다." : "SIMULATED 위치를 실제 Pickup 도메인에 전송했습니다.");
    } catch (cause) { setMessage((cause as Error).message); }
  }

  useEffect(() => {
    if (!running || !session) return;
    const timer = setInterval(() => {
      const step = pathStep.current++;
      const progress = Math.min(step / 5, 1);
      const near = { latitude: participantInitial.latitude + (vehicle.latitude + 0.00004 - participantInitial.latitude) * progress, longitude: participantInitial.longitude + (vehicle.longitude + 0.00004 - participantInitial.longitude) * progress };
      setParticipant(near);
      void transmit(vehicle, near, speed * 1_000);
      if (step >= 9) setRunning(false);
    }, 1_000);
    return () => clearInterval(timer);
  }, [running, session, speed, vehicle, vehicleLoss, participantLoss, accuracy]);

  async function participantStatus(status: "DEPARTED" | "NEARBY") {
    if (!session) return;
    try { await pickupApi.status(session.participantToken, session.participantId, status); setMessage(`참가자 상태 ${status} 이벤트를 전송했습니다.`); }
    catch (cause) { setMessage((cause as Error).message); }
  }
  async function complete() {
    if (!session) return;
    try { await pickupApi.complete(session.participantToken, session.sessionId); setRunning(false); setMessage("수동 합류 완료 및 위치 삭제를 요청했습니다."); }
    catch (cause) { setMessage((cause as Error).message); }
  }

  return <main className="panel stack simulator-panel"><h1>DEV Pickup Simulator</h1><p className="error">SIMULATED 테스트 전용 · localhost 전용 · 운영/공개 터널 접근 차단</p><KakaoSimulatorMap vehicle={vehicle} participant={participant} onVehicleChange={setVehicle} onParticipantChange={setParticipant} /><section className="coordinate-grid"><label>차량 위도<input type="number" step="0.000001" value={vehicle.latitude} onChange={event => setVehicle({ ...vehicle, latitude: Number(event.target.value) })} /></label><label>차량 경도<input type="number" step="0.000001" value={vehicle.longitude} onChange={event => setVehicle({ ...vehicle, longitude: Number(event.target.value) })} /></label><label>참가자 위도<input type="number" step="0.000001" value={participant.latitude} onChange={event => setParticipant({ ...participant, latitude: Number(event.target.value) })} /></label><label>참가자 경도<input type="number" step="0.000001" value={participant.longitude} onChange={event => setParticipant({ ...participant, longitude: Number(event.target.value) })} /></label></section><div className="row"><label>GPS 정확도(m)<input type="number" min="1" max="1000" value={accuracy} onChange={event => setAccuracy(Number(event.target.value))} /></label><label>시간 배속<input type="number" min="1" max="30" value={speed} onChange={event => setSpeed(Number(event.target.value))} /></label></div><div className="row"><label><input type="checkbox" checked={vehicleLoss} onChange={event => setVehicleLoss(event.target.checked)} /> 차량 신호 손실</label><label><input type="checkbox" checked={participantLoss} onChange={event => setParticipantLoss(event.target.checked)} /> 참가자 신호 손실</label></div><div className="row"><button onClick={createTestSession} disabled={busy}>테스트 세션 생성</button><button className="secondary" onClick={() => transmit()} disabled={!session}>현재 좌표 전송/재연결</button></div><div className="row"><button onClick={() => { pathStep.current = 0; setRunning(true); }} disabled={!session || running}>자동 경로 재생</button><button className="secondary" onClick={() => setRunning(false)} disabled={!running}>재생 중지</button></div><div className="row"><button onClick={() => participantStatus("DEPARTED")} disabled={!session}>출발 이벤트</button><button onClick={() => participantStatus("NEARBY")} disabled={!session}>근접 상태 이벤트</button><button className="danger" onClick={complete} disabled={!session}>수동 합류 완료</button></div><p className="notice">revision {revision} · 근접 후보 {proximityCandidate ? "TRUE" : "FALSE"} · {message}</p></main>;
}
