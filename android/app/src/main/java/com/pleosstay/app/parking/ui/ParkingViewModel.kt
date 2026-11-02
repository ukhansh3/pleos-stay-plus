package com.pleosstay.app.parking.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pleosstay.app.parking.data.ParkingRepository
import com.pleosstay.app.parking.domain.ParkingPlace
import com.pleosstay.app.parking.domain.ParkingPurpose
import com.pleosstay.app.parking.location.CurrentLocationGateway
import com.pleosstay.app.parking.location.CurrentLocationResult
import com.pleosstay.app.parking.navigation.NavigationGateway
import com.pleosstay.app.parking.navigation.NavigationStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

enum class LocationUiState { IDLE, LOCATING, READY, PERMISSION_DENIED, DISABLED, UNSUPPORTED, FAILED }

sealed interface ParkingSearchOrigin {
    data object CurrentVehicle : ParkingSearchOrigin
    data class ManualTest(val latitude: Double, val longitude: Double) : ParkingSearchOrigin
    data class Destination(val latitude: Double, val longitude: Double, val label: String) : ParkingSearchOrigin
}

data class ParkingUiState(
    val latitude: String = "",
    val longitude: String = "",
    val radiusMeters: String = "2000",
    val plannedMinutes: String = "10",
    val purpose: ParkingPurpose = ParkingPurpose.SHORT_WAIT,
    val freeOnly: Boolean = false,
    val showDeveloperCoordinates: Boolean = false,
    val locationState: LocationUiState = LocationUiState.IDLE,
    val locationMessage: String = "차량 현재 위치로 주변 주차장을 검색할 수 있습니다",
    val lastLocationLabel: String? = null,
    val loading: Boolean = false,
    val places: List<ParkingPlace> = emptyList(),
    val total: Int = 0,
    val dataFreshness: String = "UNKNOWN",
    val selected: ParkingPlace? = null,
    val error: String? = null,
    val navigationStatus: NavigationStatus = NavigationStatus.Initializing,
)

class ParkingViewModel(
    private val repository: ParkingRepository,
    private val navigationGateway: NavigationGateway,
    private val locationGateway: CurrentLocationGateway,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    private val mutableState = MutableStateFlow(ParkingUiState())
    val state: StateFlow<ParkingUiState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(mutableState, navigationGateway.status) { ui, navigation -> ui.copy(navigationStatus = navigation) }
                .collect { combined ->
                    if (combined.navigationStatus != mutableState.value.navigationStatus) mutableState.value = combined
                }
        }
    }

    fun updateCoordinates(latitude: String? = null, longitude: String? = null) {
        mutableState.value = mutableState.value.copy(
            latitude = latitude ?: mutableState.value.latitude,
            longitude = longitude ?: mutableState.value.longitude,
            error = null,
        )
    }

    fun updateFilters(radius: String? = null, minutes: String? = null, purpose: ParkingPurpose? = null, freeOnly: Boolean? = null) {
        mutableState.value = mutableState.value.copy(
            radiusMeters = radius ?: mutableState.value.radiusMeters,
            plannedMinutes = minutes ?: mutableState.value.plannedMinutes,
            purpose = purpose ?: mutableState.value.purpose,
            freeOnly = freeOnly ?: mutableState.value.freeOnly,
            error = null,
        )
    }

    fun toggleDeveloperCoordinates() {
        mutableState.value = mutableState.value.copy(showDeveloperCoordinates = !mutableState.value.showDeveloperCoordinates)
    }

    fun searchManual() {
        val current = mutableState.value
        val latitude = current.latitude.toDoubleOrNull()
        val longitude = current.longitude.toDoubleOrNull()
        if (latitude == null || latitude !in -90.0..90.0 || longitude == null || longitude !in -180.0..180.0) {
            mutableState.value = current.copy(error = "위도와 경도를 올바르게 입력해 주세요")
            return
        }
        search(ParkingSearchOrigin.ManualTest(latitude, longitude))
    }

    fun searchNearby() {
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(
                loading = true,
                locationState = LocationUiState.LOCATING,
                locationMessage = "차량 현재 위치를 확인하고 있습니다",
                error = null,
                selected = null,
            )
            when (val result = withContext(ioDispatcher) { locationGateway.getCurrentLocation() }) {
                is CurrentLocationResult.Success -> {
                    mutableState.value = mutableState.value.copy(
                        latitude = result.location.latitude.toString(),
                        longitude = result.location.longitude.toString(),
                        locationState = LocationUiState.READY,
                        locationMessage = "차량 위치를 확인했습니다",
                        lastLocationLabel = "${result.location.latitude.coordinate()}, ${result.location.longitude.coordinate()}",
                    )
                    search(ParkingSearchOrigin.CurrentVehicle)
                }
                CurrentLocationResult.PermissionDenied -> locationFailure(
                    LocationUiState.PERMISSION_DENIED,
                    "차량 위치 권한이 거부되었습니다. 권한을 허용하거나 개발자용 좌표를 입력해 주세요.",
                )
                CurrentLocationResult.LocationDisabled -> locationFailure(
                    LocationUiState.DISABLED,
                    "차량 위치 기능이 꺼져 있습니다. 차량 설정을 확인하거나 개발자용 좌표를 입력해 주세요.",
                )
                CurrentLocationResult.Unsupported -> locationFailure(
                    LocationUiState.UNSUPPORTED,
                    "이 차량 환경은 FusedLocation을 지원하지 않습니다. 개발자용 좌표를 이용해 주세요.",
                )
                CurrentLocationResult.Timeout -> locationFailure(
                    LocationUiState.FAILED,
                    "차량 위치 수신 시간이 초과되었습니다. 다시 시도하거나 개발자용 좌표를 입력해 주세요.",
                )
                is CurrentLocationResult.Failed -> locationFailure(
                    LocationUiState.FAILED,
                    "${result.reason}. 다시 시도하거나 개발자용 좌표를 입력해 주세요.",
                )
            }
        }
    }

    fun onLocationPermissionDenied() {
        locationFailure(
            LocationUiState.PERMISSION_DENIED,
            "차량 위치 권한이 거부되었습니다. 권한을 허용하거나 개발자용 좌표를 입력해 주세요.",
        )
    }

    fun showLocationAccessInformation() = locationGateway.showAccessBlockedInformation()

    private fun locationFailure(state: LocationUiState, message: String) {
        mutableState.value = mutableState.value.copy(
            loading = false,
            locationState = state,
            locationMessage = message,
            showDeveloperCoordinates = true,
        )
    }

    private fun search(origin: ParkingSearchOrigin) {
        val current = mutableState.value
        val (latitude, longitude) = when (origin) {
            ParkingSearchOrigin.CurrentVehicle -> current.latitude.toDoubleOrNull() to current.longitude.toDoubleOrNull()
            is ParkingSearchOrigin.ManualTest -> origin.latitude to origin.longitude
            is ParkingSearchOrigin.Destination -> origin.latitude to origin.longitude
        }
        val radius = current.radiusMeters.toIntOrNull()
        val minutes = current.plannedMinutes.toIntOrNull()
        if (latitude == null || longitude == null || radius == null || radius !in 1..10_000 || minutes == null || minutes !in 1..1_440) {
            mutableState.value = current.copy(loading = false, error = "검색 조건을 올바르게 입력해 주세요")
            return
        }
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(loading = true, error = null, selected = null)
            runCatching {
                withContext(ioDispatcher) {
                    repository.search(latitude, longitude, radius, minutes, current.purpose, current.freeOnly)
                }
            }.onSuccess { result ->
                mutableState.value = mutableState.value.copy(
                    loading = false,
                    places = result.places,
                    total = result.total,
                    dataFreshness = result.dataFreshness,
                    error = if (result.places.isEmpty()) "조건에 맞는 주차장을 찾지 못했습니다" else null,
                )
            }.onFailure { error ->
                mutableState.value = mutableState.value.copy(loading = false, error = error.message ?: "검색 실패")
            }
        }
    }

    fun select(place: ParkingPlace) {
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(loading = true, error = null)
            runCatching { withContext(ioDispatcher) { repository.detail(place.id) } }
                .onSuccess { detail -> mutableState.value = mutableState.value.copy(loading = false, selected = detail) }
                .onFailure { error -> mutableState.value = mutableState.value.copy(loading = false, error = error.message ?: "상세 조회 실패") }
        }
    }

    fun navigate() {
        mutableState.value.selected?.let(navigationGateway::requestRoute)
    }

    override fun onCleared() {
        locationGateway.close()
        navigationGateway.close()
        super.onCleared()
    }

    class Factory(
        private val repository: ParkingRepository,
        private val navigationGateway: NavigationGateway,
        private val locationGateway: CurrentLocationGateway,
        private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ParkingViewModel(repository, navigationGateway, locationGateway, ioDispatcher) as T
    }
}

private fun Double.coordinate(): String = String.format(Locale.US, "%.6f", this)
