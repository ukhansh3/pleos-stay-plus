import type { ReactNode } from "react";
export function PickupIcon({ name, className = "" }: { name: "car" | "pin" | "location" | "arrow" | "check" | "shield" | "share" | "copy" | "refresh"; className?: string }) {
  const paths: Record<string, ReactNode> = {
    car: <><path d="m5 10 2-5h10l2 5M4 10h16v8H4zM7 14h.01M17 14h.01M6 18v2M18 18v2" /></>,
    pin: <><path d="M19 10c0 5-7 11-7 11S5 15 5 10a7 7 0 1 1 14 0Z" /><circle cx="12" cy="10" r="2.5" /></>,
    location: <><circle cx="12" cy="12" r="7" /><circle cx="12" cy="12" r="2" /><path d="M12 2v3M12 19v3M2 12h3M19 12h3" /></>,
    arrow: <path d="m8 5 7 7-7 7M4 12h11" />,
    check: <path d="m5 12 4 4L19 6" />,
    shield: <><path d="m12 3 8 3v6c0 5-8 9-8 9s-8-4-8-9V6l8-3Z" /><path d="m8 11 3 3 5-5" /></>,
    share: <><path d="M12 15V3m-4 4 4-4 4 4M5 12v8h14v-8" /></>,
    copy: <><rect x="8" y="8" width="12" height="13" rx="2" /><path d="M15 8V3H3v13h5" /></>,
    refresh: <><path d="M19 8a8 8 0 1 0 1 7M19 3v5h-5" /></>,
  };
  return <svg className={`pm-icon ${className}`} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.9" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">{paths[name]}</svg>;
}
export function PickupBrand({ trailing }: { trailing?: ReactNode }) {
  return <header className="pm-header"><div className="pm-brand"><img src="/stay-brand-logo.png" alt="Stay+" width="58" height="58" /><div><span className="pm-brand-overline">STAY+ CONNECT</span><strong>Pickup</strong></div></div>{trailing}</header>;
}
export function PickupMobileShell({ children, className = "" }: { children: ReactNode; className?: string }) {
  return <main className={`pickup-mobile ${className}`}>{children}</main>;
}
export function PickupMessage({ children, error = false }: { children: ReactNode; error?: boolean }) {
  return <p className={`pm-message${error ? " pm-message--error" : ""}`} role={error ? "alert" : "status"}>{children}</p>;
}