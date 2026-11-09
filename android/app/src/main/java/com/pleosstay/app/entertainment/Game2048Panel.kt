package com.pleosstay.app.entertainment

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pleosstay.app.entertainment.games.Game2048State
import com.pleosstay.app.entertainment.games.MoveDirection
import com.pleosstay.app.ui.activity.ActivityModal
import com.pleosstay.app.ui.premium.PremiumAction
import com.pleosstay.app.ui.premium.StayColors

internal const val GAME_2048_HELP_PREFS = "entertainment_2048_help"
private const val HELP_SEEN_KEY = "seen"

@Composable
internal fun GamePanel(
    state: Game2048State,
    enabled: Boolean,
    move: (MoveDirection) -> Unit,
    restart: () -> Unit,
    showHelpInPreview: Boolean = false,
) {
    val context = LocalContext.current
    val inspection = LocalInspectionMode.current
    val preferences = remember(context, inspection) {
        if (inspection) null else context.getSharedPreferences(GAME_2048_HELP_PREFS, Context.MODE_PRIVATE)
    }
    var helpSeen by remember(preferences) { mutableStateOf(preferences?.getBoolean(HELP_SEEN_KEY, false) ?: true) }
    var helpRequested by rememberSaveable { mutableStateOf(false) }
    val helpVisible = enabled && (!helpSeen || helpRequested || (inspection && showHelpInPreview))
    val canMove = enabled && !state.gameOver && !helpVisible
    val latestMove by rememberUpdatedState(move)
    val latestCanMove by rememberUpdatedState(canMove)

    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        val fitToHeight = constraints.hasBoundedHeight
        Column(
            Modifier.widthIn(max = 680.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(10.dp), Alignment.CenterVertically) {
                Text("2048", Modifier.weight(1f), color = StayColors.Text, fontSize = 34.sp,
                    lineHeight = 40.sp, fontWeight = FontWeight.ExtraBold)
                PremiumAction("새 게임", restart, enabled = enabled && !helpVisible, secondary = true)
                FilledTonalIconButton(
                    onClick = { helpRequested = true },
                    enabled = enabled,
                    modifier = Modifier.size(56.dp).testTag("ent_game_help")
                        .semantics { contentDescription = "2048 도움말" },
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = GameSky, contentColor = GameInk,
                    ),
                ) {
                    Text("?", fontSize = 26.sp, fontWeight = FontWeight.Bold)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GameScore("점수", state.score, Modifier.weight(1f))
                GameScore("최고", state.bestScore, Modifier.weight(1f))
            }
            Text(
                when {
                    state.gameOver -> "게임이 끝났어요. 새 게임에 도전해 보세요."
                    state.won -> "2048 달성! 계속 밀어서 더 큰 숫자에 도전하세요."
                    else -> "보드를 밀어 같은 숫자를 합치세요."
                },
                fontSize = 18.sp,
                lineHeight = 24.sp,
                color = StayColors.Secondary,
            )
            val boardSpace = if (fitToHeight) Modifier.weight(1f, fill = false) else Modifier
            BoxWithConstraints(boardSpace.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                val boardSize = minOf(maxWidth, maxHeight)
                val input = if (canMove) Modifier
                    .semantics {
                        customActions = listOf(
                            "위로 이동" to MoveDirection.UP, "아래로 이동" to MoveDirection.DOWN,
                            "왼쪽으로 이동" to MoveDirection.LEFT, "오른쪽으로 이동" to MoveDirection.RIGHT,
                        ).map { (label, direction) ->
                            CustomAccessibilityAction(label) {
                                if (latestCanMove) {
                                    latestMove(direction)
                                    true
                                } else false
                            }
                        }
                    }
                    .pointerInput(boardSize) {
                        val minimumTravel = 24.dp.toPx()
                        var drag = Offset.Zero
                        detectDragGestures(
                            onDragStart = { drag = Offset.Zero },
                            onDragEnd = {
                                if (latestCanMove) gameSwipeDirection(drag.x, drag.y, minimumTravel)?.let(latestMove)
                            },
                        ) { change, amount ->
                            change.consume()
                            drag += amount
                        }
                    } else Modifier
                GameBoard(state, Modifier.size(boardSize).then(input))
            }
        }
    }
    if (helpVisible) {
        fun dismissHelp() {
            if (!helpSeen) {
                preferences?.edit()?.putBoolean(HELP_SEEN_KEY, true)?.apply()
                helpSeen = true
            }
            helpRequested = false
        }
        ActivityModal("2048 플레이 방법", onBack = ::dismissHelp) {
            Text("보드 위를 위·아래·왼쪽·오른쪽으로 미세요.\n같은 숫자가 만나면 하나로 합쳐져요.", color = StayColors.Text)
            Text("오른쪽 위 ? 버튼에서 설명을 다시 볼 수 있습니다.", color = StayColors.Secondary)
            PremiumAction("시작하기", ::dismissHelp, Modifier.testTag("ent_game_help_start"))
        }
    }
}

@Composable
private fun GameScore(label: String, score: Long, modifier: Modifier) {
    Text(
        "$label $score",
        modifier.background(StayColors.Surface, RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 8.dp),
        color = StayColors.Text,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun GameBoard(state: Game2048State, modifier: Modifier) {
    val frame = RoundedCornerShape(22.dp)
    Column(
        modifier.testTag("ent_game_board").semantics { contentDescription = "2048 게임 보드" }
            .background(StayColors.Surface, frame).border(1.dp, StayColors.Border, frame).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        state.board.chunked(4).forEach { row ->
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { tile ->
                    val (background, text) = tilePalette(tile)
                    Box(
                        Modifier.weight(1f).fillMaxHeight().background(background, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (tile != 0) BasicText(
                            tile.toString(),
                            modifier = Modifier.padding(horizontal = 4.dp),
                            style = TextStyle(color = text, fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            autoSize = TextAutoSize.StepBased(minFontSize = 12.sp, maxFontSize = if (tile >= 1024) 32.sp else 38.sp),
                        )
                    }
                }
            }
        }
    }
}

private fun tilePalette(tile: Int): Pair<Color,Color> = when(tile) {
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
