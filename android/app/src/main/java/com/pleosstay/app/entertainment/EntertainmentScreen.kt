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
import androidx.compose.ui.graphics.Color
import com.pleosstay.app.entertainment.quiz.*
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
    val match by runtime.match.state.collectAsState()
    var confirmEnd by remember { mutableStateOf(false) }
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
                EntertainmentCatalogue(false, {}, {}, {})
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    PremiumAction("콘텐츠 선택", { command { check(runtime.controller.catalogue().success) } }, enabled = !busy, secondary = true)
                    PremiumAction("활동 종료", {
                        if(match?.complete == false) confirmEnd = true
                        else if(actions!=null) actions.finish(ActivityMode.ENTERTAINMENT) else command { runtime.controller.end() }
                    }, enabled = !busy, secondary = true)
                }
                if(state.content!=null && state.phase!=EntertainmentPhase.ACTIVE && state.phase!=EntertainmentPhase.EXTERNAL) {
                    PremiumAction("콘텐츠 재개", { command { check(runtime.controller.select(checkNotNull(state.content)).success) } }, enabled=!busy)
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
                    EntertainmentContent.QUIZ_MATCH -> MatchPanel(match, !busy && state.phase == EntertainmentPhase.ACTIVE,
                        { config -> command { runtime.startMatch(config) } },
                        { id, option -> command { runtime.answerMatch(id,option) } },
                        { command { runtime.nextMatch() } }, { command { runtime.resetMatch() } })
                    else -> EntertainmentCatalogue(!busy,
                        { command { check(runtime.select2048().success) } },
                        { command { check(runtime.selectQuiz().success) } },
                        { command { check(runtime.selectMatch().success) } })
                }
            }
        }
    }
    if(confirmEnd) AlertDialog(onDismissRequest = { confirmEnd=false }, title = { Text("대결을 종료할까요?") },
        text = { Text("진행 중인 대결과 참가자 이름이 삭제됩니다. 대기는 계속 유지됩니다.") },
        confirmButton = { TextButton(onClick = { confirmEnd=false; if(actions!=null)actions.finish(ActivityMode.ENTERTAINMENT) else command { runtime.controller.end() } }) { Text("대결 종료") } },
        dismissButton = { TextButton(onClick = { confirmEnd=false }) { Text("계속 대결") } })

}


internal val GameInk = Color(0xFF002C5F)
internal val GamePaper = Color(0xFFF6F3F2)
internal val GameSky = Color(0xFFAACAE6)
internal fun tilePalette(tile: Int): Pair<Color,Color> = when(tile) {
    0 -> StayColors.Background to StayColors.Text
    2 -> GamePaper to GameInk
    4 -> Color(0xFFE4DCD3) to GameInk
    8 -> GameSky to GameInk
    16 -> Color(0xFF7DAFD8) to GameInk
    32 -> Color(0xFF00AAD2) to GameInk
    64 -> GameInk to StayColors.Text
    128 -> Color(0xFF493D78) to StayColors.Text
    256 -> Color(0xFF6A452F) to StayColors.Text
    512 -> Color(0xFF513721) to StayColors.Text
    1024 -> Color(0xFF3D386A) to StayColors.Text
    else -> Color(0xFF273550) to StayColors.Text
}

@Composable
internal fun GamePanel(state: Game2048State, enabled: Boolean, move: (MoveDirection) -> Unit, restart: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val wide = maxWidth >= 900.dp
        val board: @Composable (Modifier) -> Unit = { modifier ->
            Column(modifier.testTag("ent_game_board").background(StayColors.Surface, RoundedCornerShape(22.dp)).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                state.board.chunked(4).forEach { row -> Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    row.forEach { tile -> val (bg,fg)=tilePalette(tile)
                        Box(Modifier.weight(1f).aspectRatio(1f).background(bg,RoundedCornerShape(14.dp)),contentAlignment=Alignment.Center) {
                            if(tile!=0) Text(tile.toString(),fontSize=if(tile>=1024) 28.sp else 38.sp,fontWeight=FontWeight.Bold,color=fg)
                        }
                    }
                } }
            }
        }
        val controls: @Composable (Modifier) -> Unit = { modifier -> Column(modifier,verticalArrangement=Arrangement.spacedBy(22.dp),horizontalAlignment=Alignment.CenterHorizontally) {
            Text("점수 ${state.score}",fontSize=28.sp,color=StayColors.Text)
            Text("최고 ${state.bestScore}",fontSize=22.sp,color=StayColors.Secondary)
            PremiumAction("새 게임",restart,enabled=enabled,secondary=true)
            @Composable fun direction(label:String,d:MoveDirection) {
                Button(onClick={move(d)},enabled=enabled && !state.gameOver,modifier=Modifier.size(76.dp),
                    colors=ButtonDefaults.buttonColors(containerColor=GameSky,contentColor=GameInk)) {Text(label,fontSize=32.sp)}
            }
            Column(verticalArrangement=Arrangement.spacedBy(8.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                direction("↑",MoveDirection.UP)
                Row(horizontalArrangement=Arrangement.spacedBy(76.dp)) {direction("←",MoveDirection.LEFT);direction("→",MoveDirection.RIGHT)}
                direction("↓",MoveDirection.DOWN)
            }
            if(state.gameOver) Text("게임이 끝났어요.",color=StayColors.Text,fontSize=22.sp)
            else if(state.won) Text("2048 달성!",color=StayColors.Text,fontSize=22.sp)
        } }
        if(wide) Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(32.dp)) {
            board(Modifier.weight(1.5f).widthIn(max=700.dp));controls(Modifier.weight(1f))
        } else Column(verticalArrangement=Arrangement.spacedBy(20.dp)) {board(Modifier.widthIn(max=680.dp).fillMaxWidth());controls(Modifier.fillMaxWidth())}
    }
}

@Composable
internal fun QuizPanel(state: QuizState, enabled: Boolean, answer: (String, Int) -> Unit, next: () -> Unit, restart: () -> Unit) {
    Surface(color=GamePaper,shape=RoundedCornerShape(24.dp),modifier=Modifier.fillMaxWidth()) {
        Column(Modifier.padding(28.dp),verticalArrangement=Arrangement.spacedBy(24.dp)) {
            if(state.complete) {
                Text("퀴즈 완료",fontSize=34.sp,fontWeight=FontWeight.Bold,color=GameInk)
                Text("${state.score}점 · ${state.correctCount}/5 정답",fontSize=38.sp,color=GameInk)
                Text("최고 점수 ${state.bestScore}",fontSize=22.sp,color=GameInk)
                PremiumAction("다시 풀기",restart,enabled=enabled)
            } else state.current?.let { question ->
                Text("혼자 풀기 · 문제 ${state.index+1} / 5 · ${state.score}점",fontSize=22.sp,color=GameInk)
                LinearProgressIndicator(progress={state.index/5f},modifier=Modifier.fillMaxWidth(),color=GameInk)
                QuizQuestionPanel(question,state.answers[state.index],enabled, {answer(question.id,it)})
                state.currentCorrect?.let { correct ->
                    Text(if(correct) "✓ 정답이에요!" else "다음에 맞혀봐요. 정답: ${question.options[question.answer]}",color=GameInk,fontSize=24.sp)
                    PremiumAction(if(state.index==4) "결과 보기" else "다음 문제",next,enabled=enabled)
                }
            }
        }
    }
}

@Composable
internal fun QuizQuestionPanel(question:QuizQuestion,selected:Int?,enabled:Boolean,choose:(Int)->Unit) {
    Text(question.prompt,fontSize=32.sp,color=GameInk,fontWeight=FontWeight.Bold,modifier=Modifier.padding(vertical=20.dp))
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns=if(maxWidth>=700.dp)2 else 1
        Column(verticalArrangement=Arrangement.spacedBy(16.dp)) {
            question.options.indices.toList().chunked(columns).forEach { row -> Row(horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                row.forEach { i -> val marked=selected!=null && i==question.answer
                    val bg=if(marked)GameInk else GameSky;val fg=if(marked)StayColors.Text else GameInk
                    Button(onClick={choose(i)},enabled=enabled && selected==null,
                        modifier=Modifier.weight(1f).heightIn(min=88.dp),shape=RoundedCornerShape(16.dp),
                        colors=ButtonDefaults.buttonColors(containerColor=bg,contentColor=fg,disabledContainerColor=bg,disabledContentColor=fg)) {
                        Text("${('A'.code+i).toChar()}  ${question.options[i]}" + if(marked) "  ✓ 정답" else if(i==selected) "  선택" else "",fontSize=24.sp)
                    }
                }
            } }
        }
    }
}

@Composable
internal fun EntertainmentCatalogue(enabled:Boolean,game:()->Unit,quiz:()->Unit,match:()->Unit) {
    Column(verticalArrangement=Arrangement.spacedBy(20.dp)) {
        Text("가볍게 즐길 콘텐츠를 선택해 보세요.",color=StayColors.Secondary,fontSize=22.sp)
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val entries=listOf(Triple("2048","같은 숫자를 합쳐보세요.",game),Triple("상식 퀴즈","혼자 다섯 문제를 풀어보세요.",quiz),Triple("함께 대결하기","2~4명이 차례로 도전해요.",match))
            @Composable fun card(i:Int,modifier:Modifier) { val (title,hint,action)=entries[i]
                Surface(modifier,shape=RoundedCornerShape(22.dp),color=StayColors.Surface) {Column(Modifier.padding(24.dp),verticalArrangement=Arrangement.spacedBy(20.dp)) {
                    Text(title,fontSize=30.sp,color=StayColors.Text,fontWeight=FontWeight.Bold)
                    Text(hint,fontSize=22.sp,color=StayColors.Secondary)
                    PremiumAction("선택",action,enabled=enabled,modifier=Modifier.fillMaxWidth())
                }}
            }
            if(maxWidth>=1000.dp)Row(horizontalArrangement=Arrangement.spacedBy(20.dp)){entries.indices.forEach{card(it,Modifier.weight(1f))}}
            else Column(verticalArrangement=Arrangement.spacedBy(16.dp)){entries.indices.forEach{card(it,Modifier.fillMaxWidth())}}
        }
        Text("노래방 · 실제 마이크/반주 동시 사용 확인 필요",fontSize=22.sp,color=StayColors.Secondary)
        Text("영상 · PLEOS 대상 앱 및 실행 지원 확인 필요",fontSize=22.sp,color=StayColors.Secondary)
    }
}
