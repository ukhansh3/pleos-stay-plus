package com.pleosstay.app.entertainment

import androidx.activity.compose.BackHandler
import com.pleosstay.app.ui.activity.*
import com.pleosstay.app.modes.ActivityMode
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pleosstay.app.entertainment.games.Game2048State
import com.pleosstay.app.entertainment.games.MoveDirection
import com.pleosstay.app.entertainment.quiz.QuizState
import com.pleosstay.app.entertainment.recommendation.EntertainmentContent
import com.pleosstay.app.ui.premium.PremiumAction
import com.pleosstay.app.ui.premium.StayColors
import com.pleosstay.app.waiting.safety.WaitingSafety
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Existing game/quiz presentation. Production admission is supplied by the shared activity host. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EntertainmentScreen(runtime: EntertainmentRuntime, safety: WaitingSafety, onHome: () -> Unit, hostForeground:Boolean=true) {
    val actions=LocalActivityUiActions.current
    val state by runtime.controller.state.collectAsState()
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var game by remember { mutableStateOf<Game2048State?>(null) }
    var quiz by remember { mutableStateOf<QuizState?>(null) }
    val canUse = safety.canUse && hostForeground
    fun command(action: suspend () -> Unit) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                withContext(Dispatchers.IO) { action() }
                game = withContext(Dispatchers.IO) { runtime.game.resume() }
                quiz = withContext(Dispatchers.IO) { runtime.quiz.resume() }
                message = null
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { message = "콘텐츠를 열 수 없습니다. 다시 시도해 주세요." }
            finally { busy = false }
        }
    }
    val home:()->Unit = {
        if(actions!=null)actions.home() else
        command {
            val result = runtime.controller.onInternalHome()
            if (result.success) withContext(Dispatchers.Main) { onHome() }
        }
    }
    BackHandler(onBack = home)
    LaunchedEffect(state.content, state.phase) {
        if (canUse && state.active) {
            game = withContext(Dispatchers.IO) { runCatching { runtime.game.resume() }.getOrNull() }
            quiz = withContext(Dispatchers.IO) { runCatching { runtime.quiz.resume() }.getOrNull() }
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("엔터테인먼트", Modifier.weight(1f), fontSize = 38.sp, fontWeight = FontWeight.Bold)
            TextButton(onClick = home, enabled = !busy, modifier = Modifier.heightIn(min = 56.dp)) { Text("홈으로 이동") }
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().testTag("ent_busy"))
        if (!canUse) {
            Text(if (!safety.canUse) safety.message else "정차 상태를 확인해 주세요.", color = StayColors.Warning, fontSize = 24.sp)
        } else {
            message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (state.phase == EntertainmentPhase.ERROR) Text(state.message, color = MaterialTheme.colorScheme.error)
            if (!state.active) {
                PremiumAction("시작", { if(actions!=null)actions.startEntertainment() else command { runtime.controller.start() } }, enabled = !busy,
                    modifier = Modifier.testTag("ent_start"))
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    PremiumAction("2048", { command { runtime.select2048() } }, enabled = !busy,
                        modifier = Modifier.testTag("ent_2048"), secondary = state.content != EntertainmentContent.GAME_2048)
                    PremiumAction("상식 퀴즈", { command { runtime.selectQuiz() } }, enabled = !busy,
                        modifier = Modifier.testTag("ent_quiz"), secondary = state.content != EntertainmentContent.QUIZ)
                    OutlinedButton(onClick = { if(actions!=null)actions.finish(ActivityMode.ENTERTAINMENT) else command { runtime.controller.end() } }, enabled = !busy,
                        modifier = Modifier.heightIn(min = 64.dp)) { Text("종료", fontSize = 20.sp) }
                }
                when (state.content) {
                    EntertainmentContent.GAME_2048 -> game?.let { value ->
                        GamePanel(value, enabled = !busy && state.phase == EntertainmentPhase.ACTIVE,
                            move = { direction -> command { runtime.move2048(direction) } },
                            restart = { command { runtime.new2048() } })
                    }
                    EntertainmentContent.QUIZ -> quiz?.let { value ->
                        QuizPanel(value, enabled = !busy && state.phase == EntertainmentPhase.ACTIVE,
                            answer = { id, option -> command { runtime.answerQuiz(id, option) } },
                            next = { command { runtime.nextQuiz() } }, restart = { command { runtime.newQuiz() } })
                    }
                    else -> Unit
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun GamePanel(state: Game2048State, enabled: Boolean, move: (MoveDirection) -> Unit, restart: () -> Unit) {
    Column(Modifier.widthIn(max = 720.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Text("점수 ${state.score}", fontSize = 24.sp)
            Text("최고 ${state.bestScore}", color = StayColors.Secondary, fontSize = 22.sp)
        }
        Column(Modifier.fillMaxWidth().testTag("ent_game_board").background(StayColors.Surface, RoundedCornerShape(22.dp)).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            state.board.chunked(4).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { tile ->
                        Box(Modifier.weight(1f).aspectRatio(1f).background(
                            if (tile == 0) StayColors.Background else StayColors.Action, RoundedCornerShape(14.dp)),
                            contentAlignment = Alignment.Center) {
                            if (tile != 0) Text(tile.toString(), fontSize = if (tile >= 1024) 26.sp else 34.sp,
                                fontWeight = FontWeight.SemiBold, color = StayColors.Text)
                        }
                    }
                }
            }
        }
        if (state.gameOver) Text("게임이 끝났습니다.", fontSize = 22.sp)
        else if (state.won) Text("2048 달성", color = StayColors.Success, fontSize = 22.sp)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf("←" to MoveDirection.LEFT, "↑" to MoveDirection.UP, "↓" to MoveDirection.DOWN, "→" to MoveDirection.RIGHT).forEach { (label, direction) ->
                Button(onClick = { move(direction) }, enabled = enabled && !state.gameOver,
                    modifier = Modifier.sizeIn(minWidth = 80.dp, minHeight = 64.dp)) { Text(label, fontSize = 30.sp) }
            }
            OutlinedButton(onClick = restart, enabled = enabled, modifier = Modifier.heightIn(min = 64.dp)) { Text("새 게임") }
        }
    }
}

@Composable
internal fun QuizPanel(state: QuizState, enabled: Boolean, answer: (String, Int) -> Unit, next: () -> Unit, restart: () -> Unit) {
    Column(Modifier.widthIn(max = 920.dp).fillMaxWidth().background(StayColors.Surface, RoundedCornerShape(24.dp)).padding(28.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp)) {
        if (state.complete) {
            Text("퀴즈 완료", fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
            Text("${state.score}점 · ${state.correctCount}/5 정답", fontSize = 32.sp)
            Text("최고 점수 ${state.bestScore}", color = StayColors.Secondary)
            PremiumAction("다시 풀기", restart, enabled = enabled)
        } else state.current?.let { question ->
            Text("${state.index + 1} / 5", color = StayColors.Secondary, fontSize = 20.sp)
            Text(question.prompt, fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
            question.options.forEachIndexed { index, option ->
                OutlinedButton(onClick = { answer(question.id, index) }, enabled = enabled && state.answers[state.index] == null,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)) { Text(option, fontSize = 22.sp) }
            }
            state.currentCorrect?.let { correct ->
                Text(if (correct) "정답입니다." else "정답: ${question.options[question.answer]}",
                    color = if (correct) StayColors.Success else StayColors.Secondary)
                PremiumAction("다음", next, enabled = enabled)
            }
        }
    }
}
