package com.pleosstay.app.parking.location

import ai.pleos.playground.location.fused.FusedLocation
import ai.pleos.playground.location.fused.listener.FusedLocationListener
import ai.pleos.playground.location.fused.model.LocationData
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

const val FUSED_LOCATION_PERMISSION = "pleos.car.permission.FUSED_LOCATION"

data class VehicleLocation(
    val latitude: Double,
    val longitude: Double,
    val timestampMillis: Long,
    val confidence: Float? = null,
)

sealed interface CurrentLocationResult {
    data class Success(val location: VehicleLocation) : CurrentLocationResult
    data object PermissionDenied : CurrentLocationResult
    data object LocationDisabled : CurrentLocationResult
    data object Unsupported : CurrentLocationResult
    data object Timeout : CurrentLocationResult
    data class Failed(val reason: String) : CurrentLocationResult
}

interface CurrentLocationGateway : AutoCloseable {
    suspend fun getCurrentLocation(): CurrentLocationResult
    fun showAccessBlockedInformation()
}

class PleosFusedLocationGateway(
    context: Context,
    private val timeoutMillis: Long = 12_000,
) : CurrentLocationGateway {
    private val applicationContext = context.applicationContext
    private val fusedLocation = FusedLocation(applicationContext)
    private val locationManager = applicationContext.getSystemService(LocationManager::class.java)
    private val supported = runCatching { fusedLocation.isSupported() }.getOrDefault(false)
    private var initialized = supported && runCatching {
        fusedLocation.initialize()
        true
    }.getOrDefault(false)
    private var activeListener: FusedLocationListener? = null

    override suspend fun getCurrentLocation(): CurrentLocationResult {
        if (ContextCompat.checkSelfPermission(applicationContext, FUSED_LOCATION_PERMISSION) != PackageManager.PERMISSION_GRANTED) {
            return CurrentLocationResult.PermissionDenied
        }
        if (locationManager?.isLocationEnabled == false) return CurrentLocationResult.LocationDisabled
        if (!supported) return CurrentLocationResult.Unsupported
        if (!initialized) {
            return CurrentLocationResult.Failed("FusedLocation 초기화 실패")
        }

        val result = withTimeoutOrNull(timeoutMillis) {
            suspendCancellableCoroutine { continuation ->
                fun unregister() {
                    activeListener?.let { listener -> runCatching { fusedLocation.unregisterFusedLocationCallback(listener) } }
                    activeListener = null
                }

                fun complete(value: CurrentLocationResult) {
                    unregister()
                    if (continuation.isActive) continuation.resume(value)
                }

                val updateListener = object : FusedLocationListener {
                    override fun onChanged(location: LocationData?) {
                        if (location == null || !location.hasValidCoordinates()) {
                            complete(CurrentLocationResult.Failed("차량 위치 좌표를 사용할 수 없습니다"))
                        } else {
                            complete(CurrentLocationResult.Success(location.toVehicleLocation()))
                        }
                    }

                    override fun onError(exception: Exception) {
                        complete(CurrentLocationResult.Failed("FusedLocation 위치 수신 실패"))
                    }
                }

                val lastKnownListener = object : FusedLocationListener {
                    override fun onChanged(location: LocationData?) {
                        if (location != null && location.hasValidCoordinates()) {
                            complete(CurrentLocationResult.Success(location.toVehicleLocation()))
                        }
                    }

                    override fun onError(exception: Exception) = Unit
                }

                continuation.invokeOnCancellation { unregister() }
                activeListener = updateListener
                runCatching { fusedLocation.registerFusedLocationCallback(updateListener) }
                    .onFailure { complete(CurrentLocationResult.Failed("FusedLocation 업데이트 등록 실패")) }
                runCatching { fusedLocation.getLastKnownLocation(lastKnownListener) }
                // 일부 Connect 런타임은 마지막 위치 콜백을 보내지 않을 수 있으므로,
                // 실시간 콜백은 먼저 등록하고 마지막 위치는 빠른 응답 경로로만 사용한다.
            }
        }
        if (result == null) {
            activeListener?.let { listener -> runCatching { fusedLocation.unregisterFusedLocationCallback(listener) } }
            activeListener = null
            return CurrentLocationResult.Timeout
        }
        return result
    }

    override fun showAccessBlockedInformation() {
        if (initialized) runCatching { fusedLocation.showLocationAccessBlockedPopup() }
    }

    override fun close() {
        activeListener?.let { listener -> runCatching { fusedLocation.unregisterFusedLocationCallback(listener) } }
        activeListener = null
        if (initialized) runCatching { fusedLocation.release() }
        initialized = false
    }

    private fun LocationData.hasValidCoordinates(): Boolean =
        latitude.isFinite() && latitude in -90.0..90.0 && longitude.isFinite() && longitude in -180.0..180.0

    private fun LocationData.toVehicleLocation(): VehicleLocation =
        VehicleLocation(
            latitude = latitude,
            longitude = longitude,
            timestampMillis = timestampMillis,
            confidence = confidence.takeIf { it.isFinite() },
        )
}
