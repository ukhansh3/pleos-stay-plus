package com.pleosstay.app.pickup.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pleosstay.app.parking.location.CurrentLocationGateway
import com.pleosstay.app.parking.location.CurrentLocationResult
import com.pleosstay.app.parking.location.VehicleLocation
import com.pleosstay.app.pickup.auth.DriverIdentityException
import com.pleosstay.app.pickup.data.PickupRepository
import com.pleosstay.app.pickup.domain.PickupEvent
import com.pleosstay.app.pickup.domain.PickupEventType
import com.pleosstay.app.pickup.domain.PickupInvitation
import com.pleosstay.app.pickup.domain.PickupParticipantStatus
import com.pleosstay.app.pickup.domain.PickupSession
import com.pleosstay.app.pickup.domain.PickupSessionStatus
import com.pleosstay.app.pickup.domain.PickupSnapshot
import com.pleosstay.app.pickup.domain.PickupVehicleLocation
import com.pleosstay.app.pickup.tts.PickupSpeechEvent
import com.pleosstay.app.pickup.tts.PickupTtsGateway
import com.pleosstay.app.pickup.tts.UnsupportedPickupTtsGateway
import com.pleosstay.app.waiting.safety.WaitingSafetyGateway
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID

data class PickupUiState(
    val session: PickupSession? = null,
    val invitation: PickupInvitation? = null,
    val busy: Boolean = false,
    val sharingLocation: Boolean = false,
    val syncing: Boolean = false,
    val blocked: Boolean = false,
    val safetyMessage: String = "차량 상태 확인 중",
    val locationMessage: String? = null,
    val lastLocationAt: String? = null,
    val proximityCandidate: Boolean = false,
    val lastEventRevision: Int = 0,
    val ttsMessage: String = "Pleos TTS 초기화 대기 중",
    val error: String? = null,
)

class PickupViewModel(
    private val repository: PickupRepository,
    private val locationGateway: CurrentLocationGateway,
    private val safety: WaitingSafetyGateway,
    private val tts: PickupTtsGateway = UnsupportedPickupTtsGateway(),
    private val refreshIntervalMs: Long = 5_000,
) : ViewModel() {
    private val mutableState = MutableStateFlow(PickupUiState(ttsMessage = tts.verificationMessage))
    val state: StateFlow<PickupUiState> = mutableState.asStateFlow()
    private var locationJob: Job? = null
    private var syncJob: Job? = null
    private var createKey: String? = null
    private var invitationKey: String? = null
    private val closeKeys = mutableMapOf<String, String>()

    init {
        viewModelScope.launch {
            safety.state.collectLatest { current ->
                mutableState.update { it.copy(blocked = !current.canUse, safetyMessage = current.message) }
                val activePickup = mutableState.value.session?.status == PickupSessionStatus.ACTIVE
                if (!current.canUse && activePickup) {
                    stopLocationSharing("차량 상태가 안전 조건을 벗어나 위치 공유를 중지했습니다")
                }
            }
        }
    }

    fun start(waitingSessionId: String? = null) {
        if (mutableState.value.busy || mutableState.value.session != null) return
        if (!safety.state.value.canUse) {
            mutableState.update { it.copy(blocked = true, error = safety.state.value.message) }
            return
        }
        val key = createKey ?: UUID.randomUUID().toString().also { createKey = it }
        viewModelScope.launch {
            mutableState.update { it.copy(busy = true, error = null) }
            try {
                val session = repository.create(waitingSessionId, key)
                createKey = null
                mutableState.update {
                    it.copy(
                        session = session,
                        busy = false,
                        sharingLocation = false,
                        lastEventRevision = session.revision,
                    )
                }
                startSyncLoop(session.id)
                when (val first = locationGateway.getCurrentLocation()) {
                    is CurrentLocationResult.Success -> runCatching {
                        val location = first.location.toPickupLocation()
                        repository.sendVehicleLocation(session.id, location)
                        location
                    }.onSuccess { location ->
                        mutableState.update { it.copy(sharingLocation = true, lastLocationAt = location.observedAt, locationMessage = null) }
                        startLocationLoop(session.id)
                    }.onFailure { failure ->
                        mutableState.update { it.copy(sharingLocation = false, locationMessage = "Pickup은 시작됐지만 차량 위치를 전송하지 못했습니다: ${failure.errorMessage()}") }
                    }
                    else -> mutableState.update {
                        it.copy(sharingLocation = false, locationMessage = "Pickup은 시작됐지만 ${first.message()}")
                    }
                }
            } catch (error: Exception) {
                mutableState.update { it.copy(busy = false, blocked = error is DriverIdentityException && error.blocked, error = error.message ?: "Pickup 시작에 실패했습니다") }
            }
        }
    }

    fun issueInvitation() {
        val sessionId = mutableState.value.session?.takeIf { it.status == PickupSessionStatus.ACTIVE }?.id ?: return
        if (mutableState.value.busy || !safety.state.value.canUse) return
        val key = invitationKey ?: UUID.randomUUID().toString().also { invitationKey = it }
        viewModelScope.launch {
            mutableState.update { it.copy(busy = true, error = null) }
            runCatching { repository.issueInvitation(sessionId, key) }
                .onSuccess { invitation ->
                    invitationKey = null
                    mutableState.update { it.copy(invitation = invitation, busy = false) }
                    syncOnce(sessionId)
                }
                .onFailure { failure ->
                    if (failure is kotlinx.coroutines.CancellationException) throw failure
                    mutableState.update { it.copy(busy = false, error = failure.message ?: "초대 링크를 발급하지 못했습니다.") }
                }
        }
    }

    fun complete() = close("complete") { id, key -> repository.complete(id, key) }
    fun cancel() = close("cancel") { id, key -> repository.cancel(id, "DRIVER_CANCELLED", key) }
    fun endLinkedWaiting() {
        val session = mutableState.value.session ?: return
        if (session.waitingSessionId == null || session.status != PickupSessionStatus.ACTIVE || mutableState.value.busy) return
        close("end") { id, key -> repository.endLinkedWaiting(id, key) }
    }

    private fun close(operation: String, action: suspend (String, String) -> PickupSession) {
        val id = mutableState.value.session?.id ?: return
        if (mutableState.value.busy) return
        val key = closeKeys.getOrPut(operation) { UUID.randomUUID().toString() }
        viewModelScope.launch {
            mutableState.update { it.copy(busy = true, error = null) }
            runCatching { action(id, key) }
                .onSuccess { session ->
                    closeKeys.remove(operation)
                    locationJob?.cancel()
                    mutableState.update { it.copy(session = session, invitation = null, sharingLocation = false, busy = false) }
                    syncOnce(id)
                }
                .onFailure { failure -> mutableState.update { it.copy(busy = false, error = failure.message) } }
        }
    }

    private fun startSyncLoop(sessionId: String) {
        syncJob?.cancel()
        syncJob = viewModelScope.launch {
            while (isActive) {
                syncOnce(sessionId)
                if (mutableState.value.session?.status != PickupSessionStatus.ACTIVE) break
                delay(refreshIntervalMs)
            }
        }
    }

    private suspend fun syncOnce(sessionId: String) {
        mutableState.update { it.copy(syncing = true) }
        val after = mutableState.value.lastEventRevision
        val eventPage = runCatching { repository.events(sessionId, after) }.getOrNull()
        val snapshot = runCatching { repository.snapshot(sessionId) }.getOrNull()
        val session = snapshot?.session ?: runCatching { repository.getSession(sessionId) }.getOrNull()
        val displayName = session?.participant?.displayName ?: mutableState.value.session?.participant?.displayName
        eventPage?.events
            ?.filter { it.revision > after }
            ?.sortedWith(compareBy<PickupEvent> { it.revision }.thenBy { it.id })
            ?.forEach { processEvent(it, displayName) }
        if (eventPage != null) {
            mutableState.update { it.copy(lastEventRevision = maxOf(it.lastEventRevision, eventPage.latestRevision)) }
        }

        if (session == null) {
            mutableState.update { it.copy(syncing = false, error = it.error ?: "Pickup 상태 재연결에 실패했습니다. 자동으로 다시 시도합니다.") }
            return
        }
        applySnapshot(session, snapshot, eventPage != null)
    }

    private fun applySnapshot(session: PickupSession, snapshot: PickupSnapshot?, durableEventsAvailable: Boolean) {
        val previous = mutableState.value
        if (session.revision < (previous.session?.revision ?: 0)) {
            mutableState.update { it.copy(syncing = false) }
            return
        }
        if (!durableEventsAvailable) {
            if (previous.session?.participant?.status != PickupParticipantStatus.DEPARTED && session.participant?.status == PickupParticipantStatus.DEPARTED) {
                speak("snapshot-departed-${session.revision}", PickupSpeechEvent.DEPARTED, session.participant.displayName)
            }
            if (!previous.proximityCandidate && snapshot?.proximityCandidate == true) {
                speak("snapshot-proximity-${session.revision}", PickupSpeechEvent.PROXIMITY_CANDIDATE, session.participant?.displayName)
            }
            if (previous.session?.status == PickupSessionStatus.ACTIVE && session.status == PickupSessionStatus.COMPLETED) {
                speak("snapshot-completed-${session.revision}", PickupSpeechEvent.COMPLETED, session.participant?.displayName)
            }
        }
        mutableState.update {
            it.copy(
                session = session,
                proximityCandidate = snapshot?.proximityCandidate ?: it.proximityCandidate,
                lastLocationAt = snapshot?.vehicleLocationUpdatedAt ?: it.lastLocationAt,
                syncing = false,
                error = null,
            )
        }
        if (session.status != PickupSessionStatus.ACTIVE) {
            locationJob?.cancel()
            mutableState.update { it.copy(sharingLocation = false, invitation = null) }
        }
    }

    private fun processEvent(event: PickupEvent, displayName: String?) {
        when (event.eventType) {
            PickupEventType.PARTICIPANT_STATUS -> if (event.status == PickupParticipantStatus.DEPARTED) {
                speak(event.id, PickupSpeechEvent.DEPARTED, displayName)
            }
            PickupEventType.PROXIMITY_CANDIDATE -> speak(event.id, PickupSpeechEvent.PROXIMITY_CANDIDATE, displayName)
            PickupEventType.SESSION_COMPLETED -> speak(event.id, PickupSpeechEvent.COMPLETED, displayName)
            PickupEventType.SESSION_CANCELLED, PickupEventType.SESSION_ENDED -> Unit
        }
    }

    private fun speak(eventId: String, event: PickupSpeechEvent, displayName: String?) {
        viewModelScope.launch {
            tts.speakOnce(eventId, event, displayName)
                .onSuccess { mutableState.update { it.copy(ttsMessage = "Pleos TTS 안내 완료") } }
                .onFailure { failure -> mutableState.update { it.copy(ttsMessage = failure.message ?: "Pleos TTS 안내 실패 (SDK_VERIFY)") } }
        }
    }

    private fun startLocationLoop(sessionId: String) {
        locationJob?.cancel()
        locationJob = viewModelScope.launch {
            while (isActive && safety.state.value.canUse) {
                delay(refreshIntervalMs)
                when (val result = locationGateway.getCurrentLocation()) {
                    is CurrentLocationResult.Success -> runCatching { repository.sendVehicleLocation(sessionId, result.location.toPickupLocation()) }
                        .onSuccess {
                            mutableState.update { current -> current.copy(lastLocationAt = result.location.toPickupLocation().observedAt, locationMessage = null) }
                        }
                        .onFailure { failure -> mutableState.update { current -> current.copy(locationMessage = "차량 위치 전송 실패: ${failure.errorMessage()}") } }
                    else -> { stopLocationSharing(result.message()); break }
                }
            }
        }
    }

    private fun stopLocationSharing(message: String) {
        locationJob?.cancel(); locationJob = null
        mutableState.update { it.copy(sharingLocation = false, locationMessage = message) }
    }

    internal fun releaseResources() {
        locationJob?.cancel()
        syncJob?.cancel()
        locationGateway.close()
        tts.close()
    }

    override fun onCleared() {
        releaseResources()
        super.onCleared()
    }

    class Factory(
        private val repository: PickupRepository,
        private val locationGateway: CurrentLocationGateway,
        private val safety: WaitingSafetyGateway,
        private val tts: PickupTtsGateway = UnsupportedPickupTtsGateway(),
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = PickupViewModel(repository, locationGateway, safety, tts) as T
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
