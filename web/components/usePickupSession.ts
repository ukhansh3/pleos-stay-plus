"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { ApiFailure, pickupApi, type ParticipantStatus, type PickupSnapshot } from "../lib/api";
import { bindingMatchesInvitation, parsePickupBinding, pickupErrorMessage, type PickupBinding } from "../lib/pickup-mobile";
import { shouldApplyRevision, shouldRefreshForEvent, shouldRefreshSession } from "../lib/pickup-state";
import { watchPickupPosition } from "../lib/pickup-gps";
import { pickupSupabase } from "../lib/supabase";

const BINDING_KEY = "pickup-binding-v1";
export type PickupEnd = { kind: "complete" | "cancel" | "ended"; message: string };
export type PickupConnection = "connecting" | "connected" | "reconnecting" | "offline";
async function invitationFingerprint(token: string): Promise<string | null> {
  if (!token) return null;
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(token));
  return Array.from(new Uint8Array(digest), byte => byte.toString(16).padStart(2, "0")).join("");
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

export function usePickupSession(invitationToken: string) {
  const [restoring, setRestoring] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [binding, setBinding] = useState<PickupBinding | null>(null);
  const [access, setAccess] = useState("");
  const [snapshot, setSnapshot] = useState<PickupSnapshot | null>(null);
  const [end, setEnd] = useState<PickupEnd | null>(null);
  const [connection, setConnection] = useState<PickupConnection>("connecting");
  const [gpsSharing, setGpsSharing] = useState(false);
  const [gpsMessage, setGpsMessage] = useState("내 위치는 공유하지 않고 있어요.");
  const [realtimeEpoch, setRealtimeEpoch] = useState(0);
  const [now, setNow] = useState(() => Date.now());
  const bindingRef = useRef<PickupBinding | null>(null);
  const snapshotRef = useRef<PickupSnapshot | null>(null);
  const accessRef = useRef("");
  const generation = useRef(0);
  const identityRef = useRef("");
  const gpsStop = useRef<(() => void) | null>(null);
  const refreshFlight = useRef<{ generation: number; task: Promise<void> } | null>(null);
  const closeKeys = useRef<Partial<Record<"complete" | "cancel", string>>>({});

  const stopGps = useCallback(() => {
    gpsStop.current?.();
    gpsStop.current = null;
    setGpsSharing(false);
    setGpsMessage("내 위치는 공유하지 않고 있어요.");
  }, []);
  const clearBinding = useCallback((outcome: PickupEnd) => {
    generation.current++;
    stopGps();
    try { sessionStorage.removeItem(BINDING_KEY); sessionStorage.removeItem("pickup-binding"); } catch { /* Memory session is still cleared. */ }
    bindingRef.current = null;
    snapshotRef.current = null;
    setBinding(null);
    setSnapshot(null);
    setEnd(outcome);
    setError("");
  }, [stopGps]);
  const applySnapshot = useCallback((next: PickupSnapshot, expectedGeneration: number) => {
    if (generation.current !== expectedGeneration || bindingRef.current?.sessionId !== next.session.id) return;
    const current = snapshotRef.current;
    if (current && !shouldApplyRevision(current.revision, next.revision)) return;
    if (next.session.status !== "ACTIVE") {
      clearBinding({ kind: "ended", message: "Pickup이 종료됐어요. 새 픽업은 차량에서 초대해 주세요." });
      return;
    }
    snapshotRef.current = next;
    setSnapshot(next);
  }, [clearBinding]);
  const refresh = useCallback(async () => {
    const current = bindingRef.current;
    if (!current || !accessRef.current) return;
    const expectedGeneration = generation.current;
    const pending = refreshFlight.current;
    if (pending?.generation === expectedGeneration) return pending.task;
    const task = (async () => {
      try {
        let token = accessRef.current;
        let next: PickupSnapshot;
        try { next = await pickupApi.snapshot(token, current.sessionId); }
        catch (cause) {
          if (!(cause instanceof ApiFailure) || cause.status !== 401) throw cause;
          const renewed = await validAccessToken();
          if (!renewed) throw cause;
          if (generation.current !== expectedGeneration) return;
          token = renewed;
          accessRef.current = token;
          setAccess(token);
          next = await pickupApi.snapshot(token, current.sessionId);
        }
        if (generation.current !== expectedGeneration) return;
        applySnapshot(next, expectedGeneration);
        setError("");
        if (navigator.onLine) setConnection("connected");
      } catch (cause) {
        if (generation.current !== expectedGeneration) return;
        if (cause instanceof ApiFailure && [403, 404, 410].includes(cause.status)) {
          clearBinding({ kind: "ended", message: "초대가 철회되었거나 픽업이 종료됐어요. 차량에서 새 초대를 받아 주세요." });
        } else {
          setConnection(navigator.onLine ? "reconnecting" : "offline");
          setError(pickupErrorMessage(cause));
        }
      }
    })();
    refreshFlight.current = { generation: expectedGeneration, task };
    try { await task; } finally { if (refreshFlight.current?.task === task) refreshFlight.current = null; }
  }, [applySnapshot, clearBinding]);

  useEffect(() => {
    let disposed = false;
    let subscription: { unsubscribe: () => void } | undefined;
    const startGeneration = ++generation.current;
    // A new route/guest binding must never momentarily retain the previous pickup's coordinates.
    stopGps(); bindingRef.current = null; snapshotRef.current = null;
    setBinding(null); setSnapshot(null); setEnd(null); closeKeys.current = {};
    setRestoring(true);
    setConnection(navigator.onLine ? "connecting" : "offline");
    try {
      const supabase = pickupSupabase();
      subscription = supabase.auth.onAuthStateChange((event, session) => {
        if (disposed) return;
        if (event === "SIGNED_OUT") {
          accessRef.current = "";
          setAccess("");
          clearBinding({ kind: "ended", message: "인증이 종료됐어요. 초대 링크를 다시 열어 주세요." });
        } else if (session?.access_token) {
          if (identityRef.current && identityRef.current !== session.user.id && bindingRef.current) {
            clearBinding({ kind: "ended", message: "사용자가 변경됐어요. 초대 링크로 다시 참가해 주세요." });
          }
          identityRef.current = session.user.id;
          accessRef.current = session.access_token;
          setAccess(session.access_token);
        }
      }).data.subscription;
      void (async () => {
        try {
          const stored = parsePickupBinding(sessionStorage.getItem(BINDING_KEY) ?? sessionStorage.getItem("pickup-binding"));
          if (!stored) return;
          const fingerprint = await invitationFingerprint(invitationToken);
          if (disposed || generation.current !== startGeneration) return;
          if (!bindingMatchesInvitation(stored, fingerprint)) return;
          const token = await validAccessToken();
          if (disposed || generation.current !== startGeneration) return;
          if (!token) {
            clearBinding({ kind: "ended", message: "연결을 복구할 수 없어요. 차량에서 새 초대를 받아 주세요." });
            return;
          }
          bindingRef.current = stored;
          setBinding(stored);
          accessRef.current = token;
          setAccess(token);
          await refresh();
        } catch (cause) {
          if (!disposed) setError(pickupErrorMessage(cause));
        } finally { if (!disposed) setRestoring(false); }
      })();
    } catch { setError("연결을 준비하지 못했어요. 잠시 후 다시 시도해 주세요."); setRestoring(false); }
    return () => { disposed = true; generation.current++; gpsStop.current?.(); subscription?.unsubscribe(); };
  }, [clearBinding, invitationToken, refresh, stopGps]);

  useEffect(() => {
    const clock = setInterval(() => setNow(Date.now()), 1_000);
    return () => clearInterval(clock);
  }, []);

  useEffect(() => {
    if (!binding || !access) return;
    const supabase = pickupSupabase();
    let disposed = false;
    let reconnectTimer: ReturnType<typeof setTimeout> | null = null;
    const channel = supabase.channel(`pickup:${binding.sessionId}:${realtimeEpoch}`)
      .on("postgres_changes", { event: "*", schema: "public", table: "pickup_events", filter: `session_id=eq.${binding.sessionId}` }, payload => {
        const revision = (payload.new as { revision?: unknown } | null)?.revision;
        if (shouldRefreshForEvent(snapshotRef.current?.revision ?? 0, revision)) void refresh();
      })
      .on("postgres_changes", { event: "*", schema: "public", table: "pickup_current_locations", filter: `session_id=eq.${binding.sessionId}` }, () => { void refresh(); })
      .on("postgres_changes", { event: "*", schema: "public", table: "pickup_sessions", filter: `id=eq.${binding.sessionId}` }, () => { void refresh(); })
      .subscribe(status => {
        if (disposed) return;
        if (status === "SUBSCRIBED") { setConnection("connected"); void refresh(); }
        if (["CHANNEL_ERROR", "TIMED_OUT", "CLOSED"].includes(status)) {
          setConnection(navigator.onLine ? "reconnecting" : "offline");
          void refresh();
          if (reconnectTimer === null) reconnectTimer = setTimeout(() => setRealtimeEpoch(value => value + 1), 2_000);
        }
      });
    const poll = setInterval(() => { if (document.visibilityState === "visible") void refresh(); }, 5_000);
    const pageHide = () => stopGps();
    const pageShow = (event: PageTransitionEvent) => {
      if (!event.persisted) return;
      generation.current++; snapshotRef.current = null; setSnapshot(null); void refresh();
    };
    const online = () => { setConnection("connecting"); void refresh(); setRealtimeEpoch(value => value + 1); };
    const offline = () => setConnection("offline");
    const visible = () => { if (document.visibilityState === "visible") { setNow(Date.now()); void refresh(); } };
    window.addEventListener("pagehide", pageHide); window.addEventListener("pageshow", pageShow);
    window.addEventListener("online", online); window.addEventListener("offline", offline);
    document.addEventListener("visibilitychange", visible);
    return () => {
      disposed = true; clearInterval(poll); if (reconnectTimer) clearTimeout(reconnectTimer);
      window.removeEventListener("pagehide", pageHide); window.removeEventListener("pageshow", pageShow);
      window.removeEventListener("online", online); window.removeEventListener("offline", offline);
      document.removeEventListener("visibilitychange", visible); void supabase.removeChannel(channel);
    };
  }, [access, binding, realtimeEpoch, refresh, stopGps]);

  useEffect(() => {
    if (!gpsSharing || !binding) return;
    if (!navigator.geolocation || !window.isSecureContext) {
      setGpsMessage("이 브라우저에서는 내 위치를 공유할 수 없어요. 차량 지도는 계속 볼 수 있어요.");
      setGpsSharing(false); return;
    }
    const expectedGeneration = generation.current;
    const stop = watchPickupPosition({
      geolocation: navigator.geolocation,
      send: async (position, signal) => {
        if (generation.current !== expectedGeneration) return;
        await pickupApi.location(accessRef.current, binding.participantId, position, signal);
        if (generation.current === expectedGeneration) await refresh();
      },
      onMessage: setGpsMessage,
      onStopped: () => setGpsSharing(false),
      onSendError: cause => {
        if (cause instanceof ApiFailure && [403, 404, 410].includes(cause.status)) clearBinding({ kind: "ended", message: "연결이 종료되어 위치 공유를 중지했어요." });
        else setGpsMessage("내 위치 전송을 기다리고 있어요. 네트워크를 확인해 주세요.");
      },
    });
    gpsStop.current = stop;
    let disposed = false;
    let permission: PermissionStatus | null = null;
    const changed = () => {
      if (permission?.state === "denied") {
        stop(); setGpsSharing(false);
        setGpsMessage("위치 권한이 철회됐어요. 차량 지도와 상태 알림은 계속 사용할 수 있어요.");
      }
    };
    if (navigator.permissions?.query) void navigator.permissions.query({ name: "geolocation" }).then(result => {
      if (disposed) return;
      permission = result; permission.addEventListener("change", changed); changed();
    }).catch(() => { /* watchPosition error callback handles browsers without Permissions support. */ });
    return () => { disposed = true; permission?.removeEventListener("change", changed); stop(); if (gpsStop.current === stop) gpsStop.current = null; };
  }, [binding, clearBinding, gpsSharing, refresh]);

  async function join(name: string, consent: boolean) {
    if (busy) return;
    if (!invitationToken) { setError("차량에서 받은 초대 링크를 열어 주세요."); return; }
    if (!consent) { setError("Pickup 참가 동의를 확인해 주세요."); return; }
    setBusy(true); setError("");
    const expectedGeneration = generation.current;
    try {
      const supabase = pickupSupabase();
      let token = await validAccessToken();
      if (!token) {
        const signed = await supabase.auth.signInAnonymously();
        if (signed.error) throw signed.error;
        token = signed.data.session?.access_token ?? null;
      }
      if (!token) throw new Error("AUTH_REQUIRED");
      const joined = await pickupApi.redeem(token, invitationToken, true, name.trim());
      const fingerprint = await invitationFingerprint(invitationToken);
      if (generation.current !== expectedGeneration) return;
      const nextBinding: PickupBinding = { participantId: joined.participantId, sessionId: joined.sessionId, ...(fingerprint ? { invitationFingerprint: fingerprint } : {}) };
      bindingRef.current = nextBinding;
      snapshotRef.current = null;
      setEnd(null); setBinding(nextBinding); closeKeys.current = {};
      accessRef.current = token; setAccess(token);
      try { sessionStorage.setItem(BINDING_KEY, JSON.stringify(nextBinding)); sessionStorage.removeItem("pickup-binding"); }
      catch { setError("브라우저 저장이 꺼져 있어요. 새로고침하면 초대 링크를 다시 열어야 해요."); }
      await refresh();
      if (bindingRef.current) history.replaceState(null, "", "/pickup/session");
    } catch (cause) {
      if (generation.current === expectedGeneration) setError(pickupErrorMessage(cause));
    } finally { setBusy(false); setRestoring(false); }
  }
  async function updateStatus(status: "DEPARTED" | "NEARBY") {
    const current = bindingRef.current;
    if (!current || busy) return;
    const expectedGeneration = generation.current;
    setBusy(true); setError("");
    try {
      await pickupApi.status(accessRef.current, current.participantId, status);
      if (generation.current === expectedGeneration) await refresh();
    } catch (cause) {
      if (generation.current === expectedGeneration) { setError(pickupErrorMessage(cause)); void refresh(); }
    } finally { setBusy(false); }
  }
  async function close(kind: "complete" | "cancel") {
    const current = bindingRef.current;
    if (!current || busy) return;
    const expectedGeneration = generation.current;
    setBusy(true); setError(""); stopGps();
    const key = closeKeys.current[kind] ??= crypto.randomUUID();
    try {
      if (kind === "complete") await pickupApi.complete(accessRef.current, current.sessionId, key);
      else await pickupApi.cancel(accessRef.current, current.sessionId, key);
      if (generation.current === expectedGeneration) clearBinding({ kind, message: kind === "complete" ? "합류를 완료했어요. 위치 공유가 종료됐어요." : "픽업을 취소했어요. 위치 공유가 종료됐어요." });
    } catch (cause) { if (generation.current === expectedGeneration) { setError(pickupErrorMessage(cause)); void refresh(); } }
    finally { setBusy(false); }
  }
  function toggleGps() {
    if (busy) return;
    if (gpsSharing) { stopGps(); setGpsMessage("내 위치 공유를 중지했어요."); }
    else { setGpsMessage("내 위치 권한을 확인하고 있어요."); setGpsSharing(true); }
  }
  return { restoring, busy, error, snapshot, end, connection, gpsSharing, gpsMessage, now, binding, join, refresh, updateStatus, close, toggleGps };
}