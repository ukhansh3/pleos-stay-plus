import { PickupApiError, type AuthPrincipal } from "./domain.js";

export interface PickupAuthVerifier {
  verify(accessToken: string): Promise<AuthPrincipal>;
}

export class SupabaseAuthVerifier implements PickupAuthVerifier {
  constructor(
    private readonly supabaseUrl: string,
    private readonly publishableKey: string,
    private readonly timeoutMs: number,
  ) {}

  async verify(accessToken: string): Promise<AuthPrincipal> {
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), this.timeoutMs);
    try {
      const response = await fetch(new URL("/auth/v1/user", this.supabaseUrl), {
        headers: { apikey: this.publishableKey, authorization: `Bearer ${accessToken}` },
        signal: controller.signal,
      });
      if (!response.ok) throw new PickupApiError(401, "UNAUTHENTICATED", "유효한 인증 세션이 필요합니다");
      const body = await response.json() as { id?: unknown };
      if (typeof body.id !== "string" || body.id.length === 0) {
        throw new PickupApiError(401, "UNAUTHENTICATED", "인증 사용자 정보를 확인할 수 없습니다");
      }
      return { uid: body.id };
    } catch (error) {
      if (error instanceof PickupApiError) throw error;
      throw new PickupApiError(503, "AUTH_UNAVAILABLE", "인증 서비스를 사용할 수 없습니다");
    } finally {
      clearTimeout(timeout);
    }
  }
}

export function bearerToken(header: string | undefined): string {
  const match = /^Bearer\s+(.+)$/i.exec(header?.trim() ?? "");
  if (!match?.[1]) throw new PickupApiError(401, "UNAUTHENTICATED", "Bearer 인증 토큰이 필요합니다");
  return match[1];
}
