import { createReadStream, createWriteStream } from "node:fs";
import { mkdir, unlink } from "node:fs/promises";
import { join } from "node:path";
import { Readable, Transform } from "node:stream";
import { pipeline } from "node:stream/promises";
import { createHash } from "node:crypto";
import { requireCondition } from "./domain.js";
export interface StoredContent { size: number; sha256: string }
export interface StorageGateway {
  put(key: string, source: Readable, maxBytes: number): Promise<StoredContent>;
  open(key: string): Promise<Readable>;
  remove(key: string): Promise<void>;
}
/** Disk-backed local storage; keys are server-generated UUIDs, never filenames. */
export class LocalStorageGateway implements StorageGateway {
  constructor(private readonly root: string) {}
  private path(key: string) { requireCondition(/^[0-9a-f-]{36}$/.test(key), 400, "INVALID_STORAGE_KEY"); return join(this.root, key); }
  async put(key: string, source: Readable, maxBytes: number) {
    await mkdir(this.root, { recursive: true }); const path = this.path(key);
    let size = 0; const hash = createHash("sha256");
    const meter = new Transform({ transform(chunk: Buffer, _encoding, callback) {
      size += chunk.length;
      if (size > maxBytes) return callback(new Error("FILE_TOO_LARGE"));
      hash.update(chunk); callback(null, chunk);
    }});
    let created = false;
    const output = createWriteStream(path, { flags: "wx", mode: 0o600 });
    output.once("open", () => { created = true; });
    try { await pipeline(source, meter, output); return { size, sha256: hash.digest("hex") }; }
    catch (error) { if (created) await unlink(path).catch(() => {}); throw error; }
  }
  async open(key: string) { return createReadStream(this.path(key)); }
  async remove(key: string) { await unlink(this.path(key)).catch((e: NodeJS.ErrnoException) => { if (e.code !== "ENOENT") throw e; }); }
}
