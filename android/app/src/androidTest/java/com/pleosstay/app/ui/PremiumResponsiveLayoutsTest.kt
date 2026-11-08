package com.pleosstay.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.test.platform.app.InstrumentationRegistry
import com.pleosstay.app.parking.ui.*
import com.pleosstay.app.pickup.ui.*
import com.pleosstay.app.ui.theme.PleosStayPlusTheme
import com.pleosstay.app.waiting.domain.WaitingPlace
import com.pleosstay.app.waiting.safety.WaitingSafety
import com.pleosstay.app.waiting.ui.*
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Rule
import org.junit.Test

/** 900dp constraints exercise the real narrow layouts; fixture inputs never enter production. */
class PremiumResponsiveLayoutsTest {
    @get:Rule val ui = createComposeRule()
    private val models = mutableListOf<ViewModel>()
    private val resources = mutableListOf<AutoCloseable>()
    private fun <T : ViewModel> model(create: () -> T): T {
        lateinit var result: T
        ui.runOnUiThread { result = create(); models += result }
        return result
    }
    private fun narrow(content: @Composable () -> Unit) {
        ui.setContent { PleosStayPlusTheme { Box(Modifier.width(900.dp).fillMaxHeight()) { content() } } }
    }
    @After fun release() {
        ui.runOnUiThread { models.forEach { it.viewModelScope.cancel() } }
        resources.asReversed().forEach { it.close() }
    }
    @Test fun parkingNarrowSearchAndDetailStayReachable() {
        val model = model { ParkingViewModel(ProofParking(), ProofNavigation(), ProofLocation()) }
        narrow { ParkingScreen(model, model::searchNearby) }
        ui.onNodeWithText("내 주변 검색").performScrollTo().assertIsDisplayed().performClick()
        ui.waitUntil(15000) { model.state.value.places.size == 2 && !model.state.value.loading }
        ui.onNodeWithText("테스트 공영주차장").performScrollTo().assertIsDisplayed().performClick()
        ui.waitUntil(15000) { model.state.value.selected != null && !model.state.value.loading }
        ui.onNodeWithText("길 안내").performScrollTo().assertIsDisplayed()
        ui.onNodeWithText("이 장소에서 대기").performScrollTo().assertIsDisplayed()
    }
    @Test fun waitingNarrowActionsScrollAndUnknownHidesSession() {
        val waiting = ProofWaiting(InstrumentationRegistry.getInstrumentation().targetContext).also { resources += it }
        val model = model { WaitingViewModel(waiting.repository, waiting.safety) }
        narrow { WaitingScreen(model, WaitingPlace("proof-place", "테스트 비공개 대기 장소"), onRest = {}, onStudy = {}, onWork = {}) }
        ui.waitUntil(15000) { model.state.value.canUse }
        ui.onNodeWithText("대기 시작").performScrollTo().assertIsDisplayed().performClick()
        ui.waitUntil(15000) { model.state.value.active != null && !model.state.value.busy }
        ui.onNodeWithText("대기시간 연장").performScrollTo().assertIsDisplayed()
        ui.onNodeWithText("기록 보기").performScrollTo().assertIsDisplayed()
        ui.runOnIdle { waiting.safety.state.value = WaitingSafety() }
        ui.waitUntil(10000) { !model.state.value.canUse }
        ui.onNodeWithText("정차 확인이 필요합니다").assertExists()
        ui.onNodeWithText("테스트 비공개 대기 장소").assertDoesNotExist()
        ui.onNodeWithText("대기 종료").assertDoesNotExist()
        ui.onNodeWithText("기록 보기").assertDoesNotExist()
    }
    @Test fun pickupNarrowQrScrollAndUnknownHidesInviteAndParticipant() {
        val safety = ProofSafety()
        val model = model { PickupViewModel(ProofPickup(), ProofLocation(), safety, refreshIntervalMs = 60000) }
        narrow { PickupScreen(model, { model.start() }) }
        ui.waitUntil(10000) { !model.state.value.blocked }
        ui.onNodeWithText("현재 위치로 Pickup 시작").performScrollTo().assertIsDisplayed().performClick()
        ui.waitUntil(15000) { model.state.value.session != null && !model.state.value.busy }
        ui.onNodeWithText("QR 발급").performScrollTo().assertIsDisplayed().performClick()
        ui.waitUntil(15000) { model.state.value.invitation != null && !model.state.value.busy }
        ui.onNodeWithContentDescription("Pickup 공유 링크 QR").performScrollTo().assertIsDisplayed()
        ui.onNodeWithText("QR 재발급").performScrollTo().assertIsDisplayed()
        ui.runOnIdle { safety.state.value = WaitingSafety() }
        ui.waitUntil(10000) { model.state.value.blocked }
        ui.onNodeWithText("정차 확인이 필요합니다").assertExists()
        ui.onNodeWithContentDescription("Pickup 공유 링크 QR").assertDoesNotExist()
        ui.onNodeWithText("테스트 참가자").assertDoesNotExist()
        ui.onNodeWithText("합류 완료").assertDoesNotExist()
    }
}
