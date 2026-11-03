package com.pleosstay.app.pickup.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pleosstay.app.parking.location.CurrentLocationGateway
import com.pleosstay.app.parking.location.CurrentLocationResult
import com.pleosstay.app.parking.location.VehicleLocation
import com.pleosstay.app.pickup.auth.DriverIdentityException
import com.pleosstay.app.pickup.data.PickupRepository
import com.pleosstay.app.pickup.domain.PickupInvitation
import com.pleosstay.app.pickup.domain.PickupSession
import com.pleosstay.app.pickup.domain.PickupVehicleLocation
import com.pleosstay.app.waiting.safety.WaitingSafetyGateway
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant

data class PickupUiState(
    val session: PickupSession? = null,
    val invitation: PickupInvitation? = null,
    val busy: Boolean = false,
    val sharingLocation: Boolean = false,
    val blocked: Boolean = false,
    val safetyMessage: String = "차량 상태 확인 중",
    val lastLocationAt: String? = null,
    val error: String? = null,
)

class PickupViewModel(
    private val repository: PickupRepository,
    private val locationGateway: CurrentLocationGateway,
    private val safety: WaitingSafetyGateway,
) : ViewModel() {
    private val mutableState = MutableStateFlow(PickupUiState())
    val state: StateFlow<PickupUiState> = mutableState.asStateFlow()
    private var locationJob: Job? = null

    init {
        viewModelScope.launch {
            safety.state.collectLatest { current ->
                mutableState.update { it.copy(blocked = !current.canUse, safetyMessage = current.message) }
                if (!current.canUse) stopLocationSharing("차량 상태가 안전 조건을 벗어나 위치 공유를 중지했습니다")
            }
        }
    }

    fun start(waitingSessionId: String? = null) {
        if (mutableState.value.busy || mutableState.value.session != null) return
        if (!safety.state.value.canUse) {
            mutableState.update { it.copy(blocked = true, error = safety.state.value.message) }
            return
        }
        viewModelScope.launch {
            mutableState.update { it.copy(busy = true, error = null) }
            try {
                val first = requireLocation()
                val session = repository.create(waitingSessionId)
                repository.sendVehicleLocation(session.id, first.toPickupLocation())
                mutableState.update { it.copy(session = session, busy = false, sharingLocation = true, lastLocationAt = first.toPickupLocation().observedAt) }
                startLocationLoop(session.id)
            } catch (error: Exception) {
                mutableState.update { it.copy(busy = false, blocked = error is DriverIdentityException && error.blocked, error = error.message ?: "Pickup 시작에 실패했습니다") }
            }
        }
    }

    fun issueInvitation() {
        val sessionId = mutableState.value.session?.id ?: return
        viewModelScope.launch {
            mutableState.update { it.copy(busy = true, error = null) }
            runCatching { repository.issueInvitation(sessionId) }
                .onSuccess { invitation -> mutableState.update { it.copy(invitation = invitation, busy = false) } }
                .onFailure { failure -> mutableState.update { it.copy(busy = false, error = failure.message) } }
        }
    }

    fun complete() = close { repository.complete(it) }
    fun cancel() = close { repository.cancel(it, "DRIVER_CANCELLED") }
    fun endLinkedWaiting() {
        val session = mutableState.value.session ?: return
        if (session.waitingSessionId == null || session.status.name != "ACTIVE" || mutableState.value.busy) return
        close { repository.endLinkedWaiting(it) }
    }

    private fun close(action: suspend (String) -> PickupSession) {
        val id = mutableState.value.session?.id ?: return
        viewModelScope.launch {
            mutableState.update { it.copy(busy = true, error = null) }
            runCatching { action(id) }
                .onSuccess { session -> locationJob?.cancel(); mutableState.update { it.copy(session = session, invitation = null, sharingLocation = false, busy = false) } }
                .onFailure { failure -> mutableState.update { it.copy(busy = false, error = failure.message) } }
        }
    }

    private fun startLocationLoop(sessionId: String) {
        locationJob?.cancel()
        locationJob = viewModelScope.launch {
            while (safety.state.value.canUse) {
                delay(5_000)
                when (val result = locationGateway.getCurrentLocation()) {
                    is CurrentLocationResult.Success -> runCatching { repository.sendVehicleLocation(sessionId, result.location.toPickupLocation()) }
                        .onSuccess {
                            val snapshot = runCatching { repository.snapshot(sessionId) }.getOrNull()
                            mutableState.update { current -> current.copy(session = snapshot?.session ?: current.session, lastLocationAt = snapshot?.vehicleLocationUpdatedAt ?: result.location.toPickupLocation().observedAt, error = null) }
                        }
                        .onFailure { failure -> mutableState.update { current -> current.copy(error = "차량 위치 전송 실패: ${failure.errorMessage()}") } }
                    else -> { stopLocationSharing(result.message()); break }
                }
            }
        }
    }

    private suspend fun requireLocation(): VehicleLocation = when (val result = locationGateway.getCurrentLocation()) {
        is CurrentLocationResult.Success -> result.location
        else -> throw IllegalStateException(result.message())
    }

    private fun stopLocationSharing(message: String) {
        locationJob?.cancel(); locationJob = null
        mutableState.update { it.copy(sharingLocation = false, error = message) }
    }

    override fun onCleared() { locationJob?.cancel(); locationGateway.close(); super.onCleared() }

    class Factory(
        private val repository: PickupRepository,
        private val locationGateway: CurrentLocationGateway,
        private val safety: WaitingSafetyGateway,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = PickupViewModel(repository, locationGateway, safety) as T
    }
}

private fun VehicleLocation.toPickupLocation(): PickupVehicleLocation {
    require(timestampMillis > 0) { "FusedLocation 관측 시각이 없습니다" }
    return PickupVehicleLocation(latitude, longitude, Instant.ofEpochMilli(timestampMillis).toString(), confidence)
}
private fun Throwable.errorMessage(): String = message ?: "알 수 없는 오류"
private fun CurrentLocationResult.message(): String = when (this) {
    CurrentLocationResult.PermissionDenied -> "위치 권한이 거부되어 공유를 시작할 수 없습니다"
    CurrentLocationResult.LocationDisabled -> "차량 위치 서비스가 꺼져 있습니다"
    CurrentLocationResult.Unsupported -> "이 환경에서는 FusedLocation을 지원하지 않습니다"
    CurrentLocationResult.Timeout -> "차량 위치를 받지 못했습니다"
    is CurrentLocationResult.Failed -> reason
    is CurrentLocationResult.Success -> ""
}
