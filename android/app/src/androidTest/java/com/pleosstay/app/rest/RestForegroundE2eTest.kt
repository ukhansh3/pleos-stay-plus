package com.pleosstay.app.rest

import android.app.AlarmManager
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.os.Bundle
import android.os.SystemClock
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pleosstay.app.MainActivity
import com.pleosstay.app.restRuntime
import com.pleosstay.app.waiting.data.RoomWaitingStore
import com.pleosstay.app.waiting.domain.WaitingStatus
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Opt-in, real five-minute PLEOS UI test; never changes production presets or the device clock. */
@RunWith(AndroidJUnit4::class)
class RestForegroundE2eTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()

    @Test fun napConsentTimeoutSoundStopAndWaitingRemainIndependent() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("restE2E") == "true")
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        assertFalse(app.getSystemService(AlarmManager::class.java).canScheduleExactAlarms())
        val controller = restRuntime(app)
        ui.onNodeWithText("휴식").performClick()
        ui.waitUntil(30_000) { controller.state.value.ready && controller.safetyState.value.canUse }
        if (controller.state.value.session != null) {
            if (controller.state.value.session?.end == null) {
                ui.onNodeWithText("휴식 종료").performScrollTo().performClick()
                ui.waitUntil(10_000) { controller.state.value.session?.end != null }
            }
            ui.onNodeWithText("다시 휴식 시작").performScrollTo().performClick()
        }
        val restBefore = controller.state.value.session
        ui.onNodeWithText("파워냅 · 20분").performScrollTo().performClick()
        ui.onNodeWithText("휴식 시간(5~720분)").performTextReplacement("5")
        ui.onNodeWithText("휴식 시작").performScrollTo().performClick()
        ui.onNodeWithText("현재는 앱 실행 중 알람을 사용할 수 있어요").assertExists()
        val waitingStore = RoomWaitingStore.open(app)
        val before = waitingStore.all()
        ui.onNodeWithText("뒤로가기").performClick()
        ui.waitForIdle()
        assertEquals(before, waitingStore.all())
        assertEquals(restBefore, controller.state.value.session)
        ui.onNodeWithText("휴식 시작").performScrollTo().performClick()
        ui.onNodeWithText("앱 실행 중 알람으로 휴식 시작").performClick()
        ui.waitUntil(15_000) { controller.state.value.session != null || controller.state.value.decision != null }
        controller.state.value.decision?.let { decision ->
            ui.onNodeWithText("대기 " + decision.prompt.extensionMinutes + "분 연장").performClick()
        }
        ui.waitUntil(15_000) { controller.state.value.session != null }
        val session = checkNotNull(controller.state.value.session)
        assertEquals(RestAlarmMode.FOREGROUND_ONLY, session.alarmMode)
        assertEquals(5, session.preset.minutes)
        assertEquals(20, RestPreset.defaults[1].minutes)
        val waiting = waitingStore.all().single { it.id == session.waitingId }
        assertEquals(WaitingStatus.ACTIVE, waiting.status)
        report("REST_STARTED deadline=" + session.endsAt)

        // Internal navigation must leave the same timer alive.
        ui.onNodeWithText("홈으로 이동").performScrollTo().performClick()
        ui.onNodeWithText("휴식").performClick()
        assertEquals(session.alarmToken, controller.state.value.session?.alarmToken)
        report("INTERNAL_NAVIGATION_PRESERVED")

        ui.waitUntil(330_000) { controller.state.value.session?.end == RestEnd.TIME }
        ui.waitUntil(5_000) { controller.state.value.ringing }
        val audio = app.getSystemService(AudioManager::class.java)
        ui.waitUntil(5_000) {
            audio.activePlaybackConfigurations.any { it.audioAttributes.usage == AudioAttributes.USAGE_ALARM }
        }
        report("ALARM_ACTIVE actual=" + System.currentTimeMillis() + " deadline=" + session.endsAt)
        // Keep output active briefly for external AudioFlinger/loopback diagnostics.
        SystemClock.sleep(10_000)
        ui.onNodeWithText("알람 정지").performScrollTo().performClick()
        ui.waitUntil(5_000) { !controller.state.value.ringing }
        ui.waitUntil(5_000) {
            audio.activePlaybackConfigurations.none { it.audioAttributes.usage == AudioAttributes.USAGE_ALARM }
        }
        assertEquals(waiting, waitingStore.all().single { it.id == session.waitingId })
        report("ALARM_STOPPED_WAITING_UNCHANGED")
    }

    private fun report(text: String) {
        InstrumentationRegistry.getInstrumentation().sendStatus(0, Bundle().apply { putString("stream", text + "\n") })
    }
}

@RunWith(AndroidJUnit4::class)
class RestAlarmOutputTest {
    @Test fun bothAlarmStylesAcquireFocusPlayAndReleaseOutput() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext
        val audio = app.getSystemService(AudioManager::class.java)
        val player = RestAlarmPlayer(app)
        for (style in RestAlert.entries) {
            try {
                instrumentation.runOnMainSync { player.play(style) }
                SystemClock.sleep(1_000)
                assertTrue(player.state.value.playing)
                assertTrue(audio.activePlaybackConfigurations.any {
                    it.audioAttributes.usage == AudioAttributes.USAGE_ALARM
                })
            } finally {
                instrumentation.runOnMainSync { player.stop() }
            }
            SystemClock.sleep(300)
            assertFalse(player.state.value.playing)
            assertTrue(audio.activePlaybackConfigurations.none {
                it.audioAttributes.usage == AudioAttributes.USAGE_ALARM
            })
        }
    }
}
