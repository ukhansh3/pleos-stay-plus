import { NextRequest, NextResponse } from "next/server";
import { pickupProxyPath } from "../../../../lib/pickup-proxy";

export const dynamic = "force-dynamic";

function backendBase(): URL {
  const configured = process.env.PICKUP_BACKEND_URL?.trim()
    || (process.env.NODE_ENV === "development" ? "http://127.0.0.1:3000/api/v1" : "");
  if (!configured) throw new Error("PICKUP_BACKEND_URL is required");
  const url = new URL(configured.endsWith("/") ? configured : `${configured}/`);
  const loopback = url.hostname === "127.0.0.1" || url.hostname === "localhost" || url.hostname === "[::1]";
  if (url.protocol !== "https:" && !(url.protocol === "http:" && loopback)) {
    throw new Error("PICKUP_BACKEND_URL must use HTTPS unless it targets loopback");
  }
  if (url.username || url.password || url.search || url.hash || url.pathname !== "/api/v1/") {
    throw new Error("PICKUP_BACKEND_URL must target the Pickup API base");
  }
  return url;
}

async function forward(request: NextRequest, context: { params: Promise<{ path: string[] }> }) {
  const { path } = await context.params;
  const safePath = pickupProxyPath(path);
  if (!safePath) {
    return NextResponse.json({ error: { code: "INVALID_PROXY_PATH", message: "잘못된 Pickup 경로입니다" } }, { status: 400 });
  }
  const authorization = request.headers.get("authorization");
  if (!authorization?.startsWith("Bearer ")) {
    return NextResponse.json({ error: { code: "AUTH_REQUIRED", message: "Pickup 인증이 필요합니다" } }, { status: 401 });
  }
  let target: URL;
  try {
    target = new URL(`${safePath}${request.nextUrl.search}`, backendBase());
  } catch {
    return NextResponse.json({ error: { code: "BACKEND_NOT_CONFIGURED", message: "Pickup 서버 설정을 확인해 주세요" } }, { status: 503 });
  }
  const body = request.method === "GET" || request.method === "HEAD" ? undefined : await request.arrayBuffer();
  try {
    const response = await fetch(target, {
      method: request.method,
      headers: {
        Authorization: authorization,
        ...(request.headers.get("content-type") ? { "Content-Type": request.headers.get("content-type")! } : {}),
        ...(request.headers.get("idempotency-key") ? { "Idempotency-Key": request.headers.get("idempotency-key")! } : {}),
        "X-Forwarded-Proto": request.nextUrl.protocol.replace(":", ""),
      },
      body,
      cache: "no-store",
      redirect: "error",
      signal: AbortSignal.timeout(15_000),
    });
    return new NextResponse(response.body, {
      status: response.status,
      headers: {
        "Content-Type": response.headers.get("content-type") || "application/json",
        "Cache-Control": "no-store",
      },
    });
  } catch {
    return NextResponse.json({ error: { code: "BACKEND_UNAVAILABLE", message: "Pickup 서버에 연결할 수 없습니다" } }, { status: 503 });
  }
}

export const GET = forward;
export const POST = forward;
export const PUT = forward;
