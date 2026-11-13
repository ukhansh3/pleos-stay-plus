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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
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
        assertEquals("network", viewModel.state.value.invitationError)
        val snapshots = repository.snapshotCalls
        advanceTimeBy(5_001)
        runCurrent()
        assertTrue(repository.snapshotCalls > snapshots)
        assertEquals("network", viewModel.state.value.invitationError) // successful polling cannot hide the QR failure
        assertEquals(PickupSessionStatus.ACTIVE, viewModel.state.value.session?.status)
        viewModel.issueInvitation()
        runCurrent()
        assertEquals(null, viewModel.state.value.invitationError)
        assertEquals(repository.invitationKeys[0], repository.invitationKeys[1])

        viewModel.issueInvitation()
        runCurrent()
        assertNotEquals(repository.invitationKeys[1], repository.invitationKeys[2])
        repository.invitationFailures = 1
        viewModel.issueInvitation()
        runCurrent()
        assertEquals("network", viewModel.state.value.invitationError)
        viewModel.complete()
        runCurrent()
        assertEquals(PickupSessionStatus.COMPLETED, viewModel.state.value.session?.status)
        assertEquals(null, viewModel.state.value.invitationError)
        viewModel.releaseResources()
    }

    @Test fun failedStartCannotUnblockAnUnsafeScreen() = runTest(dispatcher) {
        val safety = MutableSafety(WaitingSafety(VehicleMotion.PARKED, false))
        val pending = CompletableDeferred<PickupSession>()
        val repository = object : PickupRepository by FakePickupRepository() {
            override suspend fun create(waitingSessionId: String?, idempotencyKey: String) = pending.await()
        }
        val model = PickupViewModel(repository, SequencedLocationGateway(CurrentLocationResult.Timeout), safety, RecordingTts())
        try {
            model.start(); runCurrent()
            safety.state.value = WaitingSafety(VehicleMotion.UNKNOWN, true); runCurrent()
            pending.completeExceptionally(java.io.IOException("network")); runCurrent()
            assertTrue(model.state.value.blocked)
            assertFalse(model.state.value.busy)
        } finally { model.releaseResources() }
    }

    @Test fun lateInitialLocationIsIgnoredAfterDrivingOrUnknown() = runTest(dispatcher) {
        for (motion in listOf(VehicleMotion.DRIVING, VehicleMotion.UNKNOWN)) {
            val safety = MutableSafety(WaitingSafety(VehicleMotion.PARKED, false))
            val pending = CompletableDeferred<CurrentLocationResult>()
            var sends = 0
            val repository = object : PickupRepository by FakePickupRepository() {
                override suspend fun sendVehicleLocation(sessionId: String, location: PickupVehicleLocation) { sends++ }
            }
            val gateway = object : CurrentLocationGateway {
                override suspend fun getCurrentLocation() = withContext(NonCancellable) { pending.await() }
                override fun showAccessBlockedInformation() = Unit
                override fun close() = Unit
            }
            val model = PickupViewModel(repository, gateway, safety, RecordingTts())
            try {
                model.start(); runCurrent()
                safety.state.value = WaitingSafety(motion, true); runCurrent()
                pending.complete(CurrentLocationResult.Success(VehicleLocation(37.0, 127.0, 1_793_600_000_000)))
                runCurrent()
                assertEquals(0, sends)
                assertFalse(model.state.value.sharingLocation)
            } finally { model.releaseResources() }
        }
    }

    @Test fun latePeriodicLocationIsIgnoredAfterSafetyLoss() = runTest(dispatcher) {
        val safety = MutableSafety(WaitingSafety(VehicleMotion.PARKED, false))
        val pending = CompletableDeferred<CurrentLocationResult>()
        var calls = 0
        var sends = 0
        val repository = object : PickupRepository by FakePickupRepository() {
            override suspend fun sendVehicleLocation(sessionId: String, location: PickupVehicleLocation) { sends++ }
        }
        val gateway = object : CurrentLocationGateway {
            override suspend fun getCurrentLocation(): CurrentLocationResult = if (++calls == 1) CurrentLocationResult.Success(VehicleLocation(37.0, 127.0, 1_793_600_000_000)) else withContext(NonCancellable) { pending.await() }
            override fun showAccessBlockedInformation() = Unit
            override fun close() = Unit
        }
        val model = PickupViewModel(repository, gateway, safety, RecordingTts())
        try {
            model.start(); runCurrent()
            assertEquals(1, sends)
            advanceTimeBy(5_001); runCurrent()
            assertEquals(2, calls)
            safety.state.value = WaitingSafety(VehicleMotion.DRIVING, true); runCurrent()
            pending.complete(CurrentLocationResult.Success(VehicleLocation(37.1, 127.1, 1_793_600_005_000)))
            runCurrent()
            assertEquals(1, sends)
            assertFalse(model.state.value.sharingLocation)
        } finally { model.releaseResources() }
    }

    @Test fun completedCancelledAndEndedPickupCanStartAFreshSession() = runTest(dispatcher) {
        for (action in listOf("complete", "cancel", "end")) {
            val repository = FakePickupRepository()
            val model = PickupViewModel(repository, SequencedLocationGateway(CurrentLocationResult.Timeout), ParkedSafety(), RecordingTts())
            try {
                model.start(if (action == "end") "waiting" else null); runCurrent()
                when (action) { "complete" -> model.complete(); "cancel" -> model.cancel(); else -> model.endLinkedWaiting() }
                runCurrent()
                assertNotEquals(PickupSessionStatus.ACTIVE, model.state.value.session?.status)
                model.start(); model.start(); runCurrent()
                assertEquals(2, repository.createCalls)
                assertEquals("session-2", model.state.value.session?.id)
                assertEquals(PickupSessionStatus.ACTIVE, model.state.value.session?.status)
                assertEquals(null, model.state.value.invitation)
                assertEquals(null, model.state.value.lastLocationAt)
                assertFalse(model.state.value.proximityCandidate)
            } finally { model.releaseResources() }
        }
    }

    @Test fun closeRequestCancelsTheFirstPositionBeforeTheServerReply() = runTest(dispatcher) {
        val pending = CompletableDeferred<CurrentLocationResult>()
        val closeReply = CompletableDeferred<Unit>()
        val base = FakePickupRepository()
        var sends = 0
        val repository = object : PickupRepository by base {
            override suspend fun sendVehicleLocation(sessionId: String, location: PickupVehicleLocation) { sends++ }
            override suspend fun complete(sessionId: String, idempotencyKey: String): PickupSession {
                closeReply.await()
                return base.complete(sessionId, idempotencyKey)
            }
        }
        val gateway = object : CurrentLocationGateway {
            override suspend fun getCurrentLocation() = withContext(NonCancellable) { pending.await() }
            override fun showAccessBlockedInformation() = Unit
            override fun close() = Unit
        }
        val model = PickupViewModel(repository, gateway, ParkedSafety(), RecordingTts())
        try {
            model.start(); runCurrent()
            model.complete(); runCurrent()
            pending.complete(CurrentLocationResult.Success(VehicleLocation(37.0, 127.0, 1_793_600_000_000)))
            runCurrent()
            assertEquals(0, sends)
            assertFalse(model.state.value.sharingLocation)
            assertTrue(model.state.value.busy)
            closeReply.complete(Unit); runCurrent()
            assertEquals(PickupSessionStatus.COMPLETED, model.state.value.session?.status)
        } finally { model.releaseResources() }
    }

    @Test fun terminalSyncReleasesBusyAndLateCommandsCannotReplaceTheNextSession() = runTest(dispatcher) {
        for (command in listOf("invite", "close")) {
            val reply = CompletableDeferred<Unit>()
            val base = FakePickupRepository()
            var ended = false
            val repository = object : PickupRepository by base {
                override suspend fun issueInvitation(sessionId: String, idempotencyKey: String): PickupInvitation {
                    reply.await()
                    return PickupInvitation("old", "https://pickup.example.test/share/" + "A".repeat(43), "2030-01-01T00:00:00Z")
                }
                override suspend fun complete(sessionId: String, idempotencyKey: String): PickupSession {
                    val old = base.getSession(sessionId)
                    reply.await()
                    return old.copy(status = PickupSessionStatus.COMPLETED, revision = 2)
                }
                override suspend fun snapshot(sessionId: String): PickupSnapshot {
                    val snapshot = base.snapshot(sessionId)
                    return if (ended && sessionId == "session") snapshot.copy(session = snapshot.session.copy(status = PickupSessionStatus.COMPLETED, revision = 2), revision = 2) else snapshot
                }
            }
            val model = PickupViewModel(repository, SequencedLocationGateway(CurrentLocationResult.Timeout), ParkedSafety(), RecordingTts())
            try {
                model.start(); runCurrent()
                if (command == "invite") model.issueInvitation() else model.complete()
                runCurrent()
                assertTrue(model.state.value.busy)
                ended = true
                advanceTimeBy(5_001); runCurrent()
                assertEquals(PickupSessionStatus.COMPLETED, model.state.value.session?.status)
                assertFalse(model.state.value.busy)
                model.start(); runCurrent()
                assertEquals("session-2", model.state.value.session?.id)
                reply.complete(Unit); runCurrent()
                assertEquals("session-2", model.state.value.session?.id)
                assertEquals(PickupSessionStatus.ACTIVE, model.state.value.session?.status)
                assertEquals(null, model.state.value.invitation)
            } finally { model.releaseResources() }
        }
    }

    @Test fun reissueHidesOldQrAndRecoversWithTheSameRetryKey() = runTest(dispatcher) {
        val pending = CompletableDeferred<PickupInvitation>()
        val keys = mutableListOf<String>()
        val first = PickupInvitation("first", "https://pickup.example.test/share/" + "A".repeat(43), "2030-01-01T00:00:00Z")
        val replacement = first.copy(invitationId = "replacement", shareUrl = "https://pickup.example.test/share/" + "B".repeat(43))
        val repository = object : PickupRepository by FakePickupRepository() {
            override suspend fun issueInvitation(sessionId: String, idempotencyKey: String): PickupInvitation {
                keys += idempotencyKey
                return when (keys.size) { 1 -> first; 2 -> pending.await(); else -> replacement }
            }
        }
        val model = PickupViewModel(repository, SequencedLocationGateway(CurrentLocationResult.Timeout), ParkedSafety(), RecordingTts())
        try {
            model.start(); runCurrent()
            model.issueInvitation(); runCurrent()
            assertEquals(first, model.state.value.invitation)
            model.issueInvitation(); runCurrent()
            assertTrue(model.state.value.busy)
            assertEquals(null, model.state.value.invitation)
            pending.completeExceptionally(java.io.IOException("lost response")); runCurrent()
            assertEquals(null, model.state.value.invitation)
            assertEquals("lost response", model.state.value.invitationError)
            model.issueInvitation(); runCurrent()
            assertEquals(replacement, model.state.value.invitation)
            assertNotEquals(keys[0], keys[1])
            assertEquals(keys[1], keys[2])
        } finally { model.releaseResources() }
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
        baseSession = PickupSession(if (createCalls == 1) "session" else "session-$createCalls", waitingSessionId, PickupSessionStatus.ACTIVE, true, null, null, 0, "2026-11-04T00:00:00Z", null)
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
