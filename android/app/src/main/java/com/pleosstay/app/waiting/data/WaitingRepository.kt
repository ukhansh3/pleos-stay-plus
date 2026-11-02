package com.pleosstay.app.waiting.data

import com.pleosstay.app.waiting.domain.*
import com.pleosstay.app.waiting.safety.WaitingSafety
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class WaitingRefreshException(cause: Exception) : IllegalStateException("저장 상태 재조회 실패. 변경을 반복하지 말고 복구를 기다려 주세요", cause)

interface WaitingStore {
    fun all(): List<WaitingSession>
    fun create(session: WaitingSession)
    fun extend(session: WaitingSession, expectedRevision: Int)
    fun end(id: String, at: Long, reason: EndReason)
}

data class PendingDrivingEnd(val sessionId: String, val observedAt: Long)
interface DrivingEndJournal {
    fun read(): PendingDrivingEnd?
    fun write(command: PendingDrivingEnd)
    fun clear()
}

interface WaitingRepository {
    val sessions: StateFlow<List<WaitingSession>>
    suspend fun restore()
    suspend fun start(minutes: Int, place: WaitingPlace? = null)
    suspend fun extend(id: String, minutes: Int)
    suspend fun end(id: String)
    suspend fun onDrivingConfirmed(observedAt: Long)
    suspend fun retryPendingEnd()
}

class LocalWaitingRepository(
    private val store: WaitingStore,
    private val journal: DrivingEndJournal,
    private val safety: () -> WaitingSafety,
    private val clock: WaitingClock = WaitingClock(System::currentTimeMillis),
    private val policy: WaitingPolicy = WaitingPolicy(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : WaitingRepository {
    private val mutableSessions = MutableStateFlow<List<WaitingSession>>(emptyList())
    override val sessions = mutableSessions.asStateFlow()
    private val mutex = Mutex()
    // Retained even if journal write fails; a second independent storage failure is surfaced.
    private var pending: PendingDrivingEnd? = null

    private suspend fun <T> locked(action: () -> T): T = withContext(dispatcher) { mutex.withLock { action() } }
    private var refreshRequired = false
    private fun publish() {
        try {
            mutableSessions.value = store.all()
            refreshRequired = false
        } catch (error: Exception) {
            refreshRequired = true
            throw WaitingRefreshException(error)
        }
    }
    private fun requireUsable() {
        check(!refreshRequired) { "저장된 상태를 다시 확인한 뒤 조작해 주세요" }
        check(pending == null && journal.read() == null) { "주행 종료 기록을 복구한 뒤 다시 시도해 주세요" }
        check(safety().canUse) { "주차 상태 및 플랫폼 사용 허용을 확인할 수 없습니다" }
    }
    override suspend fun restore() = locked {
        pending = pending ?: journal.read()
        applyPending()
        publish()
    }
    override suspend fun start(minutes: Int, place: WaitingPlace?) = locked {
        requireUsable()
        check(store.all().none { it.status == WaitingStatus.ACTIVE }) { "이미 대기 중입니다" }
        val session = policy.start(minutes, clock.now(), place)
        requireUsable()
        store.create(session)
        publish()
    }
    override suspend fun extend(id: String, minutes: Int) = locked {
        requireUsable()
        val session = store.all().firstOrNull { it.id == id } ?: error("세션을 찾을 수 없습니다")
        store.extend(policy.extend(session, minutes), session.revision)
        publish()
    }
    override suspend fun end(id: String) = locked {
        requireUsable()
        val session = store.all().firstOrNull { it.id == id } ?: error("세션을 찾을 수 없습니다")
        val ended = policy.end(session, clock.now(), EndReason.MANUAL)
        if (session.status == WaitingStatus.ACTIVE) store.end(id, ended.endedAt!!, EndReason.MANUAL)
        publish()
    }
    override suspend fun onDrivingConfirmed(observedAt: Long) = locked {
        // Journal the observation even if the DB cannot currently be read.
        // Empty session ID means resolve the active session that existed at observation time.
        pending = pending ?: journal.read() ?: PendingDrivingEnd("", observedAt)
        applyPending()
        publish()
    }
    override suspend fun retryPendingEnd() = locked {
        pending = pending ?: journal.read()
        applyPending()
        publish()
    }
    private fun applyPending() {
        val command = pending ?: return
        // Write-ahead command preserves the observation time across process death.
        journal.write(command)
        val session = if (command.sessionId.isEmpty()) {
            store.all().firstOrNull { it.status == WaitingStatus.ACTIVE && it.startedAt <= command.observedAt }
        } else {
            store.all().firstOrNull { it.id == command.sessionId }
                ?: error("주행 종료 대상 기록이 없습니다. 복구가 필요합니다")
        }
        if (session == null) {
            journal.clear()
            pending = null
            return
        }
        // Bind the retry to this ID before ending; never affect a later session.
        journal.write(command.copy(sessionId = session.id))
        val ended = policy.end(session, command.observedAt, EndReason.DRIVING)
        if (session.status == WaitingStatus.ACTIVE) store.end(session.id, ended.endedAt!!, EndReason.DRIVING)
        journal.clear()
        pending = null
    }
}


