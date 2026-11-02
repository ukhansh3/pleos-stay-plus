import assert from "node:assert/strict";
import { mkdtemp, readFile, writeFile } from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import test from "node:test";
import { buildApp } from "../src/app.js";
import type { AppConfig } from "../src/config.js";
import type { NormalizedParkingRecord, ParkingSnapshot } from "../src/domain.js";
import { PublicDataParkingProvider, normalizePublicDataItem, type ParkingProvider } from "../src/provider.js";
import { estimateFee, haversineMeters, ParkingService, ParkingSnapshotCache } from "../src/service.js";

const basePlace: NormalizedParkingRecord = {
  id: "public-parking:sample-1",
  providerId: "sample-1",
  name: "테스트 노상주차장",
  type: "ON_STREET_PARKING",
  latitude: 37.5,
  longitude: 127,
  address: "서울특별시 테스트로 1",
  allowedUse: ["PARK"],
  eligibility: "CONDITIONAL",
  eligibilityReason: "현장 표지 확인 필요",
  openingHoursText: "평일+토요일+공휴일 / 평일 00:00-23:59",
  feePolicy: {
    kind: "LINEAR",
    basicMinutes: 30,
    basicFeeKrw: 1_000,
    additionalUnitMinutes: 10,
    additionalUnitFeeKrw: 500,
    dailyPassMinutes: null,
    dailyPassFeeKrw: null,
    rawText: "유료",
    uncertaintyReason: null,
  },
  parkingCategory: "공영",
  parkingStructure: "노상",
  capacity: 20,
  restrictions: null,
  operatorName: "테스트기관",
  phoneNumber: null,
  source: "PUBLIC_PARKING_STANDARD",
  sourceUpdatedAt: "2026-11-01T00:00:00.000Z",
  liveAvailability: "UNKNOWN",
  operationDays: "평일+토요일+공휴일",
  weekdayOpen: "00:00",
  weekdayClose: "23:59",
  saturdayOpen: "00:00",
  saturdayClose: "23:59",
  holidayOpen: "00:00",
  holidayClose: "23:59",
};

const snapshot = (places: NormalizedParkingRecord[] = [basePlace], fetchedAt = "2026-11-02T00:00:00.000Z"): ParkingSnapshot => ({ fetchedAt, places });

class FakeProvider implements ParkingProvider {
  calls = 0;
  constructor(private readonly value: ParkingSnapshot | Error) {}
  async fetchSnapshot(): Promise<ParkingSnapshot> {
    this.calls += 1;
    if (this.value instanceof Error) throw this.value;
    return this.value;
  }
}

const config = (cacheFile: string): AppConfig => ({
  host: "127.0.0.1",
  port: 3000,
  providerBaseUrl: "https://api.data.go.kr/openapi/tn_pubr_prkplce_info_api",
  providerApiKey: null,
  cacheTtlMs: 24 * 60 * 60 * 1000,
  staleMaxAgeMs: 7 * 24 * 60 * 60 * 1000,
  cacheFile,
  requestTimeoutMs: 1_000,
  rateLimitPerMinute: 100,
});

test("normalizes actual standard parking fields without inventing availability", () => {
  const place = normalizePublicDataItem({
    prkplceNo: "102-1-000020", prkplceNm: "한강로2가동", prkplceSe: "공영", prkplceType: "노상",
    rdnmadr: "서울특별시 용산구 한강대로 118-1", latitude: "37.53023598", longitude: "126.969191",
    parkingchrgeInfo: "혼합", basicTime: "5", basicCharge: "250", addUnitTime: "5", addUnitCharge: "250",
    spcmnt: "경차 할인", operDay: "평일+토요일", weekdayOperOpenHhmm: "09:00", weekdayOperColseHhmm: "19:00",
    referenceDate: "2026-09-22",
  });
  assert.ok(place);
  assert.equal(place.type, "ON_STREET_PARKING");
  assert.equal(place.liveAvailability, "UNKNOWN");
  assert.equal(place.feePolicy.kind, "UNKNOWN");
  assert.equal(place.sourceUpdatedAt, "2026-09-22T00:00:00.000Z");
});

test("provider reads paginated public data JSON and deduplicates by provider id", async () => {
  const calls: URL[] = [];
  const provider = new PublicDataParkingProvider({
    baseUrl: "https://api.data.go.kr/openapi/tn_pubr_prkplce_info_api",
    apiKey: "test-key",
    timeoutMs: 1_000,
    rowsPerPage: 1,
    now: () => new Date("2026-11-02T01:00:00Z"),
    fetchImpl: async (input) => {
      const url = new URL(String(input));
      calls.push(url);
      const pageNo = Number(url.searchParams.get("pageNo"));
      const item = { prkplceNo: "same", prkplceNm: "주차장", prkplceType: "노외", latitude: "37.5", longitude: "127", parkingchrgeInfo: "무료" };
      return new Response(JSON.stringify({ response: { header: { resultCode: "00" }, body: { items: { item }, totalCount: 2 } } }), { status: 200 });
    },
  });
  const result = await provider.fetchSnapshot();
  assert.equal(calls.length, 2);
  assert.equal(calls[0]?.searchParams.get("serviceKey"), "test-key");
  assert.equal(result.places.length, 1);
});

test("fee estimate is conservative for complex terms and calculates clear linear terms", () => {
  assert.deepEqual(estimateFee(basePlace, 45), {
    placeId: basePlace.id,
    plannedMinutes: 45,
    estimatedFeeKrw: 2_000,
    status: "ESTIMATED",
    reason: "공공데이터의 기본·추가 단위 요금으로 계산한 표준 요금이며 현장 할인은 반영하지 않음",
  });
  const unknown = estimateFee({ ...basePlace, feePolicy: { ...basePlace.feePolicy, kind: "UNKNOWN", uncertaintyReason: "복합 조건" } }, 45);
  assert.equal(unknown.status, "UNKNOWN");
  assert.equal(unknown.estimatedFeeKrw, null);
});

test("haversine returns realistic straight-line distance", () => {
  const distance = haversineMeters(37.5, 127, 37.51, 127);
  assert.ok(distance > 1_100 && distance < 1_120);
});

test("cache serves stale snapshot for up to seven days when refresh fails", async () => {
  const directory = await mkdtemp(path.join(os.tmpdir(), "pleos-parking-"));
  const cacheFile = path.join(directory, "snapshot.json");
  await writeFile(cacheFile, JSON.stringify(snapshot([basePlace], "2026-10-30T00:00:00Z")));
  const cache = new ParkingSnapshotCache(new FakeProvider(new Error("offline")), {
    cacheFile,
    ttlMs: 24 * 60 * 60 * 1000,
    staleMaxAgeMs: 7 * 24 * 60 * 60 * 1000,
    now: () => new Date("2026-11-02T00:00:00Z"),
  });
  const result = await cache.get();
  assert.equal(result.freshness, "STALE");
  assert.equal(result.snapshot.places.length, 1);
});

test("cache persists a successful provider snapshot", async () => {
  const directory = await mkdtemp(path.join(os.tmpdir(), "pleos-parking-"));
  const cacheFile = path.join(directory, "snapshot.json");
  const provider = new FakeProvider(snapshot());
  const cache = new ParkingSnapshotCache(provider, {
    cacheFile,
    ttlMs: 24 * 60 * 60 * 1000,
    staleMaxAgeMs: 7 * 24 * 60 * 60 * 1000,
    now: () => new Date("2026-11-02T01:00:00Z"),
  });
  assert.equal((await cache.get()).freshness, "FRESH");
  assert.equal(JSON.parse(await readFile(cacheFile, "utf8")).places.length, 1);
});

test("search API validates queries and returns normalized ranked results", async () => {
  const directory = await mkdtemp(path.join(os.tmpdir(), "pleos-parking-"));
  const cache = new ParkingSnapshotCache(new FakeProvider(snapshot()), {
    cacheFile: path.join(directory, "snapshot.json"), ttlMs: 86_400_000, staleMaxAgeMs: 604_800_000,
    now: () => new Date("2026-11-02T00:00:00Z"),
  });
  const service = new ParkingService(cache, () => new Date("2026-11-02T03:00:00Z"));
  const app = buildApp(config(path.join(directory, "unused.json")), { service });

  const invalid = await app.inject({ method: "GET", url: "/api/v1/parking/places?lat=91&lng=127" });
  assert.equal(invalid.statusCode, 400);
  assert.equal(invalid.json().code, "INVALID_QUERY");
  assert.ok(invalid.json().requestId);

  const valid = await app.inject({ method: "GET", url: "/api/v1/parking/places?lat=37.5&lng=127&plannedMinutes=45" });
  assert.equal(valid.statusCode, 200);
  assert.equal(valid.json().places[0].type, "ON_STREET_PARKING");
  assert.equal(valid.json().places[0].estimatedFee, 2_000);
  assert.equal(valid.json().places[0].liveAvailability, "UNKNOWN");
  await app.close();
});

test("detail and estimate return 404 for an unknown place", async () => {
  const directory = await mkdtemp(path.join(os.tmpdir(), "pleos-parking-"));
  const cache = new ParkingSnapshotCache(new FakeProvider(snapshot()), {
    cacheFile: path.join(directory, "snapshot.json"), ttlMs: 86_400_000, staleMaxAgeMs: 604_800_000,
  });
  const app = buildApp(config(path.join(directory, "unused.json")), { service: new ParkingService(cache) });
  const detail = await app.inject({ method: "GET", url: "/api/v1/parking/places/unknown" });
  assert.equal(detail.statusCode, 404);
  const estimate = await app.inject({ method: "GET", url: "/api/v1/parking/places/unknown/estimate?plannedMinutes=30" });
  assert.equal(estimate.statusCode, 404);
  await app.close();
});

test("empty search is a successful response and encoded provider ids remain addressable", async () => {
  const directory = await mkdtemp(path.join(os.tmpdir(), "pleos-parking-"));
  const encodedPlace = { ...basePlace, id: "public-parking:area%2F001", providerId: "area/001" };
  const cache = new ParkingSnapshotCache(new FakeProvider(snapshot([encodedPlace])), {
    cacheFile: path.join(directory, "snapshot.json"), ttlMs: 86_400_000, staleMaxAgeMs: 604_800_000,
  });
  const app = buildApp(config(path.join(directory, "unused.json")), { service: new ParkingService(cache) });
  const empty = await app.inject({ method: "GET", url: "/api/v1/parking/places?lat=35&lng=128&radiusMeters=1" });
  assert.equal(empty.statusCode, 200);
  assert.equal(empty.json().total, 0);
  const detail = await app.inject({ method: "GET", url: "/api/v1/parking/places/public-parking%3Aarea%252F001" });
  assert.equal(detail.statusCode, 200);
  assert.equal(detail.json().id, encodedPlace.id);
  await app.close();
});

test("provider failure without a usable snapshot is surfaced as 502", async () => {
  const directory = await mkdtemp(path.join(os.tmpdir(), "pleos-parking-"));
  const cache = new ParkingSnapshotCache(new FakeProvider(new Error("offline")), {
    cacheFile: path.join(directory, "snapshot.json"), ttlMs: 86_400_000, staleMaxAgeMs: 604_800_000,
  });
  const app = buildApp(config(path.join(directory, "unused.json")), { service: new ParkingService(cache) });
  const response = await app.inject({ method: "GET", url: "/api/v1/parking/places?lat=37.5&lng=127" });
  assert.equal(response.statusCode, 502);
  assert.equal(response.json().code, "PROVIDER_UNAVAILABLE");
  await app.close();
});
