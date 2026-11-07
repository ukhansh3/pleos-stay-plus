package com.pleosstay.app.realtime

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pleosstay.app.waiting.safety.WaitingSafetyGateway
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

data class FileTransferState(val allowed: Boolean = false, val unlocked: Boolean = false, val busy: Boolean = false, val message: String = "잠김", val pairingId: String? = null, val code: String = "", val connectionId: String? = null, val files: List<JSONObject> = emptyList(), val versions: List<JSONObject> = emptyList(), val progress: Float = 0f)
/** Host owns the existing safety gateway lifecycle. This ViewModel never starts a second monitor. */
class FileTransferViewModel(private val api: RealtimeApi, safety: WaitingSafetyGateway, private val store: RevisionStore, private val downloadDirectory: File, private val resetIdentity: suspend () -> Unit = {}) : ViewModel(), RealtimeWorkBridge {
    // Workspace adapters share this exact authenticated client; never create a second refresh owner.
    internal val workspaceApi: RealtimeApi get() = api
    internal suspend fun refreshWorkspaceConnection() { checkAccess(); restore(); checkAccess() }
    private val mutable = MutableStateFlow(FileTransferState()); val state: StateFlow<FileTransferState> = mutable.asStateFlow()
    private var operation: Job? = null
    private val bridgeMutex = Mutex()
    init { viewModelScope.launch { safety.state.collect { value ->
        if (!value.canUse) { operation?.cancel(); mutable.update { it.copy(allowed=false, unlocked=false, files=emptyList(), versions=emptyList(), message=value.message) } }
        else mutable.update { it.copy(allowed=true) }
    } } }
    // Only a host that verified the local user/PIN may call this. No implicit unlock on restart.
    fun unlockAfterUserVerification() { if (state.value.allowed) mutable.update { it.copy(unlocked=true) } }
    fun lock() { operation?.cancel(); mutable.update { it.copy(unlocked=false, files=emptyList(), versions=emptyList()) } }
    private fun checkAccess() { check(state.value.allowed && state.value.unlocked) { "SAFETY_OR_USER_LOCKED" } }
    private fun run(block: suspend () -> Unit) {
        if (state.value.busy) return
        operation = viewModelScope.launch { mutable.update { it.copy(busy=true) }; try { checkAccess(); block() } catch(e: CancellationException) { throw e } catch(e: Exception) { mutable.update { it.copy(message=e.message ?: "ERROR") } } finally { mutable.update { it.copy(busy=false) } } }
    }
    private fun items(json: JSONObject): List<JSONObject> { val a=json.getJSONArray("items"); return (0 until a.length()).map { a.getJSONObject(it) } }
    fun register() = run { api.call("POST","devices",JSONObject().put("kind","VEHICLE").put("name","Stay+ 차량")); restore() }
    private suspend fun restore() { val c=api.call("GET","connections/current").optJSONObject("connection"); if(c==null || (state.value.connectionId!=null && state.value.connectionId!=c.getString("id"))) { downloadDirectory.listFiles()?.forEach { it.delete() }; store.clear() }; mutable.update { it.copy(connectionId=c?.getString("id"),message=c?.toString() ?: "연결 없음") } }
    fun issue() = run { val p=api.call("POST","pairings"); mutable.update { it.copy(pairingId=p.getString("id"),code=p.getString("code"),message="상대 기기에서 코드 입력 후 양쪽 승인") } }
    fun claim(code: String) = run { val p=api.call("POST","pairings/claim",JSONObject().put("code",code)); mutable.update { it.copy(pairingId=p.getString("id"),message=p.toString()) } }
    fun inspectPairing() = run { val p=api.call("GET","pairings/${state.value.pairingId}"); mutable.update { it.copy(message=p.toString()) } }
    fun approve() = run { api.call("POST","pairings/${state.value.pairingId}/approve"); restore() }
    fun refresh() = run { restore(); val result=api.call("GET","files"); mutable.update { it.copy(files=items(result)) } }
    fun versions(fileId: String) = run { val result=api.call("GET","files/$fileId/versions"); mutable.update { it.copy(versions=items(result)) } }
    private val progress: (Long,Long)->Unit = { done,total -> checkAccess(); mutable.update { it.copy(progress=if(total>0) done.toFloat()/total else 0f) } }
    fun download(version: JSONObject) = run { val file=api.download(version,downloadDirectory,progress); mutable.update { it.copy(message="다운로드 검증 완료: ${file.name}") } }
    fun disconnect() = run { state.value.connectionId?.let { api.call("DELETE","connections/$it") }; downloadDirectory.listFiles()?.forEach { it.delete() }; store.clear(); mutable.update { it.copy(connectionId=null,files=emptyList(),versions=emptyList(),unlocked=false) } }
    fun uploadFakeWork(uri: android.net.Uri, version: JSONObject) = run {
        check(com.pleosstay.app.BuildConfig.DEBUG)
        val (name,mime)=store.describe(uri)
        val staged=stageRevision(WorkRevisionRequest(version.getString("fileId"),version.getString("id"),uri,name,mime,java.util.UUID.randomUUID().toString()))
        val result=uploadRevision(staged,progress); mutable.update { it.copy(message="수정본 v${result.getInt("versionNo")} 확정 완료") }
    }
    fun retryUpload() = run { restore(); val staged=store.pending(state.value.connectionId ?: error("NO_CONNECTION")) ?: error("NO_PENDING_UPLOAD"); val result=uploadRevision(staged,progress); mutable.update { it.copy(message="수정본 v${result.getInt("versionNo")} 복구 완료") } }
    fun clearLocalFiles() = run { store.clear(); downloadDirectory.listFiles()?.forEach { it.delete() }; lock() }
    fun switchUserAfterConfirmation() = run {
        restore()
        state.value.connectionId?.let { api.call("DELETE","connections/$it") }
        store.clear(); downloadDirectory.listFiles()?.forEach { check(it.delete()) { "CACHE_DELETE_FAILED" } }
        resetIdentity()
        mutable.update { FileTransferState(allowed=it.allowed,message="이전 연결·파일·인증정보를 지웠습니다. 새 PIN 설정 후 등록하세요") }
    }
    fun cancel() { operation?.cancel() }
    override suspend fun stageRevision(request: WorkRevisionRequest): StagedRevision = bridgeMutex.withLock {
        refreshWorkspaceConnection()
        val connection=state.value.connectionId ?: error("NO_CONNECTION")
        val pending=store.pending(connection)
        if(pending!=null) check(pending.request.idempotencyKey==request.idempotencyKey &&
            pending.request.originalFileId==request.originalFileId && pending.request.baseVersionId==request.baseVersionId &&
            pending.request.fileName==request.fileName && pending.request.mimeType==request.mimeType) { "RETRY_OR_CLEAR_PENDING_FIRST" }
        val staged=store.stage(request)
        try {
            checkAccess()
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
        val prepared=api.call("POST","uploads",JSONObject().put("connectionId",state.value.connectionId ?: error("NO_CONNECTION")).put("fileId",r.originalFileId).put("baseVersionId",r.baseVersionId).put("fileName",r.fileName).put("mimeType",r.mimeType).put("size",staged.size).put("sha256",staged.sha256).put("idempotencyKey",r.idempotencyKey))
        val id=prepared.getString("id")
        if(prepared.getString("state") == "READY") { store.finish(staged); return@withLock prepared }
        if(prepared.getString("state") != "VERIFYING") api.upload(id,staged.file) { done,total -> checkAccess(); progress(done,total) }
        checkAccess(); api.call("POST","uploads/$id/complete").also { store.finish(staged) }
    }
}

