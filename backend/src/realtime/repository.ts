import type { LocalPairingService } from "./local-pairing-service.js";
import type { LocalTransferService } from "./local-transfer-service.js";
type AsyncCompatible<T> = { [K in keyof T]: T[K] extends (...args: infer A)=>infer R ? (...args:A)=>R|Promise<Awaited<R>> : never };
export type PairingService = AsyncCompatible<Pick<LocalPairingService,"register"|"device"|"current"|"issue"|"claim"|"get"|"approve"|"cancel"|"authorize"|"revoke">>;
export type TransferService = AsyncCompatible<Pick<LocalTransferService,"prepare"|"status"|"content"|"complete"|"list"|"listVersions"|"download"|"cancel"|"delete">>;
