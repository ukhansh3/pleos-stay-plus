package com.pleosstay.app.ui

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.test.platform.app.InstrumentationRegistry
import com.pleosstay.app.documents.AndroidEncryptedDocumentStore
import com.pleosstay.app.parking.ui.ParkingScreen
import com.pleosstay.app.parking.ui.ParkingViewModel
import com.pleosstay.app.pickup.ui.PickupScreen
import com.pleosstay.app.pickup.ui.PickupUiState
import com.pleosstay.app.pickup.ui.PickupViewModel
import com.pleosstay.app.realtime.*
import com.pleosstay.app.rest.*
import com.pleosstay.app.study.*
import com.pleosstay.app.ui.premium.*
import com.pleosstay.app.ui.theme.PleosStayPlusTheme
import com.pleosstay.app.waiting.domain.WaitingPlace
import com.pleosstay.app.waiting.ui.WaitingScreen
import com.pleosstay.app.waiting.ui.WaitingUiState
import com.pleosstay.app.waiting.ui.WaitingViewModel
import com.pleosstay.app.work.WorkDraftRepository
import com.pleosstay.app.work.WorkScreen
import com.pleosstay.app.work.WorkViewModel
import com.pleosstay.app.work.docx.DocxStyle
import kotlinx.coroutines.*
import java.io.File
import java.util.UUID
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/**
 * Real production screens/VMs with synthetic androidTest inputs. These PNGs prove rendering,
 * not live API/vehicle/PIN E2E. Sources are safe to copy to the pre-UI baseline for comparison.
 * Run with -e proofStage before|after; output is in target cacheDir/premium-final-proof/.
 */
class PremiumFeatureScreenshotsTest {
    @get:Rule val ui = createComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val models = mutableListOf<ViewModel>()
    private val closables = mutableListOf<AutoCloseable>()
    private val scopes = mutableListOf<CoroutineScope>()
    private val directories = mutableListOf<File>()
    private fun directory() = File(context.cacheDir, "premium-ui-fixture-${UUID.randomUUID()}").apply { mkdirs(); directories += this }
    private fun waiting() = ProofWaiting(context).also { closables += it }
    private fun <T : ViewModel> model(create: () -> T): T {
        lateinit var result: T
        ui.runOnUiThread { result = create(); models += result }
        return result
    }
    private fun shell(route: StayRoute, content: @Composable () -> Unit) {
        ui.setContent { PleosStayPlusTheme { StayAppShell({}, requestedRoute = route) { _, _ -> content() } } }
        ui.waitForIdle()
    }
    private fun capture(name: String, route: StayRoute) {
        ui.waitForIdle()
        val group = when (route) {
            StayRoute.WAITING -> "PARKING"
            StayRoute.REST, StayRoute.STUDY, StayRoute.WORK -> "MODES"
            else -> route.name
        }
        ui.onNodeWithTag("nav_$group").assertIsSelected()
        val bitmap = ui.onRoot().captureToImage().asAndroidBitmap()
        assertTrue(bitmap.width > 0 && bitmap.height > 0)
        val stage = InstrumentationRegistry.getArguments().getString("proofStage") ?: "after"
        require(stage in listOf("before", "after"))
        val file = File(context.cacheDir, "premium-final-proof/$stage/$name-fixture.png").apply { parentFile!!.mkdirs() }
        file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
    }
    @After fun releaseFixtures() {
        ui.runOnUiThread { models.forEach { it.viewModelScope.cancel() }; scopes.forEach { it.cancel() } }
        closables.asReversed().forEach { it.close() }
        directories.forEach { it.deleteRecursively() }
    }

    @Test fun homeFrozenEmptyFixture() {
        shell(StayRoute.HOME) { StayHomeScreen(WaitingUiState(), PickupUiState(), {}) }
        ui.onNodeWithText("Pleos Stay+").assertExists()
        capture("01-home", StayRoute.HOME)
    }

    @Test fun parkingSearchFixture() {
        val repository = ProofParking()
        val model = model { ParkingViewModel(repository, ProofNavigation(), ProofLocation()) }
        shell(StayRoute.PARKING) { ParkingScreen(model, model::searchNearby) }
        ui.onNodeWithText("내 주변 검색").performScrollTo().performClick()
        ui.waitUntil(15000) { model.state.value.places.size == 2 && !model.state.value.loading }
        ui.onAllNodesWithText("테스트 공영주차장").onFirst().assertExists()
        capture("02-parking", StayRoute.PARKING)
    }

    @Test fun parkingDetailFixture() {
        val repository = ProofParking()
        val model = model { ParkingViewModel(repository, ProofNavigation(), ProofLocation()) }
        shell(StayRoute.PARKING) { ParkingScreen(model, model::searchNearby) }
        ui.runOnIdle { model.searchNearby() }
        ui.waitUntil(15000) { model.state.value.places.size == 2 && !model.state.value.loading }
        ui.onAllNodesWithText("테스트 공영주차장").onFirst().performScrollTo().performClick()
        ui.waitUntil(15000) { model.state.value.selected != null && !model.state.value.loading }
        ui.onAllNodesWithText("테스트 공영주차장").onFirst().assertExists()
        capture("02b-parking-detail", StayRoute.PARKING)
    }

    @Test fun waitingSessionFixture() {
        val waiting = waiting()
        val model = model { WaitingViewModel(waiting.repository, waiting.safety) }
        shell(StayRoute.WAITING) { WaitingScreen(model, WaitingPlace("proof-place", "테스트 대기 장소"), onRest = {}, onStudy = {}, onWork = {}) }
        ui.waitUntil(15000) { model.state.value.canUse }
        ui.onNodeWithText("대기 시작").performScrollTo().performClick()
        ui.waitUntil(15000) { model.state.value.active != null && !model.state.value.busy }
        ui.onNodeWithText("대기 종료").assertExists()
        capture("03-waiting", StayRoute.WAITING)
    }

    @Test fun pickupQrFixture() {
        val safety = ProofSafety()
        val model = model { PickupViewModel(ProofPickup(), ProofLocation(), safety, refreshIntervalMs = 60000) }
        shell(StayRoute.PICKUP) { PickupScreen(model, { model.start() }) }
        ui.waitUntil(10000) { !model.state.value.blocked }
        ui.runOnIdle { model.start() }
        ui.waitUntil(15000) { model.state.value.session != null && !model.state.value.busy }
        ui.runOnIdle { model.issueInvitation() }
        ui.waitUntil(15000) { model.state.value.invitation != null && !model.state.value.busy }
        ui.onNodeWithContentDescription("Pickup 공유 링크 QR").assertExists()
        capture("04-pickup", StayRoute.PICKUP)
    }

    @Test fun realtimeFilesFixture() {
        val server = ProofRealtimeServer().also { closables += it }
        val model = model { FileTransferViewModel(RealtimeApi(server.url) { "synthetic-instrumentation-only" }, ProofSafety(), ProofRevisionStore(), directory()) }
        shell(StayRoute.FILES) { FileTransferScreen(model) }
        ui.waitUntil(10000) { model.state.value.allowed }
        ui.runOnIdle { model.unlockAfterUserVerification(); model.refresh() }
        ui.waitUntil(15000) { model.state.value.files.size == 2 && !model.state.value.busy }
        ui.runOnIdle { model.versions("proof-docx") }
        ui.waitUntil(15000) { model.state.value.versions.size == 1 && !model.state.value.busy }
        ui.onAllNodesWithText("테스트_회의록.docx", substring = true).onFirst().assertExists()
        capture("05-realtime", StayRoute.FILES)
    }

    @Test fun restTimerFixture() {
        val waiting = waiting()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).also { scopes += it }
        lateinit var controller: RestController
        ui.runOnUiThread { controller = proofRest(waiting, scope); controller.setForeground(true) }
        shell(StayRoute.REST) { RestScreen(controller, {}, {}) }
        ui.waitUntil(15000) { controller.state.value.ready && !controller.state.value.busy }
        ui.runOnIdle { controller.start(RestPreset.defaults[2]) }
        ui.waitUntil(15000) { controller.state.value.session != null && !controller.state.value.busy }
        assertNull(controller.state.value.session?.end)
        capture("06-rest", StayRoute.REST)
    }

    @Test fun studyDocumentAndMemoFixture() {
        val dir = directory()
        val waiting = waiting()
        val documents = ProofDocuments(dir)
        val repository = EncryptedStudyRepository(File(dir, "study"), ProofStudyCipher())
        val model = model { StudyViewModel(waiting.manager, waiting.safety, documents, repository) }
        ui.runOnUiThread { model.setForeground(true) }
        shell(StayRoute.STUDY) { StudyScreen(model, {}) }
        ui.waitUntil(10000) { model.state.value.allowed }
        ui.onNodeWithText("학습 시작").performScrollTo().performClick()
        ui.waitUntil(15000) { model.state.value.active && !model.state.value.busy }
        ui.runOnIdle { model.refreshFiles() }
        ui.waitUntil(15000) { model.state.value.files.size == 2 && !model.state.value.busy }
        ui.onAllNodesWithText(documents.txtRef.name).onFirst().performScrollTo().performClick()
        ui.waitUntil(15000) { model.state.value.versions.isNotEmpty() && !model.state.value.busy }
        ui.runOnIdle { model.open(documents.txtRef) }
        ui.waitUntil(15000) { model.state.value.document != null && !model.state.value.busy }
        ui.runOnIdle { model.saveNote(null, "테스트 메모: 핵심 내용을 정리합니다.") }
        ui.waitUntil(15000) { model.state.value.record?.notes?.isNotEmpty() == true && !model.state.value.busy }
        assertEquals(StudyFormat.TXT, model.state.value.document?.format)
        capture("07-study", StayRoute.STUDY)
    }

    private fun workModel(): Pair<WorkViewModel, ProofDocuments> {
        val dir = directory()
        val documents = ProofDocuments(dir)
        val waiting = waiting()
        val records = AndroidEncryptedDocumentStore(context, "premium-proof-${UUID.randomUUID()}")
        closables += AutoCloseable { runBlocking { records.keys().forEach { records.delete(it) } } }
        val repository = WorkDraftRepository(records, documents)
        val model = model { WorkViewModel(documents, repository, waiting.manager, waiting.safety) }
        ui.runOnUiThread { model.setForeground(true) }
        return model to documents
    }

    @Test fun workLibraryFixture() {
        val (model, documents) = workModel()
        shell(StayRoute.WORK) { WorkScreen(model, {}, {}, {}) }
        ui.waitUntil(10000) { model.state.value.allowed }
        ui.runOnIdle { model.start() }
        ui.waitUntil(15000) { model.state.value.session != null && !model.state.value.busy }
        ui.runOnIdle { model.refresh() }
        ui.waitUntil(15000) { model.state.value.files.isNotEmpty() && !model.state.value.busy }
        ui.runOnIdle { model.versions(documents.docxRef.fileId) }
        ui.waitUntil(15000) { model.state.value.versions.isNotEmpty() && !model.state.value.busy }
        ui.onAllNodesWithText(documents.docxRef.name, substring = true).onFirst().assertExists()
        capture("08-work-library", StayRoute.WORK)
    }

    @Test fun workEditorFixture() {
        val (model, documents) = workModel()
        shell(StayRoute.WORK) { WorkScreen(model, {}, {}, {}) }
        ui.waitUntil(10000) { model.state.value.allowed }
        ui.runOnIdle { model.start() }
        ui.waitUntil(15000) { model.state.value.session != null && !model.state.value.busy }
        ui.runOnIdle { model.open(documents.docxRef) }
        ui.waitUntil(20000) { model.docx.state.value.view != null && !model.docx.state.value.busy && !model.state.value.busy }
        val paragraph = model.docx.state.value.view!!.paragraphs.first()
        ui.runOnIdle { model.docx.setText(paragraph.id, "테스트 회의록 · 수정본") }
        ui.waitUntil(15000) { !model.docx.state.value.busy && model.docx.state.value.view?.paragraphs?.first()?.text == "테스트 회의록 · 수정본" }
        ui.runOnIdle { model.docx.edit { it.format(paragraph.id, 0, 3, DocxStyle(bold = true, color = "315B88")) } }
        ui.waitUntil(15000) { !model.docx.state.value.busy }
        assertTrue(model.docx.state.value.canUndo)
        capture("09-work-editor", StayRoute.WORK)
    }
}
