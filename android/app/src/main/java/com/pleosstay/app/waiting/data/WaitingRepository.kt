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

internal class WaitingWriteUncertainException(cause: Exception) :
    IllegalStateException("대기 저장 결과를 확인하지 못했습니다. 다시 시도하기 전에 기록을 복구해 주세요", cause)

internal class WaitingSafetyException(message: String) : IllegalStateException(message)
internal class WaitingRecoveryRequiredException(message: String) : IllegalStateException(message)

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
    private var restored = false
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
        val state = safety()
        if (!state.canUse) throw WaitingSafetyException(state.message)
        if (refreshRequired || pending != null || journal.read() != null) {
            throw WaitingRecoveryRequiredException("대기 기록을 복구한 뒤 다시 시도해 주세요")
        }
    }
    override suspend fun restore() = recover()
    override suspend fun start(minutes: Int, place: WaitingPlace?) = locked {
        requireUsable()
        check(store.all().none { it.status == WaitingStatus.ACTIVE }) { "이미 대기 중입니다" }
        create(minutes, place)
        Unit
    }
    override suspend fun extend(id: String, minutes: Int) = locked {
        requireUsable()
        val session = store.all().firstOrNull { it.id == id } ?: error("세션을 찾을 수 없습니다")
        extend(session, minutes)
        Unit
    }

    internal val modeDurationRange get() = policy.minimumMinutes..policy.maximumMinutes

    // Mode decisions share the same lock as manual starts, extensions and driving-end recovery.
    internal suspend fun <T> withModeWaiting(action: ModeWaitingScope.() -> T): T = locked {
        requireUsable()
        if (!restored) throw WaitingRecoveryRequiredException("대기 기록 복구가 필요합니다")
        val active = store.all().firstOrNull { it.status == WaitingStatus.ACTIVE }
        ModeWaitingScope(active).action().also { requireUsable() }
    }

    internal inner class ModeWaitingScope internal constructor(val active: WaitingSession?) {
        fun now(): Long = clock.now()
        fun create(minutes: Int, place: WaitingPlace?): WaitingSession =
            this@LocalWaitingRepository.create(minutes, place, ::saveModeWrite)

        fun extend(session: WaitingSession, minutes: Int): WaitingSession =
            this@LocalWaitingRepository.extend(session, minutes, ::saveModeWrite)
    }

    private fun create(
        minutes: Int,
        place: WaitingPlace?,
        write: (() -> Unit) -> Unit = { it() },
    ): WaitingSession {
        val session = policy.start(minutes, clock.now(), place)
        requireUsable()
        write { store.create(session) }
        publish()
        return mutableSessions.value.single { it.id == session.id }
    }

    private fun extend(
        session: WaitingSession,
        minutes: Int,
        write: (() -> Unit) -> Unit = { it() },
    ): WaitingSession {
        val updated = policy.extend(session, minutes)
        requireUsable()
        write { store.extend(updated, session.revision) }
        publish()
        return mutableSessions.value.single { it.id == session.id }
    }

    private fun saveModeWrite(write: () -> Unit) {
        try {
            write()
        } catch (error: Exception) {
            // Reconcile an uncertain outcome before accepting another write.
            refreshRequired = true
            throw WaitingWriteUncertainException(error)
        }
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
    override suspend fun retryPendingEnd() = recover()

    private suspend fun recover() = locked {
        restored = false
        pending = pending ?: journal.read()
        applyPending()
        publish()
        restored = true
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


