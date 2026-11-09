package com.pleosstay.app.modes

import com.pleosstay.app.waiting.safety.DrivingEventBuffer
import com.pleosstay.app.waiting.safety.VehicleMotion
import com.pleosstay.app.waiting.safety.WaitingSafety
import com.pleosstay.app.waiting.safety.WaitingSafetyGateway
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ActivityTransitionCoordinatorTest {
    private class Adapter(override val mode:ActivityMode,val events:MutableList<String>):ActivityAdapter {
        var id:String?=null
        var starts=0
        var ready=true
        var recovered=true
        override fun restored()=recovered
        var failStart=false
        var failStop=false
        var stopGate:CompletableDeferred<Unit>?=null
        override fun sessionId()=id
        override suspend fun preflight()=ActivityOperation(ready)
        override suspend fun start():ActivityOperation { starts++;events.add("start:"+mode);if(!failStart)id="session-"+starts;return ActivityOperation(!failStart) }
        override suspend fun resume():ActivityOperation { events.add("resume:"+mode);return ActivityOperation.Done }
        override suspend fun stop():ActivityOperation { events.add("stop-begin:"+mode);stopGate?.await();if(!failStop)id=null;events.add("stop-end:"+mode);return ActivityOperation(!failStop) }
    }
    private class Fixture {
        val events=mutableListOf<String>()
        val adapters=ActivityMode.entries.associateWith { Adapter(it,events) }
        var now=100L
        val coordinator=ActivityTransitionCoordinator(adapters.values.toList(),{now},1000)
        val rest get()=adapters.getValue(ActivityMode.REST)
        val study get()=adapters.getValue(ActivityMode.STUDY)
        suspend fun rest() { assertEquals(TransitionStatus.STARTED,coordinator.request(ActivityMode.REST).status) }
        suspend fun prompt(target:ActivityMode=ActivityMode.STUDY):String {
            val result=coordinator.request(target);assertEquals(TransitionStatus.CONFIRMATION_REQUIRED,result.status)
            assertFalse(result.state.transitioning)
            return checkNotNull(result.state.confirmation)
        }
    }
    @Test fun idleStartAndSameActivityResumeNeverStartsTwice()=runTest {
        val f=Fixture();f.rest();val id=f.rest.id
        assertEquals(TransitionStatus.RESUMED,f.coordinator.request(ActivityMode.REST).status)
        assertEquals(id,f.rest.id);assertEquals(1,f.rest.starts)
    }
    @Test fun confirmationAndCancelKeepOriginalSession()=runTest {
        val f=Fixture();f.rest();val id=f.rest.id;val token=f.prompt()
        assertEquals(0,f.study.starts)
        assertEquals(TransitionStatus.CANCELLED,f.coordinator.confirm(token,false).status)
        assertEquals(id,f.rest.id)
        assertEquals(TransitionStatus.STALE,f.coordinator.confirm(token,true).status)
    }
    @Test fun approvedSwitchWaitsForStopAndRejectsCompetingRequests()=runTest {
        val f=Fixture();f.rest();val token=f.prompt();val gate=CompletableDeferred<Unit>();f.rest.stopGate=gate
        val result=async { f.coordinator.confirm(token,true) };runCurrent()
        assertTrue(f.coordinator.state.value.transitioning);assertEquals(0,f.study.starts)
        assertEquals(TransitionStatus.BUSY,f.coordinator.request(ActivityMode.WORK).status)
        gate.complete(Unit)
        assertEquals(TransitionStatus.STARTED,result.await().status)
        assertTrue(f.events.indexOf("stop-end:REST")<f.events.indexOf("start:STUDY"))
        assertNull(f.rest.id);assertNotNull(f.study.id)
    }
    @Test fun stopFailureRetainsOccupancyUntilSuccessfulRetry()=runTest {
        val f=Fixture();f.rest();f.rest.failStop=true
        assertEquals(TransitionStatus.STOP_FAILED,f.coordinator.confirm(f.prompt(),true).status)
        assertNotNull(f.rest.id);assertEquals(0,f.study.starts)
        assertEquals(TransitionStatus.STOP_FAILED,f.coordinator.request(ActivityMode.WORK).status)
        f.rest.failStop=false;assertEquals(TransitionStatus.STOPPED,f.coordinator.end().status)
    }
    @Test fun failedStartDoesNotRestartOldActivity()=runTest {
        val f=Fixture();f.rest();f.study.failStart=true
        assertEquals(TransitionStatus.START_FAILED,f.coordinator.confirm(f.prompt(),true).status)
        assertNull(f.rest.id);assertNull(f.study.id);assertEquals(1,f.rest.starts)
        assertNull(f.coordinator.state.value.active)
    }
    @Test fun expiredApprovalAndReplacedSessionAreRejected()=runTest {
        val f=Fixture();f.rest();val expired=f.prompt();f.now+=1001
        assertEquals(TransitionStatus.STALE,f.coordinator.confirm(expired,true).status)
        val replaced=f.prompt();f.rest.id="replacement"
        assertEquals(TransitionStatus.STALE,f.coordinator.confirm(replaced,true).status)
        assertEquals(0,f.study.starts)
    }
    @Test fun supersededApprovalCannotAuthorizeAnotherTarget()=runTest {
        val f=Fixture();f.rest();val old=f.prompt();f.prompt(ActivityMode.WORK)
        assertEquals(TransitionStatus.STALE,f.coordinator.confirm(old,true).status)
        assertNotNull(f.rest.id)
    }
    @Test fun safetyChangeBeforeConfirmationDoesNotStopSource()=runTest {
        val f=Fixture();f.rest();val token=f.prompt();f.study.ready=false
        assertEquals(TransitionStatus.BLOCKED,f.coordinator.confirm(token,true).status)
        assertNotNull(f.rest.id);assertEquals(0,f.study.starts)
    }
    @Test fun safetyIsRecheckedAfterOldActivityStops()=runTest {
        val f=Fixture();f.rest();val token=f.prompt();val gate=CompletableDeferred<Unit>();f.rest.stopGate=gate
        val result=async { f.coordinator.confirm(token,true) };runCurrent();f.study.ready=false;gate.complete(Unit)
        assertEquals(TransitionStatus.BLOCKED,result.await().status);assertEquals(0,f.study.starts)
    }
    @Test fun activitiesNeverOverlapDuringSuccessiveSwitches()=runTest {
        val f=Fixture()
        for(mode in ActivityMode.entries) {
            val result=f.coordinator.request(mode)
            if(result.status==TransitionStatus.CONFIRMATION_REQUIRED) f.coordinator.confirm(result.state.confirmation!!,true)
            assertEquals(1,f.adapters.values.count { it.id!=null })
        }
    }
    @Test fun transitionWithExistingModeStartManagerKeepsRealWaiting()=runTest {
        val safety = object : WaitingSafetyGateway {
            override val drivingEvents = DrivingEventBuffer()
            override val state = MutableStateFlow(WaitingSafety(VehicleMotion.PARKED, false))
            override fun start() = Unit
            override fun stop() = Unit
            override fun close() = Unit
        }
        val waiting=com.pleosstay.app.waiting.data.LocalWaitingRepository(
            com.pleosstay.app.waiting.MemoryWaitingStore(),com.pleosstay.app.waiting.MemoryJournal(),
            {safety.state.value},com.pleosstay.app.waiting.domain.WaitingClock { 0 },dispatcher=StandardTestDispatcher(testScheduler))
        waiting.restore();waiting.start(40)
        val before=waiting.sessions.value
        val manager=ModeStartManager(waiting,safety)
        fun adapter(mode:ActivityMode)=object:ActivityAdapter {
            private var id:String?=null
            override val mode=mode
            override fun sessionId()=id
            override suspend fun preflight()=ActivityOperation(safety.state.value.canUse)
            override suspend fun start():ActivityOperation {
                val ready=manager.start(ModeStartRequest(mode,20)) is ModeStartResult.Ready
                if(ready)id=mode.name
                return ActivityOperation(ready)
            }
            override suspend fun resume()=ActivityOperation.Done
            override suspend fun stop():ActivityOperation { id=null;return ActivityOperation.Done }
        }
        val coordinator=ActivityTransitionCoordinator(listOf(adapter(ActivityMode.REST),adapter(ActivityMode.STUDY)))
        coordinator.request(ActivityMode.REST)
        val prompt=coordinator.request(ActivityMode.STUDY)
        assertEquals(TransitionStatus.STARTED,coordinator.confirm(prompt.state.confirmation!!,true).status)
        assertEquals(before,waiting.sessions.value)
        assertEquals(TransitionStatus.STOPPED, coordinator.end().status)
        assertEquals(before, waiting.sessions.value)
    }
    @Test fun persistedActivityMustRecoverBeforeAnyOtherStart()=runTest {
        val f=Fixture();f.rest.recovered=false
        assertEquals(TransitionStatus.BLOCKED,f.coordinator.request(ActivityMode.STUDY).status)
        assertEquals(0,f.study.starts)
        f.rest.id="restored-rest";f.rest.recovered=true
        assertEquals(TransitionStatus.CONFIRMATION_REQUIRED,f.coordinator.request(ActivityMode.STUDY).status)
    }
    @Test fun liveQueryReflectsNaturalActivityEnd()=runTest {
        val f=Fixture();f.rest();f.rest.id=null
        assertNull(f.coordinator.currentState().active)
    }
    @Test fun existingMultipleLegacySessionsFailClosed()=runTest {
        val f=Fixture();f.rest.id="rest";f.study.id="study"
        assertEquals(TransitionStatus.BLOCKED,f.coordinator.request(ActivityMode.WORK).status)
        assertEquals(0,f.adapters.getValue(ActivityMode.WORK).starts)
    }
    @Test fun cancellationUnlocksCoordinatorWithoutAutoRestart()=runTest {
        val f=Fixture();f.rest();val token=f.prompt();f.rest.stopGate=CompletableDeferred()
        val job=launch { f.coordinator.confirm(token,true) };runCurrent();job.cancelAndJoin()
        assertFalse(f.coordinator.state.value.transitioning)
        assertNotNull(f.rest.id);assertEquals(0,f.study.starts)
    }
}
