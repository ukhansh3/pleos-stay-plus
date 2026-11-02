package com.pleosstay.app.waiting

import androidx.lifecycle.ViewModelStore
import com.pleosstay.app.waiting.data.LocalWaitingRepository
import com.pleosstay.app.waiting.domain.*
import com.pleosstay.app.waiting.safety.*
import com.pleosstay.app.waiting.ui.WaitingViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

private class TestSafety : WaitingSafetyGateway {
    override val drivingEvents = DrivingEventBuffer()
    fun confirmDriving(at: Long) {
        state.value = WaitingSafety(VehicleMotion.DRIVING, true, observedAt = at)
        drivingEvents.record(at)
    }
    override val state = MutableStateFlow(WaitingSafety(VehicleMotion.PARKED, false))
    override fun start() = Unit
    override fun stop() { state.value = WaitingSafety() }
    override fun close() = stop()
}

@OptIn(ExperimentalCoroutinesApi::class)
class WaitingViewModelTest {
    @Test fun restrictionAndBackgroundLockWithoutEndingDrivingEndsAndFailureRetries() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val owner = ViewModelStore()
        try {
            val safety = TestSafety()
            val store = MemoryWaitingStore()
            var now = 1000L
            val repo = LocalWaitingRepository(store, MemoryJournal(), { safety.state.value }, WaitingClock { now }, dispatcher = dispatcher)
            val vm = WaitingViewModel(repo, safety, WaitingClock { now })
            owner.put("waiting", vm)
            runCurrent()
            vm.start(15, null)
            runCurrent()
            assertNotNull(vm.state.value.active)
            now = 2000
            safety.state.value = WaitingSafety(VehicleMotion.PARKED, true)
            runCurrent()
            assertFalse(vm.state.value.canUse)
            assertEquals(0, store.endings)
            safety.stop() // Background/disconnect is UNKNOWN, never a drive event.
            runCurrent()
            assertEquals(0, store.endings)
            store.failWrites = true
            safety.confirmDriving(now)
            runCurrent()
            assertFalse(vm.state.value.canUse)
            assertTrue(vm.state.value.recoveryRequired)
            assertNotNull(vm.state.value.error)
            store.failWrites = false
            now = 9000
            advanceTimeBy(1000)
            runCurrent()
            assertEquals(1, store.endings)
            assertEquals(2000L, repo.sessions.value.single().endedAt)
            assertEquals(EndReason.DRIVING, repo.sessions.value.single().endReason)
            assertNull(vm.state.value.active)
        } finally {
            owner.clear()
            Dispatchers.resetMain()
        }
    }
    @Test fun drivingWithoutObservationTimeLocksButDoesNotInventEndTime() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val owner = ViewModelStore()
        try {
            val safety = TestSafety()
            val store = MemoryWaitingStore()
            val repo = LocalWaitingRepository(store, MemoryJournal(), { safety.state.value }, WaitingClock { 1000 }, dispatcher = dispatcher)
            val vm = WaitingViewModel(repo, safety, WaitingClock { 1000 })
            owner.put("waiting", vm)
            runCurrent()
            vm.start(15, null)
            runCurrent()
            safety.state.value = WaitingSafety(VehicleMotion.DRIVING, true)
            runCurrent()
            assertFalse(vm.state.value.canUse)
            assertNotNull(vm.state.value.error)
            assertEquals(0, store.endings)
            assertNotNull(vm.state.value.active)
        } finally {
            owner.clear()
            Dispatchers.resetMain()
        }
    }    @Test fun savedExtensionReadFailureLocksUntilRefreshWithoutApplyingTwice() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val owner = ViewModelStore()
        try {
            val safety = TestSafety()
            val store = MemoryWaitingStore()
            val repo = LocalWaitingRepository(store, MemoryJournal(), { safety.state.value }, WaitingClock { 1000 }, dispatcher = dispatcher)
            val vm = WaitingViewModel(repo, safety)
            owner.put("waiting", vm)
            runCurrent()
            vm.start(30, null)
            runCurrent()
            val id = vm.state.value.active!!.id
            store.failReadAfterExtend = true
            vm.extend(id, 15)
            runCurrent()
            assertFalse(vm.state.value.canUse)
            vm.extend(id, 15)
            runCurrent()
            store.failReads = false
            store.failReadAfterExtend = false
            advanceTimeBy(1000)
            runCurrent()
            assertEquals(2_701_000L, vm.state.value.active!!.plannedEndAt)
            assertTrue(vm.state.value.canUse)
        } finally { owner.clear(); Dispatchers.resetMain() }
    }
    @Test fun rapidDrivingThenUnknownStillEndsAtObservationTime() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val owner = ViewModelStore()
        try {
            val safety = TestSafety()
            val store = MemoryWaitingStore()
            val repo = LocalWaitingRepository(store, MemoryJournal(), { safety.state.value }, WaitingClock { 1000 }, dispatcher = dispatcher)
            val vm = WaitingViewModel(repo, safety)
            owner.put("waiting", vm)
            runCurrent()
            vm.start(15, null)
            runCurrent()
            safety.confirmDriving(2000)
            safety.stop()
            runCurrent()
            assertEquals(1, store.endings)
            assertEquals(2000L, repo.sessions.value.single().endedAt)
            assertFalse(vm.state.value.canUse)
        } finally { owner.clear(); Dispatchers.resetMain() }
    }}





