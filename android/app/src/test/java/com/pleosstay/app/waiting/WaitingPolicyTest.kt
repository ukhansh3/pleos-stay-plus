package com.pleosstay.app.waiting

import com.pleosstay.app.waiting.domain.*
import org.junit.Assert.*
import org.junit.Test

class WaitingPolicyTest {
    private val policy = WaitingPolicy()
    @Test fun durationBoundariesAndInvalidInput() {
        listOf(5, 15, 30, 60, 120, 720).forEach { assertEquals(it * 60_000L, policy.start(it, 1000).plannedEndAt - 1000) }
        listOf(Int.MIN_VALUE, -1, 0, 4, 721, Int.MAX_VALUE).forEach { minutes ->
            assertThrows(IllegalArgumentException::class.java) { policy.start(minutes, 1000) }
        }
    }
    @Test fun extendAddsToDeadlineAndLimitsTotal() {
        val initial = policy.start(30, 1000)
        val extended = policy.extend(initial, 15)
        assertEquals(initial.plannedEndAt + 900_000, extended.plannedEndAt)
        assertEquals(1, extended.revision)
        assertThrows(IllegalArgumentException::class.java) { policy.extend(initial, 691) }
        assertThrows(IllegalArgumentException::class.java) { policy.extend(initial, 0) }
        assertThrows(IllegalArgumentException::class.java) { policy.extend(initial, -1) }
        assertEquals(720 * 60_000L + 1000, policy.extend(initial, 690).plannedEndAt)
    }
    @Test fun overtimeDoesNotEndSessionAndEndedDurationStops() {
        val initial = policy.start(30, 1000)
        val now = initial.plannedEndAt + 600_000
        val time = WaitingTime.calculate(initial, now)
        assertEquals(WaitingStatus.ACTIVE, initial.status)
        assertEquals(600_000L, time.overtimeMillis)
        assertEquals(0L, time.remainingMillis)
        val ended = policy.end(initial, now, EndReason.MANUAL)
        assertEquals(ended, policy.end(ended, now + 1000, EndReason.DRIVING))
        assertEquals(2_400_000L, WaitingTime.calculate(ended, now + 900_000).elapsedMillis)
        assertThrows(IllegalArgumentException::class.java) { policy.extend(ended, 5) }
    }
    @Test fun clockRollbackIsNotInventedAsAnEndTime() {
        val initial = policy.start(5, 10_000)
        assertThrows(IllegalArgumentException::class.java) { policy.end(initial, 9_000, EndReason.DRIVING) }
        assertEquals(0L, WaitingTime.calculate(initial, 9_000).elapsedMillis)
    }
}

