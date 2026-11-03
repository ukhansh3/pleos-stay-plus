package com.pleosstay.app.waiting

import com.pleosstay.app.waiting.data.LocalWaitingRepository
import com.pleosstay.app.waiting.domain.*
import com.pleosstay.app.waiting.safety.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WaitingDrivingMonitorTest {
    @Test fun withoutAnActivityOrViewModelMovementStillEndsAndRetriesOriginalTime() = runTest {
        val store = MemoryWaitingStore()
        val repository = LocalWaitingRepository(store, MemoryJournal(),
            { WaitingSafety(VehicleMotion.PARKED, false) }, WaitingClock { 1000 },
            dispatcher = StandardTestDispatcher(testScheduler))
        repository.start(15)
        val events = DrivingEventBuffer()
        val monitor = WaitingDrivingMonitor(repository, events, backgroundScope)
        runCurrent()
        store.failWrites = true
        events.record(2000)
        runCurrent()
        assertTrue(monitor.state.value.pending)
        assertNotNull(monitor.state.value.error)
        assertEquals(WaitingStatus.ACTIVE, repository.sessions.value.single().status)
        store.failWrites = false
        advanceTimeBy(1000)
        runCurrent()
        assertEquals(2000L, repository.sessions.value.single().endedAt)
        assertEquals(EndReason.DRIVING, repository.sessions.value.single().endReason)
        events.record(2000)
        runCurrent()
        assertEquals(1, store.endings)
        assertFalse(monitor.state.value.pending)
        assertTrue(events.state.value.isEmpty())
    }
}
