package com.pleosstay.app.ui

import com.pleosstay.app.waiting.safety.VehicleSafetySignals
import org.junit.Assert.*
import org.junit.Test

class ActivityForegroundOwnerTest {
    @Test fun oldActivityPauseAndStopCannotHideNewTopActivity() {
        val changes = mutableListOf<Boolean>()
        val gate = ActivityForegroundOwner(changes::add)
        val old = Any(); val top = Any()
        gate.claim(old)
        gate.claim(top)
        gate.release(old) // delayed onPause
        gate.release(old) // delayed onStop
        assertEquals(listOf(true, true), changes)
        gate.release(top)
        assertEquals(listOf(true, true, false), changes)
    }

    @Test fun duplicateCallbacksDoNotReinvalidateFreshSignals() {
        val changes = mutableListOf<Boolean>()
        val gate = ActivityForegroundOwner(changes::add)
        val owner = Any()
        gate.release(owner)
        gate.claim(owner); gate.claim(owner)
        gate.release(owner); gate.release(owner)
        gate.claim(owner)
        assertEquals(listOf(true, false, true), changes)
    }

    @Test fun activityRecreationAndReturnPreserveFailClosedVehicleChecks() {
        val signals = VehicleSafetySignals().also { it.restricted = false }
        val gate = ActivityForegroundOwner { foreground ->
            signals.foreground = foreground
            if (foreground) { signals.invalidateGear(); signals.invalidateSpeed() }
        }
        val old = Any(); val top = Any(); val now = 10_000_000_000L
        gate.claim(old)
        assertFalse(signals.snapshot(now).canUse)
        signals.gear(true, now, now); signals.speed(0f, now, now, 1000)
        assertTrue(signals.snapshot(now).canUse)
        gate.claim(top)
        assertFalse(signals.snapshot(now).canUse) // no stale P/zero-speed reuse
        signals.gear(true, now, now); signals.speed(0f, now, now, 1000)
        gate.release(old)
        assertTrue(signals.snapshot(now).canUse)
        gate.release(top)
        assertFalse(signals.snapshot(now).canUse)
        gate.claim(top)
        assertFalse(signals.snapshot(now).canUse)
    }
}
