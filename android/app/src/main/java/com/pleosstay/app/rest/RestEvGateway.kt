package com.pleosstay.app.rest

import android.content.Context
import android.content.pm.PackageManager
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.capability.AccessType
import ai.pleos.playground.vehicle.listener.EvBatteryStateOfChargeListener
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal fun interface RestBattery {
    fun readings(): kotlinx.coroutines.flow.Flow<EvReading>
}

internal class RestEvGateway(private val context: Context) : RestBattery {
    override fun readings() = callbackFlow {
        if (context.checkSelfPermission("pleos.car.permission.CAR_ENERGY") != PackageManager.PERMISSION_GRANTED) {
            trySend(EvReading(message = "차량 에너지 정보 읽기 권한이 없습니다."))
            close()
            return@callbackFlow
        }
        val vehicle = Vehicle(context)
        var cleanup: (() -> Unit)? = null
        fun publish(value: Int?) {
            trySend(if (value != null && value in 0..100) EvReading(value, "") else EvReading(message = "유효한 배터리 잔량을 받지 못했습니다."))
        }
        val task = launch {
            try {
                vehicle.initialize()
                val battery = vehicle.getEvBattery()
                // The 2.3.2 binary has initialize() without a connection callback.
                suspend fun capability() = withTimeout(4_000) {
                    callback<AccessType> { ok, fail ->
                        battery.checkEvBatteryStateOfChargeCapabilities({ ok(it.accessType) }, fail)
                    }
                }
                val access = try {
                    capability()
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    // Binding is asynchronous in 2.3.2. Retry an early service error once.
                    delay(500)
                    capability()
                }
                check(access == AccessType.READ || access == AccessType.READ_WRITE) { "이 차량은 배터리 잔량 읽기를 지원하지 않습니다." }
                suspend fun current() = withTimeout(4_000) {
                    callback<Int?> { ok, fail -> battery.getEvBatteryStateOfCharge(ok, fail) }
                }
                publish(current())
                val listener = object : EvBatteryStateOfChargeListener {
                    override fun onEvBatteryStateOfChargeUpdated(value: Int?) = publish(value)
                    override fun onFailed(e: Exception) {
                        trySend(EvReading(message = "배터리 변경 구독 오류: ${e.message.orEmpty()}"))
                    }
                }
                cleanup = { battery.unregisterEvBatteryStateOfCharge(listener) }
                battery.registerEvBatteryStateOfCharge(listener)
                while (true) {
                    delay(15_000)
                    try {
                        publish(current())
                    } catch (error: TimeoutCancellationException) {
                        trySend(EvReading(message = "배터리 정보 갱신이 지연되고 있습니다."))
                    }
                }
            } catch (error: TimeoutCancellationException) {
                trySend(EvReading(message = "Vehicle Service 배터리 응답 시간이 초과됐습니다."))
                close()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                trySend(EvReading(message = error.message ?: "Vehicle Service 또는 배터리 정보를 사용할 수 없습니다."))
                close()
            }
        }
        awaitClose {
            task.cancel()
            runCatching { cleanup?.invoke() }
            runCatching { vehicle.release() }
        }
    }

    private suspend fun <T> callback(call: ((T) -> Unit, (Exception) -> Unit) -> Unit): T =
        suspendCancellableCoroutine { continuation ->
            val completed = java.util.concurrent.atomic.AtomicBoolean()
            call({ if (completed.compareAndSet(false, true)) continuation.resume(it) },
                { if (completed.compareAndSet(false, true)) continuation.resumeWithException(it) })
        }
}
