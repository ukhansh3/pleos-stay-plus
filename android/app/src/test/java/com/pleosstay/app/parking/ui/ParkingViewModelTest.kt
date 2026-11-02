package com.pleosstay.app.parking.ui

import com.pleosstay.app.parking.data.ParkingRepository
import com.pleosstay.app.parking.domain.ParkingPlace
import com.pleosstay.app.parking.domain.ParkingPurpose
import com.pleosstay.app.parking.domain.ParkingSearchResult
import com.pleosstay.app.parking.location.CurrentLocationGateway
import com.pleosstay.app.parking.location.CurrentLocationResult
import com.pleosstay.app.parking.location.VehicleLocation
import com.pleosstay.app.parking.navigation.NavigationGateway
import com.pleosstay.app.parking.navigation.NavigationStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ParkingViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun nearbySearchUsesVehicleLocationForParkingRequest() = runTest(dispatcher) {
        val repository = RecordingRepository()
        val viewModel = ParkingViewModel(
            repository = repository,
            navigationGateway = FakeNavigationGateway(),
            locationGateway = FakeLocationGateway(
                CurrentLocationResult.Success(VehicleLocation(37.501, 127.039, 1_793_600_000_000))
            ),
            ioDispatcher = dispatcher,
        )

        viewModel.searchNearby()
        advanceUntilIdle()

        assertEquals(37.501, repository.latitude ?: Double.NaN, 0.0)
        assertEquals(127.039, repository.longitude ?: Double.NaN, 0.0)
        assertEquals(LocationUiState.READY, viewModel.state.value.locationState)
        assertEquals("37.501000, 127.039000", viewModel.state.value.lastLocationLabel)
    }

    @Test
    fun deniedLocationShowsManualCoordinateFallback() = runTest(dispatcher) {
        val viewModel = ParkingViewModel(
            repository = RecordingRepository(),
            navigationGateway = FakeNavigationGateway(),
            locationGateway = FakeLocationGateway(CurrentLocationResult.PermissionDenied),
            ioDispatcher = dispatcher,
        )

        viewModel.searchNearby()
        advanceUntilIdle()

        assertEquals(LocationUiState.PERMISSION_DENIED, viewModel.state.value.locationState)
        assertTrue(viewModel.state.value.showDeveloperCoordinates)
    }

    @Test
    fun disabledLocationShowsSettingsGuidanceAndManualFallback() = runTest(dispatcher) {
        val viewModel = ParkingViewModel(
            repository = RecordingRepository(),
            navigationGateway = FakeNavigationGateway(),
            locationGateway = FakeLocationGateway(CurrentLocationResult.LocationDisabled),
            ioDispatcher = dispatcher,
        )

        viewModel.searchNearby()
        advanceUntilIdle()

        assertEquals(LocationUiState.DISABLED, viewModel.state.value.locationState)
        assertTrue(viewModel.state.value.locationMessage.contains("차량 설정"))
        assertTrue(viewModel.state.value.showDeveloperCoordinates)
    }

    @Test
    fun failedLocationShowsRetryGuidanceAndManualFallback() = runTest(dispatcher) {
        val viewModel = ParkingViewModel(
            repository = RecordingRepository(),
            navigationGateway = FakeNavigationGateway(),
            locationGateway = FakeLocationGateway(CurrentLocationResult.Failed("위치 서비스 오류")),
            ioDispatcher = dispatcher,
        )

        viewModel.searchNearby()
        advanceUntilIdle()

        assertEquals(LocationUiState.FAILED, viewModel.state.value.locationState)
        assertTrue(viewModel.state.value.locationMessage.contains("다시 시도"))
        assertTrue(viewModel.state.value.showDeveloperCoordinates)
    }
}

private class RecordingRepository : ParkingRepository {
    var latitude: Double? = null
    var longitude: Double? = null

    override suspend fun search(
        latitude: Double,
        longitude: Double,
        radiusMeters: Int,
        plannedMinutes: Int,
        purpose: ParkingPurpose,
        freeOnly: Boolean,
    ): ParkingSearchResult {
        this.latitude = latitude
        this.longitude = longitude
        return ParkingSearchResult(emptyList(), 0, "FRESH")
    }

    override suspend fun detail(placeId: String): ParkingPlace = error("Not used")
}

private class FakeLocationGateway(private val result: CurrentLocationResult) : CurrentLocationGateway {
    override suspend fun getCurrentLocation(): CurrentLocationResult = result
    override fun showAccessBlockedInformation() = Unit
    override fun close() = Unit
}

private class FakeNavigationGateway : NavigationGateway {
    override val status: StateFlow<NavigationStatus> = MutableStateFlow(NavigationStatus.Ready)
    override fun requestRoute(place: ParkingPlace) = Unit
    override fun close() = Unit
}
