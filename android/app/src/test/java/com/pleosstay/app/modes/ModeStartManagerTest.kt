package com.pleosstay.app.modes

import com.pleosstay.app.waiting.MemoryJournal
import com.pleosstay.app.waiting.MemoryWaitingStore
import com.pleosstay.app.waiting.data.LocalWaitingRepository
import com.pleosstay.app.waiting.data.WaitingStore
import com.pleosstay.app.waiting.domain.WaitingClock
import com.pleosstay.app.waiting.domain.WaitingSession
import com.pleosstay.app.waiting.domain.WaitingStatus
import com.pleosstay.app.waiting.safety.DrivingEventBuffer
import com.pleosstay.app.waiting.safety.VehicleMotion
import com.pleosstay.app.waiting.safety.WaitingSafety
import com.pleosstay.app.waiting.safety.WaitingSafetyGateway
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class ModeStartManagerTest {
    @Test fun requiresRestoreThenCreatesWaitingForRest() = runTest {
        val f = Fixture(this)
        assertTrue(f.manager.start(rest) is ModeStartResult.RecoveryRequired)
        assertTrue(f.store.rows.isEmpty())
        f.repo.restore()
        val ready = f.manager.start(rest) as ModeStartResult.Ready
        assertEquals(WaitingUse.CREATED, ready.use)
        assertEquals(60 * minute, ready.durationMillis)
        assertEquals(f.now + 60 * minute, ready.endsAt)
        assertEquals(ready.waiting, f.active)
    }

    @Test fun reusesWaitingAndDoesNotExtendBeforeChoice() = runTest {
        val f = Fixture(this)
        f.seed(40)
        val original = f.active
        val study = f.manager.start(ModeStartRequest(ActivityMode.STUDY, 30)) as ModeStartResult.Ready
        assertEquals(WaitingUse.REUSED, study.use)
        assertEquals(original, study.waiting)
        val prompt = f.workPrompt()
        assertEquals(40 * minute, prompt.remainingMillis)
        assertEquals(20L, prompt.extensionMinutes)
        assertEquals(original, f.active)
    }

    @Test fun recalculatesExtensionFromLatestDeadlineAndTime() = runTest {
        val f = Fixture(this)
        f.seed(40)
        val prompt = f.workPrompt()
        f.repo.extend(f.active.id, 5)
        f.now += 10 * minute + 1
        val ready = f.manager.resolve(prompt, WaitingChoice.EXTEND) as ModeStartResult.Ready
        assertEquals(WaitingUse.EXTENDED, ready.use)
        assertEquals(71 * minute, f.active.plannedEndAt)
        assertEquals(2, f.active.revision)
        assertEquals(f.now + 60 * minute, ready.endsAt)
        f.now += minute
        assertTrue(f.manager.resolve(prompt, WaitingChoice.EXTEND) is ModeStartResult.DecisionAlreadyHandled)
        assertEquals(2, f.active.revision)
    }

    @Test fun shorteningUsesCurrentRemainingTimeWithoutWriting() = runTest {
        val f = Fixture(this)
        f.seed(40)
        val original = f.active
        val prompt = f.workPrompt()
        f.now += minute + 1
        val ready = f.manager.resolve(prompt, WaitingChoice.SHORTEN) as ModeStartResult.Ready
        assertEquals(WaitingUse.SHORTENED, ready.use)
        assertEquals(39 * minute - 1, ready.durationMillis)
        assertEquals(original.plannedEndAt, ready.endsAt)
        assertEquals(original, f.active)
    }

    @Test fun backPreservesSelectedTimeAndCannotBeReplayed() = runTest {
        val f = Fixture(this)
        f.seed(40)
        val original = f.active
        val prompt = f.workPrompt()
        assertEquals(ModeStartResult.Cancelled(work), f.manager.resolve(prompt, WaitingChoice.BACK))
        assertTrue(f.manager.resolve(prompt, WaitingChoice.EXTEND) is ModeStartResult.DecisionAlreadyHandled)
        assertEquals(original, f.active)
    }

    @Test fun concurrentRequestsShareOneWaiting() = runTest {
        val f = Fixture(this)
        f.repo.restore()
        val results = listOf(rest, rest, work, ModeStartRequest(ActivityMode.STUDY, 60))
            .map { request -> async { f.manager.start(request) as ModeStartResult.Ready } }.awaitAll()
        assertEquals(1, results.count { it.use == WaitingUse.CREATED })
        assertEquals(setOf(f.active.id), results.map { it.waiting?.id }.toSet())
        assertEquals(1, f.store.rows.size)
    }

    @Test fun unsafeBeforeStartOrDecisionNeverWrites() = runTest {
        val f = Fixture(this)
        f.seed(40)
        val original = f.active
        val prompt = f.workPrompt()
        f.safety.state.value = WaitingSafety(VehicleMotion.DRIVING, false)
        assertTrue(f.manager.resolve(prompt, WaitingChoice.EXTEND) is ModeStartResult.SafetyBlocked)
        f.safety.state.value = WaitingSafety()
        assertTrue(f.manager.start(rest) is ModeStartResult.SafetyBlocked)
        assertEquals(original, f.active)
    }

    @Test fun storageFailureAllowsOnlyExplicitSafeFallback() = runTest {
        val f = Fixture(this)
        f.repo.restore()
        f.store.failWrites = true
        val error = f.manager.start(rest) as ModeStartResult.StorageError
        assertTrue(error.refreshRequired)
        assertTrue(f.manager.start(rest) is ModeStartResult.RecoveryRequired)
        assertTrue(f.store.rows.isEmpty())
        val independent = f.manager.continueWithoutWaiting(error) as ModeStartResult.Ready
        assertEquals(WaitingUse.NONE, independent.use)
        assertNull(independent.waiting)
        f.safety.state.value = WaitingSafety()
        assertTrue(f.manager.continueWithoutWaiting(error) is ModeStartResult.SafetyBlocked)
    }

    @Test fun successfulWriteWithFailedReadIsReconciledWithoutRepeatingExtension() = runTest {
        val f = Fixture(this)
        f.seed(40)
        val prompt = f.workPrompt()
        f.store.failReadAfterExtend = true
        val error = f.manager.resolve(prompt, WaitingChoice.EXTEND) as ModeStartResult.StorageError
        assertTrue(error.refreshRequired)
        assertTrue(f.manager.start(work) is ModeStartResult.RecoveryRequired)
        f.store.failReads = false
        f.repo.restore()
        assertEquals(WaitingUse.REUSED, (f.manager.start(work) as ModeStartResult.Ready).use)
        assertEquals(1, f.active.revision)
        assertEquals(60 * minute, f.active.plannedEndAt)
    }

    @Test fun confirmationDoesNotApplyToReplacementWaiting() = runTest {
        val f = Fixture(this)
        f.seed(40)
        val prompt = f.workPrompt()
        val secondPrompt = f.workPrompt()
        f.repo.end(f.active.id)
        assertEquals(ModeStartResult.WaitingChanged(work), f.manager.resolve(prompt, WaitingChoice.EXTEND))
        assertEquals(1, f.store.rows.size)
        f.repo.start(30)
        val replacement = f.active
        assertEquals(ModeStartResult.WaitingChanged(work), f.manager.resolve(secondPrompt, WaitingChoice.SHORTEN))
        assertEquals(replacement, f.active)
    }

    @Test fun expiredWaitingIncludesOvertimeInExtensionAndCannotBeShortenedToZero() = runTest {
        val f = Fixture(this)
        f.seed(40)
        f.now = 50 * minute
        val prompt = f.workPrompt()
        assertEquals(70L, prompt.extensionMinutes)
        val updated = f.manager.resolve(prompt, WaitingChoice.SHORTEN) as ModeStartResult.NeedsChoice
        assertEquals(WaitingChoiceUnavailable.NO_TIME_REMAINING, updated.rejectedChoice)
        assertEquals(WaitingUse.EXTENDED, (f.manager.resolve(updated, WaitingChoice.EXTEND) as ModeStartResult.Ready).use)
        assertEquals(110 * minute, f.active.plannedEndAt)
    }

    @Test fun extensionHonorsTotalLimitAndReportsUnavailableChoice() = runTest {
        val f = Fixture(this)
        f.seed(720)
        f.now = 700 * minute
        val prompt = f.workPrompt()
        assertEquals(WaitingChoiceUnavailable.TOTAL_TIME_LIMIT, prompt.extensionUnavailable)
        val updated = f.manager.resolve(prompt, WaitingChoice.EXTEND) as ModeStartResult.NeedsChoice
        assertEquals(WaitingChoiceUnavailable.TOTAL_TIME_LIMIT, updated.rejectedChoice)
        assertEquals(0, f.active.revision)
        assertEquals(720 * minute, f.active.plannedEndAt)
    }

    @Test fun failedExtensionDoesNotReturnReadyOrChangeWaiting() = runTest {
        val f = Fixture(this)
        f.seed(40)
        val original = f.active
        val prompt = f.workPrompt()
        f.store.failWrites = true
        assertTrue(f.manager.resolve(prompt, WaitingChoice.EXTEND) is ModeStartResult.StorageError)
        assertEquals(original, f.active)
    }

    @Test fun safetyLossDuringSaveBlocksReadyEvenAfterCommit() = runTest {
        val f = Fixture(this)
        f.repo.restore()
        f.afterCreate = { f.safety.state.value = WaitingSafety() }
        assertTrue(f.manager.start(rest) is ModeStartResult.SafetyBlocked)
        assertEquals(1, f.store.rows.size)
    }

    @Test fun uncertainCreateIsReadBackBeforeAnyRetry() = runTest {
        val f = Fixture(this)
        f.repo.restore()
        f.afterCreate = { error("write response lost") }
        val error = f.manager.start(rest) as ModeStartResult.StorageError
        assertTrue(error.refreshRequired)
        assertTrue(f.manager.start(rest) is ModeStartResult.RecoveryRequired)
        val saved = f.store.rows.values.single()
        f.repo.restore()
        val ready = f.manager.start(rest) as ModeStartResult.Ready
        assertEquals(WaitingUse.REUSED, ready.use)
        assertEquals(saved, ready.waiting)
        assertEquals(1, f.store.rows.size)
    }

    private class TestSafety : WaitingSafetyGateway {
        override val state = MutableStateFlow(WaitingSafety(VehicleMotion.PARKED, false))
        override val drivingEvents = DrivingEventBuffer()
        override fun start() = Unit
        override fun stop() = Unit
        override fun close() = Unit
    }

    private class Fixture(scope: TestScope) {
        val store = MemoryWaitingStore()
        val safety = TestSafety()
        var now = 0L
        var afterCreate = {}
        private val observedStore = object : WaitingStore by store {
            override fun create(session: WaitingSession) {
                store.create(session)
                afterCreate()
            }
        }
        val repo = LocalWaitingRepository(
            observedStore, MemoryJournal(), { safety.state.value }, WaitingClock { now },
            dispatcher = StandardTestDispatcher(scope.testScheduler),
        )
        val manager = ModeStartManager(repo, safety)
        val active get() = repo.sessions.value.single { it.status == WaitingStatus.ACTIVE }
        suspend fun seed(minutes: Int) {
            repo.restore()
            repo.start(minutes)
        }
        suspend fun workPrompt() = manager.start(work) as ModeStartResult.NeedsChoice
    }

    private companion object {
        const val minute = 60_000L
        val rest = ModeStartRequest(ActivityMode.REST, 60)
        val work = ModeStartRequest(ActivityMode.WORK, 60)
    }
}
