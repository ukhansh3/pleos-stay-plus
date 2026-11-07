package com.pleosstay.app.work

import androidx.activity.compose.BackHandler
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pleosstay.app.documents.DocumentRef
import com.pleosstay.app.modes.WaitingChoice
import com.pleosstay.app.work.docx.*

/** UI contains no HTTP, file I/O, authentication, SQL or vehicle SDK calls. */
@Composable
fun WorkScreen(model: WorkViewModel, onRealtime: () -> Unit, onRecoverWaiting: () -> Unit, onHome: () -> Unit,
 onDocxPicker:(DocxPickerKind,Int?)->Unit = {_,_->}) {
    val state by model.state.collectAsState()
    val editor by model.docx.state.collectAsState()
    var deleting by remember { mutableStateOf<DocumentRef?>(null) }
    BackHandler { if (state.choice != null) model.resolve(WaitingChoice.BACK) else if (state.session != null) model.requestExit() else onHome() }
    if(state.allowed && editor.view!=null){DocxEditorPanel(model.docx,onDocxPicker,model.docx::mask);return}
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("업무 · 문서 작업", style = MaterialTheme.typography.headlineMedium)
        Text("DOCX 작성·본문·서식·표·이미지 편집 · PDF 편집과 Realtime 업로드는 이번 범위에서 제외됩니다.")
        if (!state.allowed) {
            Text("업무 문서 잠김", style = MaterialTheme.typography.titleLarge)
            Text(state.message)
            Text("정차 상태 확인 후 기존 파일 전송 화면에서 연결과 PIN을 확인해 주세요. 보관한 자료는 잠긴 상태로 유지됩니다.")
            OutlinedButton(onClick = onRealtime) { Text("파일 연결·PIN 확인") }
            TextButton(onClick = onHome) { Text("홈으로") }
            return@Column
        }
        Text(state.message)
        Text(editor.message)
        if (state.busy) Text("확인 중…")
        if (state.session == null) {
            OutlinedTextField(value = state.minutes, onValueChange = model::setMinutes, label = { Text("업무 시간 (분)") }, enabled = !state.busy)
            Button(onClick = model::start, enabled = !state.busy) { Text("업무 타이머 시작") }
        } else {
            Text("남은 업무 시간 ${duration(state.remainingMillis)}", style = MaterialTheme.typography.titleLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { model.extend(10) }, enabled = !state.busy) { Text("10분 연장 확인") }
                OutlinedButton(onClick = model::requestExit, enabled = !state.busy) { Text("업무 종료") }
            }
        }
        if (state.storageError) OutlinedButton(onClick = onRecoverWaiting, enabled = !state.busy) { Text("Waiting 복구 화면") }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = model::refresh, enabled = !state.busy) { Text("Realtime 자료함 새로고침") }
            TextButton(onClick = onRealtime) { Text("기존 파일 전송") }
        }
        Text("내 파일 · PDF / DOCX", style = MaterialTheme.typography.titleLarge)
        state.files.forEach { file ->
            OutlinedButton(onClick = { model.versions(file.fileId) }, enabled = !state.busy) { Text(file.name) }
        }
        state.versions.forEach { ref ->
            OutlinedButton(onClick = { model.open(ref) }, enabled = !state.busy && state.session != null) {
                Text("${ref.name} · v${ref.versionNo} · ${(ref.sizeBytes + 1023) / 1024} KiB · 원본 검증·암호화 보관")
            }
        }
        state.selected?.let { draft ->
            Text("선택한 기준: ${draft.source.name} · v${draft.source.versionNo}", style = MaterialTheme.typography.titleMedium)
            Text(if (draft.dirty) "보관된 엔진 초안이 있습니다. 해당 엔진 검증 후 복원할 수 있습니다." else "해시가 확인된 원본을 보관했습니다. 편집한 문서가 아닙니다.")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = model.docx::newDocument, enabled = state.session!=null&&!state.busy&&!editor.busy) { Text("새 DOCX") }
            OutlinedButton(onClick = {onDocxPicker(DocxPickerKind.OPEN,null)}, enabled=state.session!=null&&!state.busy&&!editor.busy) { Text("로컬 DOCX 열기") }
            OutlinedButton(onClick=model.docx::listDrafts,enabled=!state.busy&&!editor.busy){Text("DOCX 초안·마지막 작업")}
        }
        editor.drafts.forEach{draft->Row{TextButton(onClick={model.docx.restore(draft)},enabled=state.session!=null&&!editor.busy){Text("${draft.name} · 복원")};TextButton(onClick={model.docx.delete(draft)},enabled=!editor.busy){Text("초안 삭제")}}}
        Text("로컬 보관본", style = MaterialTheme.typography.titleLarge)
        Text("연결 철회·사용자 전환 시 잠금 후 보관하며 자동 삭제하지 않습니다. 같은 사용자·연결·버전의 서버 권한을 다시 확인해야 접근할 수 있습니다.")
        state.drafts.forEach { draft ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { model.open(draft.source) }, enabled = !state.busy && state.session != null) {
                    Text("${draft.source.name} · v${draft.source.versionNo} · ${if (draft.dirty) "초안" else "원본 보관본"}")
                }
                TextButton(onClick = { deleting = draft.source }, enabled = !state.busy) { Text("로컬 삭제") }
            }
        }
        TextButton(onClick = onHome) { Text("홈으로 · 보관본 유지") }
    }
    if (!state.allowed) deleting = null
    state.choice?.takeIf { state.allowed }?.let { prompt ->
        AlertDialog(onDismissRequest = { model.resolve(WaitingChoice.BACK) }, title = { Text("Waiting 시간 확인") },
            text = { Text("잔여 ${duration(prompt.remainingMillis)} · 요청 시간을 위해 ${prompt.extensionMinutes}분 연장이 필요합니다.") },
            confirmButton = { TextButton(onClick = { model.resolve(WaitingChoice.EXTEND) }, enabled = !state.busy && prompt.extensionUnavailable == null) { Text("Waiting 연장") } },
            dismissButton = { Row {
                TextButton(onClick = { model.resolve(WaitingChoice.SHORTEN) }, enabled = !state.busy && prompt.shortenUnavailable == null) { Text("남은 시간만") }
                TextButton(onClick = { model.resolve(WaitingChoice.BACK) }, enabled = !state.busy) { Text("뒤로") }
            } })
    }
    deleting?.takeIf { state.allowed }?.let { ref ->
        AlertDialog(onDismissRequest = { deleting = null }, title = { Text("로컬 보관본 삭제") },
            text = { Text("이 버전의 원본 보관본과 이전 엔진 초안을 삭제합니다. DOCX 편집 초안은 위 DOCX 목록에서 따로 삭제합니다. 서버 원본은 변경하지 않습니다.") },
            confirmButton = { TextButton(onClick = { model.deleteDraft(ref); deleting = null }) { Text("삭제") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("취소") } })
    }
    if (state.exitConfirmation && state.allowed) AlertDialog(onDismissRequest = model::dismissExit,
        title = { Text("외부 파일로 내보내지 않은 초안이 있습니다") }, text = { Text("암호화 초안은 보관합니다. Waiting과 Realtime 연결은 종료하지 않습니다.") },
        confirmButton = { TextButton(onClick = model::finishKeepingDrafts) { Text("초안 보관 후 업무 종료") } },
        dismissButton = { TextButton(onClick = model::dismissExit) { Text("계속 업무") } })
}

private fun duration(millis: Long): String = "%02d:%02d".format(millis / 60_000, millis / 1_000 % 60)
