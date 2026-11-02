import { mkdir, readFile, rename, writeFile } from "node:fs/promises";
import path from "node:path";
import type {
  DataFreshness,
  FeeEstimateStatus,
  NormalizedParkingRecord,
  OperatingStatus,
  ParkingEstimate,
  ParkingPlace,
  ParkingSearchQuery,
  ParkingSnapshot,
} from "./domain.js";
import { ParkingApiError } from "./errors.js";
import type { ParkingProvider } from "./provider.js";

interface CacheOptions {
  cacheFile: string;
  ttlMs: number;
  staleMaxAgeMs: number;
  now?: () => Date;
}

function isSnapshot(value: unknown): value is ParkingSnapshot {
  if (!value || typeof value !== "object") return false;
  const candidate = value as Partial<ParkingSnapshot>;
  return typeof candidate.fetchedAt === "string" && Number.isFinite(Date.parse(candidate.fetchedAt)) && Array.isArray(candidate.places);
}

export class ParkingSnapshotCache {
  private snapshot: ParkingSnapshot | null = null;
  private loading: Promise<{ snapshot: ParkingSnapshot; freshness: DataFreshness }> | null = null;
  private readonly now: () => Date;

  constructor(private readonly provider: ParkingProvider, private readonly options: CacheOptions) {
    this.now = options.now ?? (() => new Date());
  }

  async get(): Promise<{ snapshot: ParkingSnapshot; freshness: DataFreshness }> {
    if (this.loading) return this.loading;
    this.loading = this.loadOrRefresh().finally(() => { this.loading = null; });
    return this.loading;
  }

  private age(snapshot: ParkingSnapshot): number {
    return this.now().getTime() - Date.parse(snapshot.fetchedAt);
  }

  private async loadDisk(): Promise<ParkingSnapshot | null> {
    try {
      const parsed: unknown = JSON.parse(await readFile(this.options.cacheFile, "utf8"));
      return isSnapshot(parsed) ? parsed : null;
    } catch (error) {
      if ((error as NodeJS.ErrnoException).code === "ENOENT") return null;
      return null;
    }
  }

  private async save(snapshot: ParkingSnapshot): Promise<void> {
    await mkdir(path.dirname(this.options.cacheFile), { recursive: true });
    const temporary = `${this.options.cacheFile}.${process.pid}.tmp`;
    await writeFile(temporary, JSON.stringify(snapshot), { encoding: "utf8", mode: 0o600 });
    await rename(temporary, this.options.cacheFile);
  }

  private async loadOrRefresh(): Promise<{ snapshot: ParkingSnapshot; freshness: DataFreshness }> {
    this.snapshot ??= await this.loadDisk();
    if (this.snapshot && this.age(this.snapshot) <= this.options.ttlMs) return { snapshot: this.snapshot, freshness: "FRESH" };

    try {
      const refreshed = await this.provider.fetchSnapshot();
      if (refreshed.places.length === 0) throw new ParkingApiError(503, "PROVIDER_EMPTY", "공공데이터 공급자가 빈 스냅샷을 반환했습니다");
      this.snapshot = refreshed;
      await this.save(refreshed);
      return { snapshot: refreshed, freshness: "FRESH" };
    } catch (cause) {
      if (this.snapshot && this.age(this.snapshot) <= this.options.staleMaxAgeMs) return { snapshot: this.snapshot, freshness: "STALE" };
      if (cause instanceof ParkingApiError) throw cause;
      throw new ParkingApiError(502, "PROVIDER_UNAVAILABLE", "공공데이터 공급자를 사용할 수 없습니다", { cause });
    }
  }
}

export function haversineMeters(lat1: number, lng1: number, lat2: number, lng2: number): number {
  const radians = (degrees: number) => degrees * Math.PI / 180;
  const earthRadius = 6_371_000;
  const deltaLat = radians(lat2 - lat1);
  const deltaLng = radians(lng2 - lng1);
  const a = Math.sin(deltaLat / 2) ** 2
    + Math.cos(radians(lat1)) * Math.cos(radians(lat2)) * Math.sin(deltaLng / 2) ** 2;
  return earthRadius * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
}

export function estimateFee(place: NormalizedParkingRecord, plannedMinutes: number): ParkingEstimate {
  const policy = place.feePolicy;
  if (policy.kind === "FREE") {
    return { placeId: place.id, plannedMinutes, estimatedFeeKrw: 0, status: "FREE", reason: null };
  }
  if (policy.kind !== "LINEAR" || policy.basicMinutes === null || policy.basicFeeKrw === null
    || policy.additionalUnitMinutes === null || policy.additionalUnitFeeKrw === null) {
    return {
      placeId: place.id,
      plannedMinutes,
      estimatedFeeKrw: null,
      status: "UNKNOWN",
      reason: policy.uncertaintyReason ?? "요금 규칙이 충분하지 않음",
    };
  }
  const extraMinutes = Math.max(0, plannedMinutes - policy.basicMinutes);
  const extraUnits = Math.ceil(extraMinutes / policy.additionalUnitMinutes);
  return {
    placeId: place.id,
    plannedMinutes,
    estimatedFeeKrw: Math.round(policy.basicFeeKrw + extraUnits * policy.additionalUnitFeeKrw),
    status: "ESTIMATED",
    reason: "공공데이터의 기본·추가 단위 요금으로 계산한 표준 요금이며 현장 할인은 반영하지 않음",
  };
}

function koreanParts(now: Date): { weekday: string; hour: number; minute: number } {
  const parts = new Intl.DateTimeFormat("en-US", {
    timeZone: "Asia/Seoul", weekday: "short", hour: "2-digit", minute: "2-digit", hourCycle: "h23",
  }).formatToParts(now);
  const get = (type: Intl.DateTimeFormatPartTypes) => parts.find((part) => part.type === type)?.value;
  return { weekday: get("weekday") ?? "", hour: Number(get("hour")), minute: Number(get("minute")) };
}

function minutes(value: string | null): number | null {
  if (!value) return null;
  const match = /^(\d{1,2}):(\d{2})$/.exec(value);
  if (!match) return null;
  const hour = Number(match[1]);
  const minute = Number(match[2]);
  return hour <= 24 && minute <= 59 ? hour * 60 + minute : null;
}

export function operatingStatus(place: NormalizedParkingRecord, now = new Date()): OperatingStatus {
  const { weekday, hour, minute } = koreanParts(now);
  const current = hour * 60 + minute;
  const isSaturday = weekday === "Sat";
  const isSunday = weekday === "Sun";
  const dayText = place.operationDays;
  if (dayText) {
    if (isSaturday && !dayText.includes("토요일")) return "CLOSED";
    if (isSunday && !dayText.includes("공휴일") && !dayText.includes("일요일")) return "CLOSED";
    if (!isSaturday && !isSunday && !dayText.includes("평일")) return "CLOSED";
  }
  const open = minutes(isSaturday ? place.saturdayOpen : isSunday ? place.holidayOpen : place.weekdayOpen);
  const close = minutes(isSaturday ? place.saturdayClose : isSunday ? place.holidayClose : place.weekdayClose);
  if (open === null || close === null) return "UNKNOWN";
  if (open === 0 && (close === 1439 || close === 1440)) return "OPEN";
  if (open <= close) return current >= open && current <= close ? "OPEN" : "CLOSED";
  return current >= open || current <= close ? "OPEN" : "CLOSED";
}

function toPlace(record: NormalizedParkingRecord, plannedMinutes: number, distanceMeters: number | null, now: Date): ParkingPlace {
  const fee = estimateFee(record, plannedMinutes);
  const { providerId: _providerId, operationDays: _operationDays, weekdayOpen: _weekdayOpen, weekdayClose: _weekdayClose,
    saturdayOpen: _saturdayOpen, saturdayClose: _saturdayClose, holidayOpen: _holidayOpen, holidayClose: _holidayClose,
    ...publicRecord } = record;
  return {
    ...publicRecord,
    distanceMeters: distanceMeters === null ? null : Math.round(distanceMeters),
    operatingStatus: operatingStatus(record, now),
    estimatedFee: fee.estimatedFeeKrw,
    feeEstimateStatus: fee.status,
  };
}

const statusRank: Record<OperatingStatus, number> = { OPEN: 0, UNKNOWN: 1, CLOSED: 2 };
const feeRank: Record<FeeEstimateStatus, number> = { FREE: 0, ESTIMATED: 1, UNKNOWN: 2 };

export class ParkingService {
  constructor(private readonly cache: ParkingSnapshotCache, private readonly now: () => Date = () => new Date()) {}

  async search(query: ParkingSearchQuery): Promise<{ places: ParkingPlace[]; total: number; dataFreshness: DataFreshness }> {
    const { snapshot, freshness } = await this.cache.get();
    const candidates = snapshot.places
      .map((record) => ({ record, distance: haversineMeters(query.lat, query.lng, record.latitude, record.longitude) }))
      .filter(({ distance }) => distance <= query.radiusMeters)
      .map(({ record, distance }) => toPlace(record, query.plannedMinutes, distance, this.now()))
      .filter((place) => !query.freeOnly || place.feeEstimateStatus === "FREE")
      .filter((place) => query.purpose !== "LONG_STAY" || place.allowedUse.includes("PARK"));

    candidates.sort((left, right) =>
      statusRank[left.operatingStatus] - statusRank[right.operatingStatus]
      || (left.distanceMeters ?? Number.POSITIVE_INFINITY) - (right.distanceMeters ?? Number.POSITIVE_INFINITY)
      || feeRank[left.feeEstimateStatus] - feeRank[right.feeEstimateStatus]
      || (left.estimatedFee ?? Number.POSITIVE_INFINITY) - (right.estimatedFee ?? Number.POSITIVE_INFINITY)
      || left.name.localeCompare(right.name, "ko"));

    return { places: candidates.slice(0, query.limit), total: candidates.length, dataFreshness: freshness };
  }

  async detail(placeId: string, plannedMinutes = 10): Promise<ParkingPlace> {
    const { snapshot } = await this.cache.get();
    const record = snapshot.places.find((place) => place.id === placeId);
    if (!record) throw new ParkingApiError(404, "PLACE_NOT_FOUND", "주차장 정보를 찾을 수 없습니다");
    return toPlace(record, plannedMinutes, null, this.now());
  }

  async estimate(placeId: string, plannedMinutes: number): Promise<ParkingEstimate> {
    const { snapshot } = await this.cache.get();
    const record = snapshot.places.find((place) => place.id === placeId);
    if (!record) throw new ParkingApiError(404, "PLACE_NOT_FOUND", "주차장 정보를 찾을 수 없습니다");
    return estimateFee(record, plannedMinutes);
  }
}
