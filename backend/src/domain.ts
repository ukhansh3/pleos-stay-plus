export const parkingTypes = [
  "PARKING_LOT",
  "ON_STREET_PARKING",
  "PICKUP_ZONE",
  "TIME_LIMITED_ZONE",
  "PUBLIC_REST_AREA",
] as const;

export type ParkingType = (typeof parkingTypes)[number];
export type ParkingPurpose = "PICKUP" | "SHORT_WAIT" | "LONG_STAY";
export type ParkingEligibility = "VERIFIED" | "CONDITIONAL" | "UNKNOWN";
export type FeeEstimateStatus = "ESTIMATED" | "FREE" | "UNKNOWN";
export type DataFreshness = "FRESH" | "STALE" | "UNKNOWN";
export type OperatingStatus = "OPEN" | "CLOSED" | "UNKNOWN";
export type ParkingSource = "PUBLIC_PARKING_STANDARD" | "PUBLIC_REST_AREA_STANDARD";

export interface FeePolicy {
  kind: "FREE" | "LINEAR" | "UNKNOWN";
  basicMinutes: number | null;
  basicFeeKrw: number | null;
  additionalUnitMinutes: number | null;
  additionalUnitFeeKrw: number | null;
  dailyPassMinutes: number | null;
  dailyPassFeeKrw: number | null;
  rawText: string | null;
  uncertaintyReason: string | null;
}

export interface ParkingPlace {
  id: string;
  name: string;
  type: ParkingType;
  latitude: number;
  longitude: number;
  address: string | null;
  distanceMeters: number | null;
  allowedUse: Array<"PARK" | "STOP" | "PICKUP">;
  eligibility: ParkingEligibility;
  eligibilityReason: string | null;
  openingHoursText: string | null;
  operatingStatus: OperatingStatus;
  estimatedFee: number | null;
  feeEstimateStatus: FeeEstimateStatus;
  feePolicy: FeePolicy;
  parkingCategory: string | null;
  parkingStructure: string | null;
  capacity: number | null;
  restrictions: string | null;
  operatorName: string | null;
  phoneNumber: string | null;
  source: ParkingSource;
  sourceUpdatedAt: string | null;
  liveAvailability: "UNKNOWN";
}

export interface NormalizedParkingRecord extends Omit<ParkingPlace, "distanceMeters" | "operatingStatus" | "estimatedFee" | "feeEstimateStatus"> {
  providerId: string;
  operationDays: string | null;
  weekdayOpen: string | null;
  weekdayClose: string | null;
  saturdayOpen: string | null;
  saturdayClose: string | null;
  holidayOpen: string | null;
  holidayClose: string | null;
}

export interface ParkingSearchQuery {
  lat: number;
  lng: number;
  radiusMeters: number;
  plannedMinutes: number;
  purpose: ParkingPurpose;
  freeOnly: boolean;
  limit: number;
}

export interface ParkingEstimate {
  placeId: string;
  plannedMinutes: number;
  estimatedFeeKrw: number | null;
  status: FeeEstimateStatus;
  reason: string | null;
}

export interface ParkingSnapshot {
  fetchedAt: string;
  places: NormalizedParkingRecord[];
}
