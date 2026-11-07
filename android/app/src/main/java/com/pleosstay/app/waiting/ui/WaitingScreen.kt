package com.pleosstay.app.waiting.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pleosstay.app.R
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
    val policy = remember { WaitingPolicy() }
    LazyColumn(modifier.fillMaxSize().padding(horizontal = 28.dp), verticalArrangement = Arrangement.spacedBy(22.dp),
        contentPadding = PaddingValues(bottom = 32.dp)) {
        item { FeatureHeader("나만의 대기 시간", "머무는 동안에도, 안심하고 여유롭게") }
        (state.drivingRecovery.error ?: state.error)?.let { error -> item { NoticePanel("요청을 처리하지 못했습니다", error, true) } }
        if (!state.canUse) {
            item { NoticePanel("정차 확인이 필요합니다",
                (if(!state.ready) "기록 복구 중 / 사용 제한" else state.safety.message) +
                "\n주차 확인 전에는 대기 조작과 기록 화면을 사용할 수 없습니다.\n기존 세션은 백그라운드 진입이나 사용 제한만으로 종료되지 않습니다.\n차량 정보 권한을 허용한 뒤 P 기어에서 다시 열어 주세요.", true) }
        } else {
            val active = state.active
            item {
                if (active == null) GlassPanel(Modifier.fillMaxWidth()) {
                    Text("새 대기 세션", fontSize = 30.sp, fontWeight = FontWeight.Bold)
                    Text("진행 중인 대기 없음", color = StayColors.Secondary, fontSize = 19.sp)
                    Text(selectedPlace?.name ?: "Parking 장소 선택 없이 대기", fontSize = 24.sp, color = StayColors.Blue)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        policy.presets.forEach { value -> FilterChip(minutes == value.toString(), { minutes = value.toString() }, label = { Text("${value}분", fontSize = 20.sp) }) }
                    }
                    OutlinedTextField(minutes, { minutes = it }, label = { Text("대기시간 (${policy.minimumMinutes}~${policy.maximumMinutes}분)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                    PremiumAction("대기 시작", { viewModel.start(minutes.toIntOrNull() ?: 0, selectedPlace) }, enabled = !state.busy)
                } else {
                    val time = WaitingTime.calculate(active, state.now)
                    val total = (active.plannedEndAt - active.startedAt).coerceAtLeast(1L)
                    ResponsivePanels(left = {
                        GlassPanel(Modifier.fillMaxWidth()) {
                            Text("현재 대기 중", color = StayColors.Blue, fontSize = 24.sp)
                            Box(Modifier.fillMaxWidth().height(330.dp), contentAlignment = Alignment.Center) {
                                Canvas(Modifier.size(290.dp)) {
                                    val inset = 14.dp.toPx()
                                    val ring = Size(size.width-inset*2, size.height-inset*2)
                                    drawArc(StayColors.Border, 0f, 360f, false, Offset(inset,inset), ring, style = Stroke(10.dp.toPx()))
                                    drawArc(StayColors.Blue, -90f, 360f*(time.remainingMillis.toFloat()/total).coerceIn(0f,1f), false,
                                        Offset(inset,inset), ring, style = Stroke(10.dp.toPx(), cap = StrokeCap.Round))
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("남은 예정시간", color = StayColors.Secondary, fontSize = 22.sp)
                                    Text(time.remainingMillis.durationLabel(), fontSize = 44.sp, fontWeight = FontWeight.Bold)
                                    Text("경과 ${time.elapsedMillis.durationLabel()}", fontSize = 21.sp, color = StayColors.Blue)
                                }
                            }
                            when {
                                time.overtimeMillis > 0 -> Text("대기 시간 초과 · ${time.overtimeMillis.durationLabel()}", color = MaterialTheme.colorScheme.error, fontSize = 21.sp)
                                time.remainingMillis == 0L -> Text("대기 중 · 남은 시간 0분", color = StayColors.Blue, fontSize = 21.sp)
                                time.remainingMillis <= 300_000 -> Text("대기 중 · 예정 종료까지 5분 이내 · 남은 시간 ${time.remainingMillis.durationLabel()}", color = StayColors.Blue, fontSize = 21.sp)
                                else -> Text("대기 중 · 남은 시간 ${time.remainingMillis.durationLabel()}", color = StayColors.Blue, fontSize = 21.sp)
                            }
                            Text("대기는 직접 종료하거나 주행이 확인될 때까지 유지됩니다.", color = StayColors.Secondary, fontSize = 18.sp)
                        }
                    }, right = {
                        GlassPanel(Modifier.fillMaxWidth()) {
                            Text(active.place?.name ?: "선택 장소 없음", fontSize = 29.sp, fontWeight = FontWeight.Bold)
                            InfoLine("시작", active.startedAt.dateLabel())
                            InfoLine("예정 종료", active.plannedEndAt.dateLabel())
                            Text(state.safety.message, color = StayColors.Blue, fontSize = 20.sp)
                            OutlinedTextField(extension, { extension = it }, label = { Text("추가할 시간(분)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                            PremiumAction("대기시간 연장", { viewModel.extend(active.id, extension.toIntOrNull() ?: 0) }, Modifier.fillMaxWidth(), !state.busy, true)
                            PremiumAction("대기 종료", { viewModel.end(active.id) }, Modifier.fillMaxWidth(), !state.busy)
                        }
                    })
                }
            }
            item {
                BoxWithConstraints {
                    ModeCards(wide=maxWidth>=1000.dp, onRest=onRest, onStudy=onStudy, onWork=onWork)
                }
            }
            item { Text("지난 대기 기록", fontSize = 28.sp, fontWeight = FontWeight.Bold) }
            val history = state.sessions.filter { it.status == WaitingStatus.ENDED }
            if(history.isEmpty()) item { NoticePanel("대기 기록이 없습니다", "종료한 세션의 실제 대기시간이 여기에 표시됩니다.") }
            items(history, key = { it.id }) { session ->
                GlassPanel(Modifier.fillMaxWidth()) {
                    Text(session.place?.name ?: "장소 선택 없음", fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                    Text("시작 ${session.startedAt.dateLabel()}\n대기 종료 ${session.endedAt?.dateLabel()} · ${if(session.endReason == EndReason.DRIVING) "주행 확인" else "직접 종료"}", color = StayColors.Secondary, fontSize = 19.sp)
                    Text("실제 대기 ${WaitingTime.calculate(session,state.now).elapsedMillis.durationLabel()}", color = StayColors.Blue, fontSize = 22.sp)
                }
            }
        }
    }
}
private fun Long.durationLabel(): String {
    val seconds = this / 1000
    return "%02d:%02d:%02d".format(seconds/3600, seconds/60%60, seconds%60)
}
private fun Long.dateLabel(): String = DateFormat.getDateTimeInstance().format(Date(this))
