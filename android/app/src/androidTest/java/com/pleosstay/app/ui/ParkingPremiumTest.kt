package com.pleosstay.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.pleosstay.app.parking.data.ParkingRepository
import com.pleosstay.app.parking.domain.*
import com.pleosstay.app.parking.location.*
import com.pleosstay.app.parking.navigation.*
import com.pleosstay.app.parking.ui.*
import com.pleosstay.app.ui.premium.StayColors
import com.pleosstay.app.ui.premium.StayAppShell
import com.pleosstay.app.ui.premium.StayRoute
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.pleosstay.app.ui.theme.PleosStayPlusTheme
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Fixtures belong to instrumentation tests only; the production app always uses its real repository. */
class ParkingPremiumTest {
    @get:Rule val compose = createComposeRule()

    @Test fun selectionKeepsNavigationDestinationAndWaitingEntry() {
        val navigation = RecordingNavigation()
        var waitingOpened = false
        lateinit var model: ParkingViewModel
        compose.setContent {
            model = remember { ParkingViewModel(TestRepository(), navigation, TestLocation()) }
            PleosStayPlusTheme {
                StayAppShell({}) { route,navigate ->
                    if(route == StayRoute.HOME) TextButton(onClick={navigate(StayRoute.PARKING)}) { Text("테스트 주차장 검색") }
                    else ParkingScreen(model, model::searchNearby, onWaiting = { waitingOpened = true })
                }
            }
        }
        compose.onNodeWithText("테스트 주차장 검색").performClick()
        compose.onNodeWithText("내 주변 검색").performScrollTo().performClick()
        compose.waitUntil(10_000) { model.state.value.places.isNotEmpty() }
        compose.onNodeWithText("테스트 전용 장소").performScrollTo().performClick()
        compose.waitUntil(10_000) { model.state.value.selected != null }
        compose.onAllNodesWithText("80m", substring = true).onFirst().assertExists()
        compose.onNodeWithText("길 안내").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(37.0, navigation.destination?.latitude); assertEquals(127.0, navigation.destination?.longitude) }
        compose.onNodeWithText("이 장소에서 대기").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(waitingOpened) }
        compose.onNodeWithTag("back").performClick()
        compose.onNodeWithTag("nav_PARKING").assertIsSelected()
        compose.onNodeWithText("검색 조건").performScrollTo().assertIsDisplayed()
    }

    @Test fun networkFailureShowsErrorAndAllowsRetry() {
        lateinit var model: ParkingViewModel
        val repository = TestRepository(fail = true)
        compose.setContent {
            model = remember { ParkingViewModel(repository, RecordingNavigation(), TestLocation()) }
            PleosStayPlusTheme { Box(Modifier.width(480.dp)) { ParkingScreen(model, model::searchNearby) } }
        }
        compose.onNodeWithText("내 주변 검색").performScrollTo().performClick()
        compose.waitUntil(10_000) { model.state.value.error != null }
        compose.onNodeWithText("주차장 검색 오류").assertIsDisplayed()
        compose.onNodeWithText("검색 결과 · 0곳").assertIsSelected()
        repository.fail = false
        compose.onNodeWithTag("parking-retry").performScrollTo().performClick()
        compose.waitUntil(10_000) { model.state.value.places.isNotEmpty() }
        compose.onNodeWithText("테스트 전용 장소").performScrollTo().assertIsDisplayed()
    }
}
private class TestRepository(var fail: Boolean = false) : ParkingRepository {
    private val place = ParkingPlace("ui-test","테스트 전용 장소",ParkingType.PARKING_LOT,37.0,127.0,
        "테스트 주소",80,listOf("PARK"),"ELIGIBLE","테스트 전용 조건","테스트 운영시간",OperatingStatus.UNKNOWN,
        null,FeeEstimateStatus.UNKNOWN,FeePolicy("UNKNOWN",null,null,null,null,null,null),null,null,null,null,null,null,"UI_TEST",null)
    override suspend fun search(latitude:Double,longitude:Double,radiusMeters:Int,plannedMinutes:Int,purpose:ParkingPurpose,freeOnly:Boolean):ParkingSearchResult {
        if(fail) error("테스트 네트워크 실패")
        return ParkingSearchResult(listOf(place),1,"TEST_ONLY")
    }
    override suspend fun detail(placeId:String) = place.copy(distanceMeters = null)
}
private class RecordingNavigation : NavigationGateway {
    override val status = MutableStateFlow<NavigationStatus>(NavigationStatus.Ready)
    var destination:ParkingPlace? = null
    override fun requestRoute(place:ParkingPlace) { destination = place }
    override fun close() = Unit
}
private class TestLocation : CurrentLocationGateway {
    override suspend fun getCurrentLocation() = CurrentLocationResult.Success(VehicleLocation(37.0,127.0,System.currentTimeMillis()))
    override fun showAccessBlockedInformation() = Unit
    override fun close() = Unit
}
