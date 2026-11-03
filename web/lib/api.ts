export type ParticipantStatus = "JOINED" | "DEPARTED" | "NEARBY" | "ARRIVED" | "CANCELLED";
export interface PickupLocation { latitude:number; longitude:number; accuracyMeters:number|null; observedAt:string; receivedAt:string; source:"REAL"|"SIMULATED" }
export interface PickupSnapshot {
  session: { id:string; status:string; participant:{id:string;displayName:string;status:ParticipantStatus;updatedAt:string}|null };
  vehicleLocation: PickupLocation|null;
  participantLocation: PickupLocation|null;
  proximityCandidate:boolean;
  revision:number;
  occurredAt:string;
}

const base = () => process.env.NEXT_PUBLIC_PICKUP_API_BASE_URL?.replace(/\/$/, "") || "http://localhost:3000/api/v1";
export class ApiFailure extends Error { constructor(message:string, public status:number, public code?:string){ super(message); } }

async function call<T>(path:string, token:string, init:RequestInit = {}):Promise<T> {
  const response = await fetch(`${base()}${path}`, { ...init, headers:{ "Content-Type":"application/json", Authorization:`Bearer ${token}`, ...(init.headers||{}) }, cache:"no-store" });
  if (response.status === 204) return undefined as T;
  const body = await response.json().catch(() => ({}));
  if (!response.ok) throw new ApiFailure(body.error?.message || body.message || "Pickup 요청에 실패했습니다", response.status, body.error?.code || body.code);
  return body as T;
}
export const pickupApi = {
  redeem: (access:string, invitation:string, consent:boolean, displayName:string) => call<{participantId:string;sessionId:string;status:ParticipantStatus;revision:number}>("/pickup/invitations/redeem", access, {method:"POST",body:JSON.stringify({token:invitation,consent,displayName})}),
  snapshot: (access:string, sessionId:string) => call<PickupSnapshot>(`/pickup/sessions/${sessionId}/snapshot`,access),
  status: (access:string, participantId:string, status:ParticipantStatus) => call(`/pickup/participants/${participantId}/status`,access,{method:"POST",body:JSON.stringify({status,clientEventId:crypto.randomUUID()})}),
  location: (access:string, participantId:string, position:GeolocationPosition) => call(`/pickup/participants/${participantId}/location`,access,{method:"PUT",body:JSON.stringify({latitude:position.coords.latitude,longitude:position.coords.longitude,accuracyMeters:position.coords.accuracy,observedAt:new Date(position.timestamp).toISOString(),source:"REAL"})}),
  complete: (access:string, sessionId:string) => call(`/pickup/sessions/${sessionId}/complete`,access,{method:"POST",headers:{"Idempotency-Key":crypto.randomUUID()},body:"{}"}),
  cancel: (access:string, sessionId:string) => call(`/pickup/sessions/${sessionId}/cancel`,access,{method:"POST",headers:{"Idempotency-Key":crypto.randomUUID()},body:JSON.stringify({reason:"PARTICIPANT_CANCELLED"})}),
};
