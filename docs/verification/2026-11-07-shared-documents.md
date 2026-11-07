# Shared document adapter verification — 2026-11-07

Baseline: dev `28ca968`. Scope: new Android `documents` package; existing Realtime auth, pairing, PIN, server routes, DB and transfer contract retained.

## Implemented

- Typed files (real file-list fields) and exact version references `(endpoint namespace + server UID, connectionId, fileId, versionId)`. No fabricated hash/size on file list.
- Adapter consumes the SAME RealtimeApi and RealtimeWorkBridge supplied by the verified Realtime host. No second client credentials, authentication or server.
- Server `devices/me` VEHICLE/UID and `connections/current` device binding checked; list and READY versions are authoritative. Current connection checked before each operation. Exact metadata must match before restoring an encrypted note/draft.
- Temporary download content is checked for app-owned path, 20 MiB bound, size and SHA-256, and reauthorized after transfer. Original file upload is deliberately absent: VEHICLE Cloud permissions have not been proven.
- PIN/safety/foreground denial cancels document operations, invalidates access revision and removes only document temporary files. UI must observe access and release sensitive renderers/buffers. No local cache grants access.
- Remote revocation detection uses foreground polling at approximately five seconds plus per-operation validation. This is not server push or an instantaneous remote-revocation guarantee; network/auth uncertainty locks access.
- Revision upload uses existing immutable staging and bridge, content URI, base version, size/hash and returned READY version checks. Existing transfer journal remains owned by Realtime.
- User-approved retained records: Android Keystore AES-256/GCM, AtomicFile, hashed namespace/key paths, authenticated record labels, noBackup app-private location. Records stay encrypted after revoke/switch; explicit delete only. Same namespace instances share a process mutex. Store itself is a persistence port; caller must perform live source validation before display. Default 48 MiB opaque record technical bound supports source + draft; allocation/memory on actual PLEOS remains to be measured.

## Tests added and execution status

`RealtimeDocumentGatewayTest` (8 tests): stable access revisions, immediate foreground/PIN lock, temporary cleanup, remote user/connection switch, auth/network fail closed, exact version/user isolation, malformed version shape, download size/hash rejection, and cancellation racing a final transport file creation. Integrity failure must return the declared Failed result; tests no longer accept self-cancellation as a substitute.

`AndroidEncryptedDocumentStoreTest`: actual Keystore reopen, namespace isolation, hashed names/no plaintext, explicit deletion. Instrumentation NOT RUN by this agent; supervisor owns all Gradle/emulator execution.

`RealtimeWorkspaceBridgeTest` (4 instrumentation tests, separately assigned by supervisor): loopback-only test HTTP server plus existing RealtimeApi/FileTransferViewModel, fake safety/store; failed upload retry reuses the same journal/key, changed bytes rejected, connection switch blocks old stage, concurrent same-key stages share one journal. No live credentials or cloud service involved. NOT RUN here.

## Integration review corrections

- Internal fail-closed transitions exclude the failing operation from cancellation so it can return its typed failure; external PIN/safety/foreground locks still cancel every operation. Nested validations inherit the operation context, avoiding ancestor self-cancellation.
- Download tracks an owned transport result before returning from IO and deletes it if validation/cancellation fails. Supervisor additionally fixed RealtimeApi's own final rename/dispatcher-return cleanup race.
- Supervisor extended existing bridge retry to verify same key/base/name/MIME and fresh source size/hash before journal reuse, and serialized staging/upload to avoid concurrent journal overwrite. No transfer/auth product policy changed.

Actual paired Windows→Android→Windows transfer, Supabase RLS/RPC/Storage E2E, PLEOS safety transitions, renderer release, process recovery and Keystore instrumentation: NOT RUN here. No Cloud mutation or deployment performed.

## Official Android sources

- [Android Keystore](https://developer.android.com/privacy-and-security/keystore): application key isolation and AES/GCM.
- [AtomicFile](https://developer.android.com/reference/android/util/AtomicFile): atomic local replacement; caller synchronization required.
- [Context.getNoBackupFilesDir](https://developer.android.com/reference/android/content/Context#getNoBackupFilesDir()): app-private files excluded from automatic backup.

Existing contract evidence: `RealtimeApi.kt`, `RealtimeWorkBridge.kt`, `FileTransferViewModel.kt`, backend Realtime `routes.ts`, `domain.ts`, `local-transfer-service.ts`, `supabase-services.ts`; document contract and Study/Work specs. No new Pleos API or permission is introduced by this package.

## Supervisor executed validation — final update

The supervisor reported the following actual results after integration (supersedes the earlier per-agent NOT RUN status for these checks):

- Android `assembleDebug`: PASS.
- Android test APK build: PASS.
- Android JVM suite: **116 PASS**, including **8 shared-document gateway tests**.
- Actual Pleos API 34 emulator instrumentation: **9 PASS** — encrypted document store 1, Realtime workspace bridge 4, Study 3, Waiting DB 1.

The loopback bridge tests verify the real Android Realtime client/ViewModel against a test-only HTTP stub; they do not prove Cloud authorization or a paired Windows round trip. Full Supabase Auth/RLS/RPC/Storage end-to-end, actual Windows→Android→Windows document transfer, and real vehicle behavior remain **NOT RUN**. No result above establishes support for an unapproved PDF/DOCX advanced editing engine or VEHICLE original-upload permissions.
