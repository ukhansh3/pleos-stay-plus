export type PickupSessionStatus = "ACTIVE" | "COMPLETED" | "CANCELLED" | "ENDED" | "EXPIRED";
export type PickupParticipantStatus = "JOINED" | "DEPARTED" | "NEARBY" | "ARRIVED" | "CANCELLED";
export type PickupLocationSource = "REAL" | "SIMULATED";

export interface PickupParticipant {
  id: string;
  displayName: string;
  status: PickupParticipantStatus;
  updatedAt: string;
}

export interface PickupSession {
  id: string;
  waitingSessionId: string | null;
  status: PickupSessionStatus;
  locationSharingConsent: boolean;
  participant: PickupParticipant | null;
  invitationExpiresAt: string | null;
  revision: number;
  createdAt: string;
  endedAt: string | null;
}

export interface PickupLocationUpdate {
  latitude: number;
  longitude: number;
  accuracyMeters: number | null;
  confidence: number | null;
  observedAt: string;
  source: PickupLocationSource;
}

export interface PickupLocation extends PickupLocationUpdate {
  receivedAt: string;
}

export interface PickupSnapshot {
  session: PickupSession;
  vehicleLocation: PickupLocation | null;
  participantLocation: PickupLocation | null;
  proximityCandidate: boolean;
  revision: number;
  occurredAt: string;
}

export interface AuthPrincipal { uid: string }

export class PickupApiError extends Error {
  constructor(
    public readonly statusCode: number,
    public readonly code: string,
    message: string,
  ) {
    super(message);
    this.name = "PickupApiError";
  }
}

export const participantTransitions: Readonly<Record<PickupParticipantStatus, readonly PickupParticipantStatus[]>> = {
  JOINED: ["DEPARTED", "NEARBY", "CANCELLED"],
  DEPARTED: ["NEARBY", "ARRIVED", "CANCELLED"],
  NEARBY: ["ARRIVED", "CANCELLED"],
  ARRIVED: [],
  CANCELLED: [],
};

export function canTransitionParticipant(from: PickupParticipantStatus, to: PickupParticipantStatus): boolean {
  return from === to || participantTransitions[from].includes(to);
}
