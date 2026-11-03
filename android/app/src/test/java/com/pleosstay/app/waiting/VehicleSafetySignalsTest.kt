package com.pleosstay.app.waiting

import com.pleosstay.app.waiting.safety.*
import org.junit.Assert.*
import org.junit.Test

class VehicleSafetySignalsTest {
    private val now = 10_000_000_000L
    private fun signals() = VehicleSafetySignals().also { it.foreground = true; it.restricted = false }

    @Test fun parkAndFreshZeroSpeedAreBothRequired() {
        val s = signals()
        assertFalse(s.snapshot(now).canUse)
        s.speed(0f, now, now, 1000)
        assertFalse(s.snapshot(now).canUse) // Zero speed alone is not parking.
        s.gear(true, 1, now) // ONCHANGE gear can have an old timestamp.
        assertTrue(s.snapshot(now).canUse)
        s.gear(false, now, now)
        assertFalse(s.snapshot(now).canUse)
        assertEquals(VehicleMotion.UNKNOWN, s.snapshot(now).motion)
    }
    @Test fun onlyRealMovementProducesAnEventAndReverseCounts() {
        val s = signals()
        s.gear(false, now, now)
        assertNull(s.speed(0f, now, now, 1000))
        assertEquals(2000L, s.speed(-1f, now + 1, now + 1, 2000))
        assertEquals(VehicleMotion.DRIVING, s.snapshot(now + 1).motion)
        assertEquals(2000L, s.snapshot(now + 1).observedAt)
        assertNull(s.speed(-2f, now + 2, now + 2, 3000))
    }
    @Test fun restrictionAndBackgroundLockWithoutCreatingMovement() {
        val s = signals()
        s.gear(true, now, now)
        assertNull(s.speed(0f, now, now, 1000))
        s.restricted = true
        assertFalse(s.snapshot(now).canUse)
        assertEquals(VehicleMotion.PARKED, s.snapshot(now).motion)
        s.restricted = false
        s.foreground = false
        assertFalse(s.snapshot(now).canUse)
    }
    @Test fun staleFutureInvalidAndUnavailableSpeedNeverUnlockOrEnd() {
        for (speed in listOf(null, Float.NaN, Float.POSITIVE_INFINITY)) {
            val s = signals()
            s.gear(true, now, now)
            assertNull(s.speed(speed, now, now, 1000))
            assertFalse(s.snapshot(now).canUse)
        }
        for (timestamp in listOf(0L, now + 1)) {
            val s = signals()
            s.gear(true, now, now)
            assertNull(s.speed(1f, timestamp, now, 1000))
            assertFalse(s.snapshot(now).canUse)
        }
    }
    @Test fun signalSilenceAndGearErrorInvalidateParking() {
        val s = signals()
        s.gear(true, now, now)
        s.speed(0f, now, now, 1000)
        assertTrue(s.snapshot(now).canUse)
        assertFalse(s.snapshot(now + VehicleSafetySignals.MAX_AGE_NS + 1).canUse)
        s.invalidateGear()
        assertFalse(s.snapshot(now).canUse)
    }
    @Test fun lateCallbacksCannotReplaceNewerMovingOrNonParkState() {
        val s = signals()
        s.gear(false, now, now)
        s.gear(true, now - 1, now)
        s.speed(1f, now, now, 1000)
        assertNull(s.speed(0f, now - 1, now, 2000))
        assertEquals(VehicleMotion.DRIVING, s.snapshot(now).motion)
        s.speed(0f, now + 1, now + 1, 2000)
        assertFalse(s.snapshot(now + 1).canUse)
    }
}
