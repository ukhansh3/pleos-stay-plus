package com.pleosstay.app.waiting.safety

import android.car.Car
import android.car.drivingstate.CarUxRestrictions
import android.car.drivingstate.CarUxRestrictionsManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** UX restriction is NOT a validated park/drive signal. No Vehicle API is guessed here. */
class PlatformWaitingSafetyGateway(context: Context) : WaitingSafetyGateway {
    override val drivingEvents = DrivingEventBuffer() // No verified driving producer yet.
    private val context = context.applicationContext
    private val mutableState = MutableStateFlow(WaitingSafety())
    override val state = mutableState.asStateFlow()
    private var car: Car? = null
    private var manager: CarUxRestrictionsManager? = null
    private var started = false
    private var generation = 0
    override fun start() {
        if (started) return
        started = true
        val token = ++generation
        mutableState.value = WaitingSafety()
        try {
            car = Car.createCar(context, Handler(Looper.getMainLooper()), Car.CAR_WAIT_TIMEOUT_DO_NOT_WAIT) { connected, ready ->
                if (!started || generation != token) return@createCar
                mutableState.value = WaitingSafety()
                if (!ready) {
                    manager = null
                } else {
                    try {
                        manager = connected.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as? CarUxRestrictionsManager
                        manager?.registerListener { restrictions ->
                            if (started && generation == token) update(restrictions)
                        }
                        manager?.currentCarUxRestrictions?.let(::update)
                    } catch (_: Exception) {
                        mutableState.value = WaitingSafety(message = "플랫폼 주행 제한 연결 실패: Waiting 사용 불가")
                    }
                }
            }
        } catch (_: Exception) {
            mutableState.value = WaitingSafety(message = "플랫폼 주행 제한을 확인할 수 없습니다")
        } catch (_: LinkageError) {
            mutableState.value = WaitingSafety(message = "이 플랫폼 버전에서 차량 제한 API를 사용할 수 없습니다")
        }
    }
    private fun update(restrictions: CarUxRestrictions) {
        val restricted = restrictions.isRequiresDistractionOptimization ||
            restrictions.activeRestrictions != CarUxRestrictions.UX_RESTRICTIONS_BASELINE
        mutableState.value = WaitingSafety(
            motion = VehicleMotion.UNKNOWN,
            uxRestricted = restricted,
            message = if (restricted) "플랫폼 주행 제한 중입니다" else "플랫폼 제한 없음 · 주차 확인 신호는 미검증입니다",
        )
    }
    override fun stop() {
        started = false
        generation++
        mutableState.value = WaitingSafety()
        runCatching { manager?.unregisterListener() }
        manager = null
        runCatching { car?.disconnect() }
        car = null
    }
    override fun close() = stop()
}


