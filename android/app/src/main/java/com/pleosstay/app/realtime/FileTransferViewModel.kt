package com.pleosstay.app.realtime

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pleosstay.app.documents.RealtimeDocumentTransport
import com.pleosstay.app.waiting.safety.WaitingSafetyGateway
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

data class FileTransferState(val allowed: Boolean = false, val unlocked: Boolean = false, val busy: Boolean = false, val message: String = "잠김", val pairingId: String? = null, val code: String = "", val connectionId: String? = null, val files: List<JSONObject> = emptyList(), val versions: List<JSONObject> = emptyList(), val progress: Float = 0f, val workPicker: WorkTransferPicker? = null, val sendingWork: Boolean = false, val pairing: PcPairing? = null,
    val serverStatus: RealtimeServerStatus = RealtimeServerStatus.UNCHECKED,
    val pcName: String? = null, val pcDeviceId: String? = null, val pairingError: String? = null)

enum class RealtimeServerStatus { UNCHECKED, AVAILABLE, UNAVAILABLE }

data class PcPairing(val phase: String, val expiresAt: Long, val pcName: String? = null, val pcId: String? = null,
    val approvedHere: Boolean = false)

data class WorkTransferPicker(val target: WorkTransferTarget, val exports: List<WorkExportReceipt>, val selected: WorkExportReceipt? = null)
data class WorkTransferTarget(val profile: WorkExportProfile, val pcId: String)
/** Host owns the existing safety gateway lifecycle. This ViewModel never starts a second monitor. */
class FileTransferViewModel(private val api: RealtimeApi, private val safety: WaitingSafetyGateway, private val store: RevisionStore, private val downloadDirectory: File, private val workExports: WorkExportCatalog? = null, private val resetIdentity: suspend () -> Unit = {}) : ViewModel(), RealtimeWorkBridge {
    // Workspace adapters share this exact authenticated client; never create a second refresh owner.
    internal val workspaceApi: RealtimeApi get() = api
    internal suspend fun refreshWorkspaceConnection() { checkAccess(); restore(); checkAccess() }
    private val mutable = MutableStateFlow(FileTransferState()); val state: StateFlow<FileTransferState> = mutable.asStateFlow()
    private var operation: Job? = null
    private var loadingWorkPicker = false
    private var vehicleId: String? = null
    private val bridgeMutex = Mutex()
    private val transport = RealtimeDocumentTransport(api, this)
    val supportsWorkTransfer: Boolean get() = workExports != null
    init { viewModelScope.launch { safety.state.collect { value ->
        if (!value.canUse) { operation?.cancel(); mutable.update { it.copy(allowed=false, unlocked=false, files=emptyList(), versions=emptyList(), workPicker=null, sendingWork=false, message=value.message) } }
        else mutable.update { it.copy(allowed=true) }
    } } }
    // Only a host that verified the local user/PIN may call this. No implicit unlock on restart.
    fun unlockAfterUserVerification() { if (state.value.allowed) mutable.update { it.copy(unlocked=true) } }
    fun lock() { operation?.cancel(); mutable.update { it.copy(unlocked=false, files=emptyList(), versions=emptyList(), workPicker=null, sendingWork=false) } }
    private fun checkAccess() {
        check(safety.state.value.canUse && state.value.allowed && state.value.unlocked) { "SAFETY_OR_USER_LOCKED" }
    }
    private fun run(workTransfer: Boolean = false, block: suspend () -> Unit) {
        if (state.value.busy || operation?.isActive == true) return
        operation = viewModelScope.launch {
            mutable.update { it.copy(busy=true, sendingWork=workTransfer, progress=if(workTransfer) 0f else it.progress) }
            try {
                checkAccess()
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (safety.state.value.canUse && state.value.unlocked) {
                    val message = if (workTransfer) workTransferError(e) else e.message ?: "ERROR"
                    if (e is java.io.IOException || e.message?.startsWith("HTTP_5") == true) {
                        mutable.update { it.copy(serverStatus=RealtimeServerStatus.UNAVAILABLE) }
                    }
                    mutable.update { it.copy(message=message) }
                }
            } finally {
                mutable.update { it.copy(busy=false, sendingWork=false) }
            }
        }
    }
    private fun items(json: JSONObject): List<JSONObject> { val a=json.getJSONArray("items"); return (0 until a.length()).map { a.getJSONObject(it) } }
    private suspend fun ensureRegistered() {
        vehicleId = api.call("POST", "devices", JSONObject().put("kind", "VEHICLE").put("name", "Stay+ 차량")).getString("id")
        checkAccess()
    }

    fun register() = run {
        ensureRegistered()
        if (restore() != null) loadFiles()
    }
    private suspend fun restore(): JSONObject? {
        val c = api.call("GET", "connections/current").optJSONObject("connection")
        checkAccess()
        applyConnection(c)
        return c
    }

    private fun applyConnection(c: JSONObject?) {
        val id = c?.getString("id")
        val connectionLost = id == null || (state.value.connectionId != null && state.value.connectionId != id)
        if (connectionLost) {
            downloadDirectory.listFiles()?.forEach { it.delete() }
            store.clear()
        }
        val pcId = c?.getString("pcDeviceId")
        mutable.update { it.copy(connectionId=id, message=if(id == null) "연결 없음" else "",
            serverStatus=RealtimeServerStatus.AVAILABLE, pcDeviceId=pcId,
            pcName=when {
                pcId == null -> null
                it.pairing?.pcId == pcId -> it.pairing.pcName
                it.pcDeviceId == pcId -> it.pcName
                else -> null
            },
            files=if(connectionLost) emptyList() else it.files,
            versions=if(connectionLost) emptyList() else it.versions,
            workPicker=if(connectionLost) null else it.workPicker) }
    }

    suspend fun pollLinkedConnection() {
        val before = state.value
        if (before.connectionId == null || before.busy || !before.unlocked || !safety.state.value.canUse) return
        try {
            val connection = api.call("GET", "connections/current").optJSONObject("connection")
            if (state.value !== before || !safety.state.value.canUse) return
            val changed = connection?.optString("id") != before.connectionId
            if (changed || before.serverStatus != RealtimeServerStatus.AVAILABLE) {
                applyConnection(connection)
                if (changed && connection == null) mutable.update {
                    it.copy(pairingId=null, pairing=null, code="", message="PC 연결이 해제되었습니다. 새 코드로 연결해 주세요.")
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            if (state.value === before && safety.state.value.canUse) {
                mutable.update { it.copy(serverStatus=RealtimeServerStatus.UNAVAILABLE) }
            }
        }
    }

    private var connectionEntryHandled = false

    /** One user request, one automatic code. Returning after cancel/expiry must not reissue silently. */
    fun openConnection() = run {
        ensureRegistered()
        if (restore() != null) loadFiles()
        else if (!connectionEntryHandled && state.value.pairingId == null) {
            connectionEntryHandled = true
            createPairing()
        }
    }

    fun issue() = run {
        ensureRegistered()
        if (restore() != null) {
            loadFiles()
            return@run
        }
        connectionEntryHandled = true
        createPairing()
    }

    private suspend fun createPairing() {
        val response = api.call("POST", "pairings")
        checkAccess()
        mutable.update { it.copy(pairingId=response.getString("id"), code=response.getString("code"),
            pairing=PcPairing("OPEN", response.getLong("expiresAt")), pairingError=null, message="") }
    }

    fun claim(code: String) = run {
        ensureRegistered()
        val response = api.call("POST", "pairings/claim", JSONObject().put("code", code))
        checkAccess()
        applyPairing(response)
    }

    suspend fun pollPairing() {
        val before = state.value
        val id = before.pairingId ?: return
        if (before.busy || !before.allowed || !before.unlocked || !safety.state.value.canUse ||
            before.pairing?.phase !in setOf("OPEN", "CLAIMED", "CONNECTED") || before.connectionId != null) return
        try {
            val response = api.call("GET", "pairings/$id")
            if (state.value !== before || !safety.state.value.canUse) return
            applyPairing(response)
            if (response.optString("state") == "CONNECTED") refresh()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            if (state.value === before && safety.state.value.canUse) {
                mutable.update { it.copy(pairingError="연결 요청을 확인하지 못했습니다. 네트워크를 확인해 주세요.") }
            }
        }
    }

    private fun applyPairing(response: JSONObject) {
        val pc = listOf("issuer", "claimant").mapNotNull(response::optJSONObject)
            .firstOrNull { it.optString("kind") == "WINDOWS" }
        val approvals = response.optJSONArray("approvedDeviceIds")
        val approvedHere = approvals != null && (0 until approvals.length()).any { approvals.optString(it) == vehicleId }
        val phase = response.getString("state")
        mutable.update { it.copy(pairingId=response.getString("id"), pairing=PcPairing(phase,
            response.getLong("expiresAt"), pc?.optString("name"), pc?.optString("id"), approvedHere),
            code=if(phase == "OPEN") it.code else "", pairingError=null, message="",
            serverStatus=RealtimeServerStatus.AVAILABLE) }
    }

    fun approve() = run {
        val pairing = state.value.pairing ?: return@run
        val id = state.value.pairingId ?: return@run
        if (pairing.phase != "CLAIMED" || pairing.approvedHere || pairing.pcId == null) return@run
        val current = api.call("GET", "pairings/$id")
        val peerMatches = listOf("issuer","claimant").any {
            current.optJSONObject(it)?.let { peer ->
                peer.optString("id") == pairing.pcId && peer.optString("name") == pairing.pcName
            } == true
        }
        checkAccess()
        if (current.optString("state") != "CLAIMED" || !peerMatches) {
            applyPairing(current)
            return@run
        }
        val response = api.call("POST", "pairings/$id/approve")
        checkAccess()
        applyPairing(response)
        if (restore() != null) loadFiles()
    }

    fun cancelPairing() = run {
        val id = state.value.pairingId ?: return@run
        api.call("DELETE", "pairings/$id")
        checkAccess()
        mutable.update { it.copy(pairingId=null, pairing=null, code="", pairingError=null, message="연결 요청을 취소했습니다.") }
    }

    private suspend fun loadFiles() {
        val result = api.call("GET", "files")
        checkAccess()
        mutable.update { it.copy(files=items(result)) }
    }

    fun refresh() = run { if (restore() != null) loadFiles() }
    fun versions(fileId: String) = run { val result=api.call("GET","files/$fileId/versions"); mutable.update { it.copy(versions=items(result)) } }
    private val progress: (Long,Long)->Unit = { done,total -> checkAccess(); mutable.update { it.copy(progress=if(total>0) done.toFloat()/total else 0f) } }
    fun download(version: JSONObject) = run { val file=api.download(version,downloadDirectory,progress); mutable.update { it.copy(message="다운로드 검증 완료: ${file.name}") } }
    fun disconnect() = run { state.value.connectionId?.let { api.call("DELETE","connections/$it") }; downloadDirectory.listFiles()?.forEach { it.delete() }; store.clear(); mutable.update { it.copy(connectionId=null,files=emptyList(),versions=emptyList(),unlocked=false,workPicker=null, pairingId=null,pairing=null,code="",pcName=null,pcDeviceId=null,pairingError=null,message="PC 연결을 해제했습니다.") } }
    fun uploadFakeWork(uri: android.net.Uri, version: JSONObject) = run {
        check(com.pleosstay.app.BuildConfig.DEBUG)
        val (name,mime)=store.describe(uri)
        val staged=stageRevision(WorkRevisionRequest(version.getString("fileId"),version.getString("id"),uri,name,mime,java.util.UUID.randomUUID().toString()))
        val result=uploadRevision(staged,progress); mutable.update { it.copy(message="수정본 v${result.getInt("versionNo")} 확정 완료") }
    }
    fun retryUpload() = run(workTransfer = true) {
        restore()
        val staged = store.pending(state.value.connectionId ?: error("NO_CONNECTION")) ?: error("NO_PENDING_UPLOAD")
        val result = uploadRevision(staged, progress)
        finishWorkTransfer(result)
    }

    fun openWorkPicker() = run {
        val catalog = workExports ?: return@run
        loadingWorkPicker = true
        try {
            val target = workTarget()
            val exports = catalog.list(target.profile)
            checkAccess()
            currentCoroutineContext().ensureActive()
            mutable.update { it.copy(workPicker=WorkTransferPicker(target, exports)) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error(workTransferError(e))
        } finally {
            loadingWorkPicker = false
        }
    }

    fun selectWorkExport(id: String) {
        val picker = state.value.workPicker ?: return
        if (!state.value.busy && state.value.unlocked && safety.state.value.canUse) {
            mutable.update { it.copy(workPicker=picker.copy(selected=picker.exports.firstOrNull { it.id == id })) }
        }
    }

    fun dismissWorkPicker() { mutable.update { it.copy(workPicker=null) } }

    fun confirmWorkTransfer() = run(workTransfer = true) {
        val picker = state.value.workPicker ?: return@run
        val selection = picker.selected ?: return@run
        dismissWorkPicker()
        val current = workTarget()
        check(picker.target == current) { "WORK_TARGET_CHANGED" }
        val catalog = checkNotNull(workExports)
        val receipt = catalog.read(current.profile, selection.id) ?: error("WORK_EXPORT_MISSING")
        check(receipt == selection) { "WORK_EXPORT_CHANGED" }
        catalog.checkAvailable(receipt)
        validateSource(receipt)
        val source = receipt.source
        val request = WorkRevisionRequest(source?.fileId, source?.versionId, Uri.parse(receipt.contentUri),
            receipt.name, WORK_DOCX_MIME, receipt.id, receipt.profile.userScope)
        val staged = stageRevision(request)
        val result = uploadRevision(staged, progress)
        finishWorkTransfer(result)
    }

    fun closeWorkTransferScreen() {
        if (state.value.sendingWork || loadingWorkPicker) operation?.cancel()
        dismissWorkPicker()
    }

    private fun finishWorkTransfer(result: JSONObject) {
        checkAccess()
        val version = result.getInt("versionNo")
        mutable.update { it.copy(message="자료함에 버전 ${version}을 올렸어요. PC에서 내려받을 수 있어요.") }
    }

    private suspend fun workTarget(): WorkTransferTarget {
        checkAccess()
        val identity = transport.identity()
        val connection = restore() ?: error("NO_CONNECTION")
        check(connection.getString("id") == identity.connectionId && connection.isNull("revokedAt")) { "WORK_TARGET_CHANGED" }
        return WorkTransferTarget(WorkExportProfile("${api.documentNamespace}:${identity.uid}", identity.connectionId),
            connection.getString("pcDeviceId"))
    }

    private suspend fun validateSource(receipt: WorkExportReceipt) {
        val source = receipt.source ?: return
        val profile = receipt.profile
        check(source in transport.versions(profile.userScope, profile.connectionId, source.fileId)) {
            "WORK_BASE_UNAVAILABLE"
        }
        checkAccess()
    }

    private fun workTransferError(error: Exception): String {
        val detail = error.message.orEmpty()
        return when {
            detail == "NO_CONNECTION" -> "PC 연결을 확인해 주세요."
            detail == "NO_PENDING_UPLOAD" -> "재시도할 전송이 없습니다."
            detail in setOf("WORK_EXPORT_MISSING", "SOURCE_MISSING", "STAGED_FILE_MISSING") ||
                error is java.io.FileNotFoundException || error is SecurityException -> "파일을 열 수 없습니다. Work에서 다시 내보내 주세요."
            detail == "WORK_EXPORT_CHANGED" -> "내보낸 파일이 변경됐습니다. Work에서 다시 내보내 주세요."
            detail == "WORK_TARGET_CHANGED" || detail == "PENDING_DIFFERENT_CONNECTION" ||
                detail == "STAGED_CONNECTION_CHANGED" -> "PC 연결이 변경됐습니다. 연결을 다시 확인해 주세요."
            detail == "RETRY_OR_CLEAR_PENDING_FIRST" -> "중단된 전송을 먼저 재시도하거나 삭제해 주세요."
            detail.contains("BASE_VERSION") || detail.contains("FILE_UNAVAILABLE") ||
                detail == "WORK_BASE_UNAVAILABLE" -> "원본 문서 버전을 찾을 수 없습니다."
            detail.contains("FILE_TOO_LARGE") -> "20 MiB 이하의 파일만 보낼 수 있습니다."
            detail == "WORK_UPLOAD_EXPIRED" -> "전송 시간이 만료됐습니다. Work에서 파일을 다시 내보내 주세요."
            detail == "WORK_UPLOAD_BUSY" -> "이전 요청을 처리 중입니다. 잠시 후 다시 시도해 주세요."
            detail.startsWith("HTTP_401") || detail.startsWith("HTTP_403") -> "인증을 확인한 후 다시 시도해 주세요."
            else -> "파일을 보내지 못했습니다. 연결을 확인하고 다시 시도해 주세요."
        }
    }
    fun clearLocalFiles() = run { store.clear(); downloadDirectory.listFiles()?.forEach { it.delete() }; lock() }
    fun switchUserAfterConfirmation() = run {
        restore()
        state.value.connectionId?.let { api.call("DELETE","connections/$it") }
        store.clear(); downloadDirectory.listFiles()?.forEach { check(it.delete()) { "CACHE_DELETE_FAILED" } }
        resetIdentity()
        mutable.update { FileTransferState(allowed=it.allowed,message="이전 연결·파일·인증정보를 지웠습니다. 새 PIN 설정 후 등록하세요") }
    }
    fun cancel() {
        val wasSending = state.value.sendingWork
        operation?.cancel()
        if (wasSending && state.value.unlocked && safety.state.value.canUse) {
            mutable.update { it.copy(message="전송을 중단했습니다. 중단된 전송을 다시 시도할 수 있습니다.") }
        }
    }
    override suspend fun stageRevision(request: WorkRevisionRequest): StagedRevision = bridgeMutex.withLock {
        refreshWorkspaceConnection()
        val connection=state.value.connectionId ?: error("NO_CONNECTION")
        val pending=store.pending(connection)
        if(pending!=null) check(pending.request.idempotencyKey==request.idempotencyKey &&
            pending.request.originalFileId==request.originalFileId && pending.request.baseVersionId==request.baseVersionId &&
            pending.request.fileName==request.fileName && pending.request.mimeType==request.mimeType &&
            pending.request.exportOwner==request.exportOwner) { "RETRY_OR_CLEAR_PENDING_FIRST" }
        val receipt = request.exportOwner?.let { owner ->
            workExports?.read(WorkExportProfile(owner, connection), request.idempotencyKey)
                ?: error("WORK_EXPORT_MISSING")
        }
        val staged=store.stage(request)
        try {
            checkAccess()
            currentCoroutineContext().ensureActive()
            receipt?.verifyContent(staged.size, staged.sha256)
            if(pending!=null) {
                check(staged.size==pending.size && staged.sha256==pending.sha256) { "IDEMPOTENCY_CONTENT_CHANGED" }
                staged.file.delete()
                return@withLock pending
            }
            store.save(staged,connection)
            staged
        } catch(e:Throwable){staged.file.delete();throw e}
    }
    override suspend fun uploadRevision(staged: StagedRevision, progress: (Long,Long)->Unit): JSONObject = bridgeMutex.withLock {
        refreshWorkspaceConnection()
        val connection=state.value.connectionId ?: error("NO_CONNECTION")
        check(store.pending(connection)?.transferId==staged.transferId) { "STAGED_CONNECTION_CHANGED" }
        val r=staged.request
        val receipt = r.exportOwner?.let {
            val target = workTarget()
            check(target.profile == WorkExportProfile(it, connection)) { "WORK_TARGET_CHANGED" }
            val record = workExports?.read(target.profile, r.idempotencyKey) ?: error("WORK_EXPORT_MISSING")
            check(record.name == r.fileName && record.source?.fileId == r.originalFileId &&
                record.source?.versionId == r.baseVersionId && r.mimeType == WORK_DOCX_MIME) { "WORK_EXPORT_CHANGED" }
            record.verifyContent(staged.size, staged.sha256)
            validateSource(record)
            verifyStagedContent(record, staged.file)
            record
        }
        checkAccess()
        currentCoroutineContext().ensureActive()
        val prepared = api.call("POST", "uploads", JSONObject().put("connectionId", connection)
            .put("fileId", r.originalFileId).put("baseVersionId", r.baseVersionId).put("fileName", r.fileName)
            .put("mimeType", r.mimeType).put("size", staged.size).put("sha256", staged.sha256)
            .put("idempotencyKey", r.idempotencyKey))
        checkAccess()
        val id = prepared.getString("id")
        val uploadState = prepared.getString("state")
        if (receipt != null) {
            verifyUploadResponse(receipt, prepared)
            if (uploadState == "EXPIRED" || uploadState == "CANCELLED") {
                store.finish(staged)
                error("WORK_UPLOAD_EXPIRED")
            }
            check(uploadState != "UPLOADING") { "WORK_UPLOAD_BUSY" }
        }
        val result = if (uploadState == "READY") prepared else {
            if (uploadState != "VERIFYING") api.upload(id, staged.file) { done, total -> checkAccess(); progress(done, total) }
            checkAccess()
            currentCoroutineContext().ensureActive()
            api.call("POST", "uploads/$id/complete")
        }
        checkAccess()
        if (receipt != null) {
            verifyUploadResponse(receipt, result)
            check(result.getString("state") == "READY" && result.getInt("versionNo") > 0)
        }
        store.finish(staged)
        result
    }

    private fun verifyUploadResponse(receipt: WorkExportReceipt, result: JSONObject) {
        check(result.getString("connectionId") == receipt.profile.connectionId &&
            result.getString("mimeType") == WORK_DOCX_MIME && result.getString("fileName") == receipt.name)
        receipt.verifyContent(result.getLong("size"), result.getString("sha256"))
        val source = receipt.source
        if (source == null) {
            check(result.isNull("baseVersionId") && result.getString("kind") == "ORIGINAL")
        } else {
            check(result.getString("fileId") == source.fileId && result.getString("baseVersionId") == source.versionId &&
                result.getString("kind") == "REVISION")
        }
    }

    private suspend fun verifyStagedContent(receipt: WorkExportReceipt, file: File) = withContext(Dispatchers.IO) {
        check(file.isFile && file.length() == receipt.size) { "WORK_EXPORT_CHANGED" }
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(65536)
            while (true) {
                currentCoroutineContext().ensureActive()
                checkAccess()
                val size = input.read(buffer)
                if (size < 0) break
                digest.update(buffer, 0, size)
            }
        }
        receipt.verifyContent(file.length(), digest.digest().joinToString("") { "%02x".format(it) })
    }
}

