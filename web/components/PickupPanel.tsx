"use client";
import { useEffect, useRef, useState, type FormEvent } from "react";
import { KakaoPickupMap } from "./KakaoPickupMap";
import { PickupBrand, PickupIcon, PickupMessage, PickupMobileShell } from "./PickupMobileChrome";
import { usePickupSession, type PickupEnd } from "./usePickupSession";
import { canSetParticipantStatus, locationAge, locationIsStale, participantLabels, pickupStraightLineDistance, validPickupCoordinates } from "../lib/pickup-mobile";

export function PickupJoinScreen({ name, consent, busy, error, onName, onConsent, onSubmit }: {
  name: string; consent: boolean; busy: boolean; error: string;
  onName: (value: string) => void; onConsent: (value: boolean) => void; onSubmit: () => void;
}) {
  function submit(event: FormEvent) { event.preventDefault(); (document.activeElement as HTMLElement | null)?.blur(); onSubmit(); }
  return <PickupMobileShell className="pm-welcome"><PickupBrand trailing={<span className="pm-private-label"><PickupIcon name="shield" />초대 전용</span>} />
    <section className="pm-welcome-hero"><div className="pm-hero-car" aria-hidden="true"><div className="pm-hero-ring" /><PickupIcon name="car" /><span className="pm-hero-pin"><PickupIcon name="pin" /></span></div><p className="pm-eyebrow">PICKUP INVITATION</p><h1>차량 위치를 보고<br /><span>편하게 만나세요.</span></h1><p className="pm-lead">참가하면 차량 위치와 픽업 상태를<br />휴대전화에서 확인할 수 있어요.</p></section>
    <form className="pm-card pm-join-card" onSubmit={submit}>
      <h2>픽업에 참가하기</h2><label className="pm-field" htmlFor="pickup-nickname"><span>별명 <small>선택</small></span><input id="pickup-nickname" autoComplete="nickname" enterKeyHint="done" value={name} maxLength={60} onChange={event => onName(event.target.value)} placeholder="운전자에게 표시할 이름" /></label>
      <label className="pm-consent"><input type="checkbox" checked={consent} onChange={event => onConsent(event.target.checked)} /><span>차량 위치·픽업 상태 공유에 동의합니다.</span></label>
      <p className="pm-footnote">내 위치 공유는 선택할 수 있어요. 픽업이 종료되면 현재 위치 정보가 삭제돼요.</p>
      {error && <PickupMessage error>{error}</PickupMessage>}
      <button className="pm-button pm-button--primary" type="submit" disabled={busy || !consent}>{busy ? "연결하는 중…" : "참가하고 차량 보기"}{!busy && <PickupIcon name="arrow" />}</button>
    </form><p className="pm-footer-note"><PickupIcon name="shield" />이 초대에는 한 명만 참가할 수 있어요.</p>
  </PickupMobileShell>;
}
export function PickupEndScreen({ end }: { end: PickupEnd }) {
  return <PickupMobileShell className="pm-welcome"><PickupBrand /><section className="pm-end-card"><span className={`pm-end-symbol${end.kind === "complete" ? " is-success" : ""}`}><PickupIcon name={end.kind === "complete" ? "check" : "shield"} /></span><p className="pm-eyebrow">PICKUP</p><h1>{end.kind === "complete" ? "반갑게 만났네요." : end.kind === "cancel" ? "픽업을 취소했어요." : "연결이 종료됐어요."}</h1><p className="pm-lead">{end.message}</p><div className="pm-card pm-end-note"><PickupIcon name="shield" /><span>이 화면에서는 차량 위치를<br />더 이상 공유하지 않아요.</span></div></section></PickupMobileShell>;
}
function ConfirmPickup({ kind, busy, onDismiss, onConfirm }: { kind: "complete" | "cancel" | null; busy: boolean; onDismiss: () => void; onConfirm: () => void }) {
  const dialog = useRef<HTMLDialogElement>(null);
  useEffect(() => { if (kind && !dialog.current?.open) dialog.current?.showModal(); else if (!kind && dialog.current?.open) dialog.current.close(); }, [kind]);
  return <dialog className="pm-dialog" ref={dialog} aria-labelledby="pickup-confirm-title" onCancel={event => { event.preventDefault(); if (!busy) onDismiss(); }} onClick={event => { if (event.target === event.currentTarget && !busy) onDismiss(); }}>
    <div><span className="pm-dialog-symbol"><PickupIcon name={kind === "complete" ? "check" : "pin"} /></span><h2 id="pickup-confirm-title">{kind === "complete" ? "차량과 합류했나요?" : "픽업을 취소할까요?"}</h2><p>확인하면 위치 공유와 픽업 연결이 종료돼요.</p><div className="pm-dialog-actions"><button autoFocus className="pm-button pm-button--secondary" disabled={busy} onClick={onDismiss}>돌아가기</button><button className={`pm-button ${kind === "cancel" ? "pm-button--danger" : "pm-button--primary"}`} disabled={busy} onClick={onConfirm}>{busy ? "처리 중…" : kind === "complete" ? "합류 완료" : "픽업 취소"}</button></div></div>
  </dialog>;
}
export function PickupPanel({ invitationToken }: { invitationToken: string }) {
  const session = usePickupSession(invitationToken);
  const [name, setName] = useState("");
  const [consent, setConsent] = useState(false);
  const [confirmation, setConfirmation] = useState<"complete" | "cancel" | null>(null);
  const [hidden, setHidden] = useState(false);
  useEffect(() => { const changed = () => setHidden(document.visibilityState === "hidden"); document.addEventListener("visibilitychange", changed); return () => document.removeEventListener("visibilitychange", changed); }, []);
  if (session.restoring) return <PickupMobileShell><PickupBrand /><section className="pm-loading" aria-live="polite"><div className="pm-spinner" /><h1>픽업을 연결하고 있어요</h1><p>이전 참가 상태를 확인하고 있어요.</p></section></PickupMobileShell>;
  if (session.end) return <PickupEndScreen end={session.end} />;
  if (!session.snapshot) {
    if (session.binding) return <PickupMobileShell><PickupBrand /><section className="pm-loading"><PickupIcon name="refresh" /><h1>연결을 확인하고 있어요</h1>{session.error && <PickupMessage error>{session.error}</PickupMessage>}<button className="pm-button pm-button--primary" onClick={() => void session.refresh()}>다시 연결</button></section></PickupMobileShell>;
    if (!invitationToken) return <PickupEndScreen end={{ kind: "ended", message: "차량에서 받은 최신 초대 링크를 열어 주세요." }} />;
    return <PickupJoinScreen name={name} consent={consent} busy={session.busy} error={session.error} onName={setName} onConsent={setConsent} onSubmit={() => void session.join(name, consent)} />;
  }
  const snapshot = session.snapshot;
  const vehicle = snapshot.vehicleLocation && validPickupCoordinates(snapshot.vehicleLocation) ? snapshot.vehicleLocation : null;
  const participant = session.gpsSharing && !locationIsStale(snapshot.participantLocation, session.now) ? snapshot.participantLocation : null;
  const stale = locationIsStale(vehicle, session.now);
  const distance = pickupStraightLineDistance(vehicle, participant, session.now);
  const status = snapshot.session.participant?.status ?? "JOINED";
  const connectionLabel = { connecting: "연결 중", connected: "연결됨", reconnecting: "다시 연결 중", offline: "오프라인" }[session.connection];
  const mapUrl = vehicle ? `https://map.kakao.com/link/to/${encodeURIComponent("Pickup 차량")},${vehicle.latitude},${vehicle.longitude}` : null;
  async function finish() { if (!confirmation) return; await session.close(confirmation); setConfirmation(null); }
  return <PickupMobileShell className="pm-connected"><PickupBrand trailing={<span className={`pm-live-badge ${session.connection === "connected" ? "is-connected" : "is-muted"}`}><i />{connectionLabel}</span>} />
    <section className="pm-map-section" aria-label="픽업 차량 위치">
      <div className="pm-map-heading"><div><p className="pm-eyebrow">MEET YOUR RIDE</p><h1>차량 위치</h1></div><span className={`pm-freshness${stale ? " is-stale" : ""}`}>{vehicle?.source === "SIMULATED" ? "테스트 위치" : vehicle ? stale ? "갱신 지연" : "최근 위치" : "위치 대기"}</span></div>
      {vehicle ? <KakaoPickupMap vehicle={vehicle} participant={participant} stale={stale} /> : <div className="pickup-map-shell"><div className="pickup-map-empty"><span className="pickup-map-orbit"><PickupIcon name="car" /></span><strong>차량 위치를 기다리고 있어요</strong><p>운전자 앱이 위치를 보내면 여기에 표시돼요.</p><button className="pm-button pm-button--secondary" onClick={() => void session.refresh()}>다시 확인</button></div></div>}
      <div className="pm-location-caption"><span><i className={stale ? "is-stale" : ""} />{locationAge(vehicle, session.now)}</span><button onClick={() => void session.refresh()} aria-label="차량 위치 새로고침"><PickupIcon name="refresh" /></button></div>
      {vehicle && stale && <PickupMessage>마지막으로 확인한 위치예요. 차량의 현재 위치와 다를 수 있어요.</PickupMessage>}
      {session.error && <PickupMessage error>{session.error}</PickupMessage>}
    </section>
    <section className="pm-card pm-status-card"><div className="pm-status-heading"><div className="pm-car-tile"><PickupIcon name="car" /></div><div><p>픽업 차량</p><h2>{status === "JOINED" ? "출발할 준비가 됐나요?" : status === "DEPARTED" ? "만나는 곳으로 이동 중" : "차량 근처에서 만나요"}</h2></div><span className="pm-status-pill">{participantLabels[status]}</span></div>
      {distance && <p className="pm-footnote">지도상 직선거리 <strong>{distance}</strong></p>}
      {snapshot.proximityCandidate && <PickupMessage>차량과 가까워졌어요. 실제로 만난 뒤 합류를 완료해 주세요.</PickupMessage>}
      {mapUrl && <a className="pm-direction" href={mapUrl} target="_blank" rel="noreferrer"><PickupIcon name="pin" /><span>차량까지 길찾기<small>카카오맵에서 열기</small></span><PickupIcon name="arrow" /></a>}
      <div className="pm-status-actions"><button className="pm-button pm-button--secondary" disabled={session.busy || !canSetParticipantStatus(status, "DEPARTED")} onClick={() => void session.updateStatus("DEPARTED")}>{status !== "JOINED" ? <PickupIcon name="check" /> : <PickupIcon name="arrow" />}출발</button><button className="pm-button pm-button--secondary" disabled={session.busy || !canSetParticipantStatus(status, "NEARBY")} onClick={() => void session.updateStatus("NEARBY")}>{status === "NEARBY" || status === "ARRIVED" ? <PickupIcon name="check" /> : <PickupIcon name="pin" />}곧 도착</button></div>
    </section>
    <section className="pm-card pm-gps-card"><div className="pm-gps-heading"><span className={`pm-gps-icon${session.gpsSharing ? " is-active" : ""}`}><PickupIcon name="location" /></span><div><h2>내 위치 공유 <small>선택</small></h2><p>{session.gpsSharing ? "위치 권한을 허용하면 공유해요" : "공유하지 않아도 차량을 볼 수 있어요"}</p></div><button type="button" role="switch" className="pm-switch" aria-label="내 위치 공유" aria-checked={session.gpsSharing} disabled={session.busy} onClick={session.toggleGps}><span /></button></div><p className="pm-gps-message" role="status">{session.gpsMessage}</p>{session.gpsSharing && <p className="pm-footnote">{hidden ? "브라우저가 백그라운드여서 갱신이 늦어질 수 있어요." : "휴대전화 화면을 켜두면 위치 갱신에 도움이 돼요."}</p>}</section>
    <footer className="pm-complete-area"><button className="pm-button pm-button--primary" disabled={session.busy} onClick={() => setConfirmation("complete")}><PickupIcon name="check" />합류 완료</button><button className="pm-cancel-link" disabled={session.busy} onClick={() => setConfirmation("cancel")}>픽업 취소</button><p className="pm-footer-note"><PickupIcon name="shield" />종료하면 위치 공유도 멈춰요.</p></footer>
    <ConfirmPickup kind={confirmation} busy={session.busy} onDismiss={() => setConfirmation(null)} onConfirm={() => void finish()} />
  </PickupMobileShell>;
}