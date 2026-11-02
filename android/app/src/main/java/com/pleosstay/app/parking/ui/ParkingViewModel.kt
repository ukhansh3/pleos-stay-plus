package com.pleosstay.app.parking.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pleosstay.app.parking.data.ParkingRepository
import com.pleosstay.app.parking.domain.ParkingPlace
import com.pleosstay.app.parking.domain.ParkingPurpose
import com.pleosstay.app.parking.navigation.NavigationGateway
import com.pleosstay.app.parking.navigation.NavigationStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ParkingUiState(
    val latitude: String = "",
    val longitude: String = "",
    val radiusMeters: String = "2000",
    val plannedMinutes: String = "10",
    val purpose: ParkingPurpose = ParkingPurpose.SHORT_WAIT,
    val freeOnly: Boolean = false,
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
) : ViewModel() {
    private val mutableState = MutableStateFlow(ParkingUiState())
    val state: StateFlow<ParkingUiState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(mutableState, navigationGateway.status) { ui, navigation -> ui.copy(navigationStatus = navigation) }
                .collect { combined -> if (combined.navigationStatus != mutableState.value.navigationStatus) mutableState.value = combined }
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

    fun search() {
        val current = mutableState.value
        val latitude = current.latitude.toDoubleOrNull()
        val longitude = current.longitude.toDoubleOrNull()
        val radius = current.radiusMeters.toIntOrNull()
        val minutes = current.plannedMinutes.toIntOrNull()
        if (latitude == null || latitude !in -90.0..90.0 || longitude == null || longitude !in -180.0..180.0
            || radius == null || radius !in 1..10_000 || minutes == null || minutes !in 1..1_440) {
            mutableState.value = current.copy(error = "좌표·반경·예정 시간을 올바르게 입력해 주세요")
            return
        }
        viewModelScope.launch {
            mutableState.value = current.copy(loading = true, error = null, selected = null)
            runCatching {
                withContext(Dispatchers.IO) { repository.search(latitude, longitude, radius, minutes, current.purpose, current.freeOnly) }
            }.onSuccess { result ->
                mutableState.value = mutableState.value.copy(
                    loading = false, places = result.places, total = result.total, dataFreshness = result.dataFreshness,
                    error = if (result.places.isEmpty()) "조건에 맞는 주차장을 찾지 못했습니다" else null,
                )
            }.onFailure { error -> mutableState.value = mutableState.value.copy(loading = false, error = error.message ?: "검색 실패") }
        }
    }

    fun select(place: ParkingPlace) {
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(loading = true, error = null)
            runCatching { withContext(Dispatchers.IO) { repository.detail(place.id) } }
                .onSuccess { detail -> mutableState.value = mutableState.value.copy(loading = false, selected = detail) }
                .onFailure { error -> mutableState.value = mutableState.value.copy(loading = false, error = error.message ?: "상세 조회 실패") }
        }
    }

    fun navigate() {
        mutableState.value.selected?.let(navigationGateway::requestRoute)
    }

    override fun onCleared() {
        navigationGateway.close()
        super.onCleared()
    }

    class Factory(
        private val repository: ParkingRepository,
        private val navigationGateway: NavigationGateway,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = ParkingViewModel(repository, navigationGateway) as T
    }
}
