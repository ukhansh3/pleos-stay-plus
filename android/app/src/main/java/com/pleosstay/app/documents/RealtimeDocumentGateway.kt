package com.pleosstay.app.documents

import android.net.Uri
import com.pleosstay.app.realtime.RealtimeApi
import com.pleosstay.app.realtime.RealtimeWorkBridge
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private class DocumentOperationContext(val job: Job) : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<DocumentOperationContext>
}

/** Uses the already paired, PIN-verified Realtime host. Remote revocation is polled, not pushed. */
class RealtimeDocumentGateway(
    private val transport: DocumentTransport,
    private val clientAccess: StateFlow<DocumentClientAccess>,
    scope: CoroutineScope,
    private val endpointNamespace: String,
    private val temporaryDirectory: File,
    private val onAccessLost: () -> Unit = {},
) : SharedDocumentGateway {
    constructor(api: RealtimeApi, bridge: RealtimeWorkBridge, clientAccess: StateFlow<DocumentClientAccess>,
        scope: CoroutineScope, endpointNamespace: String, temporaryDirectory: File, onAccessLost: () -> Unit = {}) :
        this(RealtimeDocumentTransport(api, bridge), clientAccess, scope, endpointNamespace, temporaryDirectory, onAccessLost)

    private val mutable = MutableStateFlow(DocumentAccess())
    override val access: StateFlow<DocumentAccess> = mutable.asStateFlow()
    private val epoch = AtomicLong()
    private val mutex = Mutex()
    private val operations = ConcurrentHashMap.newKeySet<Job>()
    @Volatile private var closed = false
    private val accessWatcher: Job
    private val poller: Job
    init {
        require(Regex("[a-f0-9]{64}").matches(endpointNamespace)) { "ENDPOINT_NAMESPACE_MUST_BE_SHA256" }
        accessWatcher = scope.launch {
            clientAccess.collect { if (!it.canUse) deny("LOCKED") }
        }
        poller = scope.launch {
            while (true) {
                if (clientAccess.value.canUse && !closed) refreshAccess()
                delay(5_000)
            }
        }
    }
    @Synchronized private fun publish(allowed: Boolean, user: String? = null, connection: String? = null, reason: String) {
        val old = mutable.value
        if (old.allowed != allowed || old.userScope != user || old.connectionId != connection || old.reason != reason) {
            mutable.value = DocumentAccess(allowed, user, connection, reason, old.revision + 1)
        }
    }
    private fun deny(reason: String, notifyHost: Boolean = false, except: Job? = null) {
        epoch.incrementAndGet()
        publish(false, reason = reason)
        operations.filter { it != except }.forEach { it.cancel(CancellationException("DOCUMENT_ACCESS_LOST")) }
        clearTemporaryFiles()
        if (notifyHost) onAccessLost()
    }
    /** A failed caller receives its declared result; external locks still cancel every operation. */
    private suspend fun denyCurrent(reason: String, notifyHost: Boolean = false) =
        deny(reason, notifyHost, currentCoroutineContext()[DocumentOperationContext]?.job)
    override suspend fun refreshAccess(): DocumentAccess = mutex.withLock {
        if (closed || !clientAccess.value.canUse) {
            denyCurrent("LOCKED")
            return@withLock access.value
        }
        val before = epoch.get()
        try {
            val identity = transport.identity()
            DocumentValidation.requireId(identity.uid)
            DocumentValidation.requireId(identity.connectionId)
            currentCoroutineContext().ensureActive()
            if (closed || !clientAccess.value.canUse || before != epoch.get()) return@withLock access.value
            val user = "$endpointNamespace:${identity.uid}"
            val old = access.value
            if (old.allowed && (old.userScope != user || old.connectionId != identity.connectionId)) {
                denyCurrent("IDENTITY_CHANGED", true)
            } else publish(true, user, identity.connectionId, "READY")
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { denyCurrent("ACCESS_UNAVAILABLE", true) }
        access.value
    }
    private suspend fun authorized(): DocumentAccess {
        val a = refreshAccess()
        check(a.allowed && clientAccess.value.canUse && !closed) { "DOCUMENT_ACCESS_REQUIRED" }
        return a
    }
    private fun unchanged(a: DocumentAccess) {
        check(clientAccess.value.canUse && !closed && access.value == a) { "DOCUMENT_ACCESS_CHANGED" }
    }
    private suspend fun <T> operation(block: suspend () -> T): T {
        if (currentCoroutineContext()[DocumentOperationContext] != null) return block()
        return coroutineScope {
            val job = currentCoroutineContext()[Job]!!
            operations.add(job)
            try { withContext(DocumentOperationContext(job)) { block() } }
            finally { operations.remove(job) }
        }
    }
    override suspend fun listFiles(): List<DocumentFile> = operation {
        val a = authorized()
        try {
            val files = transport.files(a.userScope!!, a.connectionId!!)
            files.forEach {
                check(it.userScope == a.userScope && it.connectionId == a.connectionId)
                DocumentValidation.requireId(it.fileId)
                require(it.name.isNotBlank() && it.name.length <= 200 && it.name.none { c -> c.isISOControl() || c == '/' || c == '\\' })
            }
            unchanged(a)
            files
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { denyCurrent("ACCESS_UNAVAILABLE", true); error("DOCUMENT_LIST_UNAVAILABLE") }
    }
    override suspend fun listVersions(fileId: String): List<DocumentRef> = operation {
        DocumentValidation.requireId(fileId)
        val a = authorized()
        try {
            check(transport.files(a.userScope!!, a.connectionId!!).any { it.fileId == fileId && it.connectionId == a.connectionId })
            val versions = transport.versions(a.userScope, a.connectionId, fileId)
            versions.forEach {
                DocumentValidation.requireRef(it)
                check(it.userScope == a.userScope && it.connectionId == a.connectionId && it.fileId == fileId)
            }
            unchanged(a)
            versions
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { denyCurrent("ACCESS_UNAVAILABLE", true); error("DOCUMENT_VERSIONS_UNAVAILABLE") }
    }
    override suspend fun validate(ref: DocumentRef): Boolean {
        return try { DocumentValidation.requireRef(ref); listVersions(ref.fileId).any { it == ref } }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { false }
    }
    override suspend fun downloadVerified(ref: DocumentRef): DocumentOpenResult = operation {
        var downloaded: File? = null
        try {
            if (!validate(ref)) return@operation DocumentOpenResult.Blocked("SOURCE_NOT_AUTHORIZED")
            val a = access.value
            downloaded = withContext(Dispatchers.IO) {
                val file = transport.download(ref, temporaryDirectory)
                require(file.canonicalFile.parentFile == temporaryDirectory.canonicalFile && file.isFile)
                // Assign before the dispatcher return: prompt cancellation can discard its value.
                downloaded = file
                try {
                    require(file.length() == ref.sizeBytes)
                    val hash = MessageDigest.getInstance("SHA-256")
                    file.inputStream().use { input ->
                        val buffer = ByteArray(65536)
                        try {
                            while (true) { currentCoroutineContext().ensureActive(); val n = input.read(buffer); if (n < 0) break; hash.update(buffer, 0, n) }
                        } finally { buffer.fill(0) }
                    }
                    require(hash.digest().joinToString("") { "%02x".format(it) } == ref.sha256)
                    currentCoroutineContext().ensureActive()
                    file
                } catch (failure: Throwable) {
                    if (file.canonicalFile.parentFile == temporaryDirectory.canonicalFile) file.delete()
                    throw failure
                }
            }
            unchanged(a)
            if (!validate(ref)) { downloaded?.delete(); return@operation DocumentOpenResult.Blocked("SOURCE_NOT_AUTHORIZED") }
            DocumentOpenResult.Ready(downloaded!!, ref)
        } catch (e: CancellationException) { downloaded?.delete(); throw e }
        catch (_: Exception) { downloaded?.delete(); denyCurrent("DOWNLOAD_UNAVAILABLE", true); DocumentOpenResult.Failed("DOWNLOAD_UNAVAILABLE") }
    }
    override suspend fun uploadRevision(ref: DocumentRef, contentUri: Uri, idempotencyKey: String,
        progress: (Long, Long) -> Unit): DocumentUploadResult = operation {
        try {
            if (!validate(ref)) return@operation DocumentUploadResult.Blocked("SOURCE_NOT_AUTHORIZED")
            val a = access.value
            val saved = transport.upload(ref, contentUri, idempotencyKey) { sent, total -> unchanged(a); progress(sent, total) }
            unchanged(a)
            check(saved.fileId == ref.fileId && saved.connectionId == ref.connectionId && saved.userScope == ref.userScope)
            if (!validate(saved)) return@operation DocumentUploadResult.Blocked("SAVED_VERSION_NOT_AUTHORIZED")
            DocumentUploadResult.Saved(saved)
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { denyCurrent("UPLOAD_UNAVAILABLE", true); DocumentUploadResult.Failed("UPLOAD_UNAVAILABLE") }
    }
    override fun clearTemporaryFiles() { temporaryDirectory.listFiles()?.filter { it.isFile }?.forEach { it.delete() } }
    override fun close() { closed = true; accessWatcher.cancel(); poller.cancel(); deny("CLOSED") }
}
