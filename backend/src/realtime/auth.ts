import { timingSafeEqual } from "node:crypto";
import { RealtimeError, requireCondition } from "./domain.js";
export interface IdentityVerifier { verify(token: string): Promise<string> }
export class SupabaseIdentityVerifier implements IdentityVerifier {
  constructor(private readonly url: string, private readonly key: string, private readonly transport: typeof fetch = fetch) { requireCondition(new URL(url).protocol === "https:", 500, "HTTPS_REQUIRED"); }
  async verify(token: string) {
    let response: Response;
    try { response = await this.transport(`${this.url.replace(/\/$/, "")}/auth/v1/user`, { headers: { apikey: this.key, Authorization: `Bearer ${token}` }, signal: AbortSignal.timeout(10000) }); }
    catch { throw new RealtimeError(503, "AUTH_UNAVAILABLE"); }
    if (response.status >= 500 || response.status === 429) throw new RealtimeError(503, "AUTH_UNAVAILABLE");
    requireCondition(response.ok, 401, "UNAUTHENTICATED"); const user = await response.json() as { id?: unknown };
    requireCondition(typeof user.id === "string", 401, "UNAUTHENTICATED"); return user.id;
  }
}
/** Explicit local test credentials only. Never selected by the production server. */
export class LocalIdentityVerifier implements IdentityVerifier {
  constructor(private readonly identities: ReadonlyMap<string, string>) {}
  async verify(token: string) {
    for (const [known, uid] of this.identities) if (Buffer.byteLength(known) === Buffer.byteLength(token) && timingSafeEqual(Buffer.from(known), Buffer.from(token))) return uid;
    throw new RealtimeError(401, "UNAUTHENTICATED");
  }
}
