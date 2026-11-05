import { NextRequest, NextResponse } from "next/server";

export const dynamic = "force-dynamic";

const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

function isLoopback(hostname: string): boolean {
  const normalized = hostname.toLowerCase().replace(/^\[|\]$/g, "");
  return normalized === "localhost" || normalized === "127.0.0.1" || normalized === "::1";
}

function configuration(request: NextRequest): { target: URL; simulatorToken: string } | null {
  if (process.env.NODE_ENV === "production" || process.env.ENABLE_PICKUP_SIMULATOR !== "true" || !isLoopback(request.nextUrl.hostname)) return null;
  const simulatorToken = process.env.PICKUP_SIMULATOR_TOKEN?.trim();
  if (!simulatorToken || simulatorToken.length < 32) return null;
  const configured = process.env.PICKUP_BACKEND_URL?.trim() || "http://127.0.0.1:3000/api/v1";
  const target = new URL(configured.endsWith("/") ? configured : `${configured}/`);
  if (!isLoopback(target.hostname) || target.protocol !== "http:") return null;
  return { target, simulatorToken };
}

export async function PUT(request: NextRequest, context: { params: Promise<{ path: string[] }> }) {
  const config = configuration(request);
  if (!config) return NextResponse.json({ error: { code: "NOT_FOUND", message: "Not Found" } }, { status: 404 });
  const authorization = request.headers.get("authorization");
  if (!authorization?.startsWith("Bearer ")) return NextResponse.json({ error: { code: "AUTH_REQUIRED", message: "시뮬레이터 인증이 필요합니다" } }, { status: 401 });
  const { path } = await context.params;
  const allowed = path.length === 3 && uuid.test(path[1] ?? "") && (
    (path[0] === "sessions" && path[2] === "vehicle-location") ||
    (path[0] === "participants" && path[2] === "location")
  );
  if (!allowed) return NextResponse.json({ error: { code: "INVALID_SIMULATOR_PATH", message: "허용되지 않은 시뮬레이터 경로입니다" } }, { status: 404 });
  const target = new URL(`qa/pickup-simulator/${path.join("/")}`, config.target);
  try {
    const response = await fetch(target, {
      method: "PUT",
      headers: {
        Authorization: authorization,
        "Content-Type": "application/json",
        "x-pickup-simulator-token": config.simulatorToken,
      },
      body: await request.text(),
      cache: "no-store",
      signal: AbortSignal.timeout(15_000),
    });
    return new NextResponse(response.body, { status: response.status, headers: { "Content-Type": response.headers.get("content-type") || "application/json", "Cache-Control": "no-store" } });
  } catch {
    return NextResponse.json({ error: { code: "BACKEND_UNAVAILABLE", message: "Pickup 시뮬레이터 서버에 연결할 수 없습니다" } }, { status: 503 });
  }
}
