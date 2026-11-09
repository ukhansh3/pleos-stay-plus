package com.pleosstay.app.rest

import com.pleosstay.app.modes.ModeStartManager
import com.pleosstay.app.modes.WaitingChoice
import com.pleosstay.app.waiting.MemoryJournal
import com.pleosstay.app.waiting.MemoryWaitingStore
import com.pleosstay.app.waiting.data.LocalWaitingRepository
import com.pleosstay.app.waiting.domain.WaitingClock
import com.pleosstay.app.waiting.domain.WaitingStatus
import com.pleosstay.app.waiting.safety.DrivingEventBuffer
import com.pleosstay.app.waiting.safety.VehicleMotion
import com.pleosstay.app.waiting.safety.WaitingSafety
import com.pleosstay.app.waiting.safety.WaitingSafetyGateway
import kotlinx.coroutines.async
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RestControllerTest {
    @Test fun presetsCanBeCopiedEditedAndDeleted() = runTest {
        val f = Fixture(this)
        f.restore()
        assertEquals(listOf(10, 20, 30, 60), RestPreset.defaults.map { it.minutes })
        assertEquals(80, RestPreset.defaults.last().target)
        val builtin = RestPreset.defaults.first()
        f.rest.savePreset(builtin.copy(name = "내 휴식"), null)
        runCurrent()
        val saved = f.store.savedPresets.single()
        assertNotEquals(builtin.id, saved.id)
        f.rest.savePreset(saved.copy(name = "짧은 휴식", minutes = 15), saved.id)
        runCurrent()
        assertEquals("짧은 휴식", f.store.savedPresets.single().name)
        f.rest.deletePreset(saved.id)
        runCurrent()
        assertTrue(f.store.savedPresets.isEmpty())
        assertEquals(4, RestPreset.defaults.size)
    }

    @Test fun startDeduplicatesAndManualEndLeavesWaitingActive() = runTest {
        val f = Fixture(this)
        f.restore()
        f.rest.start(rain)
        f.rest.start(rain)
        runCurrent()
        val session = checkNotNull(f.store.saved)
        assertEquals(1, f.alarms.scheduled.size)
        assertEquals(session.waitingId, f.waiting.sessions.value.single().id)
        assertTrue(f.player.playing)
        f.rest.pauseSound(true)
        runCurrent()
        assertFalse(f.player.playing)
        f.rest.pauseSound(false)
        runCurrent()
        assertTrue(f.player.playing)
        f.rest.end()
        runCurrent()
        assertEquals(RestEnd.MANUAL, f.store.saved?.end)
        assertTrue(f.alarms.scheduled.isEmpty())
        assertFalse(f.player.playing)
        assertEquals(WaitingStatus.ACTIVE, f.waiting.sessions.value.single().status)
    }

    @Test fun cancelAndShortenKeepOriginalWaiting() = runTest {
        val f = Fixture(this)
        f.restore(10)
        val original = f.waiting.sessions.value.single()
        f.rest.start(rain)
        runCurrent()
        assertNotNull(f.rest.state.value.decision)
        f.rest.resolve(WaitingChoice.BACK)
        runCurrent()
        assertNull(f.store.saved)
        f.rest.start(rain)
        runCurrent()
        f.rest.resolve(WaitingChoice.SHORTEN)
        runCurrent()
        assertEquals(original, f.waiting.sessions.value.single())
        assertEquals(original.plannedEndAt, f.store.saved?.endsAt)
    }

    @Test fun approvedExtensionUsesFixedDeadlineAndReplacesAlarm() = runTest {
        val f = Fixture(this)
        f.restore()
        f.rest.start(rain)
        runCurrent()
        val original = checkNotNull(f.store.saved)
        f.now += 5 * REST_MINUTE
        f.rest.extend(10)
        runCurrent()
        assertEquals(original, f.store.saved)
        assertEquals(original.alarmToken, f.alarms.scheduled.keys.single())
        f.now += REST_MINUTE
        f.rest.resolve(WaitingChoice.EXTEND)
        runCurrent()
        val updated = checkNotNull(f.store.saved)
        assertEquals(original.endsAt + 10 * REST_MINUTE, updated.endsAt)
        assertEquals(updated.endsAt, f.waiting.sessions.value.single().plannedEndAt)
        assertEquals(updated.alarmToken, f.alarms.scheduled.keys.single())
    }

    @Test fun failedAlarmReplacementKeepsPreviousAlarmAndSession() = runTest {
        val f = Fixture(this)
        f.restore(90)
        f.rest.start(rain)
        runCurrent()
        val previous = checkNotNull(f.store.saved)
        f.alarms.failSchedule = true
        f.rest.extend(10)
        runCurrent()
        assertEquals(previous, f.store.saved)
        assertEquals(previous.alarmToken, f.alarms.scheduled.keys.single())
        assertNotNull(f.rest.state.value.message)
    }

    @Test fun failedAlarmReservationBeforeConfirmationLeavesWaitingUnchanged() = runTest {
        val f = Fixture(this)
        f.restore()
        f.rest.start(rain)
        runCurrent()
        val previous = f.waiting.sessions.value.single()
        f.rest.extend(10)
        runCurrent()
        assertNotNull(f.rest.state.value.decision)
        f.alarms.failSchedule = true
        f.rest.resolve(WaitingChoice.EXTEND)
        runCurrent()
        assertEquals(previous, f.waiting.sessions.value.single())
        assertEquals(f.store.saved?.alarmToken, f.alarms.scheduled.keys.single())
    }

    @Test fun unavailableAlarmDoesNotCreateWaiting() = runTest {
        val f = Fixture(this)
        f.restore()
        f.alarms.permission = false
        f.rest.start(rain)
        runCurrent()
        assertTrue(f.waiting.sessions.value.isEmpty())
        assertNull(f.store.saved)
        assertNotNull(f.rest.state.value.message)
    }

    @Test fun storageErrorCanStartIndependentRestAndKeepItsAlarm() = runTest {
        val f = Fixture(this)
        f.restore()
        f.waitingStore.failWrites = true
        f.rest.start(rain)
        runCurrent()
        assertNotNull(f.rest.state.value.fallback)
        f.rest.independent()
        runCurrent()
        assertNull(checkNotNull(f.store.saved).waitingId)
        assertEquals(1, f.alarms.scheduled.size)
    }

    @Test fun timeoutOnlyRingsOnceForMatchingAlarmToken() = runTest {
        val f = Fixture(this)
        f.restore()
        f.rest.start(rain)
        runCurrent()
        val active = checkNotNull(f.store.saved)
        f.now = active.endsAt
        f.fire("stale")
        assertTrue(f.alarms.rings.isEmpty())
        f.fire(active.alarmToken)
        f.fire(active.alarmToken)
        assertEquals(listOf(RestEnd.TIME), f.alarms.rings)
        assertEquals(RestEnd.TIME, f.store.saved?.end)
    }

    @Test fun restoresSessionAndDoesNotRepeatExpiredAlarm() = runTest {
        val f = Fixture(this)
        val stored = RestSession(rain, f.now, f.now + 10 * REST_MINUTE, null)
        f.store.saved = stored
        f.restore()
        assertEquals(stored, f.rest.state.value.session)
        assertEquals(stored.alarmToken, f.alarms.scheduled.keys.single())
        val expired = Fixture(this)
        expired.store.saved = stored.copy(endsAt = expired.now)
        expired.restore()
        assertEquals(RestEnd.TIME, expired.store.saved?.end)
        assertTrue(expired.alarms.rings.isEmpty())
    }

    @Test fun evTargetAndNearbyTimeoutDoNotRingTwice() = runTest {
        val f = Fixture(this)
        f.restore()
        f.rest.previewEv(true)
        f.battery.value = EvReading(65)
        runCurrent()
        f.rest.start(RestPreset.defaults.last())
        runCurrent()
        val active = checkNotNull(f.store.saved)
        f.now = active.endsAt - 2_000
        f.battery.value = EvReading(80)
        runCurrent()
        assertTrue(checkNotNull(f.store.saved).targetPrompt)
        f.rest.answerTarget(true)
        runCurrent()
        f.battery.value = EvReading(82)
        runCurrent()
        f.now = active.endsAt
        f.fire(active.alarmToken)
        assertEquals(listOf(RestEnd.TARGET), f.alarms.rings)
        assertEquals(RestEnd.TIME, f.store.saved?.end)
    }

    @Test fun evRejectsAlreadyReachedTargetAndPreservesTimeAlarmOnDataLoss() = runTest {
        val f = Fixture(this)
        f.restore()
        f.rest.previewEv(true)
        val preset = RestPreset.defaults.last()
        f.battery.value = EvReading(80)
        runCurrent()
        f.rest.start(preset)
        runCurrent()
        assertNull(f.store.saved)
        f.battery.value = EvReading(66)
        runCurrent()
        f.rest.start(preset)
        runCurrent()
        val active = checkNotNull(f.store.saved)
        f.battery.value = EvReading(message = "연결 해제")
        runCurrent()
        assertEquals(active.alarmToken, f.alarms.scheduled.keys.single())
        f.now = active.endsAt
        f.fire(active.alarmToken)
        assertEquals(listOf(RestEnd.TIME), f.alarms.rings)
    }

    @Test fun backgroundNavigationKeepsSessionButDrivingStopsRestAndAudio() = runTest {
        val f = Fixture(this)
        f.restore()
        f.rest.start(rain)
        runCurrent()
        f.safety.state.value = WaitingSafety(VehicleMotion.PARKED, true)
        runCurrent()
        assertTrue(f.player.playing)
        assertNull(f.store.saved?.end)
        f.safety.restState.value = WaitingSafety(VehicleMotion.DRIVING, true)
        runCurrent()
        assertEquals(RestEnd.SAFETY, f.store.saved?.end)
        assertTrue(f.alarms.scheduled.isEmpty())
        assertFalse(f.player.playing)
    }


    @Test fun foregroundConsentBackDoesNotTouchWaiting() = runTest {
        val f = Fixture(this)
        f.restore()
        f.alarms.permission = false
        f.alarms.foregroundSupported = true
        f.rest.start(rain)
        runCurrent()
        assertEquals(rain, f.rest.state.value.alarmConsent)
        assertTrue(f.waiting.sessions.value.isEmpty())
        f.rest.dismissAlarmConsent()
        assertNull(f.rest.state.value.alarmConsent)
        assertNull(f.store.saved)
    }

    @Test fun foregroundTimeoutPlaysOnceAndStopReleasesSound() = runTest {
        val f = Fixture(this)
        f.startForeground()
        val session = checkNotNull(f.store.saved)
        assertEquals(RestAlarmMode.FOREGROUND_ONLY, session.alarmMode)
        assertTrue(f.alarms.scheduled.isEmpty())
        f.now = session.endsAt
        advanceTimeBy(1_001)
        runCurrent()
        assertEquals(listOf(RestEnd.TIME), f.alarms.rings)
        assertTrue(f.rest.state.value.ringing)
        assertTrue(f.alarms.playback.value.playing)
        advanceTimeBy(2_000)
        runCurrent()
        assertEquals(1, f.alarms.rings.size)
        f.rest.stopAlarm()
        runCurrent()
        assertFalse(f.alarms.playback.value.playing)
        assertFalse(f.rest.state.value.ringing)
        assertEquals(WaitingStatus.ACTIVE, f.waiting.sessions.value.single().status)
    }

    @Test fun foregroundBackgroundExpiryRecoversWithoutLateAlarm() = runTest {
        val f = Fixture(this)
        f.startForeground()
        f.rest.setForeground(false)
        f.now = checkNotNull(f.store.saved).endsAt
        advanceTimeBy(2_000)
        runCurrent()
        assertTrue(f.alarms.rings.isEmpty())
        assertNull(f.store.saved?.end)
        f.rest.setForeground(true)
        runCurrent()
        assertEquals(RestEnd.TIME, f.store.saved?.end)
        assertTrue(f.alarms.rings.isEmpty())
        assertNotNull(f.rest.state.value.alarmLimitation)
        assertEquals(WaitingStatus.ACTIVE, f.waiting.sessions.value.single().status)
    }

    @Test fun foregroundReentryBeforeDeadlineRetainsSessionAndExtension() = runTest {
        val f = Fixture(this)
        f.startForeground()
        val previous = checkNotNull(f.store.saved)
        f.rest.setForeground(false)
        f.now += REST_MINUTE
        f.rest.setForeground(true)
        runCurrent()
        assertEquals(previous.copy(soundPaused = true), f.store.saved)
        assertFalse(f.player.playing)
        f.rest.extend(10)
        runCurrent()
        f.rest.resolve(WaitingChoice.EXTEND)
        runCurrent()
        assertEquals(previous.endsAt + 10 * REST_MINUTE, f.store.saved?.endsAt)
        assertEquals(RestAlarmMode.FOREGROUND_ONLY, f.store.saved?.alarmMode)
        assertTrue(f.alarms.scheduled.isEmpty())
    }

    @Test fun safetyChangeBeforeConsentPreventsWaitingCreation() = runTest {
        val f = Fixture(this)
        f.restore()
        f.alarms.permission = false
        f.alarms.foregroundSupported = true
        f.rest.start(rain)
        runCurrent()
        f.safety.state.value = WaitingSafety()
        f.rest.acceptForegroundAlarm()
        runCurrent()
        assertNull(f.store.saved)
        assertTrue(f.waiting.sessions.value.isEmpty())
    }

    @Test fun foregroundSoundFailureIsNotReportedAsRinging() = runTest {
        val f = Fixture(this)
        f.startForeground()
        f.alarms.failSound = true
        f.now = checkNotNull(f.store.saved).endsAt
        advanceTimeBy(1_001)
        runCurrent()
        assertEquals(RestEnd.TIME, f.store.saved?.end)
        assertFalse(f.rest.state.value.ringing)
        assertTrue(f.rest.state.value.message.orEmpty().contains("오디오"))
    }

    @Test fun foregroundManualEndAndDuplicateConsentLeaveWaitingActive() = runTest {
        val f = Fixture(this)
        f.startForeground()
        f.rest.acceptForegroundAlarm()
        f.rest.acceptForegroundAlarm()
        runCurrent()
        assertEquals(1, f.waiting.sessions.value.size)
        f.rest.end()
        runCurrent()
        f.now += 60 * REST_MINUTE
        advanceTimeBy(1_001)
        runCurrent()
        assertEquals(RestEnd.MANUAL, f.store.saved?.end)
        assertTrue(f.alarms.rings.isEmpty())
        assertEquals(WaitingStatus.ACTIVE, f.waiting.sessions.value.single().status)
    }

    @Test fun foregroundEvTargetAndTimeoutShareOneOutput() = runTest {
        val f = Fixture(this)
        f.startForeground(RestPreset.defaults.last())
        f.now = checkNotNull(f.store.saved).endsAt - 1_000
        f.battery.value = EvReading(80)
        runCurrent()
        assertEquals(listOf(RestEnd.TARGET), f.alarms.rings)
        f.now += 1_000
        advanceTimeBy(1_001)
        runCurrent()
        assertEquals(listOf(RestEnd.TARGET), f.alarms.rings)
        f.rest.stopAlarm()
        assertFalse(f.alarms.playback.value.playing)
    }

    @Test fun foregroundStoredSessionRestoresAndExpiredSessionDoesNotRing() = runTest {
        val f = Fixture(this)
        f.alarms.permission = false
        f.alarms.foregroundSupported = true
        f.store.saved = RestSession(rain, f.now, f.now + REST_MINUTE, null,
            alarmMode = RestAlarmMode.FOREGROUND_ONLY)
        f.restore()
        assertNull(f.store.saved?.end)
        f.now += REST_MINUTE
        advanceTimeBy(1_001)
        runCurrent()
        assertEquals(listOf(RestEnd.TIME), f.alarms.rings)
        val expired = Fixture(this)
        expired.alarms.permission = false
        expired.alarms.foregroundSupported = true
        expired.store.saved = f.store.saved?.copy(end = null, endsAt = expired.now - 1)
        expired.restore()
        assertEquals(RestEnd.TIME, expired.store.saved?.end)
        assertTrue(expired.alarms.rings.isEmpty())
    }

    @Test fun foregroundFailedExtensionPreservesBothDeadlines() = runTest {
        val f = Fixture(this)
        f.startForeground()
        val previous = f.store.saved
        val waiting = f.waiting.sessions.value.single()
        f.alarms.failSchedule = true
        f.rest.extend(10)
        runCurrent()
        assertEquals(previous, f.store.saved)
        assertEquals(waiting, f.waiting.sessions.value.single())
    }

    @Test fun transitionStopDrainsSoundAlarmAndKeepsWaitingAndPresets() = runTest {
        val f=Fixture(this);f.restore();f.rest.start(rain);runCurrent()
        val waiting=f.waiting.sessions.value.single();val presets=f.rest.state.value.presets
        assertTrue(f.rest.stopForTransition().success)
        assertFalse(f.player.playing);assertFalse(f.rest.state.value.ringing)
        assertTrue(f.alarms.scheduled.isEmpty());assertNotNull(f.store.saved?.end)
        assertEquals(waiting,f.waiting.sessions.value.single());assertEquals(presets,f.rest.state.value.presets)
        assertTrue(f.rest.stopForTransition().success)
    }
    @Test fun transitionStopAlsoSilencesNaturallyCompletedAlarm() = runTest {
        val f=Fixture(this);f.startForeground();val session=f.store.saved!!
        f.now=session.endsAt+1;advanceTimeBy(1001);runCurrent()
        assertTrue(f.rest.state.value.ringing)
        assertTrue(f.rest.stopForTransition().success)
        assertFalse(f.rest.state.value.ringing);assertFalse(f.alarms.playback.value.playing)
    }
    @Test fun transitionStopFailureIsReportedAndRetryable() = runTest {
        val f=Fixture(this);f.restore();f.rest.start(rain);runCurrent();f.store.failSave=true
        assertFalse(f.rest.stopForTransition().success);assertNull(f.rest.state.value.session?.end)
        f.store.failSave=false;assertTrue(f.rest.stopForTransition().success)
    }

    @Test fun coordinatedStartRequiresExplicitConsentBeforeIndependentRest()=runTest {
        val f=Fixture(this);f.restore();f.waitingStore.failWrites=true
        val first=async { f.rest.startForTransition(rain) };runCurrent()
        assertTrue(first.await().needsInput);assertNull(f.rest.transitionSessionId())
        assertNotNull(f.rest.state.value.fallback)
        assertTrue(f.rest.startForTransition(rain).needsInput);assertTrue(f.alarms.scheduled.isEmpty())
        val approved=async { f.rest.startForTransition(rain,withoutWaiting=true) };runCurrent()
        assertTrue(approved.await().success);assertNotNull(f.rest.transitionSessionId())
        assertNull(f.store.saved!!.waitingId);assertEquals(1,f.alarms.scheduled.size)
        assertTrue(f.waiting.sessions.value.isEmpty())
    }

    @Test fun sessionVolumeMuteAndExplicitPresetSavePreserveWaiting() = runTest {
        val f=Fixture(this);f.restore();f.rest.start(rain);runCurrent()
        val waitingId=f.store.saved!!.waitingId
        assertEquals(100,f.store.saved!!.preset.volume)
        f.rest.changeSound(volume=35);runCurrent()
        assertEquals(35,f.rest.state.value.session!!.preset.volume)
        assertTrue(f.store.savedPresets.isEmpty())
        f.rest.changeSound(muted=true);runCurrent();assertFalse(f.player.playing)
        f.rest.changeSound(muted=false);runCurrent();assertTrue(f.player.playing)
        assertEquals(35,f.rest.state.value.session!!.preset.volume)
        f.rest.saveCurrentSoundPreset();runCurrent();assertEquals(35,f.store.savedPresets.single().volume)
        f.rest.changeSound(sound=RestSound.SILENT);runCurrent();assertFalse(f.player.playing)
        assertEquals(waitingId,f.waiting.sessions.value.single().id)
    }

    @Test fun focusResumeGateRejectsUnsafeHiddenPausedExpiredOrMutedSessions() {
        val session=RestSession(rain,100,1000,null)
        assertTrue(restSoundAllowed(session,true,true,200,false))
        assertFalse(restSoundAllowed(session,false,true,200,false))
        assertFalse(restSoundAllowed(session,true,false,200,false))
        assertFalse(restSoundAllowed(session,true,true,1000,false))
        assertFalse(restSoundAllowed(session,true,true,200,true))
        assertFalse(restSoundAllowed(session.copy(soundPaused=true),true,true,200,false))
        assertFalse(restSoundAllowed(session.copy(preset=rain.copy(muted=true)),true,true,200,false))
        assertFalse(restSoundAllowed(session.copy(preset=rain.copy(volume=0)),true,true,200,false))
    }

    @Test fun soundPersistenceFailureDoesNotReportPresetSuccessOrEndSession() = runTest {
        val f=Fixture(this);f.restore();f.rest.start(rain);runCurrent();f.store.failSave=true
        f.rest.changeSound(volume=20);runCurrent()
        assertNull(f.rest.state.value.session!!.end)
        assertNotNull(f.rest.state.value.message)
        assertTrue(f.store.savedPresets.isEmpty())
        assertEquals(WaitingStatus.ACTIVE,f.waiting.sessions.value.single().status)
    }

    @Test fun foregroundLossDoesNotRestartRainUntilUserResumes() = runTest {
        val f = Fixture(this)
        f.restore()
        f.rest.start(rain)
        runCurrent()
        assertTrue(f.player.playing)
        val before = checkNotNull(f.store.saved)

        f.rest.setForeground(false)
        assertFalse(f.player.playing)
        f.rest.setForeground(true)
        runCurrent()
        assertFalse(f.player.playing)
        assertEquals(before.copy(soundPaused = true), f.store.saved)
        assertEquals(before.waitingId, f.waiting.sessions.value.single().id)

        f.rest.pauseSound(false)
        runCurrent()
        assertTrue(f.player.playing)
        assertEquals(before.preset, f.store.saved?.preset)
    }

    @Test fun permanentFocusLossIsNotDroppedWhileSavingPreset() = runTest {
        val f=Fixture(this);f.restore();f.rest.start(rain);runCurrent()
        f.rest.savePreset(rain,null);assertTrue(f.rest.state.value.busy)
        f.player.focusLost?.invoke();runCurrent()
        assertTrue(f.rest.state.value.session!!.soundPaused)
        assertFalse(f.player.playing)
    }

    private class Fixture(private val scope: TestScope) {
        var now = 100_000L
        val waitingStore = MemoryWaitingStore()
        val safety = FakeSafety()
        val store = FakeStore()
        val alarms = FakeAlarms()
        val player = FakePlayer()
        val battery = MutableStateFlow(EvReading())
        private val dispatcher = StandardTestDispatcher(scope.testScheduler)
        val waiting = LocalWaitingRepository(waitingStore, MemoryJournal(), { safety.state.value },
            WaitingClock { now }, dispatcher = dispatcher)
        val rest = RestController(store, alarms, player, RestBattery { battery }, ModeStartManager(waiting, safety),
            safety, scope.backgroundScope, dispatcher) { now }

        suspend fun restore(waitingMinutes: Int? = null) {
            waiting.restore()
            waitingMinutes?.let { waiting.start(it) }
            rest.setForeground(true)
            rest.restore()
            scope.runCurrent()
        }

        suspend fun startForeground(preset: RestPreset = rain) {
            restore()
            alarms.permission = false
            alarms.foregroundSupported = true
            if (preset.ev) {
                rest.previewEv(true)
                battery.value = EvReading(60)
                scope.runCurrent()
            }
            rest.start(preset)
            scope.runCurrent()
            rest.acceptForegroundAlarm()
            scope.runCurrent()
        }

        fun fire(token: String) {
            rest.receive(RestAlarm.ACTION_TIME, token) {}
            scope.runCurrent()
        }
    }

    private class FakeSafety : WaitingSafetyGateway {
        override val state = MutableStateFlow(WaitingSafety(VehicleMotion.PARKED, false))
        override val restState = MutableStateFlow(state.value)
        override val drivingEvents = DrivingEventBuffer()
        override fun start() = Unit
        override fun stop() = Unit
        override fun close() = Unit
    }

    private class FakeStore : RestPersistence {
        var savedPresets = emptyList<RestPreset>()
        var saved: RestSession? = null
        var failSave=false
        override fun presets() = savedPresets
        override fun savePresets(values: List<RestPreset>) { savedPresets = values }
        override fun session() = saved
        override fun saveSession(value: RestSession) { check(!failSave);saved = value }
    }

    private class FakeAlarms : RestAlerts {
        val scheduled = mutableMapOf<String, Long>()
        val rings = mutableListOf<RestEnd>()
        var permission = true
        var failSchedule = false
        var foregroundSupported = false
        var failSound = false
        override val playback = MutableStateFlow(RestPlayback())
        override fun mode(style: RestAlert) = if (permission) RestAlarmMode.SYSTEM_EXACT
            else if (foregroundSupported) RestAlarmMode.FOREGROUND_ONLY else RestAlarmMode.UNAVAILABLE
        override fun foregroundProblem(style: RestAlert) = if (foregroundSupported) null else "알람 사용 불가"
        override fun problem(style: RestAlert) = if (permission) null else "알람 권한 없음"
        override fun soundProblem(style: RestAlert) = problem(style)
        override fun schedule(session: RestSession) {
            check(!failSchedule) { "예약 실패" }
            if (session.alarmMode == RestAlarmMode.SYSTEM_EXACT) scheduled[session.alarmToken] = session.endsAt
        }
        override fun cancel(session: RestSession) { scheduled.remove(session.alarmToken) }
        override fun sound(session: RestSession, reason: RestEnd) {
            check(!failSound) { "오디오 포커스 실패" }
            rings += reason
            if (session.alarmMode == RestAlarmMode.FOREGROUND_ONLY) playback.value = RestPlayback(true)
        }
        override fun silence() { playback.value = RestPlayback() }
    }

    private class FakePlayer : RestSoundPlayer {
        var playing = false
        var focusLost: (() -> Unit)? = null
        override fun onPermanentLoss(action: () -> Unit) { focusLost = action }
        override fun play(): Boolean { playing = true; return true }
        override fun stop() { playing = false }
    }

    private companion object {
        val rain = RestPreset.defaults[2]
    }
}
