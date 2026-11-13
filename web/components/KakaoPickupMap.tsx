"use client";
import { useEffect, useRef, useState } from "react";
import type { PickupLocation } from "../lib/api";
import { validPickupCoordinates } from "../lib/pickup-mobile";

declare global { interface Window { kakao?: any } }
type Coordinate = object;
type PickupMap = { panTo: (position: Coordinate) => void; setCenter: (position: Coordinate) => void; setBounds: (bounds: object) => void; relayout: () => void };
type Overlay = { setMap: (map: PickupMap | null) => void; setPosition: (position: Coordinate) => void };
type KakaoMaps = {
  load: (ready: () => void) => void;
  LatLng: new (latitude: number, longitude: number) => Coordinate;
  LatLngBounds: new () => { extend: (coordinate: Coordinate) => void };
  Map: new (target: HTMLElement, options: { center: Coordinate; level: number }) => PickupMap;
  CustomOverlay: new (options: { map: PickupMap; position: Coordinate; content: HTMLElement; yAnchor: number; zIndex: number }) => Overlay;
  event: { addListener: (target: PickupMap, event: string, listener: () => void) => void; removeListener: (target: PickupMap, event: string, listener: () => void) => void };
};
let loading: Promise<KakaoMaps> | null = null;
function loadMaps(): Promise<KakaoMaps> {
  if (loading) return loading;
  loading = new Promise<KakaoMaps>((resolve, reject) => {
    const key = process.env.NEXT_PUBLIC_KAKAO_MAP_JS_KEY;
    if (!key) { reject(new Error("MAP_UNAVAILABLE")); return; }
    let settled = false;
    let script: HTMLScriptElement | null = null;
    const finish = (maps?: KakaoMaps) => {
      if (settled) return;
      settled = true; clearTimeout(timeout);
      script?.removeEventListener("load", loaded); script?.removeEventListener("error", failed);
      if (maps) resolve(maps); else reject(new Error("MAP_UNAVAILABLE"));
    };
    const loaded = () => {
      const maps = window.kakao?.maps as KakaoMaps | undefined;
      if (!maps) { finish(); return; }
      try { maps.load(() => finish(maps)); } catch { finish(); }
    };
    const failed = () => { if (script?.dataset.pickupMapLoader === "true") script.remove(); finish(); };
    const timeout = setTimeout(failed, 12_000);
    if (window.kakao?.maps) { loaded(); return; }
    script = document.querySelector<HTMLScriptElement>('script[src^="https://dapi.kakao.com/v2/maps/sdk.js"]');
    if (!script) {
      script = document.createElement("script");
      script.dataset.pickupMapLoader = "true";
      // Kakao domain authorization needs the origin, never the invitation path.
      script.referrerPolicy = "origin";
      script.src = `https://dapi.kakao.com/v2/maps/sdk.js?appkey=${encodeURIComponent(key)}&autoload=false`;
      script.async = true;
      script.addEventListener("load", loaded); script.addEventListener("error", failed);
      document.head.appendChild(script);
    } else {
      script.addEventListener("load", loaded); script.addEventListener("error", failed);
    }
  }).catch(error => { loading = null; throw error; });
  return loading;
}
function markerContent(kind: "vehicle" | "participant"): HTMLElement {
  const content = document.createElement("div");
  content.className = `pickup-map-pin pickup-map-pin--${kind}`;
  const glyph = document.createElement("span"); glyph.className = "pickup-map-pin-glyph";
  if (kind === "vehicle") {
    // Static artwork only; no server/user text is interpreted as HTML.
    glyph.innerHTML = '<svg viewBox="0 0 24 24" aria-hidden="true" fill="none" stroke="currentColor" stroke-width="1.8"><path d="m5 10 2-5h10l2 5M4 10h16v8H4zM7 14h.01M17 14h.01M6 18v2M18 18v2" stroke-linecap="round" stroke-linejoin="round"/></svg>';
  } else glyph.textContent = "나";
  const label = document.createElement("span"); label.className = "pickup-map-pin-label"; label.textContent = kind === "vehicle" ? "차량" : "내 위치";
  content.append(glyph, label); return content;
}
export function KakaoPickupMap({ vehicle, participant, stale = false }: { vehicle: PickupLocation; participant: PickupLocation | null; stale?: boolean }) {
  const target = useRef<HTMLDivElement>(null);
  const runtime = useRef<{ maps: KakaoMaps; map: PickupMap; vehicle: Overlay; participant: Overlay | null } | null>(null);
  const latest = useRef({ vehicle, participant }); latest.current = { vehicle, participant };
  const following = useRef(true);
  const [follow, setFollow] = useState(true);
  const [state, setState] = useState<"loading" | "ready" | "error">("loading");
  const [attempt, setAttempt] = useState(0);
  useEffect(() => {
    let disposed = false;
    let observer: ResizeObserver | null = null;
    let removeDrag: (() => void) | null = null;
    setState("loading");
    void loadMaps().then(maps => {
      if (disposed || !target.current) return;
      const position = latest.current.vehicle;
      if (!validPickupCoordinates(position)) { setState("error"); return; }
      const center = new maps.LatLng(position.latitude, position.longitude);
      const map = new maps.Map(target.current, { center, level: 4 });
      const marker = new maps.CustomOverlay({ map, position: center, content: markerContent("vehicle"), yAnchor: 1, zIndex: 2 });
      runtime.current = { maps, map, vehicle: marker, participant: null };
      const drag = () => { following.current = false; setFollow(false); };
      maps.event.addListener(map, "dragstart", drag);
      removeDrag = () => maps.event.removeListener(map, "dragstart", drag);
      if (typeof ResizeObserver !== "undefined") {
        observer = new ResizeObserver(() => {
          map.relayout();
          if (following.current) map.setCenter(new maps.LatLng(latest.current.vehicle.latitude, latest.current.vehicle.longitude));
        }); observer.observe(target.current);
      }
      setState("ready");
    }).catch(() => { if (!disposed) setState("error"); });
    return () => {
      disposed = true; observer?.disconnect(); removeDrag?.();
      runtime.current?.vehicle.setMap(null); runtime.current?.participant?.setMap(null); runtime.current = null;
      target.current?.replaceChildren();
    };
  }, [attempt]);
  useEffect(() => {
    const current = runtime.current;
    if (!current || state !== "ready" || !validPickupCoordinates(vehicle)) return;
    const position = new current.maps.LatLng(vehicle.latitude, vehicle.longitude);
    current.vehicle.setPosition(position);
    if (following.current) current.map.panTo(position);
    if (participant && validPickupCoordinates(participant)) {
      const mine = new current.maps.LatLng(participant.latitude, participant.longitude);
      if (current.participant) current.participant.setPosition(mine);
      else current.participant = new current.maps.CustomOverlay({ map: current.map, position: mine, content: markerContent("participant"), yAnchor: 1, zIndex: 1 });
    } else { current.participant?.setMap(null); current.participant = null; }
  }, [participant?.latitude, participant?.longitude, state, vehicle.latitude, vehicle.longitude]);
  function focusVehicle() {
    const current = runtime.current; if (!current) return;
    following.current = true; setFollow(true);
    current.map.panTo(new current.maps.LatLng(vehicle.latitude, vehicle.longitude));
  }
  function focusBoth() {
    const current = runtime.current; if (!current || !participant) return;
    following.current = false; setFollow(false);
    const bounds = new current.maps.LatLngBounds();
    bounds.extend(new current.maps.LatLng(vehicle.latitude, vehicle.longitude)); bounds.extend(new current.maps.LatLng(participant.latitude, participant.longitude)); current.map.setBounds(bounds);
  }
  return <div className={`pickup-map-shell${stale ? " is-stale" : ""}`}>
    <div ref={target} className="pickup-map-canvas" aria-label="차량과 내 위치 지도" />
    {state !== "ready" && <div className="pickup-map-empty" role="status">
      <span className="pickup-map-orbit" aria-hidden="true">↗</span>
      <strong>{state === "loading" ? "지도를 불러오고 있어요" : "지도를 불러오지 못했어요"}</strong>
      {state === "error" && <><p>차량 위치 갱신과 상태 알림은 계속 사용할 수 있어요.</p><button className="pm-button pm-button--secondary" onClick={() => setAttempt(value => value + 1)}>지도 다시 열기</button></>}
    </div>}
    {state === "ready" && <div className="pickup-map-tools"><button className={follow ? "is-active" : ""} onClick={focusVehicle} aria-pressed={follow}>차량 보기</button>{participant && <button onClick={focusBoth}>함께 보기</button>}</div>}
    <div className="pickup-map-legend"><span><i className="car-dot" />차량</span>{participant && <span><i className="person-dot" />내 위치</span>}{stale && <b>마지막 확인 위치</b>}</div>
  </div>;
}