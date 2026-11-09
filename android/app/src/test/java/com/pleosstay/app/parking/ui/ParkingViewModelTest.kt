package com.pleosstay.app.parking.ui

import com.pleosstay.app.BuildConfig
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
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
    fun nearbySearchUsesVehicleLocationAndRetainsAllFilters() = runTest(dispatcher) {
        val repository = RecordingRepository()
        val viewModel = createViewModel(repository, FakeLocationGateway(vehicleLocation()))
        viewModel.updateFilters(radius = "3000", minutes = "20", purpose = ParkingPurpose.PICKUP, freeOnly = true)

        viewModel.searchNearby()
        advanceUntilIdle()

        assertEquals(listOf(Search(37.501, 127.039, 3000, 20, ParkingPurpose.PICKUP, true)), repository.searches)
        assertEquals(LocationUiState.READY, viewModel.state.value.locationState)
        assertEquals("37.501000, 127.039000", viewModel.state.value.lastLocationLabel)
        assertEquals("", viewModel.state.value.latitude)
        assertEquals("", viewModel.state.value.longitude)
    }

    @Test
    fun locationFailuresShowUserGuidanceAndNeverOpenDeveloperPanel() = runTest(dispatcher) {
        val failures = listOf(
            Triple(CurrentLocationResult.PermissionDenied, LocationUiState.PERMISSION_DENIED, "위치 권한"),
            Triple(CurrentLocationResult.LocationDisabled, LocationUiState.DISABLED, "위치 설정"),
            Triple(CurrentLocationResult.Unsupported, LocationUiState.UNSUPPORTED, "현재 위치"),
            Triple(CurrentLocationResult.Timeout, LocationUiState.FAILED, "다시 시도"),
            Triple(CurrentLocationResult.Failed("SDK_VERIFY"), LocationUiState.FAILED, "다시 시도"),
        )
        for ((result, expectedState, hint) in failures) {
            val repository = RecordingRepository()
            val viewModel = createViewModel(repository, FakeLocationGateway(result))

            viewModel.searchNearby()
            advanceUntilIdle()

            val state = viewModel.state.value
            assertEquals(expectedState, state.locationState)
            assertTrue(state.locationMessage.contains(hint))
            assertFalse(state.locationMessage.contains("SDK_VERIFY"))
            assertFalse(state.showDeveloperCoordinates)
            assertFalse(state.loading)
            assertTrue(repository.searches.isEmpty())
        }
    }

    @Test
    fun deniedPermissionCallbackAllowsRetryWithCurrentLocation() = runTest(dispatcher) {
        val repository = RecordingRepository()
        val location = FakeLocationGateway(vehicleLocation())
        val viewModel = createViewModel(repository, location)
        viewModel.toggleDeveloperCoordinates()

        viewModel.onLocationPermissionDenied()

        assertEquals(LocationUiState.PERMISSION_DENIED, viewModel.state.value.locationState)
        assertFalse(viewModel.state.value.showDeveloperCoordinates)
        assertEquals(0, location.requests)

        viewModel.searchNearby()
        advanceUntilIdle()

        assertEquals(LocationUiState.READY, viewModel.state.value.locationState)
        assertEquals(1, repository.searches.size)
    }

    @Test
    fun manualEntryRequiresExplicitOptInAndDoesNotReplaceLiveVehicleCoordinates() = runTest(dispatcher) {
        assumeTrue(BuildConfig.DEBUG)
        val repository = RecordingRepository()
        val location = FakeLocationGateway(vehicleLocation())
        val viewModel = createViewModel(repository, location)

        viewModel.updateCoordinates("35.12", "129.07")
        viewModel.searchManual()
        advanceUntilIdle()
        assertEquals("", viewModel.state.value.latitude)
        assertTrue(repository.searches.isEmpty())

        viewModel.toggleDeveloperCoordinates()
        viewModel.updateCoordinates("35.12", "129.07")
        viewModel.searchManual()
        advanceUntilIdle()

        assertEquals(0, location.requests)
        assertEquals(listOf(Search(35.12, 129.07)), repository.searches)

        viewModel.searchNearby()
        advanceUntilIdle()

        assertEquals(listOf(Search(35.12, 129.07), Search(37.501, 127.039)), repository.searches)
        assertEquals("35.12", viewModel.state.value.latitude)
        assertEquals("129.07", viewModel.state.value.longitude)
    }

    @Test
    fun manualActionsAreBlockedWhenDeveloperCoordinatesAreDisabled() = runTest(dispatcher) {
        val repository = RecordingRepository()
        val viewModel = ParkingViewModel(
            repository = repository,
            navigationGateway = FakeNavigationGateway(),
            locationGateway = FakeLocationGateway(vehicleLocation()),
            ioDispatcher = dispatcher,
            allowDeveloperCoordinates = false,
        )

        assertManualActionsBlocked(viewModel, repository)

        viewModel.searchNearby()
        advanceUntilIdle()
        assertEquals(listOf(Search(37.501, 127.039)), repository.searches)
    }

    @Test
    fun releaseVariantRejectsManualActionsEvenWhenRequested() = runTest(dispatcher) {
        assumeTrue(!BuildConfig.DEBUG)
        val repository = RecordingRepository()
        val viewModel = ParkingViewModel(
            repository = repository,
            navigationGateway = FakeNavigationGateway(),
            locationGateway = FakeLocationGateway(vehicleLocation()),
            ioDispatcher = dispatcher,
            allowDeveloperCoordinates = true,
        )

        assertManualActionsBlocked(viewModel, repository)
    }

    private fun assertManualActionsBlocked(viewModel: ParkingViewModel, repository: RecordingRepository) {
        viewModel.toggleDeveloperCoordinates()
        viewModel.updateCoordinates("35.12", "129.07")
        viewModel.searchManual()
        dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.state.value
        assertFalse(state.showDeveloperCoordinates)
        assertEquals("", state.latitude)
        assertEquals("", state.longitude)
        assertNull(state.error)
        assertTrue(repository.searches.isEmpty())
    }

    private fun createViewModel(repository: RecordingRepository, location: FakeLocationGateway) =
        ParkingViewModel(repository, FakeNavigationGateway(), location, dispatcher)

    private fun vehicleLocation() = CurrentLocationResult.Success(VehicleLocation(37.501, 127.039, 1_793_600_000_000))
}

private data class Search(
    val latitude: Double,
    val longitude: Double,
    val radius: Int = 2000,
    val minutes: Int = 10,
    val purpose: ParkingPurpose = ParkingPurpose.SHORT_WAIT,
    val freeOnly: Boolean = false,
)

private class RecordingRepository : ParkingRepository {
    val searches = mutableListOf<Search>()

    override suspend fun search(
        latitude: Double,
        longitude: Double,
        radiusMeters: Int,
        plannedMinutes: Int,
        purpose: ParkingPurpose,
        freeOnly: Boolean,
    ): ParkingSearchResult {
        searches += Search(latitude, longitude, radiusMeters, plannedMinutes, purpose, freeOnly)
        return ParkingSearchResult(emptyList(), 0, "FRESH")
    }

    override suspend fun detail(placeId: String): ParkingPlace = error("Not used")
}

private class FakeLocationGateway(private val result: CurrentLocationResult) : CurrentLocationGateway {
    var requests = 0
        private set

    override suspend fun getCurrentLocation(): CurrentLocationResult {
        requests++
        return result
    }
    override fun showAccessBlockedInformation() = Unit
    override fun close() = Unit
}

private class FakeNavigationGateway : NavigationGateway {
    override val status: StateFlow<NavigationStatus> = MutableStateFlow(NavigationStatus.Ready)
    override fun requestRoute(place: ParkingPlace) = Unit
    override fun close() = Unit
}
