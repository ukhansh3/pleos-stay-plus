"use client";

import { useEffect, useRef, useState } from "react";

type Point = { latitude: number; longitude: number };
declare global { interface Window { kakao?: any } }

export function KakaoSimulatorMap({ vehicle, participant, onVehicleChange, onParticipantChange }: { vehicle: Point; participant: Point; onVehicleChange: (point: Point) => void; onParticipantChange: (point: Point) => void }) {
  const target = useRef<HTMLDivElement>(null);
  const mapRef = useRef<any>(null);
  const vehicleMarker = useRef<any>(null);
  const participantMarker = useRef<any>(null);
  const callbacks = useRef({ onVehicleChange, onParticipantChange });
  const [error, setError] = useState("");
  callbacks.current = { onVehicleChange, onParticipantChange };

  useEffect(() => {
    const key = process.env.NEXT_PUBLIC_KAKAO_MAP_JS_KEY;
    if (!key) { setError("Kakao Maps 키가 설정되지 않았습니다. 좌표 직접 입력은 계속 사용할 수 있습니다."); return; }
    const render = () => window.kakao.maps.load(() => {
      if (!target.current || mapRef.current) return;
      const center = new window.kakao.maps.LatLng(vehicle.latitude, vehicle.longitude);
      const map = new window.kakao.maps.Map(target.current, { center, level: 4 });
      const makeMarker = (position: Point, title: string, callback: (point: Point) => void) => {
        const marker = new window.kakao.maps.Marker({ map, position: new window.kakao.maps.LatLng(position.latitude, position.longitude), title, draggable: true });
        window.kakao.maps.event.addListener(marker, "dragend", () => {
          const next = marker.getPosition();
          callback({ latitude: next.getLat(), longitude: next.getLng() });
        });
        return marker;
      };
      mapRef.current = map;
      vehicleMarker.current = makeMarker(vehicle, "SIMULATED 차량", point => callbacks.current.onVehicleChange(point));
      participantMarker.current = makeMarker(participant, "SIMULATED 참가자", point => callbacks.current.onParticipantChange(point));
    });
    if (window.kakao?.maps) { render(); return; }
    const existing = document.querySelector<HTMLScriptElement>("script[data-kakao-maps]");
    if (existing) { existing.addEventListener("load", render); return () => existing.removeEventListener("load", render); }
    const script = document.createElement("script");
    script.dataset.kakaoMaps = "true";
    script.src = `https://dapi.kakao.com/v2/maps/sdk.js?appkey=${encodeURIComponent(key)}&autoload=false`;
    script.async = true;
    script.onload = render;
    script.onerror = () => setError("Kakao Maps를 불러오지 못했습니다. 좌표 직접 입력은 계속 사용할 수 있습니다.");
    document.head.appendChild(script);
  }, []);

  useEffect(() => { vehicleMarker.current?.setPosition(new window.kakao.maps.LatLng(vehicle.latitude, vehicle.longitude)); }, [vehicle.latitude, vehicle.longitude]);
  useEffect(() => { participantMarker.current?.setPosition(new window.kakao.maps.LatLng(participant.latitude, participant.longitude)); }, [participant.latitude, participant.longitude]);
  return <>{error && <p className="error">{error}</p>}<div ref={target} className="map simulator-map" aria-label="개발 전용 Pickup 시뮬레이터 지도" /></>;
}
