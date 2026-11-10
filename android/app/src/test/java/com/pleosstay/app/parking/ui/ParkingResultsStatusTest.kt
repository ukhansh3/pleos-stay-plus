package com.pleosstay.app.parking.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ParkingResultsStatusTest {
    @Test
    fun permissionAndLoadingMatchTheWorkInProgress() {
        val initial = ParkingUiState()
        assertEquals(ParkingResultsStatus.IDLE, initial.resultsStatus())
        assertEquals(ParkingResultsStatus.PERMISSION, initial.resultsStatus(permissionPending = true))
        assertEquals(ParkingResultsStatus.LOCATING, initial.copy(loading = true, locationState = LocationUiState.LOCATING).resultsStatus())
        assertEquals(ParkingResultsStatus.SEARCHING, initial.copy(loading = true, locationState = LocationUiState.READY).resultsStatus())
    }

    @Test
    fun failuresAreDistinctFromASuccessfulEmptySearch() {
        val initial = ParkingUiState()
        for (failure in listOf(LocationUiState.PERMISSION_DENIED, LocationUiState.FAILED)) {
            assertEquals(ParkingResultsStatus.LOCATION_FAILED, initial.copy(locationState = failure).resultsStatus())
        }
        assertEquals(ParkingResultsStatus.SEARCH_FAILED, initial.copy(error = "실패").resultsStatus())
        assertEquals(ParkingResultsStatus.EMPTY, initial.copy(appliedFilters = initial.filters).resultsStatus())
    }
}
