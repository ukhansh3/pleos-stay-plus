package com.pleosstay.app.study

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pleosstay.app.documents.*
import com.pleosstay.app.modes.*
import com.pleosstay.app.waiting.safety.WaitingSafetyGateway
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class StudyUiState(
    val active: Boolean = false, val remainingMillis: Long = 0, val allowed: Boolean = false,
    val busy: Boolean = false, val message: String = "학습 시간을 선택하고 시작해 주세요.",
    val prompt: ModeStartResult.NeedsChoice? = null,
    val files: List<DocumentFile> = emptyList(), val versions: List<DocumentRef> = emptyList(),
    val selected: DocumentRef? = null, val document: StudyDocument? = null,
    val record: StudyRecord? = null, val position: Int = 0, val zoom: Float = 1f,
    val page: Bitmap? = null, val noteSaveSerial: Long = 0,
    val noteText: String = "", val editId: String? = null,
)

/** UI owns rendering only. Every retained/source read requires fresh shared authorization. */
class StudyViewModel(
    private val modeStartManager: ModeStartManager,
    private val safety: WaitingSafetyGateway,
    private val documents: SharedDocumentGateway,
    private val repository: StudyRepository,
    private val reader: StudyReader = NativeStudyReader(),
) : ViewModel() {
    private val mutable = MutableStateFlow(StudyUiState())
    val state: StateFlow<StudyUiState> = mutable.asStateFlow()
    private var foreground = false
    private var generation = 0L
    private var operation: Job? = null
    private var stopping=false
    private var deadline: Long? = null
    private val noteDrafts = StudyNoteDrafts()
    private var draftWrite:Job?=null
    private var draftWriteFailed=false
    private fun retainDraft(key:StudyMaterialKey,draft:StudyNoteDraft?) {
        val previous=draftWrite
        draftWrite=viewModelScope.launch {
            previous?.join()
            try { withContext(NonCancellable) { repository.saveDraft(key,draft) };draftWriteFailed=false }
            catch(_:Exception) { draftWriteFailed=true;mutable.update { it.copy(message="메모 초안 저장에 실패했습니다. 입력은 유지됩니다.") } }
        }
    }
    @Volatile private var sessionToken:String?=null
    val sessionId: String? get() = sessionToken
    private var lastAccess = documents.access.value
    init {
        viewModelScope.launch {
            safety.state.collect { if (!it.canUse) mask(it.message) else updateAllowed() }
        }
        viewModelScope.launch {
            documents.access.collect { access ->
                if (access != lastAccess) {
                    lastAccess = access
                    mask(if (access.allowed) "자료함 권한이 변경되었습니다. 자료를 다시 열어 주세요." else "자료함 인증 또는 PIN 잠금을 확인해 주세요.")
                }
                updateAllowed()
            }
        }
        viewModelScope.launch {
            while (isActive) {
                delay(1000)
                deadline?.let { target ->
                    val remaining = (target - System.currentTimeMillis()).coerceAtLeast(0)
                    mutable.update { it.copy(remainingMillis = remaining) }
                    if (remaining == 0L) end()
                }
            }
        }
    }
    private fun canUse() = foreground && safety.state.value.canUse && documents.access.value.allowed
    private fun updateAllowed() { mutable.update { it.copy(allowed = canUse()) } }
    fun setForeground(value: Boolean) {
        foreground = value
        if (!value) mask("화면을 벗어나 학습자료를 잠갔습니다.") else updateAllowed()
    }
    /** Parent calls on route exit; source and private notes are never retained on another screen. */
    fun leave() { end(); setForeground(false) }
    fun updateNoteDraft(id: String?, body: String) {
        if (stopping || !canUse()) return
        val ref = state.value.selected ?: return
        if(body.toByteArray(Charsets.UTF_8).size>EncryptedStudyRepository.MAX_TEXT_BYTES) {
            mutable.update { it.copy(message="메모 길이 제한을 초과했습니다. 기존 입력은 유지됩니다.") };return
        }
        val old = noteDrafts.get(ref.materialKey())
        val draft = StudyNoteDraft(id ?: old?.id ?: java.util.UUID.randomUUID().toString(), body)
        noteDrafts.put(ref.materialKey(), draft)
        draftRefs[ref.materialKey()] = ref
        retainDraft(ref.materialKey(),draft)
        mutable.update { it.copy(noteText = body, editId = id) }
    }
    fun cancelNoteDraft() {
        if(stopping) return
        val ref=state.value.selected ?: return
        if (!canUse()) return
        noteDrafts.remove(ref.materialKey());draftRefs.remove(ref.materialKey())
        retainDraft(ref.materialKey(),null)
        mutable.update { it.copy(noteText="",editId=null) }
    }
    suspend fun transitionPreflight(): ActivityOperation {
        if(!foreground || !safety.state.value.canUse) return ActivityOperation(false,"정차 상태와 활성 화면을 확인해 주세요.")
        val access=documents.refreshAccess()
        return ActivityOperation(access.allowed && canUse(),"자료함 인증·PIN을 확인해 주세요.")
    }
    suspend fun startForTransition(minutes: Int, choice:WaitingChoice?=null): ActivityOperation {
        if (state.value.active) return ActivityOperation.Done
        if (stopping || operation?.isActive == true) return ActivityOperation(false,"학습 작업 중입니다.")
        check(canUse())
        val ticket=generation
        check(documents.refreshAccess().allowed)
        val prompt=state.value.prompt
        if(prompt!=null && choice==null) return ActivityOperation(false,state.value.message,true)
        val result=if(prompt!=null) modeStartManager.resolve(prompt,checkNotNull(choice)) else modeStartManager.start(ModeStartRequest(ActivityMode.STUDY,minutes))
        applyModeResult(result,ticket)
        return ActivityOperation(state.value.active,state.value.message,state.value.prompt!=null)
    }
    suspend fun stopForTransition(): ActivityOperation {
        if(stopping) return ActivityOperation(false,"학습 종료 중입니다.")
        stopping=true
        mutable.update { it.copy(busy=true) }
        try {
            operation?.cancelAndJoin()
            draftWrite?.join()
            if(draftRefs.isEmpty()) {
                if(draftWriteFailed) return ActivityOperation(false,"메모 초안 보관 결과를 확인해 주세요.")
                endNow();return ActivityOperation.Done
            }
            // Validate every material again. No off-screen/offline note access is granted.
            val access=documents.refreshAccess()
            for(ref in draftRefs.values.toList()) {
                val key=ref.materialKey()
                val draft=noteDrafts.get(key)
                if(draft==null) { draftRefs.remove(key);continue }
                if(draft.body.isNotBlank()) {
                    check(access.allowed && access.userScope==ref.userScope && access.connectionId==ref.connectionId && safety.state.value.canUse)
                    check(documents.validate(ref))
                    repository.saveNote(key,draft.id,null,draft.body)
                    mutable.update { if(it.selected?.materialKey()==key) it.copy(editId=draft.id) else it }
                }
                repository.saveDraft(key,null)
                noteDrafts.remove(key);draftRefs.remove(key)
            }
            endNow()
            return ActivityOperation.Done
        } catch(c:CancellationException){throw c}
        catch(_:Exception){return ActivityOperation(false,"작성 중 메모를 저장하지 못했습니다. 학습을 유지합니다.")}
        finally { stopping=false;mutable.update { it.copy(busy=false) } }
    }
    private val draftRefs = mutableMapOf<StudyMaterialKey,DocumentRef>()
    private fun mask(message: String) {
        generation++
        operation?.cancel(); operation = null
        // Compose releases its image reference at this state transition; do not recycle a drawn bitmap.
        mutable.update { StudyUiState(active = it.active, remainingMillis = it.remainingMillis, message = message) }
        documents.clearTemporaryFiles()
    }
    private fun operation(block: suspend (Long) -> Unit) {
        if (stopping || operation?.isActive == true) return
        operation = viewModelScope.launch {
            try {
                if (!foreground || !safety.state.value.canUse) { mask("정차 상태와 화면 잠금을 확인해 주세요."); return@launch }
                val access = try { documents.refreshAccess() }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { mask("자료함 권한을 재확인할 수 없어 문서를 잠갔습니다."); return@launch }
                if (!access.allowed || !canUse()) { mask("자료함 인증 또는 PIN 잠금을 확인해 주세요."); return@launch }
                val ticket = generation
                mutable.update { it.copy(busy = true, allowed = true) }
                block(ticket)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                if (canUse()) mutable.update { it.copy(message = "학습 작업에 실패했습니다. 연결·파일·저장공간을 확인하고 다시 시도해 주세요.") }
                else mask("학습자료 접근이 차단되었습니다.")
            } finally { mutable.update { it.copy(busy = false) } }
        }
    }
    private fun checkTicket(ticket: Long) { check(ticket == generation && canUse()) { "STUDY_LOCKED" } }
    private suspend fun authorized(ref: DocumentRef, ticket: Long) {
        checkTicket(ticket)
        val valid = try { documents.validate(ref) } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { false }
        if (!valid) { mask("자료 접근 권한을 확인할 수 없어 문서를 잠갔습니다."); throw CancellationException("STUDY_ACCESS_REVOKED") }
        checkTicket(ticket)
    }
    fun start(minutes: Int) {
        if (mutable.value.active) return
        operation { ticket -> applyModeResult(modeStartManager.start(ModeStartRequest(ActivityMode.STUDY, minutes)), ticket) }
    }
    fun choose(choice: WaitingChoice) {
        val prompt = mutable.value.prompt ?: return
        operation { ticket -> applyModeResult(modeStartManager.resolve(prompt, choice), ticket) }
    }
    private fun applyModeResult(result: ModeStartResult, ticket: Long) {
        checkTicket(ticket)
        when (result) {
            is ModeStartResult.Ready -> {
                sessionToken=java.util.UUID.randomUUID().toString()
                deadline = result.endsAt ?: System.currentTimeMillis() + result.durationMillis
                mutable.update { it.copy(active = true, remainingMillis = result.durationMillis, prompt = null, message = "자료함에서 자료를 선택해 주세요.") }
            }
            is ModeStartResult.NeedsChoice -> mutable.update { it.copy(prompt = result, message = "Waiting 시간보다 학습 시간이 깁니다. 연장 또는 단축을 선택해 주세요.") }
            is ModeStartResult.SafetyBlocked -> mask(result.message)
            is ModeStartResult.RecoveryRequired -> mutable.update { it.copy(prompt = null, message = result.message) }
            is ModeStartResult.StorageError -> mutable.update { it.copy(prompt = null, message = "Waiting 저장에 실패했습니다. Waiting 화면에서 복구 후 다시 시도해 주세요.") }
            is ModeStartResult.InvalidDuration -> mutable.update { it.copy(prompt = null, message = "허용 학습 시간: ${result.allowedMinutes.first}~${result.allowedMinutes.last}분") }
            is ModeStartResult.WaitingChanged -> mutable.update { it.copy(prompt = null, message = "Waiting이 변경되었습니다. 다시 시작해 주세요.") }
            is ModeStartResult.Cancelled -> mutable.update { it.copy(prompt = null, message = "학습 시작을 취소했습니다.") }
            is ModeStartResult.DecisionAlreadyHandled -> mutable.update { it.copy(prompt = null, message = "이미 처리한 시간 선택입니다.") }
        }
    }
    fun end() {
        // Preserve immediate legacy end/restart when there is no unsaved note to protect.
        if(draftRefs.isEmpty() && draftWrite?.isActive!=true && !draftWriteFailed) endNow() else viewModelScope.launch { stopForTransition() }
    }
    private fun endNow() {
        draftWriteFailed=false
        sessionToken=null
        deadline = null
        mask("학습을 종료했습니다. 기존 Waiting은 유지됩니다.")
        mutable.update { it.copy(active = false, remainingMillis = 0, allowed = canUse()) }
    }
    fun refreshFiles() = operation { ticket ->
        check(mutable.value.active)
        val files = documents.listFiles(); checkTicket(ticket)
        mutable.update { it.copy(files = files, versions = emptyList(), message = if (files.isEmpty()) "자료함이 비어 있습니다." else "파일과 버전을 선택해 주세요.") }
    }
    fun selectFile(file: DocumentFile) = operation { ticket ->
        check(mutable.value.active)
        val access = documents.access.value
        check(file.userScope == access.userScope && file.connectionId == access.connectionId)
        val versions = documents.listVersions(file.fileId); checkTicket(ticket)
        mutable.update { it.copy(versions = versions, message = if (versions.isEmpty()) "열 수 있는 파일 버전이 없습니다." else "열 버전을 선택해 주세요.") }
    }
    fun open(ref: DocumentRef) = operation { ticket -> openInternal(ref, ticket) }
    private suspend fun openInternal(ref: DocumentRef, ticket: Long) {
        check(mutable.value.active); authorized(ref, ticket)
        when (val result = documents.downloadVerified(ref)) {
            is DocumentOpenResult.Ready -> {
                val document = reader.open(result.file, ref.mimeType)
                authorized(ref, ticket)
                var record = repository.load(ref.materialKey())
                authorized(ref, ticket)
                val position = restorePosition(document, record.progress)
                val bitmap = if (document.format == StudyFormat.PDF) reader.renderPage(document, position, 1f) else null
                authorized(ref, ticket)
                if (record.progress == null) {
                    val initial = progress(document, position, false)
                    repository.saveProgress(ref.materialKey(), initial)
                    record = record.copy(progress = initial)
                    authorized(ref, ticket)
                }
                val draft=noteDrafts.get(ref.materialKey()) ?: record.draft
                if(draft!=null) { noteDrafts.put(ref.materialKey(),draft);draftRefs[ref.materialKey()]=ref }
                mutable.update { it.copy(selected = ref, document = document, record = record, position = position, zoom = 1f, page = bitmap,
                    noteText=draft?.body.orEmpty(),editId=draft?.id, message = document.warning ?: "자료를 열었습니다.") }
            }
            is DocumentOpenResult.Blocked -> mask("자료 접근 권한이 없습니다. 자료함을 확인해 주세요.")
            is DocumentOpenResult.Failed -> mutable.update { it.copy(message = "파일 다운로드 또는 해시 검증에 실패했습니다.") }
        }
    }
    fun restoreLast() = operation { ticket ->
        check(mutable.value.active)
        val access = documents.access.value
        val key = repository.lastMaterial(checkNotNull(access.userScope), checkNotNull(access.connectionId))
        checkTicket(ticket)
        if (key == null) { mutable.update { it.copy(message = "복원할 학습 기록이 없습니다.") }; return@operation }
        val ref = documents.listVersions(key.fileId).firstOrNull { it.materialKey() == key }
        checkTicket(ticket)
        if (ref == null) mutable.update { it.copy(message = "이전 버전에 접근할 수 없습니다. 자료함에서 다시 선택해 주세요.") }
        else openInternal(ref, ticket)
    }
    fun move(position: Int, zoom: Float = mutable.value.zoom) = operation { ticket ->
        val old = mutable.value; val ref = old.selected ?: return@operation
        val document = old.document ?: return@operation
        authorized(ref, ticket)
        val bounded = position.coerceIn(0, (document.pageCount ?: document.blocks.size) - 1)
        val magnification = zoom.coerceIn(.75f, 3f)
        val bitmap = if (document.format == StudyFormat.PDF) reader.renderPage(document, bounded, magnification) else null
        authorized(ref, ticket)
        val progress = progress(document, bounded, old.record?.progress?.completed ?: false)
        repository.saveProgress(ref.materialKey(), progress)
        authorized(ref, ticket)
        mutable.update { it.copy(position = bounded, zoom = magnification, page = bitmap, record = it.record?.copy(progress = progress)) }
    }
    fun saveNote(id: String?, body: String) = recordOperation { ref, ticket ->
        draftWrite?.join()
        val current = mutable.value
        val anchor = current.document?.let { progress(it, current.position, false).anchor }
        val draft=noteDrafts.get(ref.materialKey())
        repository.saveNote(ref.materialKey(), id ?: draft?.id, anchor, body)
        reloadRecord(ref, ticket)
        val latest=noteDrafts.get(ref.materialKey())
        if(latest==null || (latest.id==(id ?: draft?.id) && latest.body==body)) {
            noteDrafts.remove(ref.materialKey());draftRefs.remove(ref.materialKey())
            mutable.update { it.copy(noteSaveSerial = it.noteSaveSerial + 1,noteText="",editId=null) }
        }
    }
    fun deleteNote(id: String) = recordOperation { ref, ticket -> repository.deleteNote(ref.materialKey(), id); reloadRecord(ref, ticket) }
    fun complete(value: Boolean) = recordOperation { ref, ticket ->
        val current = mutable.value; val document = current.document ?: return@recordOperation
        repository.saveProgress(ref.materialKey(), progress(document, current.position, value)); reloadRecord(ref, ticket)
    }
    fun deleteLocalData() = recordOperation { ref, ticket -> repository.deleteMaterialData(ref.materialKey()); reloadRecord(ref, ticket) }
    private fun recordOperation(block: suspend (DocumentRef, Long) -> Unit) = operation { ticket ->
        val ref = mutable.value.selected ?: return@operation
        authorized(ref, ticket); block(ref, ticket)
    }
    private suspend fun reloadRecord(ref: DocumentRef, ticket: Long) {
        val record = repository.load(ref.materialKey()); authorized(ref, ticket)
        mutable.update { it.copy(record = record, message = "학습 기록을 저장했습니다.") }
    }
    override fun onCleared() { operation?.cancel(); documents.clearTemporaryFiles(); super.onCleared() }
    fun close() { noteDrafts.clear(); foreground = false; deadline = null; mask("학습자료를 잠갔습니다."); viewModelScope.cancel() }
}

internal fun DocumentRef.materialKey() = StudyMaterialKey(userScope, connectionId, fileId, versionId)
internal fun restorePosition(document: StudyDocument, saved: StudyProgress?): Int {
    if (saved == null) return 0
    val index = if (document.format == StudyFormat.TXT) document.textOffsets.indexOf(saved.anchor.toIntOrNull()) else saved.anchor.toIntOrNull() ?: 0
    return index.coerceIn(0, ((document.pageCount ?: document.blocks.size) - 1).coerceAtLeast(0))
}
private fun progress(document: StudyDocument, position: Int, completed: Boolean): StudyProgress {
    val kind = when (document.format) { StudyFormat.PDF -> "PDF_PAGE"; StudyFormat.DOCX -> "DOCX_BLOCK"; StudyFormat.TXT -> "TXT_OFFSET" }
    val anchor = if (document.format == StudyFormat.TXT) document.textOffsets[position] else position
    val count = document.pageCount ?: document.blocks.size
    return StudyProgress(kind, anchor.toString(), (position + 1).toFloat() / count, completed, System.currentTimeMillis())
}
