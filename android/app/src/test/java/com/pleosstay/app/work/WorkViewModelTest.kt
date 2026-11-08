package com.pleosstay.app.work

import com.pleosstay.app.documents.DocumentAccess
import com.pleosstay.app.documents.DocumentRef
import com.pleosstay.app.modes.ModeStartManager
import com.pleosstay.app.modes.WaitingChoice
import com.pleosstay.app.waiting.MemoryJournal
import com.pleosstay.app.waiting.MemoryWaitingStore
import com.pleosstay.app.waiting.data.LocalWaitingRepository
import com.pleosstay.app.waiting.domain.WaitingClock
import com.pleosstay.app.waiting.safety.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WorkViewModelTest {
    @Test fun readyStartsOnceAndWorkExitKeepsExistingWaitingAndPairing() = runTest {
        withFixture { f ->
            f.waiting.restore()
            f.model.setForeground(true); runCurrent()
            f.model.start(); f.model.start(); runCurrent()
            assertNotNull(f.model.state.value.session)
            assertEquals(1, f.store.rows.size)
            assertEquals(30 * 60_000L, f.model.state.value.remainingMillis)
            val saved = f.store.rows.values.single()
            f.model.finishKeepingDrafts(); runCurrent()
            assertNull(f.model.state.value.session)
            assertEquals(saved, f.store.rows.values.single())
            assertEquals("link", f.gateway.access.value.connectionId)
        }
    }

    @Test fun needsChoiceDoesNotStartUntilResolvedAndShortenDoesNotExtendWaiting() = runTest {
        withFixture { f ->
            f.waiting.restore(); f.waiting.start(20)
            val before = f.store.rows.values.single()
            f.model.setForeground(true); runCurrent()
            f.model.start(); runCurrent()
            assertNull(f.model.state.value.session)
            assertNotNull(f.model.state.value.choice)
            assertEquals(before, f.store.rows.values.single())
            f.model.resolve(WaitingChoice.SHORTEN); runCurrent()
            assertEquals(20 * 60_000L, f.model.state.value.remainingMillis)
            assertEquals(before, f.store.rows.values.single())
        }
    }

    @Test fun recoveryRequiredDoesNotBypassManagerOrStartTimer() = runTest {
        withFixture { f ->
            f.model.setForeground(true); runCurrent(); f.model.start(); runCurrent()
            assertTrue(f.model.state.value.storageError)
            assertNull(f.model.state.value.session)
            assertTrue(f.store.rows.isEmpty())
        }
    }

    @Test fun unknownAndPinLockClearSensitiveListsAndBlockCommands() = runTest {
        withFixture { f ->
            f.waiting.restore(); f.model.setForeground(true); runCurrent()
            f.model.refresh(); runCurrent()
            assertEquals(1, f.model.state.value.files.size)
            f.safety.state.value = WaitingSafety(); runCurrent()
            assertFalse(f.model.state.value.allowed)
            assertTrue(f.model.state.value.files.isEmpty())
            assertTrue(f.model.state.value.drafts.isEmpty())
            f.model.start(); runCurrent()
            assertNull(f.model.state.value.session)
            f.safety.state.value = WaitingSafety(VehicleMotion.PARKED, false)
            f.gateway.access.value = DocumentAccess(false, "owner", "link", "PIN_LOCKED"); runCurrent()
            assertFalse(f.model.state.value.allowed)
            f.model.refresh(); runCurrent()
            assertTrue(f.model.state.value.files.isEmpty())
        }
    }

    @Test fun backgroundAndUserSwitchNeverRestoreSensitiveStateImplicitly() = runTest {
        withFixture { f ->
            f.waiting.restore(); f.model.setForeground(true); runCurrent()
            f.model.start(); runCurrent(); f.model.refresh(); runCurrent()
            f.model.setForeground(false)
            assertFalse(f.model.state.value.allowed)
            assertTrue(f.model.state.value.files.isEmpty())
            f.model.setForeground(true); runCurrent()
            assertTrue(f.model.state.value.files.isEmpty())
            f.gateway.access.value = DocumentAccess(true, "another-owner", "another-link", "READY"); runCurrent()
            assertNull(f.model.state.value.session)
            assertTrue(f.model.state.value.files.isEmpty())
            assertEquals(1, f.store.rows.size)
        }
    }

    @Test fun invalidDurationNeverCreatesWaiting() = runTest {
        withFixture { f ->
            f.waiting.restore(); f.model.setForeground(true); runCurrent()
            f.model.setMinutes("1"); f.model.start(); runCurrent()
            assertNull(f.model.state.value.session)
            assertTrue(f.model.state.value.message.contains("5~720"))
            assertTrue(f.store.rows.isEmpty())
        }
    }

    @Test fun identityRedactedDuringLockPreservesTimerButNeverSensitiveLists() = runTest {
        withFixture { f ->
            f.waiting.restore(); f.model.setForeground(true); runCurrent()
            f.model.start(); runCurrent(); f.model.refresh(); runCurrent()
            val active = f.model.state.value.session
            f.gateway.access.value = DocumentAccess(reason = "PIN_OR_FOREGROUND_LOCKED"); runCurrent()
            assertFalse(f.model.state.value.allowed)
            assertEquals(active, f.model.state.value.session)
            assertTrue(f.model.state.value.files.isEmpty())
            f.gateway.access.value = DocumentAccess(true, "owner", "link", "READY"); runCurrent()
            assertEquals(active, f.model.state.value.session)
            assertTrue(f.model.state.value.files.isEmpty())
            f.gateway.access.value = DocumentAccess(true, "other", "other-link", "READY"); runCurrent()
            assertNull(f.model.state.value.session)
        }
    }

    @Test fun endingDuringNetworkWaitCancelsAndAllowsExplicitRetry() = runTest {
        withFixture { f ->
            f.waiting.restore(); f.model.setForeground(true); runCurrent()
            f.model.start(); runCurrent()
            f.gateway.beforeList = { awaitCancellation() }
            f.model.refresh(); runCurrent()
            assertTrue(f.model.state.value.busy)
            f.model.finishKeepingDrafts(); runCurrent()
            assertFalse(f.model.state.value.busy)
            assertNull(f.model.state.value.session)
            assertTrue(f.model.state.value.files.isEmpty())
            f.gateway.beforeList = {}
            f.model.refresh(); runCurrent()
            assertEquals(1, f.model.state.value.files.size)
        }
    }

    @Test fun homeAndReentryKeepSameWorkSessionWithoutShowingDocuments() = runTest {
        withFixture { f ->
            f.waiting.restore();f.model.setForeground(true);runCurrent();f.model.start();runCurrent()
            val session=f.model.state.value.session
            f.model.setForeground(false);runCurrent();advanceTimeBy(2000);runCurrent()
            assertEquals(session,f.model.state.value.session);assertFalse(f.model.state.value.allowed)
            assertNull(f.model.state.value.selected);assertNull(f.model.docx.state.value.view)
            f.model.setForeground(true);runCurrent()
            assertEquals(session,f.model.state.value.session)
            assertTrue(f.model.stopForTransition().success)
            assertNull(f.model.state.value.session);assertEquals(1,f.store.rows.size)
        }
    }

    @Test fun expiredTimerDoesNotReleaseActivityWithFailedPrivateRetention()=runTest {
        withFixture { f ->
            f.waiting.restore();f.model.setForeground(true);runCurrent();f.model.start();runCurrent()
            val session=f.model.state.value.session!!
            f.model.docx.newDocument();f.model.docx.state.first { it.draft!=null && !it.busy }
            f.failWrites=true;f.model.docx.setText(0,"keep activity until retained")
            f.model.docx.state.first { !it.busy }
            f.now=session.endsAt+1;advanceTimeBy(1001);runCurrent()
            assertNull(f.model.state.value.session)
            assertEquals(session.id,f.model.transitionSessionId)
            assertFalse(f.model.stopForTransition().success)
            f.failWrites=false;assertTrue(f.model.docx.retryRetention().success)
            assertNull(f.model.docx.state.value.view)
            assertTrue(f.model.stopForTransition().success);assertNull(f.model.transitionSessionId)
        }
    }

    private suspend fun TestScope.withFixture(block: suspend TestScope.(Fixture) -> Unit) {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val fixture = Fixture(this)
        try { block(fixture) } finally { fixture.model.close(); Dispatchers.resetMain() }
    }
    private class Fixture(scope: TestScope) {
        val store = MemoryWaitingStore()
        val safety = WorkTestSafety()
        var now = 1_000L
        val waiting = LocalWaitingRepository(store, MemoryJournal(), { safety.state.value }, WaitingClock { now }, dispatcher = StandardTestDispatcher(scope.testScheduler))
        val ref = DocumentRef("owner", "link", "file", "v1", "fixture.pdf", "application/pdf", "0".repeat(64), 1, 1)
        val gateway = WorkTestGateway(ref)
        var failWrites=false
        private val records=MemoryWorkRecords()
        private val retained=object:com.pleosstay.app.documents.DocumentRecordStore by records {
            override suspend fun write(key:String,bytes:ByteArray) { check(!failWrites);records.write(key,bytes) }
        }
        val model = WorkViewModel(gateway, WorkDraftRepository(retained, gateway), ModeStartManager(waiting, safety), safety) { now }
    }

    private class WorkTestSafety : WaitingSafetyGateway {
        override val state = MutableStateFlow(WaitingSafety(VehicleMotion.PARKED, false))
        override val drivingEvents = DrivingEventBuffer()
        override fun start() = Unit
        override fun stop() = Unit
        override fun close() = Unit
    }
}
