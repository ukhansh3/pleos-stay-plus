package com.pleosstay.app.waiting.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.pleosstay.app.waiting.domain.*
import java.text.DateFormat
import java.util.Date

@Composable
fun WaitingScreen(viewModel: WaitingViewModel, selectedPlace: WaitingPlace?, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsState()
    var minutes by remember { mutableStateOf("30") }
    var extension by remember { mutableStateOf("15") }
    val policy = remember { WaitingPolicy() }
    LazyColumn(modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("대기 관리", style = MaterialTheme.typography.headlineMedium) }
        (state.drivingRecovery.error ?: state.error)?.let { error -> item { Text(error, color = MaterialTheme.colorScheme.error) } }
        if (!state.canUse) {
            item {
                Text(if (!state.ready) "기록 복구 중 / 사용 제한" else state.safety.message)
                Text("주차 확인 전에는 대기 조작과 기록 화면을 사용할 수 없습니다.")
                Text("기존 세션은 백그라운드 진입이나 사용 제한만으로 종료되지 않습니다.")
                Text("차량 정보 권한을 허용한 뒤 P 기어에서 다시 대기 관리를 열어 주세요.", style = MaterialTheme.typography.bodySmall)
            }
        } else {
            val active = state.active
            if (active == null) {
                item {
                    Text(selectedPlace?.name ?: "Parking 장소 선택 없이 대기")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        policy.presets.forEach { value -> TextButton(onClick = { minutes = value.toString() }) { Text("${value}분") } }
                    }
                    OutlinedTextField(minutes, { minutes = it }, label = { Text("대기시간 (${policy.minimumMinutes}~${policy.maximumMinutes}분)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                    Button(onClick = { viewModel.start(minutes.toIntOrNull() ?: 0, selectedPlace) }, enabled = !state.busy) { Text("대기 시작") }
                }
            } else {
                val time = WaitingTime.calculate(active, state.now)
                item {
                    Text("대기 중 · ${active.place?.name ?: "선택 장소 없음"}")
                    Text("경과 ${time.elapsedMillis.durationLabel()}")
                    when {
                        time.overtimeMillis > 0 -> Text("예정시간 초과 ${time.overtimeMillis.durationLabel()}", color = MaterialTheme.colorScheme.error)
                        time.remainingMillis == 0L -> Text("예정 종료시간입니다. 대기는 직접 종료할 때까지 유지됩니다.")
                        time.remainingMillis <= 300_000 -> Text("예정 종료까지 5분 이내 · 남은 시간 ${time.remainingMillis.durationLabel()}")
                        else -> Text("남은 예정시간 ${time.remainingMillis.durationLabel()}")
                    }
                    OutlinedTextField(extension, { extension = it }, label = { Text("추가할 시간(분)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = { viewModel.extend(active.id, extension.toIntOrNull() ?: 0) }, enabled = !state.busy) { Text("연장") }
                        Button(onClick = { viewModel.end(active.id) }, enabled = !state.busy) { Text("대기 종료") }
                    }
                }
            }
            item { Text("지난 대기 기록", style = MaterialTheme.typography.titleLarge) }
            val history = state.sessions.filter { it.status == WaitingStatus.ENDED }
            if (history.isEmpty()) item { Text("대기 기록이 없습니다") }
            items(history, key = { it.id }) { session ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(session.place?.name ?: "장소 선택 없음")
                        Text("시작 ${session.startedAt.dateLabel()}")
                        Text("종료 ${session.endedAt?.dateLabel()} · ${if (session.endReason == EndReason.DRIVING) "주행 확인" else "직접 종료"}")
                        Text("실제 대기 ${WaitingTime.calculate(session, state.now).elapsedMillis.durationLabel()}")
                    }
                }
            }
        }
    }
}
private fun Long.durationLabel(): String {
    val seconds = this / 1_000
    return "%02d:%02d:%02d".format(seconds / 3600, seconds / 60 % 60, seconds % 60)
}
private fun Long.dateLabel(): String = DateFormat.getDateTimeInstance().format(Date(this))
