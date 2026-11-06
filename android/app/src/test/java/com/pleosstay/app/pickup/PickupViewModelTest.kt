package com.pleosstay.app.pickup

import com.pleosstay.app.hasEndedLinkedWaiting
import com.pleosstay.app.parking.location.CurrentLocationGateway
import com.pleosstay.app.parking.location.CurrentLocationResult
import com.pleosstay.app.parking.location.VehicleLocation
import com.pleosstay.app.pickup.data.PickupRepository
import com.pleosstay.app.pickup.domain.*
import com.pleosstay.app.pickup.tts.PickupSpeechEvent
import com.pleosstay.app.pickup.tts.PickupTtsGateway
import com.pleosstay.app.pickup.ui.PickupViewModel
import com.pleosstay.app.waiting.domain.EndReason
import com.pleosstay.app.waiting.domain.WaitingPolicy
import com.pleosstay.app.waiting.domain.WaitingTime
import com.pleosstay.app.waiting.safety.DrivingEventBuffer
import com.pleosstay.app.waiting.safety.VehicleMotion
import com.pleosstay.app.waiting.safety.WaitingSafety
import com.pleosstay.app.waiting.safety.WaitingSafetyGateway
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PickupViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun preSessionUnknownSafetyDoesNotLeaveLocationErrorAfterRecovery() = runTest(dispatcher) {
        val safety = MutableSafety(WaitingSafety(message = "차량 상태 확인 중"))
        val viewModel = PickupViewModel(
            FakePickupRepository(),
            SequencedLocationGateway(CurrentLocationResult.Timeout),
            safety,
            RecordingTts(),
        )

        runCurrent()
        assertTrue(viewModel.state.value.blocked)
        assertEquals(null, viewModel.state.value.locationMessage)

        safety.state.value = WaitingSafety(VehicleMotion.PARKED, uxRestricted = false, observedAt = 1, message = "주차 확인")
        runCurrent()
        assertFalse(viewModel.state.value.blocked)
        assertEquals(null, viewModel.state.value.locationMessage)
        viewModel.releaseResources()
    }

    @Test fun initialLocationFailureKeepsSessionAndParticipantSyncActive() = runTest(dispatcher) {
        val repository = FakePickupRepository()
        val viewModel = PickupViewModel(
            repository,
            SequencedLocationGateway(CurrentLocationResult.Timeout),
            ParkedSafety(),
            RecordingTts(),
            refreshIntervalMs = 5_000,
        )

        viewModel.start()
        runCurrent()

        assertEquals(1, repository.createCalls)
        assertEquals("session", viewModel.state.value.session?.id)
        assertFalse(viewModel.state.value.sharingLocation)
        assertTrue(repository.snapshotCalls > 0)
        assertTrue(viewModel.state.value.locationMessage.orEmpty().contains("Pickup은 시작"))

        val snapshots = repository.snapshotCalls
        advanceTimeBy(5_001)
        runCurrent()
        assertTrue(repository.snapshotCalls > snapshots)
        viewModel.releaseResources()
    }

    @Test fun independentPickupSurvivesWaitingOvertimeAndManualEnd() = runTest(dispatcher) {
        val repository = FakePickupRepository()
        val viewModel = PickupViewModel(repository, SequencedLocationGateway(CurrentLocationResult.Timeout), ParkedSafety(), RecordingTts())
        val policy = WaitingPolicy()
        val waiting = policy.start(30, 0)
        val extended = policy.extend(waiting, 15)
        val overtime = extended.plannedEndAt + 60_000

        viewModel.start()
        runCurrent()
        assertEquals(listOf<String?>(null), repository.createWaitingIds)
        assertEquals(PickupSessionStatus.ACTIVE, viewModel.state.value.session?.status)
        val linkedId = viewModel.state.value.session?.waitingSessionId

        assertTrue(WaitingTime.calculate(extended, overtime).overtimeMillis > 0)
        assertFalse(hasEndedLinkedWaiting(linkedId, listOf(extended)))
        assertFalse(hasEndedLinkedWaiting(linkedId, listOf(policy.end(extended, overtime, EndReason.MANUAL))))
        viewModel.endLinkedWaiting()
        runCurrent()
        assertEquals(0, repository.linkedEndCalls)
        assertEquals(PickupSessionStatus.ACTIVE, viewModel.state.value.session?.status)

        viewModel.complete()
        runCurrent()
        assertEquals(PickupSessionStatus.COMPLETED, viewModel.state.value.session?.status)
        viewModel.releaseResources()
    }

    @Test fun linkedPickupClosesOnlyForItsEndedWaiting() = runTest(dispatcher) {
        val repository = FakePickupRepository()
        val viewModel = PickupViewModel(repository, SequencedLocationGateway(CurrentLocationResult.Timeout), ParkedSafety(), RecordingTts())
        val policy = WaitingPolicy()
        val waiting = policy.start(30, 0)
        val unrelated = policy.end(policy.start(30, 0), 1, EndReason.MANUAL)

        viewModel.start(waiting.id)
        runCurrent()
        val linkedId = viewModel.state.value.session?.waitingSessionId
        assertEquals(waiting.id, linkedId)
        assertFalse(hasEndedLinkedWaiting(linkedId, listOf(waiting, unrelated)))
        assertTrue(hasEndedLinkedWaiting(linkedId, listOf(policy.end(waiting, 1, EndReason.MANUAL))))

        viewModel.endLinkedWaiting()
        runCurrent()
        assertEquals(1, repository.linkedEndCalls)
        assertEquals(PickupSessionStatus.ENDED, viewModel.state.value.session?.status)
        viewModel.releaseResources()
    }

    @Test fun participantSyncContinuesAfterVehicleLocationFails() = runTest(dispatcher) {
        val repository = FakePickupRepository()
        val location = SequencedLocationGateway(
            CurrentLocationResult.Success(VehicleLocation(37.0, 127.0, 1_793_600_000_000)),
            CurrentLocationResult.Timeout,
        )
        val viewModel = PickupViewModel(repository, location, ParkedSafety(), RecordingTts(), refreshIntervalMs = 5_000)

        viewModel.start()
        runCurrent()
        val initialSnapshots = repository.snapshotCalls

        advanceTimeBy(5_001)
        runCurrent()
        assertFalse(viewModel.state.value.sharingLocation)
        assertTrue(repository.snapshotCalls > initialSnapshots)
        val afterLocationFailure = repository.snapshotCalls

        advanceTimeBy(5_001)
        runCurrent()
        assertTrue(repository.snapshotCalls > afterLocationFailure)
        viewModel.releaseResources()
    }

    @Test fun durableEventsAreSortedAndSpokenOnlyOnce() = runTest(dispatcher) {
        val repository = FakePickupRepository().apply {
            eventResult = PickupEventPage(
                listOf(
                    PickupEvent("near", PickupEventType.PROXIMITY_CANDIDATE, 2, "2026-11-04T00:00:02Z"),
                    PickupEvent("depart", PickupEventType.PARTICIPANT_STATUS, 1, "2026-11-04T00:00:01Z", PickupParticipantStatus.DEPARTED),
                ),
                latestRevision = 2,
            )
        }
        val tts = RecordingTts()
        val viewModel = PickupViewModel(
            repository,
            SequencedLocationGateway(CurrentLocationResult.Success(VehicleLocation(37.0, 127.0, 1_793_600_000_000))),
            ParkedSafety(),
            tts,
            refreshIntervalMs = 5_000,
        )

        viewModel.start()
        runCurrent()
        assertEquals(listOf(PickupSpeechEvent.DEPARTED, PickupSpeechEvent.PROXIMITY_CANDIDATE), tts.events)
        assertEquals(2, viewModel.state.value.lastEventRevision)

        advanceTimeBy(5_001)
        runCurrent()
        assertEquals(2, tts.events.size)
        viewModel.releaseResources()
    }

    @Test fun invitationRetryReusesKeyAndSuccessfulReissueGetsNewKey() = runTest(dispatcher) {
        val repository = FakePickupRepository().apply { invitationFailures = 1 }
        val viewModel = PickupViewModel(
            repository,
            SequencedLocationGateway(CurrentLocationResult.Success(VehicleLocation(37.0, 127.0, 1_793_600_000_000))),
            ParkedSafety(),
            RecordingTts(),
        )
        viewModel.start()
        runCurrent()

        viewModel.issueInvitation()
        runCurrent()
        viewModel.issueInvitation()
        runCurrent()
        assertEquals(repository.invitationKeys[0], repository.invitationKeys[1])

        viewModel.issueInvitation()
        runCurrent()
        assertNotEquals(repository.invitationKeys[1], repository.invitationKeys[2])
        viewModel.releaseResources()
    }
}

private class FakePickupRepository : PickupRepository {
    private var baseSession = PickupSession("session", null, PickupSessionStatus.ACTIVE, true, null, null, 0, "2026-11-04T00:00:00Z", null)
    var eventResult = PickupEventPage(emptyList(), 0)
    var snapshotCalls = 0
    var invitationFailures = 0
    var createCalls = 0
    val invitationKeys = mutableListOf<String>()
    val createWaitingIds = mutableListOf<String?>()
    var linkedEndCalls = 0

    override suspend fun create(waitingSessionId: String?, idempotencyKey: String): PickupSession {
        createCalls++
        createWaitingIds += waitingSessionId
        baseSession = baseSession.copy(waitingSessionId = waitingSessionId)
        return baseSession
    }
    override suspend fun getSession(sessionId: String) = baseSession
    override suspend fun events(sessionId: String, afterRevision: Int) = eventResult
    override suspend fun issueInvitation(sessionId: String, idempotencyKey: String): PickupInvitation {
        invitationKeys += idempotencyKey
        if (invitationFailures-- > 0) error("network")
        return PickupInvitation("invite", "https://example.test/pickup", "2026-11-04T01:00:00Z")
    }
    override suspend fun sendVehicleLocation(sessionId: String, location: PickupVehicleLocation) = Unit
    override suspend fun snapshot(sessionId: String): PickupSnapshot {
        snapshotCalls++
        return PickupSnapshot(baseSession, false, baseSession.revision, "2026-11-04T00:00:00Z", null)
    }
    override suspend fun complete(sessionId: String, idempotencyKey: String) = baseSession.copy(status = PickupSessionStatus.COMPLETED, revision = 1)
    override suspend fun cancel(sessionId: String, reason: String, idempotencyKey: String) = baseSession.copy(status = PickupSessionStatus.CANCELLED, revision = 1)
    override suspend fun endLinkedWaiting(sessionId: String, idempotencyKey: String): PickupSession {
        linkedEndCalls++
        return baseSession.copy(status = PickupSessionStatus.ENDED, revision = 1)
    }
}

private class SequencedLocationGateway(vararg results: CurrentLocationResult) : CurrentLocationGateway {
    private val queue = ArrayDeque(results.toList())
    private val fallback = results.last()
    override suspend fun getCurrentLocation(): CurrentLocationResult = if (queue.isEmpty()) fallback else queue.removeFirst()
    override fun showAccessBlockedInformation() = Unit
    override fun close() = Unit
}

private class ParkedSafety : WaitingSafetyGateway {
    override val drivingEvents = DrivingEventBuffer()
    override val state = MutableStateFlow(WaitingSafety(VehicleMotion.PARKED, uxRestricted = false, observedAt = 1, message = "주차 확인"))
    override fun start() = Unit
    override fun stop() = Unit
    override fun close() = Unit
}

private class MutableSafety(initial: WaitingSafety) : WaitingSafetyGateway {
    override val drivingEvents = DrivingEventBuffer()
    override val state = MutableStateFlow(initial)
    override fun start() = Unit
    override fun stop() = Unit
    override fun close() = Unit
}

private class RecordingTts : PickupTtsGateway {
    override val supported = true
    val events = mutableListOf<PickupSpeechEvent>()
    private val ids = mutableSetOf<String>()
    override suspend fun speakOnce(eventId: String, event: PickupSpeechEvent, displayName: String?): Result<Unit> {
        if (ids.add(eventId)) events += event
        return Result.success(Unit)
    }
}
