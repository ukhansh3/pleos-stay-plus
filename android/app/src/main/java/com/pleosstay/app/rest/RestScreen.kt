package com.pleosstay.app.rest

import com.pleosstay.app.ui.premium.featureInspection
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.pleosstay.app.R
import com.pleosstay.app.ui.premium.StayColors
import com.pleosstay.app.ui.premium.PremiumAction
import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import com.pleosstay.app.ui.activity.*
import com.pleosstay.app.modes.ActivityMode
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
    val actions=LocalActivityUiActions.current
    val liveState by controller.state.collectAsState()
    val state = featureInspection()?.rest ?: liveState
    val liveSafety by controller.safetyState.collectAsState()
    val safety = featureInspection()?.safety ?: liveSafety
    val inspection = featureInspection()
    var editor by remember { mutableStateOf<RestPreset?>(inspection?.restEditor) }
    var editingId by remember { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf<RestPreset?>(null) }
    var showPresets by remember { mutableStateOf(false) }
    var extend by remember { mutableStateOf(false) }
    var extension by remember { mutableStateOf("10") }
    val session = state.session
    val active = session?.takeIf { it.end == null }
    val context = LocalContext.current
    val exactSettingsAvailable = !androidx.compose.ui.platform.LocalInspectionMode.current && Build.VERSION.SDK_INT >= 31 && Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
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
        else if (state.decision != null) { if(actions!=null)actions.choose(ActivityMode.REST,WaitingChoice.BACK) else controller.resolve(WaitingChoice.BACK) }
        else if (editor != null) editor = null else onHome()
    }

    val colors = if (active == null) MaterialTheme.colorScheme else when (active.preset.display) {
        RestDisplay.DARK -> darkColorScheme(background = StayColors.Background, onBackground = StayColors.Text)
        RestDisplay.MINIMAL -> darkColorScheme(background = Color.Black, onBackground = StayColors.Text)
        RestDisplay.CALM -> darkColorScheme(background = StayColors.Background, onBackground = StayColors.Text)
    }
    val selected = active?.preset ?: editor ?: RestPreset.defaults.first()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(active?.alarmToken) {
        while (active != null) { now = System.currentTimeMillis(); delay(500) }
    }
    MaterialTheme(colorScheme = colors) {
        Surface(color = colors.background, modifier = Modifier.fillMaxSize()) {
            BoxWithConstraints(Modifier.fillMaxSize().padding(28.dp)) {
                val wide = maxWidth >= 920.dp
                val panelHeight = (maxHeight - 100.dp).coerceIn(340.dp, 1000.dp)
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(22.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("휴식", Modifier.weight(1f), fontSize = 38.sp, fontWeight = FontWeight.Bold)
                        TextButton(onClick = onHome, modifier = Modifier.heightIn(min = 56.dp)) { Text("홈으로 이동") }
                    }
                    val timer: @Composable (Modifier) -> Unit = { modifier ->
                        RestTimerPanel(modifier.height(panelHeight), selected,
                            remaining = active?.remaining(inspection?.nowMillis ?: now) ?: selected.minutes * REST_MINUTE,
                            active = active != null, canUse = safety.canUse, safetyMessage = safety.message,
                            ended = session?.end?.takeIf { active == null && !showPresets && editor == null }?.label,
                            endsAt = active?.endsAt,
                            onAgain = { showPresets = true },
                            actions = {
                                if (active != null) {
                                    if (safety.canUse) {
                                        if (active.preset.sound != RestSound.SILENT && !active.targetPrompt) {
                                            OutlinedButton(onClick = { controller.pauseSound(!active.soundPaused) },
                                                enabled = !state.busy, modifier = Modifier.heightIn(min = 60.dp)) {
                                                Text(if (active.soundPaused) "사운드 재개" else "사운드 일시정지",fontSize=20.sp)
                                            }
                                        }
                                        OutlinedButton(onClick = { extend = true }, enabled = !state.busy,
                                            modifier = Modifier.heightIn(min = 60.dp)) { Text("휴식 시간 연장",fontSize=20.sp) }
                                    }
                                    PremiumAction("휴식 종료", { if(actions!=null)actions.finish(ActivityMode.REST) else controller.end() }, enabled = !state.busy)
                                }
                            })
                    }
                    val settings: @Composable (Modifier) -> Unit = { modifier ->
                        RestPanel(modifier.height(if (wide) panelHeight else 620.dp)) {
                            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                                Text("휴식 설정", fontSize = 27.sp, fontWeight = FontWeight.SemiBold)
                                state.message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                                when {
                                    !state.ready -> {
                                        Text("휴식 기록을 불러오는 중입니다.", color = StayColors.Secondary)
                                        TextButton(onClick = { controller.restore() }, enabled = !state.busy) { Text("다시 불러오기") }
                                    }
                                    !safety.canUse -> Text(safety.message, color = StayColors.Warning, fontSize = 22.sp)
                                    active != null -> {
                                        if (safety.canUse) {
                                            Text(active.preset.name, fontSize = 22.sp)
                                            if (active.preset.display != RestDisplay.MINIMAL) {
                                                RestSettingLine("화면", active.preset.display.label)
                                            }
                                                RestSoundControls(active.preset, !state.busy,
                                                    { controller.changeSound(sound = it) },
                                                    { controller.changeSound(volume = it) },
                                                    { controller.changeSound(muted = it) })
                                                PremiumAction("프리셋에 저장", controller::saveCurrentSoundPreset, enabled = !state.busy, secondary = true)
                                            RestSettingLine("종료 알람", active.preset.alert.label)
                                            if (active.preset.ev) {
                                                HorizontalDivider(color = StayColors.Border)
                                                Text("EV 배터리", fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                                                Text(evText(state.ev, active.preset.target), fontSize = 20.sp)
                                                if (state.ev.percent == null) TextButton(onClick = controller::retryEv) { Text("배터리 정보 다시 확인") }
                                            }
                                            if (active.targetPrompt) {
                                                Text("목표 충전율에 도달했어요.", color = StayColors.Success)
                                                PremiumAction("휴식 종료", { controller.answerTarget(false) }, enabled = !state.busy)
                                                PremiumAction("계속 휴식", { controller.answerTarget(true) }, enabled = !state.busy, secondary = true)
                                            }
                                        } else Text(safety.message, color = StayColors.Warning)
                                    }
                                    session?.end != null && !showPresets && editor == null -> {
                                        Text(session.end.label, color = StayColors.Secondary, fontSize = 22.sp)
                                    }
                                    else -> {
                                        if(editor == null) {
                                        Text("기본 프리셋", color = StayColors.Secondary, fontSize = 18.sp)
                                        RestPreset.defaults.forEach { preset ->
                                            val chosen = editor?.id == preset.id
                                            OutlinedButton(onClick = { editingId = null; editor = preset },
                                                modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp),
                                                border = androidx.compose.foundation.BorderStroke(1.dp,
                                                    if (chosen) StayColors.Purple else StayColors.Border)) {
                                                Text("${preset.name} · ${preset.minutes}분", color = if (chosen) StayColors.Text else StayColors.Secondary)
                                            }
                                        }
                                        HorizontalDivider(color = StayColors.Border)
                                        if (state.presets.isNotEmpty()) Text("내 프리셋", color = StayColors.Secondary, fontSize = 18.sp)
                                        state.presets.forEach { preset ->
                                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                                TextButton(onClick = { editingId = preset.id; editor = preset },
                                                    modifier = Modifier.weight(1f).heightIn(min = 56.dp)) { Text(preset.name) }
                                                TextButton(onClick = { deleting = preset }, enabled = !state.busy) { Text("삭제") }
                                            }
                                        }
                                        OutlinedButton(onClick = { editingId = null; editor = RestPreset() },
                                            modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp)) { Text("새 프리셋 만들기") }
                                        }
                                        editor?.let { preset ->
                                            HorizontalDivider(color = StayColors.Border)
                                            RestEditor(preset, { editor = it })
                                            if (preset.ev) {
                                                Text(evText(state.ev, preset.target))
                                                TextButton(onClick = controller::retryEv, enabled = safety.canUse) { Text("배터리 정보 다시 확인") }
                                            }
                                            if (!safety.canUse) Text(safety.message, color = StayColors.Warning)
                                            PremiumAction("휴식 시작", { if(actions!=null)actions.startRest(preset) else controller.start(preset) },
                                                modifier = Modifier.fillMaxWidth().testTag("rest_start"),
                                                enabled = !state.busy && safety.canUse && preset.valid &&
                                                    (!preset.ev || state.ev.percent?.let { it < preset.target } == true))
                                            TextButton(onClick = { controller.savePreset(preset, editingId) }, enabled = !state.busy && preset.valid) {
                                                Text(if (editingId == null) "내 프리셋으로 저장" else "프리셋 수정 저장")
                                            }
                                            TextButton(onClick = { editor = null }) { Text("프리셋 목록") }
                                        }
                                    }
                                }
                                state.alarmLimitation?.let {
                                    HorizontalDivider(color = StayColors.Border)
                                    Text(it, color = StayColors.Warning, fontSize = 17.sp)
                                }
                                if (state.ringing) PremiumAction("알람 정지", controller::stopAlarm)
                                if (editor != null || state.message?.let { "알람" in it || "알림" in it } == true) {
                                    TextButton(onClick = {
                                        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED)
                                            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                                        else openRestSettings(context, Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                    }, modifier = Modifier.heightIn(min = 56.dp)) { Text("휴식 알림 설정") }
                                    if (exactSettingsAvailable) TextButton(onClick = {
                                        if (!openRestSettings(context, Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)) controller.alarmSettingsUnavailable()
                                    }) { Text("정확한 알람 권한 설정") }
                                }
                                if (state.fallback != null || state.message?.contains("대기") == true) {
                                    TextButton(onClick = onRecoverWaiting, enabled = !state.busy) { Text("대기 기록 다시 확인") }
                                }
                            }
                        }
                    }
                    if (wide) Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        timer(Modifier.weight(1.3f)); settings(Modifier.weight(1f))
                    } else Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                        timer(Modifier.fillMaxWidth()); settings(Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }

    if (actions==null && state.alarmConsent != null && safety.canUse) AlertDialog(
        onDismissRequest = controller::dismissAlarmConsent,
        title = { Text("현재는 앱 실행 중 알람을 사용할 수 있어요") },
        text = { Text("Stay+를 화면에 유지하는 동안에만 종료 알람이 울립니다. 다른 앱으로 이동하거나 화면이 꺼지면 알람이 울리지 않을 수 있어요.") },
        confirmButton = {
            TextButton(onClick = controller::acceptForegroundAlarm, enabled = !state.busy && safety.canUse) {
                Text("앱 실행 중 알람으로 휴식 시작")
            }
        },
        dismissButton = { TextButton(onClick = controller::dismissAlarmConsent) { Text("뒤로가기") } },
    )

    val decision = state.decision
    if (decision != null && safety.canUse && (actions==null || state.session!=null)) {
        val prompt = decision.prompt
        AlertDialog(
            onDismissRequest = { if(actions!=null)actions.choose(ActivityMode.REST,WaitingChoice.BACK) else controller.resolve(WaitingChoice.BACK) },
            title = { Text("대기 시간을 확인해 주세요") },
            text = {
                Column {
                    Text("휴식 종료가 남은 대기 시간보다 ${prompt.extensionMinutes}분 늦어요. 전체 대기 시간도 연장할까요?")
                    Text("남은 대기 ${duration(prompt.remainingMillis)}")
                    if (prompt.extensionUnavailable != null) Text("대기 총 예정 시간 720분 한도 내에서 연장할 수 없어요.")
                    if (prompt.shortenUnavailable != null) Text("남은 대기 시간이 없어 단축할 수 없어요.")
                    else TextButton(onClick = { if(actions!=null)actions.choose(ActivityMode.REST,WaitingChoice.SHORTEN) else controller.resolve(WaitingChoice.SHORTEN) }, enabled = safety.canUse && !state.busy) {
                        Text("휴식 시간을 ${duration(prompt.remainingMillis)}로 줄이기")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { if(actions!=null)actions.choose(ActivityMode.REST,WaitingChoice.EXTEND) else controller.resolve(WaitingChoice.EXTEND) },
                    enabled = safety.canUse && !state.busy && prompt.extensionUnavailable == null) { Text("대기 ${prompt.extensionMinutes}분 연장") }
            },
            dismissButton = { TextButton(onClick = { if(actions!=null)actions.choose(ActivityMode.REST,WaitingChoice.BACK) else controller.resolve(WaitingChoice.BACK) }) { Text("뒤로가기") } },
        )
    }
    if (actions==null && state.fallback != null && safety.canUse) AlertDialog(
        onDismissRequest = controller::dismissFallback,
        title = { Text("대기 관리 없이 휴식할까요?") },
        text = { Text("대기 상태와 종료 예정 시각은 관리되지 않아요. 휴식 알람은 예약됩니다.") },
        confirmButton = { TextButton(onClick = { controller.independent() }, enabled = safety.canUse && !state.busy) { Text("독립 휴식 시작") } },
        dismissButton = { TextButton(onClick = controller::dismissFallback) { Text("취소") } },
    )
    deleting?.takeIf { safety.canUse }?.let { preset ->
        AlertDialog(onDismissRequest = { deleting = null }, title = { Text("${preset.name} 삭제") },
            confirmButton = { TextButton(onClick = { controller.deletePreset(preset.id); deleting = null }) { Text("삭제") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("취소") } })
    }
    if (extend && active != null && safety.canUse) AlertDialog(
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
private fun RestPanel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    Column(modifier.clip(shape).background(Brush.verticalGradient(listOf(StayColors.Surface, StayColors.Background)))
        .border(1.dp, StayColors.Border, shape).padding(26.dp), verticalArrangement = Arrangement.spacedBy(18.dp), content = content)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RestTimerPanel(modifier: Modifier, preset: RestPreset, remaining: Long, active: Boolean,
    canUse: Boolean, safetyMessage: String, ended: String?, endsAt: Long?, onAgain: () -> Unit,
    actions: @Composable FlowRowScope.() -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    Box(modifier.clip(shape).background(if (preset.display == RestDisplay.MINIMAL && active) Color.Black else StayColors.Surface)
        .border(1.dp, StayColors.Border, shape)) {
        if (canUse && (preset.display != RestDisplay.MINIMAL || !active)) {
            Image(painterResource(R.drawable.rest_pillow), null, Modifier.matchParentSize(),
                contentScale = ContentScale.Crop, alignment = Alignment.Center, alpha = .52f)
            Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(
                StayColors.Background.copy(alpha = .82f), StayColors.Background.copy(alpha = .34f), StayColors.Background.copy(alpha = .86f)))))
        }
        Column(Modifier.fillMaxSize().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (active) "편안한 휴식" else "휴식 준비", Modifier.align(Alignment.Start), fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            if (!canUse) {
                Text(safetyMessage, fontSize = 24.sp, color = StayColors.Warning)
            } else if (ended != null) {
                Text(ended, fontSize = 30.sp, color = StayColors.Text)
                Spacer(Modifier.height(24.dp))
                PremiumAction("다시 휴식 시작", onAgain)
            } else {
                Text(preset.name.ifBlank { "나만의 휴식" }, fontSize = 24.sp, color = StayColors.Secondary)
                Spacer(Modifier.height(24.dp))
                BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    val timerSize = if (maxWidth >= 480.dp) 82.sp else 58.sp
                    Text(clockDuration(remaining), fontSize = timerSize, fontWeight = FontWeight.SemiBold,
                        color = StayColors.Text, maxLines = 1, modifier = Modifier.testTag("rest_timer"))
                }
                Spacer(Modifier.height(20.dp))
                Text(if (endsAt != null) "종료 예정 ${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(endsAt))}" else "${preset.minutes}분", color = StayColors.Secondary, fontSize = 20.sp)
            }
            Spacer(Modifier.weight(1f))
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(12.dp), content = actions)
        }
    }
}

@Composable
private fun RestSettingLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(label, Modifier.weight(1f), color = StayColors.Secondary, fontSize = 19.sp)
        Text(value, fontSize = 20.sp)
    }
}

private fun clockDuration(millis: Long): String {
    val seconds = (millis + 999) / 1_000
    return "%02d:%02d:%02d".format(seconds / 3600, seconds / 60 % 60, seconds % 60)
}

@Composable
private fun RestEditor(preset: RestPreset, onChange: (RestPreset) -> Unit) {
    var minutes by remember(preset.id) { mutableStateOf(preset.minutes.toString()) }
    var target by remember(preset.id) { mutableStateOf(preset.target.toString()) }
    OutlinedTextField(preset.name, { onChange(preset.copy(name = it)) }, label = { Text("이름") }, singleLine = true)
    OutlinedTextField(minutes, { minutes = it; onChange(preset.copy(minutes = it.toIntOrNull() ?: 0)) },
        label = { Text(if (preset.ev) "최대 휴식 시간(5~720분)" else "휴식 시간(5~720분)") }, singleLine = true)
    RestOptions(RestDisplay.entries, preset.display, { it.label }) { onChange(preset.copy(display = it)) }
    RestSoundControls(preset, true, { onChange(preset.copy(sound = it)) },
        { onChange(preset.copy(volume = it)) }, { onChange(preset.copy(muted = it)) })
    RestOptions(RestAlert.entries, preset.alert, { it.label }) { onChange(preset.copy(alert = it)) }
    Row {
        TextButton(onClick = { onChange(preset.copy(ev = false, target = target.toIntOrNull()?.takeIf { it in 1..100 } ?: 80)) }) { Text("일반 휴식") }
        TextButton(onClick = { onChange(preset.copy(ev = true, target = target.toIntOrNull() ?: 0)) }) { Text("EV 목표 휴식") }
    }
    if (preset.ev) OutlinedTextField(target, { target = it; onChange(preset.copy(target = it.toIntOrNull() ?: 0)) },
        label = { Text("목표 충전율(1~100%)") }, singleLine = true)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> RestOptions(values: List<T>, selected: T, label: (T) -> String, enabled: Boolean = true, select: (T) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { values.forEach { value -> TextButton(onClick = { select(value) }, enabled = enabled, colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = Color(0xFFAACAE6)), modifier = Modifier.heightIn(min = 64.dp)) {
        Text((if (value == selected) "✓ " else "") + label(value),fontSize=20.sp)
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

@Composable
internal fun RestSoundControls(preset: RestPreset, enabled: Boolean, sound: (RestSound) -> Unit,
    volume: (Int) -> Unit, mute: (Boolean) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("배경음", fontSize = 24.sp)
        RestOptions(RestSound.entries, preset.sound, { it.label }, enabled) { sound(it) }
        Text("배경음 볼륨 ${preset.volume}%", fontSize = 22.sp)
        androidx.compose.material3.Slider(value = preset.volume.toFloat(), onValueChange = { volume(it.toInt()) },
            valueRange = 0f..100f, enabled = enabled && preset.sound != RestSound.SILENT,
            colors = androidx.compose.material3.SliderDefaults.colors(thumbColor = Color(0xFFAACAE6), activeTrackColor = Color(0xFFAACAE6), inactiveTrackColor = StayColors.Border),
            modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).testTag("rest_sound_volume"))
        OutlinedButton(onClick = { mute(!preset.muted) }, enabled = enabled && preset.sound != RestSound.SILENT,
            modifier = Modifier.heightIn(min = 64.dp)) { Text(if (preset.muted) "음소거 해제" else "음소거", fontSize = 20.sp) }
        Text("배경음에만 적용됩니다.", color = StayColors.Secondary, fontSize = 18.sp)
    }
}
