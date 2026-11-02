package com.pleosstay.app.parking.navigation

import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.data.RouteInfo
import ai.pleos.playground.navi.data.RouteStartInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import android.content.Context
import com.pleosstay.app.parking.domain.ParkingPlace
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface NavigationStatus {
    data object Initializing : NavigationStatus
    data object Ready : NavigationStatus
    data class Requesting(val destination: String) : NavigationStatus
    data class Started(val destination: String) : NavigationStatus
    data class Failed(val message: String) : NavigationStatus
}

interface NavigationGateway : AutoCloseable {
    val status: StateFlow<NavigationStatus>
    fun requestRoute(place: ParkingPlace)
}

class PleosNavigationGateway(context: Context) : NavigationGateway {
    private val helper = NaviHelper(context.applicationContext)
    private val mutableStatus = MutableStateFlow<NavigationStatus>(NavigationStatus.Initializing)
    override val status: StateFlow<NavigationStatus> = mutableStatus.asStateFlow()
    private var listenerRegistered = false
    private var requestedName: String = ""

    private val listener = object : NaviHelperEventListener {
        override fun onRouteStarted(routeStartInfo: RouteStartInfo) {
            mutableStatus.value = NavigationStatus.Started(requestedName)
        }

        override fun onRoutingCompleted() {
            mutableStatus.value = NavigationStatus.Started(requestedName)
        }

        override fun onError(errorCode: NaviErrorCode) {
            mutableStatus.value = NavigationStatus.Failed("NaviHelper 오류: $errorCode")
        }
    }

    init {
        helper.initialize(
            onConnected = {
                helper.addListener(listener)
                listenerRegistered = true
                mutableStatus.value = NavigationStatus.Ready
            },
            onFailed = { error -> mutableStatus.value = NavigationStatus.Failed(error.message ?: "NaviHelper 연결 실패") },
        )
    }

    override fun requestRoute(place: ParkingPlace) {
        if (mutableStatus.value !is NavigationStatus.Ready && mutableStatus.value !is NavigationStatus.Started) {
            mutableStatus.value = NavigationStatus.Failed("NaviHelper가 준비되지 않았습니다")
            return
        }
        requestedName = place.name
        mutableStatus.value = NavigationStatus.Requesting(place.name)
        runCatching {
            helper.requestRoute(
                RouteInfo(
                    latitude = place.latitude,
                    longitude = place.longitude,
                    displayLatitude = place.latitude,
                    displayLongitude = place.longitude,
                    poiName = place.name,
                    poiId = place.id,
                    address = place.address.orEmpty(),
                    poiSubId = "",
                    poiProvider = place.source,
                )
            )
        }.onFailure { error -> mutableStatus.value = NavigationStatus.Failed(error.message ?: "길 안내 요청 실패") }
    }

    override fun close() {
        if (listenerRegistered) helper.removeListener(listener)
        listenerRegistered = false
        helper.release()
    }
}
