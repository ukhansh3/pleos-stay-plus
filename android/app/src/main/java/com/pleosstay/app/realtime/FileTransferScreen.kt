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
    val state by model.state.collectAsState()
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

    var selectedFile by remember(state.connectionId, state.allowed, state.unlocked) { mutableStateOf<String?>(null) }
    var selectedVersion by remember(state.connectionId, state.allowed, state.unlocked) { mutableStateOf<String?>(null) }
    if (!state.allowed || !state.unlocked) { NoticePanel("파일 화면 잠김", "정차 상태와 PIN을 확인해 주세요.", true); return }
    val pairing = runCatching { JSONObject(state.message) }.getOrNull()?.takeIf { it.has("issuer") && it.has("claimant") }
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
                        Text(if (state.connectionId == null) "연결되지 않음" else "PC 연결됨", color = if (state.connectionId == null) StayColors.Secondary else StayColors.Success, fontSize = 20.sp)
                        PremiumAction("연결 복원", { otherOperation(model::register) }, Modifier.fillMaxWidth(), !state.busy, true)
                        if (state.connectionId == null) {
                            Text("8자리 페어링 코드", color = StayColors.Secondary)
                            if (state.code.isNotBlank()) Text(state.code, fontSize = 30.sp, color = StayColors.Blue, fontWeight = FontWeight.SemiBold)
                            PremiumAction("페어링 코드 발급", { otherOperation(model::issue) }, Modifier.fillMaxWidth(), !state.busy, true)
                            OutlinedTextField(code, { code = it.filter(Char::isDigit).take(8) }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                                label = { Text("상대 기기 코드") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), enabled = !state.busy)
                            PremiumAction("코드 입력", { otherOperation { model.claim(code) } }, Modifier.fillMaxWidth(), !state.busy && code.length == 8, true)
                            if (state.pairingId != null) {
                                PremiumAction("상대 기기 확인", { otherOperation(model::inspectPairing) }, Modifier.fillMaxWidth(), !state.busy, true)
                                pairing?.let { p ->
                                    listOf("issuer", "claimant").mapNotNull { p.optJSONObject(it) }.forEach { device ->
                                        Text(device.optString("name"), fontSize = 19.sp)
                                    }
                                    Text(when (p.optString("state")) { "OPEN" -> "코드 입력 대기"; "CLAIMED" -> "양쪽 승인 대기"; "CONNECTED" -> "연결 승인됨"; "EXPIRED" -> "코드 만료"; "CANCELLED" -> "연결 취소됨"; else -> "상태 확인 필요" }, color = StayColors.Secondary)
                                }
                                PremiumAction("상대 승인", { otherOperation(model::approve) }, Modifier.fillMaxWidth(), !state.busy && pairing?.optString("state") == "CLAIMED", true)
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
                        if (state.files.isEmpty()) item { Text("파일이 없습니다.", color = StayColors.Secondary) }
                        items(state.files, key = { it.getString("id") }) { file ->
                            val id = file.getString("id")
                            Surface(onClick = { selectedFile = id; selectedVersion = null; otherOperation { model.versions(id) } }, enabled = !state.busy,
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
                        PremiumAction("이 버전 다운로드", { version?.let { downloadUi = DownloadUiProgress(state.progress); model.download(it) } }, Modifier.fillMaxWidth(), version != null && !state.busy)
                        HorizontalDivider(color = StayColors.Border)
                        Text("전송 진행 상태", fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                        if (state.busy && downloadUi?.freshSample == true) {
                            LinearProgressIndicator(progress = { state.progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth(), color = StayColors.Purple)
                            Text("${(state.progress * 100).toInt()}%", fontSize = 26.sp, color = StayColors.Blue)
                        } else if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                        Text(if (state.busy) "처리 중…" else "진행 중인 작업 없음", color = StayColors.Secondary)
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

private data class DownloadUiProgress(val baseline: Float, val busySeen: Boolean = false, val freshSample: Boolean = false)
