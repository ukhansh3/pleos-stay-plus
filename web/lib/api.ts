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
export interface PickupSession { id:string; status:string; revision:number }
export interface PickupInvitation { invitationId:string; shareUrl:string; expiresAt:string }

// Browser calls stay same-origin. The Next.js route handler owns the fixed,
// server-only Fastify target so tunnel deployments do not need CORS or expose an
// arbitrary proxy destination to the client bundle.
const base = () => "/api/pickup";
export class ApiFailure extends Error { constructor(message:string, public status:number, public code?:string){ super(message); } }

async function call<T>(path:string, token:string, init:RequestInit = {}):Promise<T> {
  const response = await fetch(`${base()}${path}`, { ...init, headers:{ ...(init.body === undefined ? {} : {"Content-Type":"application/json"}), Authorization:`Bearer ${token}`, ...(init.headers||{}) }, cache:"no-store" });
  if (response.status === 204) return undefined as T;
  const body = await response.json().catch(() => ({}));
  if (!response.ok) throw new ApiFailure(body.error?.message || body.message || "Pickup 요청에 실패했습니다", response.status, body.error?.code || body.code);
  return body as T;
}
export const pickupApi = {
  create: (access:string, idempotencyKey=crypto.randomUUID()) => call<PickupSession>("/pickup/sessions",access,{method:"POST",headers:{"Idempotency-Key":idempotencyKey},body:JSON.stringify({waitingSessionId:null,locationSharingConsent:true})}),
  invite: (access:string, sessionId:string, idempotencyKey=crypto.randomUUID()) => call<PickupInvitation>(`/pickup/sessions/${sessionId}/invitations`,access,{method:"POST",headers:{"Idempotency-Key":idempotencyKey},body:"{}"}),
  redeem: (access:string, invitation:string, consent:boolean, displayName:string) => call<{participantId:string;sessionId:string;status:ParticipantStatus;revision:number}>("/pickup/invitations/redeem", access, {method:"POST",body:JSON.stringify({token:invitation,consent,displayName})}),
  snapshot: (access:string, sessionId:string) => call<PickupSnapshot>(`/pickup/sessions/${sessionId}/snapshot`,access),
  status: (access:string, participantId:string, status:ParticipantStatus) => call(`/pickup/participants/${participantId}/status`,access,{method:"POST",body:JSON.stringify({status,clientEventId:crypto.randomUUID()})}),
  location: (access:string, participantId:string, position:GeolocationPosition) => call(`/pickup/participants/${participantId}/location`,access,{method:"PUT",body:JSON.stringify({latitude:position.coords.latitude,longitude:position.coords.longitude,accuracyMeters:position.coords.accuracy,observedAt:new Date(position.timestamp).toISOString(),source:"REAL"})}),
  complete: (access:string, sessionId:string) => call(`/pickup/sessions/${sessionId}/complete`,access,{method:"POST",headers:{"Idempotency-Key":crypto.randomUUID()},body:"{}"}),
  cancel: (access:string, sessionId:string) => call(`/pickup/sessions/${sessionId}/cancel`,access,{method:"POST",headers:{"Idempotency-Key":crypto.randomUUID()},body:JSON.stringify({reason:"PARTICIPANT_CANCELLED"})}),
};

export interface SimulatedLocationInput { latitude:number;longitude:number;accuracyMeters:number;observedAt:string;simulatedNow?:string }
async function simulatorCall(path:string, token:string, location:SimulatedLocationInput) {
  const response = await fetch(`/api/dev/pickup-simulator/${path}`, { method:"PUT", headers:{"Content-Type":"application/json",Authorization:`Bearer ${token}`}, body:JSON.stringify(location), cache:"no-store" });
  const body = await response.json().catch(() => ({}));
  if (!response.ok) throw new ApiFailure(body.error?.message || "Pickup 시뮬레이터 요청에 실패했습니다", response.status, body.error?.code);
  return body as {revision:number;proximityCandidate:boolean};
}

export const pickupSimulatorApi = {
  vehicleLocation: (access:string, sessionId:string, location:SimulatedLocationInput) => simulatorCall(`sessions/${sessionId}/vehicle-location`,access,location),
  participantLocation: (access:string, participantId:string, location:SimulatedLocationInput) => simulatorCall(`participants/${participantId}/location`,access,location),
};
