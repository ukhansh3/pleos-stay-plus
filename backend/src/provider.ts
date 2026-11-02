import { createHash } from "node:crypto";
import type { FeePolicy, NormalizedParkingRecord, ParkingSnapshot, ParkingType } from "./domain.js";
import { ParkingApiError } from "./errors.js";

export interface ParkingProvider {
  readonly supportedTypes?: readonly ParkingType[];
  fetchSnapshot(): Promise<ParkingSnapshot>;
}

export class CompositeParkingProvider implements ParkingProvider {
  readonly supportedTypes: readonly ParkingType[];

  constructor(private readonly providers: readonly ParkingProvider[], private readonly now: () => Date = () => new Date()) {
    this.supportedTypes = [...new Set(providers.flatMap((provider) => provider.supportedTypes ?? []))];
  }

  async fetchSnapshot(): Promise<ParkingSnapshot> {
    const snapshots = await Promise.all(this.providers.map((provider) => provider.fetchSnapshot()));
    const deduplicated = new Map<string, NormalizedParkingRecord>();
    for (const place of snapshots.flatMap((snapshot) => snapshot.places)) deduplicated.set(place.id, place);
    return { fetchedAt: this.now().toISOString(), places: [...deduplicated.values()] };
  }
}

abstract class DisabledParkingProvider implements ParkingProvider {
  abstract readonly supportedTypes: readonly ParkingType[];
  async fetchSnapshot(): Promise<ParkingSnapshot> {
    return { fetchedAt: new Date().toISOString(), places: [] };
  }
}

export class PickupZoneProvider extends DisabledParkingProvider {
  readonly supportedTypes = ["PICKUP_ZONE"] as const;
}

export class TimedZoneProvider extends DisabledParkingProvider {
  readonly supportedTypes = ["TIME_LIMITED_ZONE"] as const;
}

export class RestAreaProvider extends DisabledParkingProvider {
  readonly supportedTypes = ["PUBLIC_REST_AREA"] as const;
}

interface PublicDataItem {
  prkplceNo?: unknown;
  prkplceNm?: unknown;
  prkplceSe?: unknown;
  prkplceType?: unknown;
  rdnmadr?: unknown;
  lnmadr?: unknown;
  prkcmprt?: unknown;
  operDay?: unknown;
  weekdayOperOpenHhmm?: unknown;
  weekdayOperColseHhmm?: unknown;
  satOperOperOpenHhmm?: unknown;
  satOperCloseHhmm?: unknown;
  holidayOperOpenHhmm?: unknown;
  holidayCloseOpenHhmm?: unknown;
  parkingchrgeInfo?: unknown;
  basicTime?: unknown;
  basicCharge?: unknown;
  addUnitTime?: unknown;
  addUnitCharge?: unknown;
  dayCmmtktAdjTime?: unknown;
  dayCmmtkt?: unknown;
  spcmnt?: unknown;
  institutionNm?: unknown;
  phoneNumber?: unknown;
  latitude?: unknown;
  longitude?: unknown;
  referenceDate?: unknown;
  instt_code?: unknown;
}

interface ProviderOptions {
  baseUrl: string;
  apiKey: string | null;
  timeoutMs: number;
  rowsPerPage?: number;
  fetchImpl?: typeof fetch;
  now?: () => Date;
}

const text = (value: unknown): string | null => {
  if (typeof value !== "string" && typeof value !== "number") return null;
  const normalized = String(value).trim();
  return normalized === "" ? null : normalized;
};

const number = (value: unknown): number | null => {
  const normalized = text(value)?.replaceAll(",", "");
  if (!normalized) return null;
  const parsed = Number(normalized);
  return Number.isFinite(parsed) ? parsed : null;
};

function dateTime(value: unknown): string | null {
  const normalized = text(value);
  if (!normalized || !/^\d{4}-\d{2}-\d{2}$/.test(normalized)) return null;
  return `${normalized}T00:00:00.000Z`;
}

function makeId(item: PublicDataItem): { id: string; providerId: string } {
  const explicit = text(item.prkplceNo);
  const providerId = explicit ?? createHash("sha256")
    .update([text(item.instt_code), text(item.prkplceNm), text(item.rdnmadr), text(item.lnmadr)].join("|"))
    .digest("hex")
    .slice(0, 24);
  return { providerId, id: `public-parking:${encodeURIComponent(providerId)}` };
}

function feePolicy(item: PublicDataItem): FeePolicy {
  const chargeInfo = text(item.parkingchrgeInfo);
  const rawText = [chargeInfo, text(item.spcmnt)].filter(Boolean).join(" / ") || null;
  if (chargeInfo === "무료") {
    return {
      kind: "FREE", basicMinutes: null, basicFeeKrw: 0, additionalUnitMinutes: null,
      additionalUnitFeeKrw: null, dailyPassMinutes: null, dailyPassFeeKrw: null,
      rawText, uncertaintyReason: null,
    };
  }

  const basicMinutes = number(item.basicTime);
  const basicFeeKrw = number(item.basicCharge);
  const additionalUnitMinutes = number(item.addUnitTime);
  const additionalUnitFeeKrw = number(item.addUnitCharge);
  const dailyPassMinutes = number(item.dayCmmtktAdjTime);
  const dailyPassFeeKrw = number(item.dayCmmtkt);
  const hasAmbiguousTerms = chargeInfo !== "유료" || text(item.spcmnt) !== null || dailyPassMinutes !== null || dailyPassFeeKrw !== null;
  const hasBase = basicMinutes !== null && basicMinutes > 0 && basicFeeKrw !== null && basicFeeKrw >= 0;
  const hasIncrement = additionalUnitMinutes !== null && additionalUnitMinutes > 0 && additionalUnitFeeKrw !== null && additionalUnitFeeKrw >= 0;

  return {
    kind: !hasAmbiguousTerms && hasBase && hasIncrement ? "LINEAR" : "UNKNOWN",
    basicMinutes,
    basicFeeKrw,
    additionalUnitMinutes,
    additionalUnitFeeKrw,
    dailyPassMinutes,
    dailyPassFeeKrw,
    rawText,
    uncertaintyReason: hasAmbiguousTerms
      ? "할인·혼합·일일요금 등 복합 조건을 자동 계산할 수 없음"
      : !hasBase || !hasIncrement
        ? "기본 또는 추가 요금 규칙이 충분하지 않음"
        : null,
  };
}

export function normalizePublicDataItem(item: PublicDataItem): NormalizedParkingRecord | null {
  const latitude = number(item.latitude);
  const longitude = number(item.longitude);
  const name = text(item.prkplceNm);
  if (name === null || latitude === null || longitude === null || latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) return null;

  const ids = makeId(item);
  const structure = text(item.prkplceType);
  const weekdayOpen = text(item.weekdayOperOpenHhmm);
  const weekdayClose = text(item.weekdayOperColseHhmm);
  const saturdayOpen = text(item.satOperOperOpenHhmm);
  const saturdayClose = text(item.satOperCloseHhmm);
  const holidayOpen = text(item.holidayOperOpenHhmm);
  const holidayClose = text(item.holidayCloseOpenHhmm);
  const operationDays = text(item.operDay);
  const openingParts = [
    operationDays,
    weekdayOpen && weekdayClose ? `평일 ${weekdayOpen}-${weekdayClose}` : null,
    saturdayOpen && saturdayClose ? `토요일 ${saturdayOpen}-${saturdayClose}` : null,
    holidayOpen && holidayClose ? `공휴일 ${holidayOpen}-${holidayClose}` : null,
  ].filter(Boolean);

  return {
    ...ids,
    name,
    type: structure === "노상" ? "ON_STREET_PARKING" : "PARKING_LOT",
    latitude,
    longitude,
    address: text(item.rdnmadr) ?? text(item.lnmadr),
    allowedUse: ["PARK"],
    eligibility: "CONDITIONAL",
    eligibilityReason: "공공데이터상 주차장이며 현재 운영시간·차량 조건·현장 표지를 확인해야 함",
    openingHoursText: openingParts.join(" / ") || null,
    feePolicy: feePolicy(item),
    parkingCategory: text(item.prkplceSe),
    parkingStructure: structure,
    capacity: number(item.prkcmprt),
    restrictions: text(item.spcmnt),
    operatorName: text(item.institutionNm),
    phoneNumber: text(item.phoneNumber),
    source: "PUBLIC_PARKING_STANDARD",
    sourceUpdatedAt: dateTime(item.referenceDate),
    liveAvailability: "UNKNOWN",
    operationDays,
    weekdayOpen,
    weekdayClose,
    saturdayOpen,
    saturdayClose,
    holidayOpen,
    holidayClose,
  };
}

function responseBody(payload: unknown): { items: PublicDataItem[]; totalCount: number } {
  if (!payload || typeof payload !== "object") throw new ParkingApiError(502, "PROVIDER_INVALID_RESPONSE", "공공데이터 응답 형식이 올바르지 않습니다");
  const root = payload as Record<string, unknown>;
  const response = (root.response ?? root) as Record<string, unknown>;
  const header = (response.header ?? {}) as Record<string, unknown>;
  const resultCode = text(header.resultCode);
  if (resultCode && resultCode !== "00" && resultCode !== "0") {
    throw new ParkingApiError(502, "PROVIDER_REJECTED", `공공데이터 공급자 오류: ${text(header.resultMsg) ?? resultCode}`);
  }
  const body = (response.body ?? response) as Record<string, unknown>;
  const rawItems = body.items;
  const itemsContainer = rawItems && typeof rawItems === "object" && !Array.isArray(rawItems)
    ? (rawItems as Record<string, unknown>).item ?? rawItems
    : rawItems;
  const items = Array.isArray(itemsContainer) ? itemsContainer : itemsContainer ? [itemsContainer] : [];
  return { items: items.filter((entry): entry is PublicDataItem => Boolean(entry) && typeof entry === "object"), totalCount: number(body.totalCount) ?? items.length };
}

export class PublicDataParkingProvider implements ParkingProvider {
  readonly supportedTypes = ["PARKING_LOT", "ON_STREET_PARKING"] as const;
  private readonly fetchImpl: typeof fetch;
  private readonly rowsPerPage: number;
  private readonly now: () => Date;

  constructor(private readonly options: ProviderOptions) {
    this.fetchImpl = options.fetchImpl ?? fetch;
    this.rowsPerPage = options.rowsPerPage ?? 1000;
    this.now = options.now ?? (() => new Date());
  }

  async fetchSnapshot(): Promise<ParkingSnapshot> {
    if (!this.options.apiKey) throw new ParkingApiError(503, "PROVIDER_KEY_MISSING", "PARKING_PROVIDER_API_KEY가 설정되지 않았습니다");

    const normalized = new Map<string, NormalizedParkingRecord>();
    let pageNo = 1;
    let totalCount = Number.POSITIVE_INFINITY;
    let receivedCount = 0;
    while (receivedCount < totalCount) {
      const url = new URL(this.options.baseUrl);
      url.searchParams.set("serviceKey", this.options.apiKey);
      url.searchParams.set("pageNo", String(pageNo));
      url.searchParams.set("numOfRows", String(this.rowsPerPage));
      url.searchParams.set("type", "json");

      const response = await this.fetchImpl(url, { signal: AbortSignal.timeout(this.options.timeoutMs), headers: { accept: "application/json" } });
      if (!response.ok) throw new ParkingApiError(502, "PROVIDER_HTTP_ERROR", `공공데이터 공급자 HTTP 오류 (${response.status})`);
      const raw = await response.text();
      let payload: unknown;
      try { payload = JSON.parse(raw); } catch (cause) {
        throw new ParkingApiError(502, "PROVIDER_INVALID_RESPONSE", "공공데이터 공급자가 JSON이 아닌 응답을 반환했습니다", { cause });
      }
      const page = responseBody(payload);
      totalCount = page.totalCount;
      receivedCount += page.items.length;
      for (const item of page.items) {
        const place = normalizePublicDataItem(item);
        if (place) normalized.set(place.providerId, place);
      }
      if (page.items.length === 0) break;
      pageNo += 1;
    }

    return { fetchedAt: this.now().toISOString(), places: [...normalized.values()] };
  }
}
