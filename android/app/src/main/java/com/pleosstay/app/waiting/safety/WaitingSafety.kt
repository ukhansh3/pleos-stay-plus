package com.pleosstay.app.waiting.safety

import kotlinx.coroutines.flow.StateFlow

enum class VehicleMotion { UNKNOWN, PARKED, DRIVING }
data class WaitingSafety(
    val motion: VehicleMotion = VehicleMotion.UNKNOWN,
    val uxRestricted: Boolean = true,
    val observedAt: Long? = null,
    val message: String = "주차 상태를 확인 중입니다: Waiting 조작을 허용하지 않습니다",
) {
    val canUse: Boolean get() = motion == VehicleMotion.PARKED && !uxRestricted
}

// Implementations may emit PARKED/DRIVING only from a validated official signal.
interface WaitingSafetyGateway : AutoCloseable {
    val drivingEvents: DrivingEventBuffer
    val state: StateFlow<WaitingSafety>
    // Rest may retain its timer when the app is hidden, while vehicle checks still apply.
    val restState: StateFlow<WaitingSafety> get() = state
    fun setForeground(foreground: Boolean) = Unit
    fun start()
    fun stop()
}

class UnavailableWaitingSafetyGateway : WaitingSafetyGateway {
    override val drivingEvents = DrivingEventBuffer()
    override val state = kotlinx.coroutines.flow.MutableStateFlow(WaitingSafety(message = "차량 플랫폼 연결 불가: Waiting 사용 불가"))
    override fun start() = Unit
    override fun stop() = Unit
    override fun close() = Unit
}

/** Pending observations survive rapid state changes and remain until acknowledged.
 * In-memory handoff only: the repository journal supplies process-restart recovery.
 * A validated producer must record the observation when it receives the official signal.
 */
data class DrivingObservation(val id: String, val observedAt: Long)
class DrivingEventBuffer {
    private val pending = kotlinx.coroutines.flow.MutableStateFlow<List<DrivingObservation>>(emptyList())
    val state: StateFlow<List<DrivingObservation>> = pending
    @Synchronized fun record(observedAt: Long) {
        pending.value = pending.value + DrivingObservation(java.util.UUID.randomUUID().toString(), observedAt)
    }
    @Synchronized fun acknowledge(id: String) {
        pending.value = pending.value.filterNot { it.id == id }
    }
}
