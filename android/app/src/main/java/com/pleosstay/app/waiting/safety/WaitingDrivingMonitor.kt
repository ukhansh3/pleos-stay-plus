package com.pleosstay.app.waiting.safety

import com.pleosstay.app.waiting.data.WaitingRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class DrivingRecoveryState(val pending: Boolean = false, val error: String? = null)

/** The production owner is the app process, not an Activity: platform UI hiding must not cancel saving. */
class WaitingDrivingMonitor(
    private val repository: WaitingRepository,
    private val events: DrivingEventBuffer,
    scope: CoroutineScope,
) {
    private val mutex = Mutex()
    private val mutableState = MutableStateFlow(DrivingRecoveryState())
    val state = mutableState.asStateFlow()
    init {
        scope.launch { events.state.collect { drain() } }
        scope.launch {
            while (isActive) { delay(1_000); drain() }
        }
    }
    private suspend fun drain() = mutex.withLock {
        while (true) {
            val event = events.state.value.firstOrNull() ?: break
            mutableState.value = DrivingRecoveryState(pending = true)
            try {
                repository.onDrivingConfirmed(event.observedAt)
                events.acknowledge(event.id)
                mutableState.value = DrivingRecoveryState()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                mutableState.value = DrivingRecoveryState(true, "주행 종료 기록 저장 실패: 자동 재시도 중입니다")
                break
            }
        }
    }
}
