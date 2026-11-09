package com.pleosstay.app.realtime

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import android.content.ClipData
import android.content.ClipboardManager
import com.pleosstay.app.BuildConfig
import com.pleosstay.app.ui.qrBitmap
import kotlinx.coroutines.delay
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pleosstay.app.ui.premium.*
import org.json.JSONObject

/** The same authenticated transport and PIN gate supply every action on this screen. */
@Composable
fun FileTransferScreen(model: FileTransferViewModel) {
    DisposableEffect(model) { onDispose { model.closeWorkTransferScreen() } }
    val liveState by model.state.collectAsState()
    val state = featureInspection()?.files ?: liveState
    var confirmSwitch by remember(state.allowed, state.unlocked) { mutableStateOf(false) }
    var confirmClear by remember(state.allowed, state.unlocked) { mutableStateOf(false) }
    var code by remember(state.allowed, state.unlocked) { mutableStateOf("") }
    var downloadUi by remember(state.connectionId, state.allowed, state.unlocked) { mutableStateOf<DownloadUiProgress?>(null) }
    // The VM progress is shared and retained across operations. Attribute samples only
    // to a download started here; an unchanged prior sample is never fresh progress.
    LaunchedEffect(state.busy, state.progress, downloadUi) {
        downloadUi?.let { current ->
            if (state.busy) {
                val next = current.copy(busySeen = true, freshSample = current.freshSample || state.progress != current.baseline)
                if (next != current) downloadUi = next
            } else if (current.busySeen) downloadUi = null
        }
    }
    fun otherOperation(action: () -> Unit) { downloadUi = null; action() }

    val inspectionFiles = featureInspection()?.files
    var selectedFile by remember(state.connectionId, state.allowed, state.unlocked) { mutableStateOf(inspectionFiles?.files?.firstOrNull()?.optString("id")) }
    var selectedVersion by remember(state.connectionId, state.allowed, state.unlocked) { mutableStateOf(inspectionFiles?.versions?.firstOrNull()?.optString("id")) }
    if (!state.allowed || !state.unlocked) { NoticePanel("파일 화면 잠김", "정차 상태와 PIN을 확인해 주세요.", true); return }
    if (inspectionFiles == null) {
        LaunchedEffect(model) { model.register() }
        LaunchedEffect(model, state.pairingId, state.connectionId) {
            while (state.pairingId != null || state.connectionId != null) {
                delay(if (state.connectionId == null) 2500 else 10_000)
                if (state.connectionId == null) model.pollPairing() else model.pollLinkedConnection()
            }
        }
    }
    val pairing = state.pairing
    val filesReachable = state.connectionId != null && state.serverStatus != RealtimeServerStatus.UNAVAILABLE
    val version = state.versions.firstOrNull { it.optString("id") == selectedVersion }
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text("파일 작업 · Realtime", fontSize = 38.sp, fontWeight = FontWeight.Bold)
        realtimeNotice(state.message)?.let { Text(it, color = StayColors.Secondary, fontSize = 18.sp) }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val connection: @Composable (Modifier) -> Unit = { modifier ->
                TransferPanel(modifier) {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                        Text("PC 연결", fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
                        Surface(shape = RoundedCornerShape(20.dp), color = StayColors.Background.copy(alpha = .45f), border = BorderStroke(1.dp, StayColors.Border)) {
                            Row(Modifier.fillMaxWidth().height(145.dp).padding(20.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                                StayIcon("WORK", Modifier.size(54.dp), StayColors.Blue)
                                Text("↔", fontSize = 34.sp, color = StayColors.Secondary)
                                StayIcon("FILES", Modifier.size(54.dp), StayColors.Blue)
                            }
                        }
                        val networkDown = state.serverStatus == RealtimeServerStatus.UNAVAILABLE
                        val linked = state.connectionId != null && !networkDown
                        Text(when {
                            networkDown -> "서버에 연결할 수 없습니다"
                            linked -> state.pcName?.let { "$it · 연결 완료" } ?: "PC 연결 완료"
                            state.busy && pairing == null -> "연결 준비 중…"
                            else -> "연결된 PC 없음"
                        }, color = if (linked) StayColors.Success else StayColors.Secondary, fontSize = 20.sp)
                        if (networkDown) PremiumAction("연결 다시 확인", { otherOperation(model::register) }, Modifier.fillMaxWidth(), !state.busy, true)
                        if (state.connectionId == null) {
                            PcPairingContent(state, model)
                            if (BuildConfig.DEBUG) {
                                var showReverse by remember { mutableStateOf(false) }
                                TextButton(onClick = { showReverse = !showReverse }) { Text("개발자용 PC 발급 코드 입력") }
                                if (showReverse) {
                                    OutlinedTextField(code, { code = it.filter { c -> c in '0'..'9' }.take(8) }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                                        label = { Text("PC의 8자리 코드") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), enabled = !state.busy)
                                    PremiumAction("코드 확인", { otherOperation { model.claim(code) } }, Modifier.fillMaxWidth(), !state.busy && code.length == 8, true)
                                }
                            }
                        } else {
                            HorizontalDivider(color = StayColors.Border)
                            PremiumAction("연결 해제", { otherOperation(model::disconnect) }, Modifier.fillMaxWidth(), !state.busy, true)
                        }
                        TextButton(onClick = { confirmSwitch = true }, enabled = !state.busy) { Text("사용자 전환") }
                    }
                }
            }
            val library: @Composable (Modifier) -> Unit = { modifier ->
                TransferPanel(modifier) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("공통 파일 자료함", Modifier.weight(1f), fontSize = 25.sp, fontWeight = FontWeight.SemiBold)
                        TextButton(onClick = { otherOperation(model::refresh) }, enabled = !state.busy) { Text("새로고침") }
                    }
                    Text("TXT · PDF · DOCX", color = StayColors.Secondary, fontSize = 17.sp)
                    LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        if (state.files.isEmpty()) item { Text(when {
                            state.serverStatus == RealtimeServerStatus.UNAVAILABLE -> "서버 연결을 확인해 주세요."
                            state.connectionId == null -> "PC를 연결하면 파일을 볼 수 있습니다."
                            state.busy -> "파일을 불러오는 중…"
                            else -> "파일이 없습니다."
                        }, color = StayColors.Secondary) }
                        items(state.files, key = { it.getString("id") }) { file ->
                            val id = file.getString("id")
                            Surface(onClick = { selectedFile = id; selectedVersion = null; otherOperation { model.versions(id) } }, enabled = !state.busy && filesReachable,
                                color = if (selectedFile == id) StayColors.Purple.copy(alpha = .28f) else StayColors.Surface,
                                shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, if (selectedFile == id) StayColors.Purple else StayColors.Border)) {
                                Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                    StayIcon("FILES", Modifier.size(36.dp), StayColors.Blue)
                                    Text(file.getString("name"), fontSize = 21.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                        item { HorizontalDivider(color = StayColors.Border); Spacer(Modifier.height(16.dp)); Text("선택한 파일의 버전", fontSize = 23.sp, fontWeight = FontWeight.SemiBold) }
                        if (state.versions.isEmpty()) item { Text("파일을 선택해 주세요.", color = StayColors.Secondary) }
                        items(state.versions, key = { it.getString("id") }) { v ->
                            Surface(onClick = { selectedVersion = v.getString("id") }, enabled = !state.busy,
                                shape = RoundedCornerShape(16.dp), color = if (selectedVersion == v.getString("id")) StayColors.Purple.copy(alpha = .28f) else StayColors.Background.copy(alpha = .3f),
                                border = BorderStroke(1.dp, if (selectedVersion == v.getString("id")) StayColors.Purple else StayColors.Border)) {
                                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("v${v.getInt("versionNo")} · ${v.getString("fileName")}", fontSize = 20.sp)
                                    Text("${(v.getLong("size") + 1023) / 1024} KB", color = StayColors.Secondary)
                                }
                            }
                        }
                    }
                }
            }
            val transfer: @Composable (Modifier) -> Unit = { modifier ->
                TransferPanel(modifier) {
                    Text("다운로드·전송", fontSize = 25.sp, fontWeight = FontWeight.SemiBold)
                    Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                        Text("선택한 버전 다운로드", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                        version?.let { Text("${it.getString("fileName")} · v${it.getInt("versionNo")}", color = StayColors.Secondary) }
                        PremiumAction("이 버전 다운로드", { version?.let { downloadUi = DownloadUiProgress(state.progress); model.download(it) } }, Modifier.fillMaxWidth(), version != null && !state.busy && filesReachable)
                        if (model.supportsWorkTransfer || inspectionFiles != null) {
                            HorizontalDivider(color = StayColors.Border)
                            PremiumAction("차량 파일 보내기", { otherOperation(model::openWorkPicker) },
                                Modifier.fillMaxWidth(), filesReachable && !state.busy)
                        }
                        HorizontalDivider(color = StayColors.Border)
                        Text("전송 진행 상태", fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                        if (state.busy && (state.sendingWork || downloadUi?.freshSample == true)) {
                            LinearProgressIndicator(progress = { state.progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth(), color = StayColors.Purple)
                            Text("${(state.progress * 100).toInt()}%", fontSize = 26.sp, color = StayColors.Blue)
                        } else if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                        Text(when {
                            state.sendingWork -> "전송 중…"
                            state.busy -> "처리 중…"
                            else -> "진행 중인 작업 없음"
                        }, color = StayColors.Secondary)
                        if (state.busy) PremiumAction("전송 중단", { otherOperation(model::cancel) }, Modifier.fillMaxWidth(), secondary = true)
                        PremiumAction("중단된 전송 재시도", { otherOperation(model::retryUpload) }, Modifier.fillMaxWidth(), !state.busy, true)
                    }
                    TextButton(onClick = { confirmClear = true }, enabled = !state.busy) { Text("로컬 파일 삭제") }
                    PremiumAction("화면 잠금", { otherOperation(model::lock) }, Modifier.fillMaxWidth(), secondary = true)
                }
            }
            if (maxWidth >= 1000.dp) Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                connection(Modifier.weight(1f).fillMaxHeight()); library(Modifier.weight(2.25f).fillMaxHeight()); transfer(Modifier.weight(1.15f).fillMaxHeight())
            } else Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                connection(Modifier.fillMaxWidth().height(800.dp)); library(Modifier.fillMaxWidth().height(700.dp)); transfer(Modifier.fillMaxWidth().height(650.dp))
            }
        }
    }
    state.workPicker?.let {
        WorkTransferPickerDialog(it, model::selectWorkExport, model::confirmWorkTransfer, model::dismissWorkPicker)
    }
    if (confirmSwitch) AlertDialog(onDismissRequest = { confirmSwitch = false }, title = { Text("사용자 전환") },
        text = { Text("PC 연결을 해제하고 이 차량의 파일·인증정보·PIN을 지웁니다. 새 사용자는 다시 연결해야 합니다.") },
        confirmButton = { TextButton(onClick = { confirmSwitch = false; otherOperation(model::switchUserAfterConfirmation) }) { Text("지우고 전환") } }, dismissButton = { TextButton(onClick = { confirmSwitch = false }) { Text("취소") } })
    if (confirmClear) AlertDialog(onDismissRequest = { confirmClear = false }, title = { Text("로컬 파일 삭제") },
        text = { Text("이 차량의 다운로드와 중단된 전송 파일을 지우고 화면을 잠급니다. 서버 원본은 유지됩니다.") },
        confirmButton = { TextButton(onClick = { confirmClear = false; otherOperation(model::clearLocalFiles) }) { Text("삭제 후 잠금") } }, dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("취소") } })
}

@Composable
private fun TransferPanel(modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier, shape = RoundedCornerShape(24.dp), color = StayColors.Surface.copy(alpha = .72f), border = BorderStroke(1.dp, StayColors.Border)) {
        Column(Modifier.fillMaxSize().padding(22.dp), verticalArrangement = Arrangement.spacedBy(18.dp), content = content)
    }
}
private fun realtimeNotice(value: String): String? = when {
    value.startsWith("{") -> if (runCatching { JSONObject(value).has("error") }.getOrDefault(false)) "요청을 처리하지 못했습니다. 연결을 확인해 주세요." else null
    value == "잠김" || value == "연결 없음" -> null
    value.startsWith("다운로드 검증 완료:") -> "다운로드 완료"
    value == "NO_CONNECTION" -> "PC 연결을 확인해 주세요."
    value == "NO_PENDING_UPLOAD" -> "재시도할 전송이 없습니다."
    value.contains("INTEGRITY_MISMATCH") || value.contains("SIZE_MISMATCH") -> "파일 검증에 실패했습니다. 다시 다운로드해 주세요."
    value.contains("PAIRING") -> "연결 코드를 확인하고 다시 시도해 주세요."
    value.contains("AUTH") || value.contains("401") || value.contains("403") -> "인증이 만료됐습니다. 연결을 다시 확인해 주세요."
    value.contains("FAILED") || value == "ERROR" || value.contains("Exception") -> "파일 작업에 실패했습니다. 연결을 확인하고 다시 시도해 주세요."
    value.startsWith("수정본 v") -> "전송 완료"
    else -> value.takeIf { it.isNotBlank() }?.let { if (it.any { c -> c in '가'..'힣' }) it else "작업을 완료하지 못했습니다. 연결을 확인해 주세요." }
}

@Composable
private fun PcPairingContent(state: FileTransferState, model: FileTransferViewModel) {
    val pairing = state.pairing
    var now by remember(pairing?.expiresAt) { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(pairing?.expiresAt) {
        while (pairing != null && now < pairing.expiresAt) {
            delay(1000)
            now = System.currentTimeMillis()
        }
    }
    val seconds = pairing?.let { ((it.expiresAt - now + 999) / 1000).coerceAtLeast(0) } ?: 0
    val expired = pairing?.phase == "EXPIRED" || (pairing?.phase in setOf("OPEN", "CLAIMED") && seconds == 0L)
    val phase = if (expired) "EXPIRED" else pairing?.phase
    when (phase) {
        "OPEN" -> {
            Text("PC 연결 대기 중", color = StayColors.Blue)
            Text(state.code, fontSize = 32.sp, fontWeight = FontWeight.SemiBold)
            Text("Windows Stay+에서 이 8자리 코드를 입력하세요.")
            val guide = RealtimeDeployment.guide
            if (guide != null) {
                val context = LocalContext.current
                val bitmap = remember(guide) { runCatching { qrBitmap(guide).asImageBitmap() }.getOrNull() }
                bitmap?.let { Image(it, "PC 연결 안내 페이지 QR", Modifier.size(168.dp).background(Color.White)) }
                Text(guide, color = StayColors.Secondary)
                TextButton(onClick = {
                    context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("PC 연결 안내", guide))
                }) { Text("안내 주소 복사") }
            }
        }
        "CLAIMED" -> {
            Text(if (pairing?.approvedHere == true) "PC 승인 대기 중" else "PC 연결 요청 도착", color = StayColors.Blue)
            pairing?.pcName?.let { Text(it, fontWeight = FontWeight.SemiBold) }
            if (pairing?.approvedHere == false) {
                Text("이 PC의 이름을 확인한 뒤 승인하세요.")
                PremiumAction("이 PC 승인", model::approve, Modifier.fillMaxWidth(), !state.busy && pairing.pcId != null)
            }
        }
        "CONNECTED" -> Text("승인 완료 · 연결 확인 중…", color = StayColors.Blue)
        "EXPIRED" -> Text("연결 코드가 만료되었습니다.", color = StayColors.Warning)
        "CANCELLED" -> Text("연결 요청이 취소되었습니다.", color = StayColors.Secondary)
    }
    state.pairingError?.let { Text(it, color = StayColors.Warning) }
    if (phase == "OPEN" || phase == "CLAIMED") {
        Text("남은 시간 %d:%02d".format(seconds / 60, seconds % 60), color = StayColors.Secondary)
        TextButton(onClick = model::cancelPairing, enabled = !state.busy) { Text("연결 취소") }
    }
    if (phase !in setOf("OPEN", "CLAIMED", "CONNECTED")) {
        PremiumAction(if (expired) "새 코드 발급" else "PC 연결하기", model::issue, Modifier.fillMaxWidth(), !state.busy)
    }
}

private data class DownloadUiProgress(val baseline: Float, val busySeen: Boolean = false, val freshSample: Boolean = false)
