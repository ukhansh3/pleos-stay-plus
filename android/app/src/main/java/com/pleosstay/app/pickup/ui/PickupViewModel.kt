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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
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
    val invitationError: String? = null,
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
    private var sessionGeneration = 0L
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
        if (mutableState.value.busy || mutableState.value.session?.status == PickupSessionStatus.ACTIVE) return
        if (!safety.state.value.canUse) {
            mutableState.update { it.copy(blocked = true, error = safety.state.value.message) }
            return
        }
        val generation = ++sessionGeneration
        locationJob?.cancel()
        syncJob?.cancel()
        invitationKey = null
        closeKeys.clear()
        val key = createKey ?: UUID.randomUUID().toString().also { createKey = it }
        mutableState.value = PickupUiState(busy = true, safetyMessage = safety.state.value.message, ttsMessage = mutableState.value.ttsMessage)
        viewModelScope.launch {
            try {
                val session = repository.create(waitingSessionId, key)
                currentCoroutineContext().ensureActive()
                if (generation != sessionGeneration) return@launch
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
                if (canShare(session.id, generation)) startLocationLoop(session.id, generation)
                else stopLocationSharing("차량 상태가 안전 조건을 벗어나 위치 공유를 중지했습니다")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                mutableState.update { it.copy(busy = false, blocked = !safety.state.value.canUse || (error is DriverIdentityException && error.blocked), error = error.message ?: "Pickup 시작에 실패했습니다") }
            }
        }
    }

    fun issueInvitation() {
        val sessionId = mutableState.value.session?.takeIf { it.status == PickupSessionStatus.ACTIVE }?.id ?: return
        if (mutableState.value.busy || !safety.state.value.canUse) return
        val generation = sessionGeneration
        val key = invitationKey ?: UUID.randomUUID().toString().also { invitationKey = it }
        // A request may have committed even when its response is lost. Never
        // keep presenting the previous link as usable during reissue/retry.
        mutableState.update { it.copy(busy = true, invitation = null, error = null, invitationError = null) }
        viewModelScope.launch {
            runCatching { repository.issueInvitation(sessionId, key) }
                .onSuccess { invitation ->
                    currentCoroutineContext().ensureActive()
                    if (!isCurrentSession(sessionId, generation) || mutableState.value.session?.status != PickupSessionStatus.ACTIVE) return@launch
                    invitationKey = null
                    mutableState.update { it.copy(invitation = invitation, busy = false, invitationError = null) }
                    syncOnce(sessionId)
                }
                .onFailure { failure ->
                    if (failure is CancellationException) throw failure
                    if (!isCurrentSession(sessionId, generation)) return@launch
                    mutableState.update { it.copy(busy = false, invitationError = failure.message ?: "초대 링크를 발급하지 못했습니다.") }
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
        val generation = sessionGeneration
        val key = closeKeys.getOrPut(operation) { UUID.randomUUID().toString() }
        // User termination stops local sharing before waiting for the server.
        stopLocationSharing("위치 공유를 중지했습니다")
        mutableState.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            runCatching { action(id, key) }
                .onSuccess { session ->
                    currentCoroutineContext().ensureActive()
                    if (!isCurrentSession(id, generation)) return@launch
                    closeKeys.remove(operation)
                    locationJob?.cancel()
                    mutableState.update { it.copy(session = session, invitation = null, sharingLocation = false, busy = false, invitationError = null) }
                    syncOnce(id)
                }
                .onFailure { failure ->
                    if (failure is CancellationException) throw failure
                    if (!isCurrentSession(id, generation)) return@launch
                    mutableState.update { it.copy(busy = false, error = failure.message) }
                }
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
        val generation = sessionGeneration
        val after = mutableState.value.lastEventRevision
        val eventPage = runCatching { repository.events(sessionId, after) }.getOrNull()
        val snapshot = runCatching { repository.snapshot(sessionId) }.getOrNull()
        val session = snapshot?.session ?: runCatching { repository.getSession(sessionId) }.getOrNull()
        currentCoroutineContext().ensureActive()
        if (!isCurrentSession(sessionId, generation)) return
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
            mutableState.update { it.copy(sharingLocation = false, invitation = null, invitationError = null, busy = false) }
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

    private fun isCurrentSession(sessionId: String, generation: Long) =
        generation == sessionGeneration && mutableState.value.session?.id == sessionId

    private fun canShare(sessionId: String, generation: Long) =
        isCurrentSession(sessionId, generation) && safety.state.value.canUse &&
            mutableState.value.session?.status == PickupSessionStatus.ACTIVE

    private fun startLocationLoop(sessionId: String, generation: Long) {
        locationJob?.cancel()
        locationJob = viewModelScope.launch {
            var first = true
            while (isActive && canShare(sessionId, generation)) {
                if (!first) delay(refreshIntervalMs)
                currentCoroutineContext().ensureActive()
                if (!canShare(sessionId, generation)) break
                val result = try { locationGateway.getCurrentLocation() }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (failure: Exception) { CurrentLocationResult.Failed(failure.errorMessage()) }
                currentCoroutineContext().ensureActive()
                if (!canShare(sessionId, generation)) break
                when (result) {
                    is CurrentLocationResult.Success -> {
                        try {
                            val location = result.location.toPickupLocation()
                            repository.sendVehicleLocation(sessionId, location)
                            currentCoroutineContext().ensureActive()
                            if (!canShare(sessionId, generation)) break
                            mutableState.update { it.copy(sharingLocation = true, lastLocationAt = location.observedAt, locationMessage = null) }
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (failure: Exception) {
                            mutableState.update { it.copy(locationMessage = if (first) "Pickup은 시작됐지만 차량 위치를 전송하지 못했습니다: ${failure.errorMessage()}" else "차량 위치 전송 실패: ${failure.errorMessage()}") }
                            if (first) break
                        }
                    }
                    else -> {
                        stopLocationSharing(if (first) "Pickup은 시작됐지만 ${result.message()}" else result.message())
                        break
                    }
                }
                first = false
            }
        }
    }

    private fun stopLocationSharing(message: String) {
        locationJob?.cancel(); locationJob = null
        mutableState.update { it.copy(sharingLocation = false, locationMessage = message) }
    }

    internal fun releaseResources() {
        sessionGeneration++
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
