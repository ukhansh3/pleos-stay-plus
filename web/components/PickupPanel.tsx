"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import type { RealtimeChannel } from "@supabase/supabase-js";
import { ApiFailure, pickupApi, type ParticipantStatus, type PickupSnapshot } from "../lib/api";
import { isStale, shouldApplyRevision, shouldRefreshForEvent, shouldRefreshSession, shouldUploadPosition } from "../lib/pickup-state";
import { pickupSupabase } from "../lib/supabase";
import { KakaoPickupMap } from "./KakaoPickupMap";

const BINDING_KEY = "pickup-binding-v1";
type Binding = { participantId: string; sessionId: string };

function readBinding(): Binding | null {
  const raw = sessionStorage.getItem(BINDING_KEY) ?? sessionStorage.getItem("pickup-binding");
  if (!raw) return null;
  try {
    const value = JSON.parse(raw) as Partial<Binding>;
    return typeof value.participantId === "string" && typeof value.sessionId === "string"
      ? { participantId: value.participantId, sessionId: value.sessionId }
      : null;
  } catch {
    return null;
  }
}

async function validAccessToken(): Promise<string | null> {
  const supabase = pickupSupabase();
  let { data: { session }, error } = await supabase.auth.getSession();
  if (error) throw error;
  if (session && shouldRefreshSession(session.expires_at)) {
    const refreshed = await supabase.auth.refreshSession();
    if (refreshed.error) throw refreshed.error;
    session = refreshed.data.session;
  }
  return session?.access_token ?? null;
}

export function PickupPanel({ invitationToken }: { invitationToken: string }) {
  const [consent, setConsent] = useState(false);
  const [name, setName] = useState("");
  const [busy, setBusy] = useState(false);
  const [restoring, setRestoring] = useState(true);
  const [error, setError] = useState("");
  const [access, setAccess] = useState("");
  const [participantId, setParticipantId] = useState("");
  const [sessionId, setSessionId] = useState("");
  const [snapshot, setSnapshot] = useState<PickupSnapshot | null>(null);
  const [gpsSharing, setGpsSharing] = useState(false);
  const [gpsMessage, setGpsMessage] = useState("GPS는 선택 사항입니다.");
  const [realtimeEpoch, setRealtimeEpoch] = useState(0);
  const snapshotRef = useRef<PickupSnapshot | null>(null);
  const accessRef = useRef("");
  const watchRef = useRef<number | null>(null);
  const lastGpsUploadRef = useRef<number | null>(null);
  const gpsUploadInFlight = useRef(false);

  const applySnapshot = useCallback((next: PickupSnapshot) => {
    const current = snapshotRef.current;
    if (!current || shouldApplyRevision(current.revision, next.revision)) {
      snapshotRef.current = next;
      setSnapshot(next);
    }
    if (next.session.status !== "ACTIVE") setGpsSharing(false);
  }, []);

  const clearBinding = useCallback((message: string) => {
    sessionStorage.removeItem(BINDING_KEY);
    sessionStorage.removeItem("pickup-binding");
    setGpsSharing(false);
    setParticipantId("");
    setSessionId("");
    setSnapshot(null);
    snapshotRef.current = null;
    setError(message);
  }, []);

  const refresh = useCallback(async (token = accessRef.current, id = sessionId) => {
    if (!token || !id) return;
    try {
      applySnapshot(await pickupApi.snapshot(token, id));
      setError("");
    } catch (cause) {
      if (cause instanceof ApiFailure && cause.status === 401) {
        const nextToken = await validAccessToken();
        if (nextToken && nextToken !== token) {
          accessRef.current = nextToken;
          setAccess(nextToken);
          applySnapshot(await pickupApi.snapshot(nextToken, id));
          return;
        }
      }
      if (cause instanceof ApiFailure && (cause.status === 404 || cause.status === 410)) {
        clearBinding("초대가 철회되었거나 Pickup이 종료되었습니다. 새 초대를 요청해 주세요.");
        return;
      }
      setError((cause as Error).message);
    }
  }, [applySnapshot, clearBinding, sessionId]);

  useEffect(() => {
    const supabase = pickupSupabase();
    const { data: authListener } = supabase.auth.onAuthStateChange((event, session) => {
      if (event === "SIGNED_OUT") {
        accessRef.current = "";
        setAccess("");
        clearBinding("인증 세션이 종료되었습니다. 초대 링크로 다시 참가해 주세요.");
      } else if (session?.access_token) {
        accessRef.current = session.access_token;
        setAccess(session.access_token);
      }
    });
    void (async () => {
      const binding = readBinding();
      if (!binding) {
        sessionStorage.removeItem(BINDING_KEY);
        sessionStorage.removeItem("pickup-binding");
        setRestoring(false);
        return;
      }
      sessionStorage.setItem(BINDING_KEY, JSON.stringify(binding));
      sessionStorage.removeItem("pickup-binding");
      try {
        const token = await validAccessToken();
        if (!token) {
          clearBinding("인증 세션이 만료되었습니다. 초대 링크로 다시 참가해 주세요.");
          return;
        }
        accessRef.current = token;
        setAccess(token);
        setParticipantId(binding.participantId);
        setSessionId(binding.sessionId);
        applySnapshot(await pickupApi.snapshot(token, binding.sessionId));
      } catch (cause) {
        clearBinding(cause instanceof ApiFailure && (cause.status === 404 || cause.status === 410)
          ? "초대가 철회되었거나 Pickup이 종료되었습니다. 새 초대를 요청해 주세요."
          : (cause as Error).message);
      } finally {
        setRestoring(false);
      }
    })();
    return () => authListener.subscription.unsubscribe();
  }, [applySnapshot, clearBinding]);

  async function join() {
    if (!invitationToken) { setError("유효한 초대 링크가 필요합니다."); return; }
    if (!consent) { setError("정보 제공 동의가 필요합니다."); return; }
    setBusy(true);
    setError("");
    try {
      const supabase = pickupSupabase();
      let token = await validAccessToken();
      if (!token) {
        const result = await supabase.auth.signInAnonymously();
        if (result.error) throw result.error;
        token = result.data.session?.access_token ?? null;
      }
      if (!token) throw new Error("Supabase 익명 인증이 비활성화되어 있습니다");
      const joined = await pickupApi.redeem(token, invitationToken, true, name.trim());
      const binding = { participantId: joined.participantId, sessionId: joined.sessionId };
      sessionStorage.setItem(BINDING_KEY, JSON.stringify(binding));
      accessRef.current = token;
      setAccess(token);
      setParticipantId(binding.participantId);
      setSessionId(binding.sessionId);
      applySnapshot(await pickupApi.snapshot(token, binding.sessionId));
      history.replaceState(null, "", "/pickup/session");
    } catch (cause) {
      setError((cause as Error).message);
    } finally {
      setBusy(false);
      setRestoring(false);
    }
  }

  useEffect(() => {
    if (!access || !sessionId) return;
    const supabase = pickupSupabase();
    let channel: RealtimeChannel | null = null;
    let reconnectTimer: ReturnType<typeof setTimeout> | null = null;
    let disposed = false;
    channel = supabase.channel(`pickup:${sessionId}:${realtimeEpoch}`)
      .on("postgres_changes", { event: "*", schema: "public", table: "pickup_events", filter: `session_id=eq.${sessionId}` }, payload => {
        const revision = (payload.new as { revision?: unknown } | null)?.revision;
        if (shouldRefreshForEvent(snapshotRef.current?.revision ?? 0, revision)) void refresh();
      })
      .subscribe(status => {
        if (status === "SUBSCRIBED") void refresh();
        if ((status === "CHANNEL_ERROR" || status === "TIMED_OUT" || status === "CLOSED") && !disposed) {
          void refresh();
          reconnectTimer = setTimeout(() => setRealtimeEpoch(value => value + 1), 1_500);
        }
      });
    const poll = setInterval(() => void refresh(), 15_000);
    const onVisibility = () => { if (document.visibilityState === "visible") void refresh(); };
    window.addEventListener("online", onVisibility);
    document.addEventListener("visibilitychange", onVisibility);
    return () => {
      disposed = true;
      clearInterval(poll);
      if (reconnectTimer) clearTimeout(reconnectTimer);
      window.removeEventListener("online", onVisibility);
      document.removeEventListener("visibilitychange", onVisibility);
      if (channel) void supabase.removeChannel(channel);
    };
  }, [access, realtimeEpoch, refresh, sessionId]);

  useEffect(() => {
    if (!gpsSharing || !access || !participantId) return;
    if (!navigator.geolocation) {
      setGpsMessage("브라우저 GPS를 지원하지 않습니다. 지도와 수동 상태 기능은 계속 사용할 수 있습니다.");
      setGpsSharing(false);
      return;
    }
    const stop = () => {
      if (watchRef.current !== null) navigator.geolocation.clearWatch(watchRef.current);
      watchRef.current = null;
    };
    watchRef.current = navigator.geolocation.watchPosition(position => {
      const now = Date.now();
      if (!shouldUploadPosition(lastGpsUploadRef.current, now) || gpsUploadInFlight.current) return;
      lastGpsUploadRef.current = now;
      gpsUploadInFlight.current = true;
      void pickupApi.location(accessRef.current, participantId, position)
        .then(() => {
          setGpsMessage(document.visibilityState === "hidden"
            ? "브라우저가 백그라운드여서 위치 갱신이 지연될 수 있습니다."
            : "내 위치를 공유 중입니다 (약 5초 간격 목표)."
          );
          return refresh();
        })
        .catch(cause => {
          if (cause instanceof ApiFailure && (cause.status === 404 || cause.status === 410)) {
            stop();
            clearBinding("초대가 철회되었거나 Pickup이 종료되어 위치 공유를 중지했습니다.");
          } else setGpsMessage((cause as Error).message);
        })
        .finally(() => { gpsUploadInFlight.current = false; });
    }, failure => {
      setGpsMessage(failure.code === failure.PERMISSION_DENIED
        ? "GPS 동의를 거부하거나 철회했습니다. 지도와 수동 상태 기능은 계속 사용할 수 있습니다."
        : "GPS 위치를 받지 못했습니다. 지도와 수동 상태 기능은 계속 사용할 수 있습니다."
      );
      stop();
      setGpsSharing(false);
    }, { enableHighAccuracy: true, timeout: 10_000, maximumAge: 5_000 });
    return stop;
  }, [access, clearBinding, gpsSharing, participantId, refresh]);

  async function updateStatus(value: ParticipantStatus) {
    if (!access || !participantId) return;
    setBusy(true);
    try { await pickupApi.status(access, participantId, value); await refresh(); }
    catch (cause) { setError((cause as Error).message); }
    finally { setBusy(false); }
  }

  async function close(kind: "complete" | "cancel") {
    if (!access || !sessionId) return;
    setBusy(true);
    try {
      if (kind === "complete") await pickupApi.complete(access, sessionId);
      else await pickupApi.cancel(access, sessionId);
      clearBinding(kind === "complete" ? "Pickup 합류를 완료했습니다." : "Pickup을 취소했습니다.");
    } catch (cause) { setError((cause as Error).message); }
    finally { setBusy(false); }
  }

  if (restoring) return <main className="panel stack"><h1>Pickup 연결 복구</h1><p>인증과 참가 상태를 확인하고 있습니다…</p></main>;
  if (!snapshot) return <main className="panel stack"><h1>Pickup 참가</h1><p>차량의 최신 위치와 픽업 상태를 공유받습니다. 위치 이력은 저장하지 않으며 내 GPS 제공은 선택 사항입니다.</p><label><input type="checkbox" checked={consent} onChange={event => setConsent(event.target.checked)} /> 정보 제공 및 Pickup 참가에 동의합니다.</label><label>별명(선택)<input value={name} maxLength={60} onChange={event => setName(event.target.value)} placeholder="픽업 대상" /></label>{invitationToken && <button onClick={join} disabled={busy}>{busy ? "확인 중…" : "참가하기"}</button>}{!invitationToken && <p className="notice">이 세션은 더 이상 복구할 수 없습니다. 차량에서 새 초대 QR을 받아 주세요.</p>}{error && <p className="error">{error}</p>}</main>;

  const vehicle = snapshot.vehicleLocation;
  const stale = isStale(vehicle?.receivedAt ?? null);
  const mapUrl = vehicle ? `https://map.kakao.com/link/to/${encodeURIComponent("Pickup 차량")},${vehicle.latitude},${vehicle.longitude}` : "#";
  return <main className="panel stack"><h1>Pickup 상태</h1><p>상태: {snapshot.session.participant?.status ?? snapshot.session.status}</p>{snapshot.proximityCandidate && <p className="notice">차량과 가까운 위치가 감지되었습니다. 실제 합류는 직접 확인해 주세요.</p>}{vehicle ? <><KakaoPickupMap vehicle={vehicle} participant={snapshot.participantLocation} /><p className={stale ? "error" : ""}>차량 위치 갱신: {vehicle.receivedAt}{stale ? " · 오래된 위치" : ""}</p><a className="action" href={mapUrl} target="_blank" rel="noreferrer">Kakao 길찾기 열기</a></> : <p className="error">차량 위치를 아직 받지 못했습니다.</p>}<button className="secondary" onClick={() => setGpsSharing(value => !value)}>{gpsSharing ? "내 위치 공유 중지" : "내 위치 계속 공유"}</button><p>{gpsMessage}</p><div className="row"><button onClick={() => updateStatus("DEPARTED")} disabled={busy}>출발</button><button onClick={() => updateStatus("NEARBY")} disabled={busy}>곧 도착</button></div><div className="row"><button onClick={() => close("complete")} disabled={busy}>합류 완료</button><button className="danger" onClick={() => close("cancel")} disabled={busy}>픽업 취소</button></div>{error && <p className="error">{error}</p>}</main>;
}
