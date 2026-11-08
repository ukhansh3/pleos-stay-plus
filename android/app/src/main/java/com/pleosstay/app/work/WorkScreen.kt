package com.pleosstay.app.work

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pleosstay.app.R
import com.pleosstay.app.documents.DocumentRef
import com.pleosstay.app.modes.WaitingChoice
import com.pleosstay.app.ui.premium.*
import com.pleosstay.app.work.docx.*

/** Presentation only. Mode, authorization, draft retention and export policies are unchanged. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WorkScreen(model: WorkViewModel, onRealtime: () -> Unit, onRecoverWaiting: () -> Unit, onHome: () -> Unit,
    onDocxPicker: (DocxPickerKind, Int?) -> Unit = { _, _ -> }) {
    val liveState by model.state.collectAsState()
    val state = featureInspection()?.work ?: liveState
    val liveEditor by model.docx.state.collectAsState()
    val editor = featureInspection()?.docx ?: liveEditor
    var deleting by remember(state.allowed) { mutableStateOf<DocumentRef?>(null) }
    var deletingDocx by remember(state.allowed) { mutableStateOf<DocxDraftInfo?>(null) }
    var selectedVersion by remember(state.allowed) { mutableStateOf<DocumentRef?>(null) }
    var tab by remember { mutableIntStateOf(0) }
    BackHandler { if (state.choice != null) model.resolve(WaitingChoice.BACK) else if (state.session != null) model.requestExit() else onHome() }
    if (state.allowed && editor.view != null) { DocxEditorPanel(model.docx, onDocxPicker, model.docx::mask); return }
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("업무 · Work", Modifier.weight(1f), fontSize = 38.sp, fontWeight = FontWeight.Bold)
            TextButton(onClick = onRealtime) { Text("자료함 연결") }
        }
        if (!state.allowed) {
            NoticePanel("업무 문서 잠김", "정차 상태와 자료함 PIN을 확인해 주세요.", true)
            PremiumAction("연결·PIN 확인", onRealtime, secondary = true)
            return@Column
        }
        workMessage(state.message)?.let { Text(it, color = StayColors.Secondary) }
        if (editor.message != "DOCX를 열거나 새 문서를 만드세요.") workMessage(editor.message)?.let { Text(it, color = StayColors.Secondary) }
        if (state.busy || editor.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val library: @Composable (Modifier) -> Unit = { modifier ->
                WorkPanel(modifier) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("내 문서", Modifier.weight(1f), fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
                        PremiumAction("+ 새 DOCX", model.docx::newDocument, enabled = state.session != null && !state.busy && !editor.busy)
                    }
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        listOf("Realtime 문서", "DOCX 초안", "최근 작업").forEachIndexed { index, label ->
                            FilterChip(selected = tab == index, onClick = { tab = index; if (index > 0) model.docx.listDrafts() }, label = { Text(label) }, enabled = !state.busy && !editor.busy)
                        }
                        TextButton(onClick = { model.refresh(); if (tab > 0) model.docx.listDrafts() }, enabled = !state.busy && !editor.busy) { Text("새로고침") }
                    }
                    LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        if (tab == 0) {
                            if (state.files.isEmpty()) item { Text("문서가 없습니다.", color = StayColors.Secondary) }
                            items(state.files, key = { it.fileId }) { file ->
                                Surface(onClick = { model.versions(file.fileId); selectedVersion = null }, enabled = !state.busy,
                                    color = StayColors.Surface, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, StayColors.Border)) {
                                    Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(file.name, fontSize = 21.sp, fontWeight = FontWeight.SemiBold)
                                        Text(file.name.substringAfterLast('.', "문서").uppercase(), color = StayColors.Secondary)
                                    }
                                }
                            }
                            if (state.versions.isNotEmpty()) item { Text("버전 선택", fontSize = 22.sp, fontWeight = FontWeight.SemiBold) }
                            items(state.versions, key = { it.versionId }) { ref ->
                                Surface(onClick = { selectedVersion = ref }, enabled = !state.busy,
                                    shape = RoundedCornerShape(16.dp), color = if (selectedVersion?.versionId == ref.versionId) StayColors.Purple.copy(alpha = .28f) else StayColors.Surface,
                                    border = BorderStroke(1.dp, if (selectedVersion?.versionId == ref.versionId) StayColors.Purple else StayColors.Border)) {
                                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                                        Text(ref.name, fontSize = 20.sp)
                                        Text("v${ref.versionNo} · ${(ref.sizeBytes + 1023) / 1024} KB", color = StayColors.Secondary)
                                    }
                                }
                            }
                            state.selected?.takeIf { it.source.mimeType != DOCX_MIME }?.let { draft -> item { Text("${draft.source.name} · PDF 편집은 지원하지 않습니다.", color = StayColors.Secondary) } }
                        } else {
                            if (editor.drafts.isEmpty()) item { Text("보관한 DOCX 초안이 없습니다.", color = StayColors.Secondary) }
                            items(editor.drafts, key = { it.id }) { draft ->
                                Surface(color = StayColors.Surface, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, StayColors.Border)) {
                                    Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Text(draft.name, fontSize = 21.sp, fontWeight = FontWeight.SemiBold)
                                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                            OutlinedButton(onClick = { model.docx.restore(draft) }, enabled = state.session != null && !editor.busy) { Text("이어서 편집") }
                                            TextButton(onClick = { deletingDocx = draft }, enabled = !editor.busy) { Text("삭제") }
                                        }
                                    }
                                }
                            }
                        }
                        if (tab == 1 && state.drafts.isNotEmpty()) {
                            item { HorizontalDivider(color = StayColors.Border); Text("원본 보관본", fontSize = 22.sp) }
                            items(state.drafts, key = { it.id }) { draft ->
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    TextButton(onClick = { model.open(draft.source) }, enabled = !state.busy && state.session != null, modifier = Modifier.weight(1f)) { Text("${draft.source.name} · v${draft.source.versionNo}") }
                                    TextButton(onClick = { deleting = draft.source }, enabled = !state.busy) { Text("삭제") }
                                }
                            }
                        }
                    }
                    PremiumAction(if (selectedVersion?.mimeType == DOCX_MIME) "선택한 DOCX 편집" else "선택한 원본 보관",
                        { selectedVersion?.let(model::open) }, Modifier.fillMaxWidth(), selectedVersion != null && state.session != null && !state.busy && !editor.busy)
                }
            }
            val tools: @Composable (Modifier) -> Unit = { modifier ->
                WorkPanel(modifier) {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        Text("업무 시간", fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
                        if (state.session == null) {
                            OutlinedTextField(state.minutes, model::setMinutes, label = { Text("시간 · 분") }, enabled = !state.busy, modifier = Modifier.fillMaxWidth(), singleLine = true)
                            PremiumAction("업무 시작", model::start, Modifier.fillMaxWidth(), !state.busy)
                        } else {
                            Text(duration(state.remainingMillis), fontSize = 44.sp, color = StayColors.Text, fontWeight = FontWeight.SemiBold)
                            PremiumAction("10분 연장", { model.extend(10) }, Modifier.fillMaxWidth(), !state.busy, true)
                            PremiumAction("업무 종료", model::requestExit, Modifier.fillMaxWidth(), !state.busy, true)
                        }
                        if (state.storageError) PremiumAction("대기 기록 복구", onRecoverWaiting, Modifier.fillMaxWidth(), !state.busy, true)
                        HorizontalDivider(color = StayColors.Border)
                        Text("로컬 문서", fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                        Image(painterResource(R.drawable.work_laptop), null, Modifier.fillMaxWidth().height(170.dp), contentScale = ContentScale.Crop)
                        PremiumAction("DOCX 파일 열기", { onDocxPicker(DocxPickerKind.OPEN, null) }, Modifier.fillMaxWidth(), state.session != null && !state.busy && !editor.busy, true)
                        TextButton(onClick = { tab = 1; model.docx.listDrafts() }, enabled = !editor.busy) { Text("보관한 초안") }
                    }
                }
            }
            if (maxWidth >= 1000.dp) Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                library(Modifier.weight(2.5f).fillMaxHeight()); tools(Modifier.weight(1f).fillMaxHeight())
            } else Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                tools(Modifier.fillMaxWidth().height(650.dp)); library(Modifier.fillMaxWidth().height(800.dp))
            }
        }
    }
    state.choice?.takeIf { state.allowed }?.let { prompt ->
        AlertDialog(onDismissRequest = { model.resolve(WaitingChoice.BACK) }, title = { Text("대기 시간 확인") },
            text = { Text("잔여 ${duration(prompt.remainingMillis)} · 대기 시간을 ${prompt.extensionMinutes}분 연장할까요?") },
            confirmButton = { TextButton(onClick = { model.resolve(WaitingChoice.EXTEND) }, enabled = !state.busy && prompt.extensionUnavailable == null) { Text("대기 연장") } },
            dismissButton = { Row {
                TextButton(onClick = { model.resolve(WaitingChoice.SHORTEN) }, enabled = !state.busy && prompt.shortenUnavailable == null) { Text("남은 시간만") }
                TextButton(onClick = { model.resolve(WaitingChoice.BACK) }, enabled = !state.busy) { Text("취소") }
            } })
    }
    deleting?.takeIf { state.allowed }?.let { ref ->
        AlertDialog(onDismissRequest = { deleting = null }, title = { Text("보관본 삭제") }, text = { Text("이 버전의 보관본을 삭제합니다. 서버 원본은 유지됩니다.") },
            confirmButton = { TextButton(onClick = { model.deleteDraft(ref); deleting = null }) { Text("삭제") } }, dismissButton = { TextButton(onClick = { deleting = null }) { Text("취소") } })
    }
    deletingDocx?.takeIf { state.allowed }?.let { draft ->
        AlertDialog(onDismissRequest = { deletingDocx = null }, title = { Text("초안 삭제") }, text = { Text("보관한 DOCX 초안을 삭제할까요?") },
            confirmButton = { TextButton(onClick = { model.docx.delete(draft); deletingDocx = null }) { Text("삭제") } }, dismissButton = { TextButton(onClick = { deletingDocx = null }) { Text("취소") } })
    }
    if (state.exitConfirmation && state.allowed) AlertDialog(onDismissRequest = model::dismissExit,
        title = { Text("초안을 보관하고 종료할까요?") }, text = { Text("내보내지 않은 초안은 보관됩니다. 대기는 계속됩니다.") },
        confirmButton = { TextButton(onClick = model::finishKeepingDrafts) { Text("보관 후 종료") } }, dismissButton = { TextButton(onClick = model::dismissExit) { Text("계속 편집") } })
}

@Composable
private fun WorkPanel(modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier, shape = RoundedCornerShape(24.dp), color = StayColors.Surface.copy(alpha = .72f), border = BorderStroke(1.dp, StayColors.Border)) {
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(18.dp), content = content)
    }
}
private fun duration(millis: Long): String = "%02d:%02d".format(millis / 60_000, millis / 1_000 % 60)
private fun workMessage(value: String): String? = when {
    value.startsWith("업무 타이머를 시작했습니다.") -> null
    value == "정차 상태와 기존 파일 연결·PIN을 확인해 주세요." -> null
    value == "기존 Realtime 자료함을 불러왔습니다." -> null
    value == "업무에 사용할 정확한 버전을 선택해 주세요." -> null
    value.startsWith("DOCX 원본을 검증·보관했습니다.") -> null
    value.startsWith("원본 바이트와 해시를 확인해") -> "원본 보관 완료"
    value.startsWith("기존 엔진 초안이 있습니다.") -> "보관한 PDF가 있습니다. PDF 편집은 지원하지 않습니다."
    value.startsWith("자료함이 비어 있습니다.") -> null
    value.startsWith("업무를 종료했습니다.") -> "업무 종료 · 대기는 계속됩니다."
    value.startsWith("업무 시간이 끝났습니다.") -> "업무 시간 종료 · 대기는 계속됩니다."
    value.startsWith("DOCX 구조 기반 편집") -> null
    value == "마지막 작업 순으로 표시합니다." -> null
    value.startsWith("자동 초안 저장됨") -> "초안 저장됨"
    value.startsWith("문서 잠김") -> null
    else -> value.replace("Waiting", "대기").takeIf { it.isNotBlank() }
}
