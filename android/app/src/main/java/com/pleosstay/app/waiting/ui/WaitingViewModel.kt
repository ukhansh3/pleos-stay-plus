package com.pleosstay.app.waiting.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pleosstay.app.waiting.data.WaitingRefreshException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.pleosstay.app.waiting.data.WaitingRepository
import com.pleosstay.app.waiting.domain.*
import com.pleosstay.app.waiting.safety.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class WaitingUiState(
    val sessions: List<WaitingSession> = emptyList(),
    val safety: WaitingSafety = WaitingSafety(),
    val now: Long = 0,
    val ready: Boolean = false,
    val busy: Boolean = false,
    val error: String? = null,
    val recoveryRequired: Boolean = false,
) {
    val active: WaitingSession? get() = sessions.firstOrNull { it.status == WaitingStatus.ACTIVE }
    val canUse: Boolean get() = ready && safety.canUse && !recoveryRequired
}

class WaitingViewModel(
    private val repository: WaitingRepository,
    private val safetyGateway: WaitingSafetyGateway,
    private val clock: WaitingClock = WaitingClock(System::currentTimeMillis),
) : ViewModel() {
    private val drivingMutex = Mutex()
    private val mutableState = MutableStateFlow(WaitingUiState(now = clock.now()))
    val state = mutableState.asStateFlow()
    init {
        viewModelScope.launch { repository.sessions.collect { mutableState.value = mutableState.value.copy(sessions = it) } }
        viewModelScope.launch {
            safetyGateway.state.collect { safety ->
                // Hide/disable UI first, before any suspending DB work.
                mutableState.value = mutableState.value.copy(safety = safety)
                if (safety.motion == VehicleMotion.DRIVING && safety.observedAt == null) {
                    mutableState.value = mutableState.value.copy(error = "주행 확인 시각이 없어 자동 종료를 보류합니다")
                }
            }
        }
        viewModelScope.launch {
            safetyGateway.drivingEvents.state.collect { drainDrivingEvents() }
        }
        reload()
        viewModelScope.launch {
            while (true) {
                delay(1_000)
                mutableState.value = mutableState.value.copy(now = clock.now())
                if (mutableState.value.recoveryRequired) performRecovery { repository.retryPendingEnd() }
                drainDrivingEvents()
            }
        }
    }
    private suspend fun drainDrivingEvents() = drivingMutex.withLock {
        while (true) {
            val event = safetyGateway.drivingEvents.state.value.firstOrNull() ?: break
            mutableState.value = mutableState.value.copy(recoveryRequired = true)
            if (!performRecovery { repository.onDrivingConfirmed(event.observedAt) }) break
            safetyGateway.drivingEvents.acknowledge(event.id)
        }
    }
    fun reload() {
        viewModelScope.launch { performRecovery { repository.restore() } }
    }
    private suspend fun performRecovery(block: suspend () -> Unit): Boolean {
        try {
            block()
            mutableState.value = mutableState.value.copy(ready = true, recoveryRequired = false, error = null)
            return true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            mutableState.value = mutableState.value.copy(recoveryRequired = true, error = "복구/저장 실패: ${error.message}. 재시도 중입니다")
            return false
        }
    }
    fun start(minutes: Int, place: WaitingPlace?) = action { repository.start(minutes, place) }
    fun extend(id: String, minutes: Int) = action { repository.extend(id, minutes) }
    fun end(id: String) = action { repository.end(id) }
    private fun action(block: suspend () -> Unit) {
        if (!mutableState.value.canUse || mutableState.value.busy) return
        mutableState.value = mutableState.value.copy(busy = true, error = null)
        viewModelScope.launch {
            try { block() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: WaitingRefreshException) {
                mutableState.value = mutableState.value.copy(recoveryRequired = true,
                    error = "변경은 저장됐지만 화면 갱신에 실패했습니다. 재조회 중입니다")
            }
            catch (error: Exception) { mutableState.value = mutableState.value.copy(error = "저장하지 못했습니다: ${error.message}") }
            finally { mutableState.value = mutableState.value.copy(busy = false) }
        }
    }
    class Factory(private val repository: WaitingRepository, private val safety: WaitingSafetyGateway) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(WaitingViewModel::class.java))
            return WaitingViewModel(repository, safety) as T
        }
    }
}



