package com.pleosstay.app.rest

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.pleosstay.app.modes.WaitingChoice
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date

@Composable
internal fun RestScreen(controller: RestController, onRecoverWaiting: () -> Unit, onHome: () -> Unit) {
    val state by controller.state.collectAsState()
    val safety by controller.safetyState.collectAsState()
    var editor by remember { mutableStateOf<RestPreset?>(null) }
    var editingId by remember { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf<RestPreset?>(null) }
    var showPresets by remember { mutableStateOf(false) }
    var extend by remember { mutableStateOf(false) }
    var extension by remember { mutableStateOf("10") }
    val session = state.session
    val active = session?.takeIf { it.end == null }
    val context = LocalContext.current
    val exactSettingsAvailable = Build.VERSION.SDK_INT >= 31 && Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
        Uri.parse("package:${context.packageName}")).resolveActivity(context.packageManager) != null
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        // Start is deliberately explicit after returning from the permission dialog.
    }

    LaunchedEffect(active?.alarmToken) {
        if (active != null) {
            editor = null
            showPresets = false
        } else {
            extend = false
        }
    }
    val previewEv = editor?.ev == true && safety.canUse
    DisposableEffect(previewEv) {
        controller.previewEv(previewEv)
        onDispose { controller.previewEv(false) }
    }
    BackHandler {
        if (state.alarmConsent != null) controller.dismissAlarmConsent()
        else if (state.decision != null) controller.resolve(WaitingChoice.BACK)
        else if (editor != null) editor = null else onHome()
    }

    val colors = if (active == null) MaterialTheme.colorScheme else when (active.preset.display) {
        RestDisplay.DARK -> darkColorScheme(background = Color(0xff101114), onBackground = Color(0xffe5e5e9))
        RestDisplay.MINIMAL -> darkColorScheme(background = Color.Black, onBackground = Color(0xffdddddd))
        RestDisplay.CALM -> darkColorScheme(background = Color(0xff142c2b), onBackground = Color(0xffdef4e9))
    }
    MaterialTheme(colorScheme = colors) {
        Surface(color = colors.background, modifier = Modifier.fillMaxSize()) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("휴식", style = MaterialTheme.typography.headlineMedium)
                state.message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                state.alarmLimitation?.let { Text(it) }
                when {
                    !state.ready -> {
                        Text("휴식 기록을 불러오는 중입니다.")
                        TextButton(onClick = { controller.restore() }, enabled = !state.busy) { Text("다시 불러오기") }
                    }
                    active != null -> {
                        Text(active.preset.name, style = MaterialTheme.typography.titleLarge)
                        if (safety.canUse) {
                            var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
                            LaunchedEffect(active.alarmToken) {
                                while (true) { now = System.currentTimeMillis(); delay(500) }
                            }
                            Text(duration(active.remaining(now)), style = MaterialTheme.typography.displaySmall)
                            Text("종료 예정 ${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(active.endsAt))}")
                            if (active.preset.display != RestDisplay.MINIMAL) {
                                Text("${active.preset.display.label} · ${active.preset.sound.label}")
                            }
                            if (active.waitingId == null) Text("독립 휴식 · 대기 종료 예정 시각을 관리하지 않아요.")
                            if (active.preset.ev) {
                                Text(evText(state.ev, active.preset.target))
                                if (state.ev.percent == null) TextButton(onClick = controller::retryEv) { Text("배터리 정보 다시 확인") }
                            }
                            if (active.targetPrompt) {
                                Text("목표 충전율에 도달했어요.")
                                Row {
                                    TextButton(onClick = { controller.answerTarget(false) }, enabled = !state.busy) { Text("휴식 종료") }
                                    TextButton(onClick = { controller.answerTarget(true) }, enabled = !state.busy) { Text("계속 휴식") }
                                }
                            }
                            if (active.preset.sound != RestSound.SILENT && !active.targetPrompt) {
                                TextButton(onClick = { controller.pauseSound(!active.soundPaused) }, enabled = !state.busy) {
                                    Text(if (active.soundPaused) "사운드 재개" else "사운드 일시정지")
                                }
                            }
                            TextButton(onClick = { extend = true }, enabled = !state.busy) { Text("휴식 시간 연장") }
                        } else Text(safety.message)
                        if (state.message?.let { "알람" in it || "알림" in it } == true) {
                            TextButton(onClick = { openRestSettings(context, Settings.ACTION_APP_NOTIFICATION_SETTINGS) }) { Text("휴식 알림 설정") }
                            if (exactSettingsAvailable) TextButton(onClick = {
                                if (!openRestSettings(context, Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)) {
                                    controller.alarmSettingsUnavailable()
                                }
                            }) { Text("정확한 알람 권한 설정") }
                        }
                        Button(onClick = controller::end, enabled = !state.busy) { Text("휴식 종료") }
                    }
                    editor != null -> {
                        val preset = checkNotNull(editor)
                        RestEditor(preset, { editor = it })
                        if (preset.ev) {
                            Text(evText(state.ev, preset.target))
                            TextButton(onClick = controller::retryEv, enabled = safety.canUse) { Text("배터리 정보 다시 확인") }
                        }
                        if (!safety.canUse) Text(safety.message)
                        TextButton(onClick = onRecoverWaiting, enabled = !state.busy) { Text("대기 기록 다시 확인") }
                        Button(onClick = { controller.start(preset) }, enabled = !state.busy && safety.canUse &&
                            preset.valid && (!preset.ev || state.ev.percent?.let { it < preset.target } == true)) { Text("휴식 시작") }
                        TextButton(onClick = { controller.savePreset(preset, editingId) }, enabled = !state.busy && preset.valid) {
                            Text(if (editingId == null) "내 프리셋으로 저장" else "프리셋 수정 저장")
                        }
                        TextButton(onClick = { editor = null }) { Text("프리셋 목록") }
                        TextButton(onClick = {
                            if (Build.VERSION.SDK_INT >= 33 &&
                                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else openRestSettings(context, Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        }) { Text("휴식 알림 설정") }
                        if (exactSettingsAvailable) TextButton(onClick = {
                            if (!openRestSettings(context, Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)) {
                                    controller.alarmSettingsUnavailable()
                                }
                        }) { Text("정확한 알람 권한 설정") }
                    }
                    session?.end != null && !showPresets -> {
                        Text(session.end.label, style = MaterialTheme.typography.headlineSmall)
                        Button(onClick = { showPresets = true }) { Text("다시 휴식 시작") }
                    }
                    else -> {
                        Text("기본 프리셋")
                        RestPreset.defaults.forEach { preset ->
                            TextButton(onClick = { editingId = null; editor = preset }) {
                                Text("${preset.name} · ${preset.minutes}분" + if (preset.ev) " · EV 지원 확인 필요" else "")
                            }
                        }
                        Text("내 프리셋")
                        state.presets.forEach { preset ->
                            Row {
                                TextButton(onClick = { editingId = preset.id; editor = preset }) { Text(preset.name) }
                                TextButton(onClick = { deleting = preset }, enabled = !state.busy) { Text("삭제") }
                            }
                        }
                        Button(onClick = { editingId = null; editor = RestPreset() }) { Text("새 프리셋 만들기") }
                    }
                }
                if (state.ringing) TextButton(onClick = controller::stopAlarm) { Text("알람 정지") }
                TextButton(onClick = onHome) { Text("홈으로 이동") }
            }
        }
    }

    if (state.alarmConsent != null) AlertDialog(
        onDismissRequest = controller::dismissAlarmConsent,
        title = { Text("현재는 앱 실행 중 알람을 사용할 수 있어요") },
        text = { Text("현재 차량 환경에서는 시스템 알람을 사용할 수 없어요.\n\nStay+를 화면에 유지하는 동안에는 설정한 시간이 지나면 휴식 종료 알람이 울리도록 사용할 수 있어요.\n\n다른 앱으로 이동하거나 화면이 꺼지고 차량이 절전 상태로 전환되면 알람이 울리지 않을 수 있어요.") },
        confirmButton = {
            TextButton(onClick = controller::acceptForegroundAlarm, enabled = !state.busy && safety.canUse) {
                Text("앱 실행 중 알람으로 휴식 시작")
            }
        },
        dismissButton = { TextButton(onClick = controller::dismissAlarmConsent) { Text("뒤로가기") } },
    )

    val decision = state.decision
    if (decision != null) {
        val prompt = decision.prompt
        AlertDialog(
            onDismissRequest = { controller.resolve(WaitingChoice.BACK) },
            title = { Text("대기 시간을 확인해 주세요") },
            text = {
                Column {
                    Text("휴식 종료가 남은 대기 시간보다 ${prompt.extensionMinutes}분 늦어요. 전체 대기 시간도 연장할까요?")
                    Text("남은 대기 ${duration(prompt.remainingMillis)}")
                    if (prompt.extensionUnavailable != null) Text("대기 총 예정 시간 720분 한도 내에서 연장할 수 없어요.")
                    if (prompt.shortenUnavailable != null) Text("남은 대기 시간이 없어 단축할 수 없어요.")
                    else TextButton(onClick = { controller.resolve(WaitingChoice.SHORTEN) }, enabled = safety.canUse && !state.busy) {
                        Text("휴식 시간을 ${duration(prompt.remainingMillis)}로 줄이기")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { controller.resolve(WaitingChoice.EXTEND) },
                    enabled = safety.canUse && !state.busy && prompt.extensionUnavailable == null) { Text("대기 ${prompt.extensionMinutes}분 연장") }
            },
            dismissButton = { TextButton(onClick = { controller.resolve(WaitingChoice.BACK) }) { Text("뒤로가기") } },
        )
    }
    if (state.fallback != null) AlertDialog(
        onDismissRequest = controller::dismissFallback,
        title = { Text("대기 관리 없이 휴식할까요?") },
        text = { Text("대기 상태와 종료 예정 시각은 관리되지 않아요. 휴식 알람은 예약됩니다.") },
        confirmButton = { TextButton(onClick = { controller.independent() }, enabled = safety.canUse && !state.busy) { Text("독립 휴식 시작") } },
        dismissButton = { TextButton(onClick = controller::dismissFallback) { Text("취소") } },
    )
    deleting?.let { preset ->
        AlertDialog(onDismissRequest = { deleting = null }, title = { Text("${preset.name} 삭제") },
            confirmButton = { TextButton(onClick = { controller.deletePreset(preset.id); deleting = null }) { Text("삭제") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("취소") } })
    }
    if (extend && active != null) AlertDialog(
        onDismissRequest = { extend = false }, title = { Text("휴식 연장") },
        text = { OutlinedTextField(extension, { extension = it }, label = { Text("추가 시간(분)") }, singleLine = true) },
        confirmButton = {
            TextButton(onClick = { controller.extend(extension.toInt()); extend = false },
                enabled = !state.busy && (extension.toIntOrNull() ?: 0) in 1..720) { Text("연장 확인") }
        },
        dismissButton = { TextButton(onClick = { extend = false }) { Text("취소") } },
    )
}

@Composable
private fun RestEditor(preset: RestPreset, onChange: (RestPreset) -> Unit) {
    var minutes by remember(preset.id) { mutableStateOf(preset.minutes.toString()) }
    var target by remember(preset.id) { mutableStateOf(preset.target.toString()) }
    OutlinedTextField(preset.name, { onChange(preset.copy(name = it)) }, label = { Text("이름") }, singleLine = true)
    OutlinedTextField(minutes, { minutes = it; onChange(preset.copy(minutes = it.toIntOrNull() ?: 0)) },
        label = { Text(if (preset.ev) "최대 휴식 시간(5~720분)" else "휴식 시간(5~720분)") }, singleLine = true)
    RestOptions(RestDisplay.entries, preset.display, { it.label }) { onChange(preset.copy(display = it)) }
    RestOptions(RestSound.entries, preset.sound, { it.label }) { onChange(preset.copy(sound = it)) }
    RestOptions(RestAlert.entries, preset.alert, { it.label }) { onChange(preset.copy(alert = it)) }
    Row {
        TextButton(onClick = { onChange(preset.copy(ev = false, target = target.toIntOrNull()?.takeIf { it in 1..100 } ?: 80)) }) { Text("일반 휴식") }
        TextButton(onClick = { onChange(preset.copy(ev = true, target = target.toIntOrNull() ?: 0)) }) { Text("EV 목표 휴식") }
    }
    if (preset.ev) OutlinedTextField(target, { target = it; onChange(preset.copy(target = it.toIntOrNull() ?: 0)) },
        label = { Text("목표 충전율(1~100%)") }, singleLine = true)
}

@Composable
private fun <T> RestOptions(values: List<T>, selected: T, label: (T) -> String, select: (T) -> Unit) {
    Row { values.forEach { value -> TextButton(onClick = { select(value) }) {
        Text((if (value == selected) "✓ " else "") + label(value))
    } } }
}

private fun duration(millis: Long): String {
    val seconds = (millis + 999) / 1_000
    return "${seconds / 60}분 ${seconds % 60}초"
}

private fun evText(reading: EvReading, target: Int): String = reading.percent?.let {
    "배터리 잔량 ${it}% · 목표 ${target}%" + if (it >= target) " · 이미 목표에 도달했어요." else ""
} ?: reading.message

private fun openRestSettings(context: android.content.Context, action: String): Boolean {
    val packageUri = Uri.parse("package:${context.packageName}")
    val intent = Intent(action).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    if (action == Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM) {
        intent.data = packageUri
        return runCatching { context.startActivity(intent) }.isSuccess
    }
    return runCatching { context.startActivity(intent) }.recoverCatching {
        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri))
    }.isSuccess
}
