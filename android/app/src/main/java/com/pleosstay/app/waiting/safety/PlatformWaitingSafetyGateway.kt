package com.pleosstay.app.waiting.safety

import android.car.Car
import android.car.VehicleGear
import android.car.VehiclePropertyIds
import android.car.drivingstate.CarUxRestrictions
import android.car.drivingstate.CarUxRestrictionsManager
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Pleos Standard First: public AAOS gear/speed properties, plus independent UX restrictions. */
class PlatformWaitingSafetyGateway(context: Context) : WaitingSafetyGateway {
    override val drivingEvents = DrivingEventBuffer()
    private val context = context.applicationContext
    private val mutableState = MutableStateFlow(WaitingSafety())
    override val state = mutableState.asStateFlow()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var car: Car? = null
    private var uxManager: CarUxRestrictionsManager? = null
    private var connectionJob: Job? = null
    private var watchdog: Job? = null
    private var started = false
    private var foreground = false
    private var failureMessage: String? = null
    private var generation = 0
    private var signals = VehicleSafetySignals()

    override fun setForeground(foreground: Boolean) {
        this.foreground = foreground
        signals.foreground = foreground
        if (foreground) {
            // Re-entry must not expose values retained while the UI was hidden.
            signals.invalidateGear()
            signals.invalidateSpeed()
        }
        publish()
    }

    override fun start() {
        if (started) return
        started = true
        failureMessage = null
        val token = ++generation
        signals = VehicleSafetySignals().also { it.foreground = foreground }
        publish()
        watchdog = scope.launch {
            while (isActive) {
                delay(250)
                if (!hasPermissions()) {
                    signals.invalidateGear()
                    signals.invalidateSpeed()
                }
                publish()
            }
        }
        try {
            car = Car.createCar(context, Handler(Looper.getMainLooper()), Car.CAR_WAIT_TIMEOUT_DO_NOT_WAIT) { connected, ready ->
                if (!started || generation != token) return@createCar
                connectionJob?.cancel()
                runCatching { uxManager?.unregisterListener() }
                uxManager = null
                // Invalidate callbacks from the previous connection, including queued events.
                val connection = ++connectionGeneration
                failureMessage = null
                signals = VehicleSafetySignals().also { it.foreground = foreground }
                publish()
                if (!ready) {
                    uxManager = null
                } else {
                    connect(connected, token, connection)
                }
            }
        } catch (_: Exception) { fail("차량 플랫폼 연결 실패") }
        catch (_: LinkageError) { fail("이 플랫폼에서 차량 API를 사용할 수 없습니다") }
    }

    private var connectionGeneration = 0
    private fun current(token: Int, connection: Int) = started && generation == token && connectionGeneration == connection
    @Suppress("DEPRECATION") // Public Android 14 register/unregister API; newer subscribe API is unavailable there.
    private fun connect(connected: Car, token: Int, connection: Int) {
        try {
            uxManager = connected.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as? CarUxRestrictionsManager
            uxManager?.registerListener { restrictions ->
                if (current(token, connection)) updateRestrictions(restrictions)
            }
            uxManager?.currentCarUxRestrictions?.let(::updateRestrictions)
            val properties = connected.getCarManager(Car.PROPERTY_SERVICE) as? CarPropertyManager
                ?: return fail("차량 속성 서비스 미지원")
            val callback = object : CarPropertyManager.CarPropertyEventCallback {
                override fun onChangeEvent(value: CarPropertyValue<*>) {
                    if (current(token, connection)) accept(value)
                }
                override fun onErrorEvent(propertyId: Int, areaId: Int) {
                    if (current(token, connection)) invalidate(propertyId)
                }
            }
            connectionJob = scope.launch {
                try {
                    val supported = withContext(Dispatchers.IO) {
                        val ids = properties.propertyList.map { it.propertyId }
                        GEAR in ids && SPEED in ids
                    }
                    if (!supported || !hasPermissions()) {
                        fail("기어·속도 정보 미지원 또는 차량 정보 권한 없음")
                        return@launch
                    }
                    // Public pre-Android-16 subscription API, compatible with the Pleos API 34 image.
                    @Suppress("DEPRECATION")
                    val subscribed = withContext(Dispatchers.IO) {
                        properties.registerCallback(callback, GEAR, CarPropertyManager.SENSOR_RATE_ONCHANGE) &&
                            properties.registerCallback(callback, SPEED, CarPropertyManager.SENSOR_RATE_UI)
                    }
                    if (!subscribed) { fail("차량 정보 구독 실패"); return@launch }
                    while (isActive && current(token, connection)) {
                        // Reads may block: never perform them on the UI thread. Also recheck status
                        // because an ONCHANGE property need not send an event when status changes.
                        for (id in listOf(GEAR, SPEED)) {
                            try {
                                val value = withContext(Dispatchers.IO) { properties.getProperty<Any>(id, 0) }
                                if (current(token, connection)) {
                                    if (value == null) invalidate(id) else accept(value)
                                }
                            } catch (cancelled: CancellationException) { throw cancelled }
                            catch (_: Exception) { if (current(token, connection)) invalidate(id) }
                        }
                        delay(1_000)
                    }
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { if (current(token, connection)) fail("차량 정보 연결 실패") }
                catch (_: LinkageError) { if (current(token, connection)) fail("차량 속성 API 버전 미지원") }
                finally {
                    withContext(NonCancellable + Dispatchers.IO) { runCatching { properties.unregisterCallback(callback) } }
                }
            }
        } catch (_: Exception) { fail("차량 정보 또는 플랫폼 제한 연결 실패") }
        catch (_: LinkageError) { fail("차량 API 버전 미지원") }
    }

    private fun hasPermissions() = context.checkSelfPermission(Car.PERMISSION_POWERTRAIN) == PackageManager.PERMISSION_GRANTED &&
        context.checkSelfPermission(Car.PERMISSION_SPEED) == PackageManager.PERMISSION_GRANTED

    @Suppress("DEPRECATION")
    private fun accept(value: CarPropertyValue<*>?) {
        if (value == null) return
        if (value.areaId != 0 || !hasPermissions() || value.status != CarPropertyValue.STATUS_AVAILABLE) {
            invalidate(value.propertyId)
            return
        }
        val now = SystemClock.elapsedRealtimeNanos()
        var drivingAt: Long? = null
        when (value.propertyId) {
            GEAR -> {
                val gear = value.value as? Int
                signals.gear(when (gear) { null, VehicleGear.GEAR_UNKNOWN -> null; VehicleGear.GEAR_PARK -> true; else -> false }, value.timestamp, now)
            }
            SPEED -> drivingAt = signals.speed(value.value as? Float, value.timestamp, now, System.currentTimeMillis())
        }
        publish() // Lock synchronously before queuing any database work.
        drivingAt?.let(drivingEvents::record)
        publish()
    }
    private fun updateRestrictions(restrictions: CarUxRestrictions) {
        signals.restricted = restrictions.isRequiresDistractionOptimization ||
            restrictions.activeRestrictions != CarUxRestrictions.UX_RESTRICTIONS_BASELINE
        publish()
    }
    private fun invalidate(property: Int) {
        when (property) { GEAR -> signals.invalidateGear(); SPEED -> signals.invalidateSpeed() }
        publish()
    }
    private fun publish() {
        val snapshot = signals.snapshot(SystemClock.elapsedRealtimeNanos()).let {
            if (!it.canUse && failureMessage != null) it.copy(message = failureMessage!!) else it
        }
        mutableState.value = if (drivingEvents.state.value.isNotEmpty()) snapshot.copy(uxRestricted = true) else snapshot
    }
    private fun fail(message: String) {
        failureMessage = message
        signals.invalidateGear()
        signals.invalidateSpeed()
        mutableState.value = WaitingSafety(message = message)
    }
    override fun stop() {
        started = false
        generation++
        connectionGeneration++
        connectionJob?.cancel()
        watchdog?.cancel()
        signals = VehicleSafetySignals()
        mutableState.value = WaitingSafety()
        runCatching { uxManager?.unregisterListener() }
        uxManager = null
        runCatching { car?.disconnect() }
        car = null
    }
    override fun close() { stop(); scope.cancel() }

    companion object {
        private const val GEAR = VehiclePropertyIds.GEAR_SELECTION
        private const val SPEED = VehiclePropertyIds.PERF_VEHICLE_SPEED
    }
}
