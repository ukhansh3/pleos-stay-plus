package com.pleosstay.app.modes

import java.util.UUID
import kotlinx.coroutines.withContext
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex

internal class ActivityExecution(val mode:ActivityMode):AbstractCoroutineContextElement(Key) {
    companion object Key:CoroutineContext.Key<ActivityExecution>
}

data class ActivityOperation(val success: Boolean, val message: String = "", val needsInput:Boolean=false) {
    companion object { val Done = ActivityOperation(true) }
}

/** Implementations must not call the coordinator from inside these methods. */
interface ActivityAdapter {
    val mode: ActivityMode
    fun sessionId(): String?
    /** A persisted activity must finish recovery before another activity can start. */
    fun restored():Boolean = true
    /** No Waiting writes, resource acquisition, or playback here. */
    suspend fun preflight(): ActivityOperation
    suspend fun start(): ActivityOperation
    suspend fun resume(): ActivityOperation
    /** Return success only after retained data and resource cleanup are confirmed. */
    suspend fun stop(): ActivityOperation
}

data class ActivityTransitionState(val active: ActivityMode? = null, val transitioning: Boolean = false,
    val confirmation: String? = null, val error: String? = null)
enum class TransitionStatus { STARTED, RESUMED, NEEDS_INPUT, CONFIRMATION_REQUIRED, CANCELLED, STOPPED, BUSY, STALE, BLOCKED, STOP_FAILED, START_FAILED }
data class ActivityTransitionResult(val status: TransitionStatus, val state: ActivityTransitionState, val message: String = "")

/** One host owns this and its adapters. It never owns or ends Waiting/Pickup/Realtime. */
class ActivityTransitionCoordinator(adapterList: List<ActivityAdapter>,
    private val now: () -> Long = { System.nanoTime()/1_000_000L },
    private val confirmationLifetimeMillis: Long = 60_000,
) {
    private val adapters = adapterList.associateBy { it.mode }
    private val mutex = Mutex()
    private val mutable = MutableStateFlow(ActivityTransitionState())
    val state = mutable.asStateFlow()
    /** Live session query, including natural expiry outside a transition command. */
    fun currentState()=state.value.copy(active=failedStop ?: sessions().keys.singleOrNull())
    private data class Approval(val token: String, val source: Map<ActivityMode, String>, val target: ActivityMode, val expires: Long)
    private var pending: Approval? = null
    @Volatile private var failedStop: ActivityMode? = null
    init { require(this.adapters.size == adapterList.size); require(confirmationLifetimeMillis > 0) }
    private fun sessions() = adapters.mapNotNull { (mode, adapter) -> adapter.sessionId()?.let { mode to it } }.toMap()
    private fun result(status: TransitionStatus, message: String = "") = ActivityTransitionResult(status, state.value.copy(active=failedStop ?: sessions().keys.singleOrNull(),transitioning=false,confirmation=pending?.token), message)
    private suspend fun guarded(action: suspend () -> ActivityTransitionResult): ActivityTransitionResult {
        if (!mutex.tryLock()) return ActivityTransitionResult(TransitionStatus.BUSY,currentState())
        try {
            if(adapters.values.any { !it.restored() }) return result(TransitionStatus.BLOCKED,"이전 활동 복구를 먼저 완료해 주세요.")
            val running = sessions()
            if (running.size > 1) return result(TransitionStatus.BLOCKED, "여러 기존 활동이 실행 중입니다. 먼저 종료해 주세요.")
            mutable.value = state.value.copy(active = failedStop ?: running.keys.singleOrNull(), transitioning = true, error = null)
            return action()
        } catch (cancelled: CancellationException) { pending = null; failedStop=sessions().keys.singleOrNull() ?: state.value.active;throw cancelled }
        catch (_: Exception) {
            pending = null
            mutable.value = state.value.copy(error = "활동 상태를 확인하지 못했습니다. 다시 확인해 주세요.")
            return result(TransitionStatus.BLOCKED, state.value.error!!)
        } finally {
            try { mutable.value = state.value.copy(active = failedStop ?: sessions().keys.singleOrNull(), transitioning = false, confirmation = pending?.token) }
            finally { mutex.unlock() }
        }
    }
    private suspend fun checked(block: suspend () -> ActivityOperation): ActivityOperation = try { block() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { ActivityOperation(false, "활동 작업을 완료하지 못했습니다.") }

    suspend fun request(target: ActivityMode): ActivityTransitionResult = guarded {
        val adapter = adapters[target] ?: return@guarded result(TransitionStatus.BLOCKED, "연결되지 않은 활동입니다.")
        pending = null
        if (failedStop!=null && !(failedStop==target && adapter.sessionId()!=null)) return@guarded result(TransitionStatus.STOP_FAILED,"이전 종료를 다시 확인해 주세요.")
        if (state.value.active == target) {
            val resumed = checked { adapter.resume() }
            return@guarded result(if (resumed.success) TransitionStatus.RESUMED else TransitionStatus.BLOCKED, resumed.message)
        }
        val ready = checked { adapter.preflight() }
        if (!ready.success) return@guarded result(TransitionStatus.BLOCKED, ready.message)
        val running = sessions()
        if (running.isNotEmpty()) {
            val approval = Approval(UUID.randomUUID().toString(), running, target, now() + confirmationLifetimeMillis)
            pending = approval
            mutable.value = state.value.copy(confirmation = approval.token)
            result(TransitionStatus.CONFIRMATION_REQUIRED)
        } else start(adapter)
    }

    suspend fun confirm(token: String, accepted: Boolean): ActivityTransitionResult = guarded {
        val approval = pending
        if (approval == null || approval.token != token) return@guarded result(TransitionStatus.STALE)
        pending = null
        if (now() >= approval.expires || approval.source != sessions()) return@guarded result(TransitionStatus.STALE)
        if (!accepted) return@guarded result(TransitionStatus.CANCELLED)
        val target = adapters.getValue(approval.target)
        val ready = checked { target.preflight() }
        if (!ready.success) return@guarded result(TransitionStatus.BLOCKED, ready.message)
        val old = adapters.getValue(approval.source.keys.single())
        val stopped = checked { old.stop() }
        if (!stopped.success || old.sessionId() != null) {
            failedStop=old.mode
            return@guarded result(TransitionStatus.STOP_FAILED, stopped.message)
        }
        start(target)
    }

    private suspend fun start(adapter: ActivityAdapter): ActivityTransitionResult {
        if(sessions().isNotEmpty()) return result(TransitionStatus.BLOCKED,"다른 활동이 실행 중입니다.")
        val ready = checked { adapter.preflight() }
        if (!ready.success) return result(TransitionStatus.BLOCKED, ready.message)
        val started = checked { withContext(ActivityExecution(adapter.mode)) { adapter.start() } }
        mutable.value = state.value.copy(active = sessions().keys.singleOrNull())
        return result(if (started.success && adapter.sessionId() != null) TransitionStatus.STARTED else if(started.needsInput) TransitionStatus.NEEDS_INPUT else TransitionStatus.START_FAILED, started.message)
    }

    suspend fun end(): ActivityTransitionResult = guarded {
        pending = null
        val adapter = state.value.active?.let(adapters::getValue) ?: return@guarded result(TransitionStatus.STOPPED)
        val stopped = checked { adapter.stop() }
        val success=stopped.success && adapter.sessionId()==null
        failedStop=if(success)null else adapter.mode
        result(if(success) TransitionStatus.STOPPED else TransitionStatus.STOP_FAILED, stopped.message)
    }
}
