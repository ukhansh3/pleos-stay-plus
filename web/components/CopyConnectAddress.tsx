"use client";

import { useState } from "react";

export function CopyConnectAddress() {
  const [message, setMessage] = useState("");
  async function copy() {
    const address = new URL(location.pathname, location.origin).href;
    try {
      await navigator.clipboard.writeText(address);
      setMessage("연결 안내 주소를 복사했습니다.");
    } catch {
      setMessage("브라우저 주소창에서 주소를 복사해 주세요.");
    }
  }
  return <div className="stack"><button onClick={copy}>PC에 보낼 안내 주소 복사</button><p role="status">{message}</p></div>;
}
