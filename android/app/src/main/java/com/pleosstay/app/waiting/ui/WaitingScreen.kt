package com.pleosstay.app.waiting.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pleosstay.app.ui.premium.*
import com.pleosstay.app.waiting.domain.*
import java.text.DateFormat
import java.util.Date

@Composable
fun WaitingScreen(viewModel: WaitingViewModel, selectedPlace: WaitingPlace?, modifier: Modifier = Modifier,
    onRest: (() -> Unit)? = null, onStudy: (() -> Unit)? = null, onWork: (() -> Unit)? = null) {
    val state by viewModel.state.collectAsState()
    var minutes by remember { mutableStateOf("30") }
    var extension by remember { mutableStateOf("15") }
    var endConfirm by remember { mutableStateOf(false) }
    var historyOpen by remember { mutableStateOf(false) }
    val policy = remember { WaitingPolicy() }
    val active = state.active
    val history = state.sessions.filter { it.status == WaitingStatus.ENDED }
    if (endConfirm && state.canUse && active != null) AlertDialog(
        onDismissRequest = { endConfirm = false }, title = { Text("대기를 종료할까요?") },
        confirmButton = { TextButton(onClick = { endConfirm = false; viewModel.end(active.id) }) { Text("종료") } },
        dismissButton = { TextButton(onClick = { endConfirm = false }) { Text("계속 대기") } })
    if (historyOpen && state.canUse) AlertDialog(onDismissRequest = { historyOpen = false }, title = { Text("지난 대기 기록") },
        text = {
            LazyColumn(Modifier.heightIn(max = 460.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                if (history.isEmpty()) item { Text("대기 기록이 없습니다.") }
                items(history, key = { it.id }) { session ->
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(session.place?.name ?: "직접 대기", fontWeight = FontWeight.SemiBold)
                        Text(session.startedAt.dateLabel(), color = StayColors.Secondary)
                        Text(WaitingTime.calculate(session, state.now).elapsedMillis.durationLabel(), color = StayColors.Blue)
                        Text(if (session.endReason == EndReason.DRIVING) "주행 확인으로 종료" else "직접 종료", color = StayColors.Secondary)
                        HorizontalDivider(color = StayColors.Border)
                    }
                }
            }
        }, confirmButton = { TextButton(onClick = { historyOpen = false }) { Text("닫기") } })
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 28.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Text("대기 관리", fontSize = 38.sp, lineHeight = 46.sp, fontWeight = FontWeight.Bold)
        (state.drivingRecovery.error ?: state.error)?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 20.sp) }
        if (!state.canUse) {
            FeaturePanel(Modifier.fillMaxWidth()) {
                Text("정차 확인이 필요합니다", fontSize = 27.sp, fontWeight = FontWeight.Bold)
                Text(if (!state.ready) "대기 기록을 확인하고 있습니다." else state.safety.message, color = StayColors.Secondary, fontSize = 21.sp)
            }
        } else {
            ResponsivePanels(leftWeight = 1.18f, left = {
                FeaturePanel(Modifier.fillMaxWidth().heightIn(min = 540.dp)) {
                    Text(if (active == null) "새 대기" else "대기 세션 진행 중", fontSize = 27.sp, fontWeight = FontWeight.SemiBold)
                    val time = active?.let { WaitingTime.calculate(it, state.now) }
                    val total = active?.let { (it.plannedEndAt - it.startedAt).coerceAtLeast(1L) } ?: 1L
                    BoxWithConstraints(Modifier.fillMaxWidth().weight(1f).heightIn(min = 380.dp), contentAlignment = Alignment.Center) {
                        val ringSize = minOf(maxWidth - 24.dp, 360.dp).coerceAtLeast(160.dp)
                        Canvas(Modifier.size(ringSize)) {
                            val inset = 12.dp.toPx()
                            val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
                            drawArc(StayColors.Border, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(12.dp.toPx()))
                            val remaining = if (time == null) 1f else (time.remainingMillis.toFloat() / total).coerceIn(0f, 1f)
                            drawArc(StayColors.Purple, -90f, 360f * remaining, false, Offset(inset, inset), arcSize,
                                style = Stroke(12.dp.toPx(), cap = StrokeCap.Round))
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(if (time == null) "대기 예정 시간" else "남은 예정 시간", color = StayColors.Secondary, fontSize = 21.sp)
                            Text(time?.remainingMillis?.durationLabel() ?: "${minutes.toIntOrNull() ?: 0}분", fontSize = 46.sp, fontWeight = FontWeight.Bold)
                            if (time != null) Text("경과 ${time.elapsedMillis.durationLabel()}", color = StayColors.Secondary, fontSize = 20.sp)
                        }
                    }
                    if (time != null && time.overtimeMillis > 0) Text("예정 시간 초과 ${time.overtimeMillis.durationLabel()}", color = StayColors.Warning, fontSize = 21.sp)
                }
            }, right = {
                FeaturePanel(Modifier.fillMaxWidth().heightIn(min = 540.dp)) {
                    Text("현재 대기 정보", fontSize = 27.sp, fontWeight = FontWeight.Bold)
                    InfoLine("장소", active?.place?.name ?: selectedPlace?.name ?: "직접 대기")
                    if (active == null) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            policy.presets.forEach { value -> FilterChip(minutes == value.toString(), { minutes = value.toString() }, label = { Text("${value}분") }) }
                        }
                        OutlinedTextField(minutes, { minutes = it }, label = { Text("대기 시간(분)") },
                            supportingText = { Text("${policy.minimumMinutes}~${policy.maximumMinutes}분") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth())
                        PremiumAction("대기 시작", { viewModel.start(minutes.toIntOrNull() ?: 0, selectedPlace) }, Modifier.fillMaxWidth(), !state.busy)
                    } else {
                        InfoLine("시작", active.startedAt.dateLabel())
                        InfoLine("예정 종료", active.plannedEndAt.dateLabel())
                        OutlinedTextField(extension, { extension = it }, label = { Text("추가 대기 시간(분)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth())
                        PremiumAction("대기시간 연장", { viewModel.extend(active.id, extension.toIntOrNull() ?: 0) }, Modifier.fillMaxWidth(), !state.busy, true)
                        PremiumAction("대기 종료", { endConfirm = true }, Modifier.fillMaxWidth(), !state.busy)
                    }
                }
            })
            ResponsivePanels(leftWeight = 2.2f, left = {
                FeaturePanel(Modifier.fillMaxWidth()) {
                    Text("대기 시간을 활용하기", fontSize = 25.sp, fontWeight = FontWeight.SemiBold)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        WaitingMode("공부", "STUDY", onStudy, Modifier.weight(1f))
                        WaitingMode("업무", "WORK", onWork, Modifier.weight(1f))
                        WaitingMode("휴식", "REST", onRest, Modifier.weight(1f))
                    }
                }
            }, right = {
                FeaturePanel(Modifier.fillMaxWidth()) {
                    Text("지난 대기 기록", fontSize = 25.sp, fontWeight = FontWeight.SemiBold)
                    Text("${history.size}건", color = StayColors.Secondary, fontSize = 20.sp)
                    PremiumAction("기록 보기", { historyOpen = true }, Modifier.fillMaxWidth(), secondary = true)
                }
            })
        }
    }
}

@Composable
private fun WaitingMode(label: String, icon: String, action: (() -> Unit)?, modifier: Modifier) {
    OutlinedButton(onClick = { action?.invoke() }, modifier = modifier.heightIn(min = 86.dp).testTag("waiting_mode_$icon"), enabled = action != null) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            StayIcon(icon, Modifier.size(28.dp))
            Text(label, fontSize = 19.sp)
        }
    }
}
private fun Long.durationLabel(): String {
    val seconds = this / 1000
    return "%02d:%02d:%02d".format(seconds / 3600, seconds / 60 % 60, seconds % 60)
}
private fun Long.dateLabel(): String = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(this))
