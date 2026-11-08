package com.pleosstay.app.entertainment

import android.content.ContextWrapper
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.pleosstay.app.entertainment.games.Game2048Engine
import com.pleosstay.app.entertainment.games.MoveDirection
import com.pleosstay.app.ui.theme.PleosStayPlusTheme
import com.pleosstay.app.waiting.data.WaitingRepository
import com.pleosstay.app.waiting.domain.WaitingPlace
import com.pleosstay.app.waiting.domain.WaitingSession
import com.pleosstay.app.waiting.safety.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID

/** Test-only admission and vehicle input. No production permission, audio or Cloud writes. */
class EntertainmentUiTest {
    @get:Rule val ui = createComposeRule()
    private var fixture: Fixture? = null

    @After fun cleanup() {
        fixture?.let { value ->
            runBlocking { value.runtime.controller.close() }
            value.scope.cancel()
            value.directory.deleteRecursively()
        }
    }

    @Test fun real2048MovePersistsAndUnknownImmediatelyMasksTheBoard() {
        val value = show()
        ui.onNodeWithTag("ent_start").performClick()
        ui.waitUntil(10_000) { value.runtime.controller.state.value.active }
        awaitCommand()
        ui.onNodeWithTag("ent_2048").performClick()
        ui.waitUntil(10_000) { value.runtime.game.resume() != null }
        awaitCommand()
        ui.onNodeWithTag("ent_game_board").assertExists()
        val before = checkNotNull(value.runtime.game.resume())
        val direction = MoveDirection.entries.first { Game2048Engine().slide(before.board, it).changed }
        val label = when (direction) { MoveDirection.LEFT -> "←"; MoveDirection.RIGHT -> "→"; MoveDirection.UP -> "↑"; MoveDirection.DOWN -> "↓" }
        ui.onNodeWithText(label).performScrollTo().performClick()
        ui.waitUntil(10_000) { value.runtime.game.resume()?.board != before.board }
        assertNotEquals(before.board, value.runtime.game.resume()!!.board)
        ui.runOnIdle { value.safety.state.value = WaitingSafety(message = "정차 상태 확인 필요") }
        ui.waitForIdle()
        ui.onNodeWithTag("ent_game_board").assertDoesNotExist()
        ui.onNodeWithText("정차 상태 확인 필요").assertExists()
        ui.onNodeWithTag("ent_2048").assertDoesNotExist()
        assertEquals(1, value.admission.acquired)
        assertEquals(0, value.waiting.mutations)
    }

    @Test fun quizAnswersUseTheRealServiceAndUnverifiedMediaRemainAbsent() {
        val value = show()
        ui.onNodeWithTag("ent_start").performClick()
        ui.waitUntil(10_000) { value.runtime.controller.state.value.active }
        awaitCommand()
        ui.onNodeWithTag("ent_quiz").performClick()
        ui.waitUntil(10_000) { value.runtime.quiz.resume() != null }
        awaitCommand()
        val question = value.runtime.quiz.resume()!!.current!!
        ui.onNodeWithText(question.options[question.answer]).performScrollTo().performClick()
        ui.waitUntil(10_000) { value.runtime.quiz.resume()?.currentCorrect == true }
        awaitCommand()
        ui.onNodeWithText("정답입니다.").performScrollTo().assertIsDisplayed()
        ui.onNodeWithText("다음").performScrollTo().performClick()
        ui.waitUntil(10_000) { value.runtime.quiz.resume()?.index == 1 }
        ui.onNodeWithText("가라오케").assertDoesNotExist()
        ui.onNodeWithText("YouTube").assertDoesNotExist()
        ui.onNodeWithText("Netflix").assertDoesNotExist()
        assertEquals(0, value.waiting.mutations)
    }

    private fun awaitCommand() {
        ui.waitUntil(10_000) { ui.onAllNodesWithTag("ent_busy").fetchSemanticsNodes().isEmpty() }
    }

    private fun show(): Fixture {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(app.cacheDir, "entertainment-ui-test-${UUID.randomUUID()}").apply { check(mkdirs()) }
        val context = object : ContextWrapper(app) { override fun getNoBackupFilesDir() = directory }
        val safe = TestOnlySafety()
        val waiting = TestOnlyWaiting()
        val admission = TestOnlyAdmission()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val runtime = EntertainmentRuntime.create(context, scope, safe, waiting, admission, emptyList())
        runBlocking { runtime.controller.onHostForeground(true) }
        return Fixture(runtime, safe, waiting, admission, scope, directory).also { value ->
            fixture = value
            ui.setContent {
                val safety by value.safety.state.collectAsState()
                PleosStayPlusTheme { EntertainmentScreen(value.runtime, safety, {}) }
            }
        }
    }

    private data class Fixture(val runtime: EntertainmentRuntime, val safety: TestOnlySafety,
        val waiting: TestOnlyWaiting, val admission: TestOnlyAdmission, val scope: CoroutineScope, val directory: File)
    private class TestOnlyAdmission : EntertainmentActivityAdmission {
        var acquired = 0
        override suspend fun acquire(): String { acquired++; return "androidTest-only-$acquired" }
        override suspend fun release(permit: String) = Unit
    }
    private class TestOnlySafety : WaitingSafetyGateway {
        override val state = MutableStateFlow(WaitingSafety(VehicleMotion.PARKED, false))
        override val drivingEvents = DrivingEventBuffer()
        override fun start() = Unit
        override fun stop() = Unit
        override fun close() = Unit
    }
    private class TestOnlyWaiting : WaitingRepository {
        override val sessions = MutableStateFlow<List<WaitingSession>>(emptyList())
        var mutations = 0
        override suspend fun restore() { mutations++ }
        override suspend fun start(minutes: Int, place: WaitingPlace?) { mutations++ }
        override suspend fun extend(id: String, minutes: Int) { mutations++ }
        override suspend fun end(id: String) { mutations++ }
        override suspend fun onDrivingConfirmed(observedAt: Long) { mutations++ }
        override suspend fun retryPendingEnd() { mutations++ }
    }
}
