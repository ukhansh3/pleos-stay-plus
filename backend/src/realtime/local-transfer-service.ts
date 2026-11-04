import { validateMetadata, validateDocument } from "./document-validator.js";
import { randomUUID } from "node:crypto";
import { Readable } from "node:stream";
import { LocalPairingService } from "./local-pairing-service.js";
import { requireCondition, RealtimeError } from "./domain.js";
import { type StorageGateway } from "./storage-gateway.js";
export interface UploadRequest { connectionId: string; fileId?: string; baseVersionId?: string; fileName: string; mimeType: string; size: number; sha256: string; idempotencyKey: string }
export interface FileRecord { id: string; connectionId: string; name: string; deleted: boolean; createdAt: string }
export interface Version { id: string; fileId: string; connectionId: string; baseVersionId: string | null; versionNo: number | null; kind: "ORIGINAL" | "REVISION"; uploadedByDeviceId: string; fileName: string; mimeType: string; size: number; sha256: string; state: "PENDING" | "UPLOADING" | "VERIFYING" | "READY" | "FAILED" | "CANCELLED" | "EXPIRED"; createdAt: string; expiresAt: number; committedAt: string | null }
/** In-memory metadata adapter for explicit local development only. */
export class LocalTransferService {
  private readonly files = new Map<string, FileRecord>();
  private readonly versions = new Map<string, Version>();
  private readonly requests = new Map<string, { digest: string; id: string }>();
  constructor(private readonly pairing: LocalPairingService, private readonly storage: StorageGateway,
    readonly maxBytes = 20 * 1024 * 1024, private readonly now: () => number = Date.now) {}
  snapshot() { return {files:[...this.files],versions:[...this.versions],requests:[...this.requests]}; }
  restore(state: ReturnType<LocalTransferService["snapshot"]>) {this.files.clear();this.versions.clear();this.requests.clear();for(const [k,v] of state.files)this.files.set(k,v);for(const [k,v] of state.versions)this.versions.set(k,{...v,state:v.state==="UPLOADING"?"FAILED":v.state});for(const [k,v] of state.requests)this.requests.set(k,v);}
  prepare(uid: string, input: UploadRequest): Version {
    this.pairing.authorize(uid, input.connectionId); const device = this.pairing.device(uid);
    requireCondition(typeof input.fileName === "string" && input.fileName.length > 0 && input.fileName.length <= 200 && !/[\\/\x00-\x1f]/.test(input.fileName), 400, "INVALID_FILENAME");
    validateMetadata(input.fileName,input.mimeType);
    requireCondition(Number.isSafeInteger(input.size) && input.size > 0 && input.size <= this.maxBytes, 413, "FILE_TOO_LARGE");
    requireCondition(/^[a-f0-9]{64}$/.test(input.sha256) && /^[a-zA-Z0-9-]{16,100}$/.test(input.idempotencyKey), 400, "INVALID_INPUT");
    requireCondition(Boolean(input.fileId) === Boolean(input.baseVersionId), 400, "BASE_VERSION_REQUIRED");
    const digest = JSON.stringify([input.connectionId,input.fileId ?? null,input.baseVersionId ?? null,input.fileName,input.mimeType,input.size,input.sha256]);
    const requestKey = `${device.id}:${input.idempotencyKey}`; const replay = this.requests.get(requestKey);
    if (replay) { requireCondition(replay.digest === digest, 409, "IDEMPOTENCY_CONFLICT"); return this.status(uid, replay.id); }
    let file: FileRecord;
    if (input.fileId) {
      file = this.file(uid, input.fileId);
      requireCondition(file.connectionId === input.connectionId, 404, "FILE_UNAVAILABLE");
      const base = this.versions.get(input.baseVersionId!);
      requireCondition(base?.fileId === file.id && base.state === "READY", 409, "BASE_VERSION_INVALID");
    } else {
      file = { id: randomUUID(), connectionId: input.connectionId, name: input.fileName, deleted: false, createdAt: new Date(this.now()).toISOString() };
      this.files.set(file.id, file);
    }
    const version: Version = { id: randomUUID(), fileId: file.id, connectionId: file.connectionId, baseVersionId: input.baseVersionId ?? null, versionNo: null, kind: input.fileId ? "REVISION" : "ORIGINAL", uploadedByDeviceId: device.id, fileName: input.fileName, mimeType: input.mimeType, size: input.size, sha256: input.sha256, state: "PENDING", createdAt: new Date(this.now()).toISOString(), expiresAt: this.now() + 3_600_000, committedAt: null };
    this.versions.set(version.id, version); this.requests.set(requestKey, { digest, id: version.id }); return { ...version };
  }
  private file(uid: string, id: string): FileRecord {
    const f = this.files.get(id); requireCondition(f && !f.deleted, 404, "FILE_UNAVAILABLE"); this.pairing.authorize(uid, f.connectionId); return f;
  }
  private owned(uid: string, id: string): Version {
    const v = this.versions.get(id); requireCondition(v, 404, "UPLOAD_UNAVAILABLE"); this.file(uid, v.fileId);
    requireCondition(v.uploadedByDeviceId === this.pairing.device(uid).id, 404, "UPLOAD_UNAVAILABLE");
    if (v.expiresAt <= this.now() && v.state !== "READY" && v.state !== "CANCELLED") v.state = "EXPIRED";
    return v;
  }
  status(uid: string, id: string) { return { ...this.owned(uid, id) }; }
  async content(uid: string, id: string, source: Readable) {
    const v = this.owned(uid, id); requireCondition(v.state === "PENDING" || v.state === "FAILED", 409, "UPLOAD_STATE_CONFLICT");
    v.state = "UPLOADING";
    try {
      await this.storage.remove(v.id);
      const stored = await this.storage.put(v.id, source, Math.min(v.size, this.maxBytes));
      this.owned(uid, id); requireCondition(v.state === "UPLOADING", 409, "UPLOAD_STATE_CONFLICT");
      requireCondition(stored.size === v.size && stored.sha256 === v.sha256, 422, "INTEGRITY_MISMATCH");
      await validateDocument(await this.storage.open(v.id),v.fileName,v.mimeType,this.maxBytes);
      this.owned(uid,id); requireCondition(v.state === "UPLOADING",409,"UPLOAD_STATE_CONFLICT");
      v.state = "VERIFYING"; return { ...v };
    } catch (e) {
      if (v.state === "UPLOADING") v.state = "FAILED";
      await this.storage.remove(v.id);
      if (e instanceof RealtimeError) throw e;
      throw new RealtimeError(422, "TRANSFER_FAILED");
    }
  }
  complete(uid: string, id: string) {
    const v = this.owned(uid, id); if (v.state === "READY") return { ...v };
    requireCondition(v.state === "VERIFYING", 409, "UPLOAD_NOT_VERIFIED");
    const numbers = [...this.versions.values()].filter(x => x.fileId === v.fileId && x.state === "READY").map(x => x.versionNo!);
    v.versionNo = Math.max(0, ...numbers) + 1; v.state = "READY"; v.committedAt = new Date(this.now()).toISOString(); return { ...v };
  }
  list(uid: string) {
    const c = this.pairing.current(uid); if (!c) return [];
    return [...this.files.values()].filter(f => !f.deleted && f.connectionId === c.id && [...this.versions.values()].some(v => v.fileId === f.id && v.state === "READY")).map(f => ({ ...f }));
  }
  listVersions(uid: string, fileId: string) { this.file(uid, fileId); return [...this.versions.values()].filter(v => v.fileId === fileId && v.state === "READY").map(v => ({ ...v })); }
  async download(uid: string, id: string) {
    const v = this.versions.get(id); requireCondition(v?.state === "READY", 404, "FILE_UNAVAILABLE"); this.file(uid, v.fileId);
    return { version: { ...v }, stream: await this.storage.open(id) };
  }
  async cancel(uid: string, id: string) { const v = this.owned(uid, id); requireCondition(v.state !== "READY", 409, "ALREADY_COMPLETE"); v.state = "CANCELLED"; await this.storage.remove(id); }
  async delete(uid: string, id: string) { const f = this.file(uid, id); f.deleted = true; for (const v of this.versions.values()) if (v.fileId === id) await this.storage.remove(v.id); }
  async cleanup() { for (const v of this.versions.values()) if ((v.expiresAt <= this.now() && v.state !== "READY") || this.files.get(v.fileId)?.deleted) { if (v.state !== "READY") v.state = "EXPIRED"; await this.storage.remove(v.id); } }
}


