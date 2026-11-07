package com.pleosstay.app.rest

import android.content.Intent
import android.os.SystemClock
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pleosstay.app.MainActivity
import com.pleosstay.app.modes.WaitingChoice
import com.pleosstay.app.restRuntime
import com.pleosstay.app.waiting.data.RoomWaitingStore
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Explicit opt-in because this exercises the real app's persistent sessions on a test AVD. */
@RunWith(AndroidJUnit4::class)
class RestForegroundLifecycleTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()

    @Test fun androidHomeAndReturnPreserveDeadlineWithoutBackgroundSound() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("restE2E") == "true")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext
        val controller = restRuntime(app)
        waitFor { controller.state.value.ready && controller.safetyState.value.canUse }
        if (controller.state.value.session?.end == null && controller.state.value.session != null) {
            ui.runOnIdle { controller.end() }
            waitFor { controller.state.value.session?.end != null }
        }
        ui.runOnIdle { controller.start(RestPreset.defaults.first()) }
        waitFor { controller.state.value.alarmConsent != null }
        ui.runOnIdle { controller.acceptForegroundAlarm() }
        waitFor { controller.state.value.session?.end == null && controller.state.value.session != null ||
            controller.state.value.decision != null }
        if (controller.state.value.decision != null) ui.runOnIdle { controller.resolve(WaitingChoice.EXTEND) }
        waitFor { controller.state.value.session?.end == null && controller.state.value.session != null }
        val session = checkNotNull(controller.state.value.session)
        val store = RoomWaitingStore.open(app)
        val waiting = store.all().single { it.id == session.waitingId }
        instrumentation.uiAutomation.executeShellCommand("input keyevent KEYCODE_HOME").close()
        waitFor { controller.state.value.alarmLimitation?.contains("벗어나") == true }
        assertEquals(session, controller.state.value.session)
        assertFalse(controller.state.value.ringing)
        assertNotNull(controller.state.value.alarmLimitation)
        instrumentation.runOnMainSync {
            app.startActivity(Intent(app, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP))
        }
        waitFor { controller.safetyState.value.canUse &&
            ui.activity.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED) &&
            controller.state.value.alarmLimitation?.startsWith("앱 실행 중 알람") == true }
        assertEquals(session, controller.state.value.session)
        assertEquals(waiting, store.all().single { it.id == session.waitingId })
        ui.runOnIdle { controller.end() }
        waitFor { controller.state.value.session?.end == RestEnd.MANUAL }
        assertEquals(waiting, store.all().single { it.id == session.waitingId })
    }

    private fun waitFor(condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 30_000
        while (!condition() && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(100)
        assertTrue("Lifecycle condition timed out", condition())
    }
}
