import type { PairingService, TransferService } from "./repository.js";
import type { FastifyInstance } from "fastify";
import type { Readable } from "node:stream";
import { RealtimeError } from "./domain.js";
import type { IdentityVerifier } from "./auth.js";
import type { LocalPairingService } from "./local-pairing-service.js";
import type { LocalTransferService, UploadRequest } from "./local-transfer-service.js";
const object = (properties: object, required: string[]) => ({ type: "object", properties, required, additionalProperties: false });
const text = { type: "string", minLength: 1, maxLength: 200 };
export async function registerRealtimeRoutes(app: FastifyInstance, pairing: PairingService, transfer: TransferService, auth: IdentityVerifier) {
  await app.register(async scope => {
    const actors = new WeakMap<object, string>();
    scope.addHook("onRequest", async request => {
      const token = /^Bearer (\S+)$/.exec(request.headers.authorization ?? "")?.[1];
      if (!token) throw new RealtimeError(401, "UNAUTHENTICATED"); actors.set(request, await auth.verify(token));
    });
    scope.setErrorHandler((error, request, reply) => {
      const known = error instanceof RealtimeError; const invalid = error instanceof Error && "validation" in error;
      const status = known ? error.statusCode : invalid ? 400 : 500;
      reply.code(status).send({ error: { code: known ? error.code : invalid ? "INVALID_INPUT" : "INTERNAL_ERROR", message: known ? error.code : "Request failed", requestId: request.id } });
    });
    scope.addContentTypeParser("application/octet-stream", (_request, payload, done) => done(null, payload));
    const actor = (request: object) => actors.get(request)!;
    scope.post<{ Body: { kind: "WINDOWS" | "VEHICLE"; name: string } }>("/devices", { schema: { body: object({ kind: { enum: ["WINDOWS", "VEHICLE"] }, name: text }, ["kind", "name"]) } }, async r => pairing.register(actor(r), r.body.kind, r.body.name));
    scope.get("/devices/me", async r => pairing.device(actor(r)));
    scope.post("/pairings", async r => pairing.issue(actor(r)));
    scope.post<{ Body: { code: string } }>("/pairings/claim", { schema: { body: object({ code: { type: "string", pattern: "^[0-9]{8}$" } }, ["code"]) } }, async r => pairing.claim(actor(r), r.body.code, r.ip));
    scope.get<{ Params: { id: string } }>("/pairings/:id", async r => pairing.get(actor(r), r.params.id));
    scope.post<{ Params: { id: string } }>("/pairings/:id/approve", async r => pairing.approve(actor(r), r.params.id));
    scope.delete<{ Params: { id: string } }>("/pairings/:id", async (r, reply) => { await pairing.cancel(actor(r), r.params.id); return reply.code(204).send(); });
    scope.get("/connections/current", async r => ({ connection: await pairing.current(actor(r)) }));
    scope.delete<{ Params: { id: string } }>("/connections/:id", async (r, reply) => { await pairing.revoke(actor(r), r.params.id); return reply.code(204).send(); });
    scope.get("/files", async r => ({ items: await transfer.list(actor(r)) }));
    scope.get<{ Params: { id: string } }>("/files/:id/versions", async r => ({ items: await transfer.listVersions(actor(r), r.params.id) }));
    scope.delete<{ Params: { id: string } }>("/files/:id", async (r, reply) => { await transfer.delete(actor(r), r.params.id); return reply.code(204).send(); });
    scope.post<{ Body: UploadRequest }>("/uploads", { schema: { body: object({ connectionId: text, fileId: text, baseVersionId: text, fileName: text, mimeType: text, size: { type: "integer", minimum: 1 }, sha256: { type: "string", pattern: "^[a-f0-9]{64}$" }, idempotencyKey: text }, ["connectionId", "fileName", "mimeType", "size", "sha256", "idempotencyKey"]) } }, async r => transfer.prepare(actor(r), r.body));
    scope.get<{ Params: { id: string } }>("/uploads/:id", async r => transfer.status(actor(r), r.params.id));
    scope.put<{ Params: { id: string }; Body: Readable }>("/uploads/:id/content", async r => {
      if (r.headers["content-type"] !== "application/octet-stream") throw new RealtimeError(415, "UNSUPPORTED_CONTENT_TYPE");
      return transfer.content(actor(r), r.params.id, r.body);
    });
    scope.post<{ Params: { id: string } }>("/uploads/:id/complete", async r => transfer.complete(actor(r), r.params.id));
    scope.delete<{ Params: { id: string } }>("/uploads/:id", async (r, reply) => { await transfer.cancel(actor(r), r.params.id); return reply.code(204).send(); });
    scope.get<{ Params: { id: string } }>("/versions/:id/content", async (r, reply) => {
      const { version, stream } = await transfer.download(actor(r), r.params.id);
      return reply.header("Cache-Control", "no-store").header("X-Content-Type-Options", "nosniff").header("X-Content-SHA256", version.sha256).header("Content-Length", version.size).type("application/octet-stream").send(stream);
    });
  }, { prefix: "/api/v1/realtime" });
}


