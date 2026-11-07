package com.pleosstay.app.ui

import androidx.compose.runtime.remember
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.pleosstay.app.parking.location.*
import com.pleosstay.app.pickup.data.PickupRepository
import com.pleosstay.app.pickup.domain.*
import com.pleosstay.app.pickup.ui.PickupScreen
import com.pleosstay.app.pickup.ui.PickupViewModel
import com.pleosstay.app.ui.theme.PleosStayPlusTheme
import com.pleosstay.app.waiting.data.*
import com.pleosstay.app.waiting.domain.*
import com.pleosstay.app.waiting.safety.*
import com.pleosstay.app.waiting.ui.WaitingScreen
import com.pleosstay.app.waiting.ui.WaitingViewModel
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.Instant

/** Only androidTest substitutes vehicle/network inputs; real screens, VMs and Room execute. */
class PremiumFeatureActionsTest {
    @get:Rule val ui = createComposeRule()

    @Test fun waitingStartExtendSafetyMaskAndEndUseTheExistingLocalRepository() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(app, WaitingDatabase::class.java).build()
        val safety = UiSafety()
        val journal = object : DrivingEndJournal {
            override fun read(): PendingDrivingEnd? = null
            override fun write(command: PendingDrivingEnd) {}
            override fun clear() {}
        }
        val repository = LocalWaitingRepository(RoomWaitingStore(db), journal, { safety.state.value })
        lateinit var model: WaitingViewModel
        var created = false
        try {
            ui.setContent {
                model = remember { WaitingViewModel(repository, safety) }
                created = true
                PleosStayPlusTheme { WaitingScreen(model, WaitingPlace("UI_TEST", "테스트 전용 장소")) }
            }
            ui.waitUntil(15000) { model.state.value.canUse }
            ui.onNodeWithText("대기 시작").performScrollTo().performClick()
            ui.waitUntil(15000) { model.state.value.active != null && !model.state.value.busy }
            val before = model.state.value.active!!
            ui.onNodeWithText("대기시간 연장").performScrollTo().performClick()
            ui.waitUntil(15000) { model.state.value.active?.revision == before.revision + 1 && !model.state.value.busy }
            assertEquals(before.plannedEndAt + 15 * 60000L, model.state.value.active!!.plannedEndAt)
            ui.runOnIdle { safety.state.value = WaitingSafety() }
            ui.onNodeWithText("정차 확인이 필요합니다").assertExists()
            ui.onNodeWithText("대기 종료").assertDoesNotExist()
            ui.runOnIdle { safety.state.value = WaitingSafety(VehicleMotion.PARKED, false) }
            ui.waitUntil(10000) { model.state.value.canUse }
            ui.onNodeWithText("대기 종료").performScrollTo().performClick()
            ui.waitUntil(15000) { model.state.value.active == null && !model.state.value.busy }
            assertEquals(EndReason.MANUAL, model.state.value.sessions.single().endReason)
            assertEquals("테스트 전용 장소", model.state.value.sessions.single().place?.name)
        } finally {
            if (created) ui.runOnIdle { model.viewModelScope.cancel() }
            db.close()
        }
    }

    @Test fun independentPickupQrReissueErrorAndCompletionCallTheExistingViewModel() {
        val repository = UiPickupRepository()
        lateinit var model: PickupViewModel
        var created = false
        try {
            ui.setContent {
                model = remember { PickupViewModel(repository, object : CurrentLocationGateway {
                    override suspend fun getCurrentLocation() = CurrentLocationResult.Timeout
                    override fun showAccessBlockedInformation() {}
                    override fun close() {}
                }, UiSafety(), refreshIntervalMs = 60000) }
                created = true
                PleosStayPlusTheme { PickupScreen(model, { model.start() }) }
            }
            ui.onNodeWithText("현재 위치로 Pickup 시작").performScrollTo().performClick()
            ui.waitUntil(15000) { model.state.value.session != null && !model.state.value.busy }
            assertNull(repository.waitingId)
            assertNotNull(model.state.value.locationMessage)
            ui.onNodeWithText("60분 초대 QR 발급").performScrollTo().performClick()
            ui.waitUntil(15000) { model.state.value.invitation != null && !model.state.value.busy }
            ui.onNodeWithContentDescription("Pickup 공유 링크 QR").performScrollTo().assertIsDisplayed()
            val first = model.state.value.invitation!!.shareUrl
            ui.onNodeWithText("QR 재발급 · 기존 참가 권한 폐기").performScrollTo().performClick()
            ui.waitUntil(15000) { repository.invites == 2 && !model.state.value.busy }
            assertNotEquals(first, model.state.value.invitation!!.shareUrl)
            repository.failInvite = true
            ui.onNodeWithText("QR 재발급 · 기존 참가 권한 폐기").performScrollTo().performClick()
            ui.waitUntil(15000) { model.state.value.error != null && !model.state.value.busy }
            ui.onNodeWithText("테스트 전용 네트워크 오류").performScrollTo().assertIsDisplayed()
            ui.onNodeWithText("합류 완료").performScrollTo().performClick()
            ui.waitUntil(15000) { model.state.value.session?.status == PickupSessionStatus.COMPLETED && !model.state.value.busy }
            assertEquals(1, repository.completions)
            assertNull(model.state.value.invitation)
            ui.onNodeWithContentDescription("Pickup 공유 링크 QR").assertDoesNotExist()
        } finally {
            if (created) ui.runOnIdle { model.releaseResources(); model.viewModelScope.cancel() }
        }
    }
}

private class UiSafety : WaitingSafetyGateway {
    override val state = MutableStateFlow(WaitingSafety(VehicleMotion.PARKED, false, message = "테스트 전용 정차 입력"))
    override val drivingEvents = DrivingEventBuffer()
    override fun start() {}
    override fun stop() {}
    override fun close() {}
}
private class UiPickupRepository : PickupRepository {
    var waitingId: String? = "NOT_CALLED"
    var invites = 0
    var completions = 0
    var failInvite = false
    private var current = PickupSession("ui-test-only", null, PickupSessionStatus.ACTIVE, true, null, null, 0, Instant.now().toString(), null)
    override suspend fun create(waitingSessionId: String?, idempotencyKey: String): PickupSession {
        waitingId = waitingSessionId
        return current
    }
    override suspend fun getSession(sessionId: String) = current
    override suspend fun events(sessionId: String, afterRevision: Int) = PickupEventPage(emptyList(), current.revision)
    override suspend fun issueInvitation(sessionId: String, idempotencyKey: String): PickupInvitation {
        if (failInvite) error("테스트 전용 네트워크 오류")
        invites++
        return PickupInvitation("ui-invite-$invites", "https://example.test/share/UI_TEST_$invites", Instant.now().plusSeconds(3600).toString())
    }
    override suspend fun sendVehicleLocation(sessionId: String, location: PickupVehicleLocation) = Unit
    override suspend fun snapshot(sessionId: String) = PickupSnapshot(current, false, current.revision, Instant.now().toString(), null)
    override suspend fun complete(sessionId: String, idempotencyKey: String): PickupSession {
        completions++
        current = current.copy(status = PickupSessionStatus.COMPLETED, revision = current.revision + 1, endedAt = Instant.now().toString())
        return current
    }
    override suspend fun cancel(sessionId: String, reason: String, idempotencyKey: String) = error("not used")
    override suspend fun endLinkedWaiting(sessionId: String, idempotencyKey: String) = error("not used")
}
