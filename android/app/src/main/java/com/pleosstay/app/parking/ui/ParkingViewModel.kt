package com.pleosstay.app.parking.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pleosstay.app.BuildConfig
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

class ParkingViewModel internal constructor(
    private val repository: ParkingRepository,
    private val navigationGateway: NavigationGateway,
    private val locationGateway: CurrentLocationGateway,
    private val ioDispatcher: CoroutineDispatcher,
    allowDeveloperCoordinates: Boolean,
) : ViewModel() {
    constructor(
        repository: ParkingRepository,
        navigationGateway: NavigationGateway,
        locationGateway: CurrentLocationGateway,
        ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    ) : this(repository, navigationGateway, locationGateway, ioDispatcher, allowDeveloperCoordinates = true)

    internal val developerCoordinatesEnabled = BuildConfig.DEBUG && allowDeveloperCoordinates
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
        val current = mutableState.value
        if (!developerCoordinatesEnabled || !current.showDeveloperCoordinates || current.loading) return
        mutableState.value = current.copy(
            latitude = latitude ?: current.latitude,
            longitude = longitude ?: current.longitude,
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
        val current = mutableState.value
        if (!developerCoordinatesEnabled || current.loading) return
        mutableState.value = current.copy(showDeveloperCoordinates = !current.showDeveloperCoordinates)
    }

    fun searchManual() {
        val current = mutableState.value
        if (!developerCoordinatesEnabled || !current.showDeveloperCoordinates || current.loading) return
        val latitude = current.latitude.toDoubleOrNull()
        val longitude = current.longitude.toDoubleOrNull()
        if (latitude == null || latitude !in -90.0..90.0 || longitude == null || longitude !in -180.0..180.0) {
            mutableState.value = current.copy(error = "위도와 경도를 올바르게 입력해 주세요")
            return
        }
        mutableState.value = current.copy(loading = true, error = null, selected = null)
        viewModelScope.launch { search(latitude, longitude) }
    }

    fun searchNearby() {
        val current = mutableState.value
        if (current.loading) return
        mutableState.value = current.copy(
            loading = true,
            locationState = LocationUiState.LOCATING,
            locationMessage = "현재 위치를 확인하고 있습니다.",
            error = null,
            selected = null,
        )
        viewModelScope.launch {
            when (val result = withContext(ioDispatcher) { locationGateway.getCurrentLocation() }) {
                is CurrentLocationResult.Success -> {
                    val (latitude, longitude) = result.location
                    mutableState.value = mutableState.value.copy(
                        locationState = LocationUiState.READY,
                        locationMessage = "차량 위치를 확인했습니다",
                        lastLocationLabel = "${latitude.coordinate()}, ${longitude.coordinate()}",
                    )
                    search(latitude, longitude)
                }
                CurrentLocationResult.PermissionDenied -> onLocationPermissionDenied()
                CurrentLocationResult.LocationDisabled -> locationFailure(
                    LocationUiState.DISABLED,
                    "차량의 위치 설정을 켠 뒤 다시 검색해 주세요.",
                )
                CurrentLocationResult.Unsupported -> locationFailure(
                    LocationUiState.UNSUPPORTED,
                    "이 차량에서 현재 위치를 확인할 수 없습니다.",
                )
                CurrentLocationResult.Timeout, is CurrentLocationResult.Failed -> locationFailure(
                    LocationUiState.FAILED,
                    "위치를 확인할 수 없습니다. 다시 시도해 주세요.",
                )
            }
        }
    }

    fun onLocationPermissionDenied() {
        locationFailure(
            LocationUiState.PERMISSION_DENIED,
            "위치 권한을 허용한 뒤 다시 검색해 주세요.",
        )
    }

    fun showLocationAccessInformation() = locationGateway.showAccessBlockedInformation()

    private fun locationFailure(state: LocationUiState, message: String) {
        mutableState.value = mutableState.value.copy(
            loading = false,
            locationState = state,
            locationMessage = message,
            showDeveloperCoordinates = false,
        )
    }

    private suspend fun search(latitude: Double, longitude: Double) {
        val current = mutableState.value
        val radius = current.radiusMeters.toIntOrNull()
        val minutes = current.plannedMinutes.toIntOrNull()
        if (radius == null || radius !in 1..10_000 || minutes == null || minutes !in 1..1_440) {
            mutableState.value = current.copy(loading = false, error = "검색 조건을 올바르게 입력해 주세요")
            return
        }
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
