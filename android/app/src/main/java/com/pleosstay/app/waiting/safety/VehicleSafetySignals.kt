package com.pleosstay.app.waiting.safety

/** Pure policy: AAOS values are translated by the platform adapter, never invented here. */
internal class VehicleSafetySignals {
    private var park: Boolean? = null
    private var gearTimestamp = -1L
    private var gearCheckedAt = -1L
    private var speed: Float? = null
    private var speedTimestamp = -1L
    private var moving = false
    private var drivingObservedAt: Long? = null
    var restricted = true
    var foreground = false

    fun gear(isPark: Boolean?, timestamp: Long, now: Long) {
        if (timestamp < gearTimestamp) return
        if (timestamp < 0 || timestamp > now) { invalidateGear(); return }
        gearTimestamp = timestamp
        gearCheckedAt = now
        park = isPark
    }

    /** Returns receipt time only on a newly confirmed movement, never a guessed departure time. */
    fun speed(value: Float?, timestamp: Long, now: Long, receivedAt: Long): Long? {
        if (timestamp < speedTimestamp) return null
        if (value == null || !value.isFinite() || timestamp < 0 || timestamp > now || now - timestamp > MAX_AGE_NS) {
            invalidateSpeed()
            return null
        }
        speedTimestamp = timestamp
        speed = value
        if (value == 0f) {
            moving = false
            drivingObservedAt = null
            return null
        }
        if (moving) return null
        moving = true
        drivingObservedAt = receivedAt
        return receivedAt
    }

    fun invalidateGear() { park = null; gearCheckedAt = -1 }
    fun invalidateSpeed() { speed = null }

    fun snapshot(now: Long): WaitingSafety {
        val freshSpeed = speed?.takeIf { now >= speedTimestamp && now - speedTimestamp <= MAX_AGE_NS }
        val freshGear = gearCheckedAt >= 0 && now >= gearCheckedAt && now - gearCheckedAt <= MAX_AGE_NS
        val motion = when {
            freshSpeed != null && freshSpeed != 0f -> VehicleMotion.DRIVING
            freshGear && park == true && freshSpeed == 0f -> VehicleMotion.PARKED
            else -> VehicleMotion.UNKNOWN
        }
        return WaitingSafety(motion, restricted || !foreground,
            observedAt = if (motion == VehicleMotion.DRIVING) drivingObservedAt else null,
            message = when {
                !foreground -> "화면이 비활성 상태입니다"
                restricted -> "플랫폼 주행 제한 중이거나 제한 상태를 확인 중입니다"
                motion == VehicleMotion.DRIVING -> "차량 이동 확인: Waiting 사용 불가"
                motion == VehicleMotion.PARKED -> "P 기어 및 정지 상태 확인"
                park == false -> "P 기어가 아닙니다: Waiting 사용 불가"
                else -> "차량 정보 미수신·오류·갱신 지연: 주차 상태 확인 불가"
            })
    }

    companion object {
        // Adapter requests 5 Hz speed and rechecks properties each second. Fail closed on silence.
        const val MAX_AGE_NS = 3_000_000_000L
    }
}
