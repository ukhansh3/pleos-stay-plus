import { createHmac, randomInt, randomUUID } from "node:crypto";
import { type Device, type Connection, requireCondition } from "./domain.js";
interface Pairing {
  id: string; issuer: string; claimant: string | null; digest: string;
  expiresAt: number; approvals: Set<string>; state: "OPEN" | "CLAIMED" | "CONNECTED" | "CANCELLED";
  connectionId: string | null;
}
/** Local fake only. Synchronous mutations are atomic within this process; never a Cloud repository. */
export class LocalPairingService {
  private readonly devices = new Map<string, Device>();
  private readonly pairings = new Map<string, Pairing>();
  private readonly connections = new Map<string, Connection>();
  private readonly attempts = new Map<string, { count: number; until: number }>();
  constructor(private readonly secret: string, private readonly ttlMs = 600_000,
    private readonly now: () => number = Date.now) {
    requireCondition(secret.length >= 32 && ttlMs > 0, 500, "INVALID_CONFIGURATION");
  }
  snapshot() { return {devices:[...this.devices],pairings:[...this.pairings].map(([id,p])=>[id,{...p,approvals:[...p.approvals]}] as const),connections:[...this.connections],attempts:[...this.attempts]}; }
  restore(state: ReturnType<LocalPairingService["snapshot"]>) { this.devices.clear();this.pairings.clear();this.connections.clear();this.attempts.clear();for(const [k,v] of state.devices)this.devices.set(k,v);for(const [k,v] of state.pairings)this.pairings.set(k,{...v,approvals:new Set(v.approvals)});for(const [k,v] of state.connections)this.connections.set(k,v);for(const [k,v] of state.attempts)this.attempts.set(k,v); }
  register(uid: string, kind: Device["kind"], name: string): Device {
    requireCondition(uid && (kind === "WINDOWS" || kind === "VEHICLE") && name.trim().length > 0 && name.length <= 80, 400, "INVALID_DEVICE");
    const existing = [...this.devices.values()].find(d => d.uid === uid);
    if (existing) { requireCondition(existing.kind === kind, 409, "DEVICE_KIND_CONFLICT"); return { ...existing }; }
    const device: Device = { id: randomUUID(), uid, kind, name: name.trim() };
    this.devices.set(device.id, device); return { ...device };
  }
  device(uid: string): Device {
    const device = [...this.devices.values()].find(d => d.uid === uid);
    requireCondition(device, 404, "DEVICE_NOT_REGISTERED"); return { ...device };
  }
  private digest(code: string): string { return createHmac("sha256", this.secret).update(code).digest("hex"); }
  private limit(subject: string): void {
    const now = this.now();
    for (const [key, value] of this.attempts) if (value.until <= now) this.attempts.delete(key);
    const entry = this.attempts.get(subject) ?? { count: 0, until: now + 600_000 };
    requireCondition(entry.count < 5, 429, "RATE_LIMITED"); entry.count++; this.attempts.set(subject, entry);
  }
  current(uid: string): Connection | null {
    const id = this.device(uid).id;
    const found = [...this.connections.values()].find(c => !c.revokedAt && (c.pcDeviceId === id || c.vehicleDeviceId === id));
    return found ? { ...found } : null;
  }
  issue(uid: string): { id: string; code: string; expiresAt: number } {
    const device = this.device(uid); this.limit(`issue:${device.id}`);
    requireCondition(!this.current(uid), 409, "ALREADY_CONNECTED");
    for (const p of this.pairings.values()) if (p.issuer === device.id && (p.state === "OPEN" || p.state === "CLAIMED")) p.state = "CANCELLED";
    let code: string;
    do { code = randomInt(0, 100_000_000).toString().padStart(8, "0"); }
    while ([...this.pairings.values()].some(p => p.digest === this.digest(code)));
    const p: Pairing = { id: randomUUID(), issuer: device.id, claimant: null, digest: this.digest(code), expiresAt: this.now() + this.ttlMs, approvals: new Set(), state: "OPEN", connectionId: null };
    this.pairings.set(p.id, p); return { id: p.id, code, expiresAt: p.expiresAt };
  }
  claim(uid: string, code: string, ip: string): ReturnType<LocalPairingService["get"]> {
    const device = this.device(uid); this.limit(`claim:${device.id}`); this.limit(`ip:${ip}`);
    requireCondition(/^\d{8}$/.test(code), 400, "INVALID_PAIRING_CODE");
    const p = [...this.pairings.values()].find(p => p.digest === this.digest(code) && p.state === "OPEN" && p.expiresAt > this.now());
    requireCondition(p, 400, "INVALID_PAIRING_CODE");
    requireCondition(this.devices.get(p.issuer)?.kind !== device.kind && !this.current(uid), 409, "INCOMPATIBLE_DEVICE");
    p.claimant = device.id; p.state = "CLAIMED"; return this.get(uid, p.id);
  }
  private owned(uid: string, id: string): Pairing {
    const device = this.device(uid); const p = this.pairings.get(id);
    requireCondition(p && (p.issuer === device.id || p.claimant === device.id), 404, "PAIRING_NOT_FOUND"); return p;
  }
  get(uid: string, id: string) {
    const p = this.owned(uid, id);
    const label = (id: string | null) => { const d = id ? this.devices.get(id) : null; return d ? { id: d.id, name: d.name, kind: d.kind } : null; };
    return { id: p.id, state: p.expiresAt <= this.now() && p.state !== "CONNECTED" && p.state !== "CANCELLED" ? "EXPIRED" : p.state, issuer: label(p.issuer), claimant: label(p.claimant), expiresAt: p.expiresAt, approvedDeviceIds: [...p.approvals], connectionId: p.connectionId };
  }
  approve(uid: string, id: string) {
    const p = this.owned(uid, id);
    if (p.state === "CONNECTED") return this.get(uid, id);
    requireCondition(p.state === "CLAIMED" && p.expiresAt > this.now(), 409, "PAIRING_NOT_ACTIVE");
    const issuer = this.devices.get(p.issuer)!; const claimant = this.devices.get(p.claimant!)!;
    requireCondition(!this.current(issuer.uid) && !this.current(claimant.uid), 409, "ALREADY_CONNECTED");
    p.approvals.add(this.device(uid).id);
    if (p.approvals.size === 2) {
      const c: Connection = { id: randomUUID(), pcDeviceId: issuer.kind === "WINDOWS" ? issuer.id : claimant.id, vehicleDeviceId: issuer.kind === "VEHICLE" ? issuer.id : claimant.id, revokedAt: null };
      this.connections.set(c.id, c); p.connectionId = c.id; p.state = "CONNECTED";
    }
    return this.get(uid, id);
  }
  cancel(uid: string, id: string): void {
    const p = this.owned(uid, id); requireCondition(p.state !== "CONNECTED", 409, "ALREADY_CONNECTED"); p.state = "CANCELLED";
  }
  authorize(uid: string, connectionId: string): Connection {
    const c = this.current(uid); requireCondition(c?.id === connectionId, 404, "CONNECTION_UNAVAILABLE"); return c;
  }
  revoke(uid: string, id: string): void {
    const device = this.device(uid); const c = this.connections.get(id);
    requireCondition(c && (c.pcDeviceId === device.id || c.vehicleDeviceId === device.id), 404, "CONNECTION_UNAVAILABLE");
    c.revokedAt ??= this.now();
  }
}

