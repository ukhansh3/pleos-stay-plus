package com.pleosstay.app.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import com.pleosstay.app.ui.premium.*
import com.pleosstay.app.ui.theme.PleosStayPlusTheme
import com.pleosstay.app.waiting.ui.WaitingUiState
import com.pleosstay.app.pickup.ui.PickupUiState
import org.junit.Rule
import org.junit.Test

class StayAppShellTest {
     @Test fun studyAndWorkCardsOpenCurrentModesAndBackRestoresTheGroup() {
        compose.setContent {
            PleosStayPlusTheme { StayAppShell({}) { route,navigate ->
                when (route) {
                    StayRoute.HOME -> StayHomeScreen(WaitingUiState(),PickupUiState(),navigate)
                    StayRoute.MODES -> StayModeMenuScreen(onStudy={navigate(StayRoute.STUDY)},onWork={navigate(StayRoute.WORK)})
                    else -> Text("screen:${route.name}")
                }
            } }
        }
        for (route in listOf(StayRoute.STUDY,StayRoute.WORK)) {
            compose.onNodeWithTag("mode_${route.name}").performScrollTo().performClick()
            compose.onNodeWithText("screen:${route.name}").assertIsDisplayed()
            compose.onNodeWithTag("nav_MODES").assertIsSelected()
            compose.onNodeWithTag("back").performClick()
            compose.onNodeWithTag("nav_HOME").assertIsSelected()
        }
        compose.onNodeWithTag("nav_MODES").performScrollTo().performClick()
        compose.onNodeWithTag("mode_WORK").performScrollTo().performClick()
        compose.onNodeWithTag("back").performClick()
        compose.onNodeWithTag("nav_MODES").assertIsSelected()
        compose.onNodeWithTag("mode_WORK").assertExists()
    }

    @Test fun workNavigationWaitsForTheHostToKeepTheDraftBeforeLeaving() {
        var pending:(()->Unit)? = null
        var dirty = true
        compose.setContent {
            PleosStayPlusTheme { StayAppShell({},navigationGuard={from,_,proceed->
                if(from==StayRoute.WORK&&dirty) pending=proceed else proceed()
            }) { route,navigate ->
                if(route==StayRoute.HOME) androidx.compose.material3.TextButton(onClick={navigate(StayRoute.WORK)}) { Text("TEST_WORK") }
                else Text("screen:${route.name}")
            } }
        }
        compose.onNodeWithText("TEST_WORK").performClick()
        compose.onNodeWithTag("nav_FILES").performScrollTo().performClick()
        compose.onNodeWithText("screen:WORK").assertIsDisplayed()
        compose.onNodeWithTag("nav_MODES").assertIsSelected()
        compose.runOnIdle { org.junit.Assert.assertNotNull(pending); dirty=false; pending!!(); pending=null }
        compose.onNodeWithText("screen:FILES").assertIsDisplayed()
        compose.onNodeWithTag("nav_FILES").assertIsSelected()
        compose.onNodeWithTag("back").performClick()
        compose.onNodeWithText("screen:WORK").assertIsDisplayed()
        compose.onNodeWithTag("nav_MODES").assertIsSelected()
    }
    @get:Rule val compose = createComposeRule()

    @Test fun homeAndModesOpenRestWhileKeepingModesSelected() {
        compose.setContent {
            PleosStayPlusTheme { StayAppShell({}) { route,navigate ->
                when(route) {
                    StayRoute.HOME -> StayHomeScreen(WaitingUiState(),PickupUiState(),navigate)
                    StayRoute.MODES -> StayModeMenuScreen(onRest={navigate(StayRoute.REST)})
                    else -> Text("screen:${route.name}")
                }
            } }
        }
        compose.onNodeWithTag("mode_REST").performScrollTo().performClick()
        compose.onNodeWithText("screen:REST").assertIsDisplayed()
        compose.onNodeWithTag("nav_MODES").assertIsSelected()
        compose.onNodeWithTag("back").performClick()
        compose.onNodeWithTag("nav_HOME").assertIsSelected()
        compose.onNodeWithTag("nav_MODES").performScrollTo().performClick()
        compose.onNodeWithTag("mode_REST").performScrollTo().performClick()
        compose.onNodeWithText("screen:REST").assertIsDisplayed()
        compose.onNodeWithTag("nav_MODES").assertIsSelected()
    }

    @Test fun alarmRouteRequestIsConsumedAndBackDoesNotReopenRest() {
        var requested by mutableStateOf<StayRoute?>(null)
        compose.setContent {
            PleosStayPlusTheme { StayAppShell({},requestedRoute=requested,onRequestedRouteHandled={requested=null}) { route,_ ->
                Text("screen:${route.name}")
            } }
        }
        compose.runOnIdle { requested=StayRoute.REST }
        compose.onNodeWithText("screen:REST").assertIsDisplayed()
        compose.onNodeWithTag("nav_MODES").assertIsSelected()
        compose.runOnIdle { org.junit.Assert.assertNull(requested) }
        compose.onNodeWithTag("back").performClick()
        compose.onNodeWithText("screen:HOME").assertIsDisplayed()
        compose.onNodeWithTag("nav_HOME").assertIsSelected()
    }

    @Test fun compactDensityKeepsShortcutsAndSettingsReachable() {
        compose.setContent {
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(2f, 1.3f)
            ) {
                PleosStayPlusTheme { StayAppShell({}) { route,navigate ->
                    if(route==StayRoute.HOME) StayHomeScreen(WaitingUiState(),PickupUiState(),navigate)
                    else Text("screen:${route.name}")
                } }
            }
        }
        compose.onNodeWithTag("home_FILES").performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithTag("nav_FILES").assertIsSelected()
        compose.onNodeWithTag("nav_SETTINGS").performScrollTo().performClick()
        compose.onNodeWithTag("nav_SETTINGS").assertIsSelected()
        compose.onNodeWithText("screen:SETTINGS").assertIsDisplayed()
    }

    @Test fun railTracksRoutesBackAndSavedState() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            PleosStayPlusTheme { StayAppShell({}) { route, navigate ->
                if(route==StayRoute.HOME) StayHomeScreen(WaitingUiState(),PickupUiState(),navigate)
                else Text("screen:${route.name}")
            } }
        }
        compose.onNodeWithTag("nav_HOME").assertIsSelected()
        compose.onNodeWithTag("home_waiting").performScrollTo().performClick()
        compose.onNodeWithText("screen:WAITING").assertIsDisplayed()
        compose.onNodeWithTag("nav_PARKING").assertIsSelected()
        for(route in listOf(StayRoute.PICKUP,StayRoute.FILES,StayRoute.MODES)) {
            compose.onNodeWithTag("nav_${route.name}").performScrollTo().performClick()
            compose.onNodeWithTag("nav_${route.name}").assertIsSelected()
        }
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("nav_MODES").assertIsSelected()
        compose.onNodeWithTag("back").performClick()
        compose.onNodeWithTag("nav_FILES").assertIsSelected()
        compose.onNodeWithTag("back").performClick()
        compose.onNodeWithTag("nav_PICKUP").assertIsSelected()
        compose.onNodeWithTag("back").performClick()
        compose.onNodeWithTag("nav_PARKING").assertIsSelected()
    }
}
