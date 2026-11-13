"use client";
import { useEffect, useState } from "react";
import { PickupBrand, PickupIcon, PickupMessage, PickupMobileShell } from "./PickupMobileChrome";

export function SharePanel({ token }: { token: string }) {
  const [message, setMessage] = useState("");
  const [failed, setFailed] = useState(false);
  const [link, setLink] = useState("");
  useEffect(() => setLink(new URL(`/pickup/${encodeURIComponent(token)}`, location.origin).href), [token]);
  async function copy() {
    setMessage(""); setFailed(false);
    try { await navigator.clipboard.writeText(link); setMessage("초대 링크를 복사했어요."); }
    catch { setFailed(true); setMessage("복사하지 못했어요. 아래 링크를 길게 눌러 복사해 주세요."); }
  }
  async function share() {
    setMessage(""); setFailed(false);
    try {
      if (navigator.share) await navigator.share({ title: "Stay+ Pickup", text: "차량 위치를 확인하고 만나요.", url: link });
      else await copy();
    } catch (cause) { if ((cause as DOMException).name !== "AbortError") { setFailed(true); setMessage("공유하지 못했어요. 링크를 복사해 전달해 주세요."); } }
  }
  return <PickupMobileShell className="pm-welcome"><PickupBrand trailing={<span className="pm-private-label"><PickupIcon name="shield" />운전자용</span>} />
    <section className="pm-welcome-hero"><div className="pm-hero-car" aria-hidden="true"><div className="pm-hero-ring" /><PickupIcon name="share" /></div><p className="pm-eyebrow">SHARE YOUR PICKUP</p><h1>초대 링크를<br /><span>전달해 주세요.</span></h1><p className="pm-lead">픽업 대상이 참가하면<br />차량 위치와 상태를 확인할 수 있어요.</p></section>
    <section className="pm-card pm-share-card"><h2>한 사람에게 초대 보내기</h2><ol className="pm-share-steps"><li>카카오톡·문자로 링크 전달</li><li>상대방이 동의하고 참가</li><li>차량에서 참가 상태 확인</li></ol><div className="pm-share-buttons"><button className="pm-button pm-button--primary" disabled={!link} onClick={() => void share()}><PickupIcon name="share" />공유하기</button><button className="pm-button pm-button--secondary" disabled={!link} onClick={() => void copy()}><PickupIcon name="copy" />링크 복사</button></div>
      {message && <PickupMessage error={failed}>{message}</PickupMessage>}<label className="pm-field" htmlFor="pickup-share-link"><span>초대 링크</span><input id="pickup-share-link" className="pm-share-input" readOnly value={link} onFocus={event => event.target.select()} /></label><p className="pm-footnote">초대는 발급 후 60분 동안 참가할 수 있어요. 새 QR을 만들면 이전 링크는 사용할 수 없어요.</p>
    </section><p className="pm-footer-note"><PickupIcon name="shield" />이 공유 화면에서는 차량 위치를 표시하지 않아요.</p>
  </PickupMobileShell>;
}