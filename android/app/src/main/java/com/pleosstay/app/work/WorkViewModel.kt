package com.pleosstay.app.work

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pleosstay.app.documents.DocumentOpenResult
import com.pleosstay.app.documents.DocumentRef
import com.pleosstay.app.documents.SharedDocumentGateway
import com.pleosstay.app.modes.ActivityMode
import com.pleosstay.app.modes.ModeStartManager
import com.pleosstay.app.modes.ModeStartRequest
import com.pleosstay.app.modes.ModeStartResult
import com.pleosstay.app.modes.WaitingChoice
import com.pleosstay.app.waiting.safety.WaitingSafetyGateway
import com.pleosstay.app.work.docx.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Presentation only: no authentication, SDK or Waiting policy implemented here. */
class WorkViewModel(
    private val documents: SharedDocumentGateway,
    private val drafts: WorkDraftRepository,
    private val manager: ModeStartManager,
    private val safety: WaitingSafetyGateway,
    docxFiles: DocxFileGateway? = null,
    private val now: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    private val mutable = MutableStateFlow(WorkState())
    val state = mutable.asStateFlow()
    private val foreground = MutableStateFlow(false)
    private var operation: Job? = null
    private var stopping=false
    private var generation = 0L
    private var scopeIdentity: Pair<String?, String?>? = null
    private var lastSessionId:String?=null
    val transitionSessionId:String? get()=state.value.session?.id ?: lastSessionId?.takeIf { docx.requiresRetentionRecovery }
    val docx = DocxEditorController(drafts, documents, docxFiles, viewModelScope,
        retentionPermitted={ foreground.value && safety.state.value.canUse && documents.access.value.allowed }) {
        foreground.value && safety.state.value.canUse && documents.access.value.allowed &&
            state.value.session?.remaining(now())?.let { it > 0 } == true
    }

    init {
        viewModelScope.launch {
            combine(safety.state, documents.access, foreground) { vehicle, access, visible ->
                Triple(vehicle, access, visible)
            }.collect { (vehicle, access, visible) ->
                val identity = access.userScope to access.connectionId
                // A denied access snapshot may intentionally erase identity fields. That is a lock,
                // not evidence of a different user; preserve only the nonsensitive timer.
                val identified = access.allowed && access.userScope != null && access.connectionId != null
                val changed = identified && scopeIdentity != null && scopeIdentity != identity
                if (identified) scopeIdentity = identity
                val allowed = visible && vehicle.canUse && access.allowed
                if (!allowed || changed) {
                    docx.mask()
                    generation++
                    operation?.cancel()
                    mutable.update { previous -> WorkState(
                        allowed = allowed, minutes = previous.minutes,
                        session = if (changed) null else previous.session,
                        remainingMillis = if (changed) 0 else previous.remainingMillis,
                        message = when { !visible -> "화면이 숨겨져 업무 문서를 잠갔습니다."
                            !vehicle.canUse -> vehicle.message
                            !access.allowed -> access.reason
                            else -> "사용자 또는 연결이 바뀌었습니다. 자료를 다시 선택해 주세요." },
                    ) }
                } else mutable.update { it.copy(allowed = true) }
            }
        }
        viewModelScope.launch {
            while (true) {
                delay(1_000)
                val session = state.value.session ?: continue
                val remaining = session.remaining(now())
                if (remaining == 0L) {
                    docx.mask()
                    operation?.cancel()
                    generation++
                    mutable.update { it.copy(session = null, remainingMillis = 0, busy = false, choice = null,
                        files = emptyList(), versions = emptyList(), selected = null,
                        message = "업무 시간이 끝났습니다. 보관한 초안과 기존 Waiting은 유지합니다.") }
                } else mutable.update { it.copy(remainingMillis = remaining) }
            }
        }
    }

    /** Host Activity/route lifecycle calls this; never unlocks Realtime PIN. */
    fun setForeground(value: Boolean) {
        foreground.value = value
        if (!value) {
            docx.mask()
            generation++; operation?.cancel()
            mutable.update { it.copy(allowed = false, busy = false, files = emptyList(), versions = emptyList(),
                drafts = emptyList(), selected = null, choice = null, exitConfirmation = false) }
        }
    }

    fun setMinutes(value: String) { if (!state.value.busy) mutable.update { it.copy(minutes = value.filter(Char::isDigit).take(3)) } }

    suspend fun transitionPreflight(): com.pleosstay.app.modes.ActivityOperation {
        if(!foreground.value || !safety.state.value.canUse) return com.pleosstay.app.modes.ActivityOperation(false,"정차 상태와 활성 화면을 확인해 주세요.")
        val access=documents.refreshAccess()
        return com.pleosstay.app.modes.ActivityOperation(access.allowed && foreground.value && safety.state.value.canUse,"자료함 인증·PIN을 확인해 주세요.")
    }

    suspend fun startForTransition(choice:WaitingChoice?=null): com.pleosstay.app.modes.ActivityOperation {
        check(documents.refreshAccess().allowed)
        checkVisible()
        if (state.value.session != null) return com.pleosstay.app.modes.ActivityOperation.Done
        if (state.value.busy) return com.pleosstay.app.modes.ActivityOperation(false,"업무 작업 중입니다.")
        val prompt=state.value.choice
        if(prompt!=null && choice==null) return com.pleosstay.app.modes.ActivityOperation(false,state.value.message,true)
        val result=if(prompt!=null) manager.resolve(prompt,checkNotNull(choice)) else manager.start(ModeStartRequest(ActivityMode.WORK,state.value.minutes.toIntOrNull() ?: 0))
        applyStartResult(result)
        return com.pleosstay.app.modes.ActivityOperation(state.value.session != null,state.value.message,state.value.choice!=null)
    }

    suspend fun stopForTransition(): com.pleosstay.app.modes.ActivityOperation {
        if(stopping) return com.pleosstay.app.modes.ActivityOperation(false,"업무 종료 중입니다.")
        stopping=true
        return try {
            operation?.cancelAndJoin()
            mutable.update { it.copy(busy=true) }
            val saved=docx.flushForTransition()
            if (!saved.success) { mutable.update { it.copy(message=saved.message) };saved } else {
                finishKeepingDraftsNow()
                com.pleosstay.app.modes.ActivityOperation.Done
            }
        } finally { stopping=false;mutable.update { it.copy(busy=false) } }
    }

    fun start() = command {
        check(state.value.session == null) { "WORK_ALREADY_STARTED" }
        val minutes = state.value.minutes.toIntOrNull()
        if (minutes == null) { mutable.update { it.copy(message = "허용된 업무 시간을 분 단위로 입력해 주세요.") }; return@command }
        applyStartResult(manager.start(ModeStartRequest(ActivityMode.WORK, minutes)))
    }

    fun resolve(choice: WaitingChoice) = command {
        val prompt = state.value.choice ?: return@command
        mutable.update { it.copy(choice = null) }
        applyStartResult(manager.resolve(prompt, choice))
    }

    fun extend(minutes: Int) = command {
        require(minutes > 0) { "WORK_DURATION_INVALID" }
        val active = state.value.session ?: return@command
        val waitingId = active.waitingId ?: error("WORK_WAITING_RECOVERY_REQUIRED")
        applyStartResult(manager.adjustEnd(ActivityMode.WORK, waitingId, Math.addExact(active.endsAt, minutes.toLong() * 60_000)))
    }

    private fun applyStartResult(result: ModeStartResult) {
        checkVisible()
        when (result) {
            is ModeStartResult.Ready -> {
                val previous=state.value.session
                val session = WorkSession(result.waiting?.id, result.endsAt ?: Math.addExact(now(), result.durationMillis),
                    id=previous?.id ?: java.util.UUID.randomUUID().toString(),startedAt=previous?.startedAt ?: now())
                lastSessionId=session.id
                check(session.remaining(now()) > 0) { "WORK_DURATION_EXPIRED" }
                mutable.update { it.copy(session = session, remainingMillis = session.remaining(now()), choice = null,
                    storageError = false, message = "업무 타이머를 시작했습니다. DOCX를 열거나 새 문서를 작성하세요. PDF 편집·업로드는 제외됩니다.") }
            }
            is ModeStartResult.NeedsChoice -> mutable.update { it.copy(choice = result, message = "Waiting 잔여시간이 부족합니다. 연장 또는 단축을 선택해 주세요.") }
            is ModeStartResult.SafetyBlocked -> mutable.update { it.copy(message = result.message) }
            is ModeStartResult.RecoveryRequired -> mutable.update { it.copy(storageError = true, message = result.message) }
            is ModeStartResult.StorageError -> mutable.update { it.copy(storageError = true, message = "Waiting 저장 결과를 확인하지 못했습니다. 복구 후 다시 시도해 주세요.") }
            is ModeStartResult.InvalidDuration -> mutable.update { it.copy(message = "업무 시간은 ${result.allowedMinutes.first}~${result.allowedMinutes.last}분입니다.") }
            is ModeStartResult.WaitingChanged -> mutable.update { it.copy(message = "Waiting이 변경됐습니다. 최신 상태를 확인하고 다시 시도해 주세요.") }
            is ModeStartResult.Cancelled -> mutable.update { it.copy(message = "시작 또는 시간 변경을 취소했습니다.") }
            is ModeStartResult.DecisionAlreadyHandled -> Unit
        }
    }

    fun refresh() = command {
        check(documents.refreshAccess().allowed) { "WORK_ACCESS_BLOCKED" }
        val files = documents.listFiles()
        val local = drafts.summaries()
        checkVisible()
        mutable.update { it.copy(files = files, versions = emptyList(), drafts = local,
            message = if (files.isEmpty()) "자료함이 비어 있습니다. Windows에서 PDF 또는 DOCX를 전송해 주세요." else "기존 Realtime 자료함을 불러왔습니다.") }
    }

    fun versions(fileId: String) = command {
        val versions = documents.listVersions(fileId).filter { WorkFormat.of(it) != null }
        checkVisible()
        mutable.update { it.copy(versions = versions, message = if (versions.isEmpty()) "이 파일에는 지원 가능한 PDF/DOCX 버전이 없습니다." else "업무에 사용할 정확한 버전을 선택해 주세요.") }
    }

    fun open(ref: DocumentRef) = command {
        requireSession()
        check(WorkFormat.of(ref) != null) { "WORK_FORMAT_UNSUPPORTED" }
        val restored = drafts.restore(ref)
        val summary = restored ?: when (val downloaded = documents.downloadVerified(ref)) {
            is DocumentOpenResult.Ready -> try { drafts.retainSource(downloaded.ref, downloaded.file) } finally { downloaded.file.delete() }
            is DocumentOpenResult.Blocked -> error("WORK_ACCESS_BLOCKED")
            is DocumentOpenResult.Failed -> error("WORK_DOWNLOAD_FAILED")
        }
        checkVisible()
        requireSession()
        mutable.update { it.copy(selected = summary, drafts = (it.drafts.filterNot { draft -> draft.id == summary.id } + summary),
            message = if (ref.mimeType == DOCX_MIME) "DOCX 원본을 검증·보관했습니다. 기존 편집 초안이 있으면 이어서 엽니다."
                else if (summary.dirty) "기존 엔진 초안이 있습니다. PDF 편집은 이번 범위에서 제외됩니다."
                else "원본 바이트와 해시를 확인해 암호화 보관했습니다. PDF 편집은 이번 범위에서 제외됩니다.") }
        if (ref.mimeType == DOCX_MIME) docx.openRemote(ref)
    }

    fun deleteDraft(ref: DocumentRef) = command {
        drafts.delete(ref)
        checkVisible()
        mutable.update { it.copy(drafts = it.drafts.filterNot { draft -> draft.source.key == ref.key },
            selected = it.selected?.takeUnless { draft -> draft.source.key == ref.key }, message = "선택한 로컬 보관본을 삭제했습니다. 서버 원본은 유지됩니다.") }
    }

    fun requestExit() {
        if (state.value.selected?.dirty == true || docx.state.value.dirty) mutable.update { it.copy(exitConfirmation = true) }
        else finishKeepingDrafts()
    }
    fun dismissExit() { mutable.update { it.copy(exitConfirmation = false) } }
    fun finishKeepingDrafts() {
        viewModelScope.launch { stopForTransition() }
    }
    private fun finishKeepingDraftsNow() {
        docx.mask()
        lastSessionId=null
        generation++; operation?.cancel()
        mutable.update { it.copy(session = null, remainingMillis = 0, busy = false, selected = null, versions = emptyList(),
            choice = null, exitConfirmation = false, message = "업무를 종료했습니다. 로컬 보관본·Waiting·Realtime 연결은 유지됩니다.") }
    }

    /** For a host-owned remember instance; Android ViewModelStore invokes onCleared itself. */
    fun close() { setForeground(false); viewModelScope.cancel() }
    override fun onCleared() { setForeground(false); super.onCleared() }

    private fun requireSession() { check(state.value.session?.remaining(now())?.let { it > 0 } == true) { "WORK_START_REQUIRED" } }
    private fun checkVisible() {
        check(foreground.value && safety.state.value.canUse && documents.access.value.allowed) { "WORK_ACCESS_BLOCKED" }
    }
    private fun command(block: suspend () -> Unit) {
        if (stopping || state.value.busy) return
        val token = generation
        // Claim synchronously: two taps before the first coroutine dispatch must not both start.
        mutable.update { it.copy(busy = true) }
        operation = viewModelScope.launch {
            try { checkVisible(); check(token == generation); block() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                // Never show raw server errors (possibly containing names/tokens/content).
                if (token == generation) mutable.update { it.copy(message = "작업을 완료하지 못했습니다. 연결·권한·문서 상태를 확인한 뒤 다시 시도해 주세요.") }
            } finally { if (token == generation) mutable.update { it.copy(busy = false) } }
        }
    }
}
