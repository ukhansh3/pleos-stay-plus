package com.pleosstay.app.study

import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pleosstay.app.modes.WaitingChoice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Functionality-first UI. Authentication/SDK/network and persistence stay in adapters/VM. */
@Composable
fun StudyScreen(viewModel: StudyViewModel, onOpenFiles: () -> Unit) {
    val state by viewModel.state.collectAsState()
    BackHandler { viewModel.leave(); onOpenFiles() }
    var minutes by remember { mutableStateOf("30") }
    var noteText by remember(state.selected?.key, state.allowed) { mutableStateOf("") }
    var editId by remember(state.selected?.key, state.allowed) { mutableStateOf<String?>(null) }
    var confirmDelete by remember(state.selected?.key, state.allowed) { mutableStateOf(false) }
    LaunchedEffect(state.noteSaveSerial) {
        if (state.noteSaveSerial > 0) { noteText = ""; editId = null }
    }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Study · 학습 자료", style = MaterialTheme.typography.headlineSmall)
        Text(state.message)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(minutes, { minutes = it.filter(Char::isDigit).take(3) }, label = { Text("분 (5~720)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.width(150.dp), enabled = state.allowed && !state.active && !state.busy)
            Button(onClick = { viewModel.start(minutes.toIntOrNull() ?: 0) }, enabled = state.allowed && !state.active && !state.busy) { Text("학습 시작") }
            if (state.active) {
                Text("남은 시간 ${state.remainingMillis / 60000}:${(state.remainingMillis / 1000 % 60).toString().padStart(2, '0')}")
                OutlinedButton(onClick = viewModel::end) { Text("학습 종료") }
            }
            OutlinedButton(onClick = onOpenFiles) { Text("자료함 · PIN/연결") }
        }
        state.prompt?.let { prompt ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { viewModel.choose(WaitingChoice.EXTEND) }, enabled = !state.busy && prompt.extensionUnavailable == null) { Text("Waiting ${prompt.extensionMinutes}분 연장") }
                Button(onClick = { viewModel.choose(WaitingChoice.SHORTEN) }, enabled = !state.busy && prompt.shortenUnavailable == null) { Text("남은 시간만 학습") }
                OutlinedButton(onClick = { viewModel.choose(WaitingChoice.BACK) }, enabled = !state.busy) { Text("뒤로") }
            }
        }
        if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (!state.allowed || !state.active) {
            Text("정차·앱 활성·자료함 인증·PIN 해제 후 자료를 열 수 있습니다. 잠긴 로컬 기록은 삭제되지 않습니다.")
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = viewModel::refreshFiles, enabled = !state.busy) { Text("파일 목록") }
                OutlinedButton(onClick = viewModel::restoreLast, enabled = !state.busy) { Text("마지막 학습 복원") }
            }
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LazyColumn(Modifier.widthIn(min = 130.dp, max = 210.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(state.files, key = { it.fileId }) { file -> TextButton(onClick = { viewModel.selectFile(file) }, enabled = !state.busy) { Text(file.name) } }
                    items(state.versions, key = { it.versionId }) { ref -> OutlinedButton(onClick = { viewModel.open(ref) }, enabled = !state.busy) { Text("버전 ${ref.versionNo} · ${ref.sizeBytes} B") } }
                }
                Column(Modifier.weight(1.5f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.selected?.let { Text("${it.name} · v${it.versionNo}", fontWeight = FontWeight.Bold) }
                    state.document?.let { document ->
                        val count = document.pageCount ?: document.blocks.size
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton(onClick = { viewModel.move(state.position - 1) }, enabled = !state.busy && state.position > 0) { Text("이전") }
                            Text(if (document.format == StudyFormat.PDF) "${state.position + 1} / $count 페이지" else "${state.position + 1} / $count 구간")
                            OutlinedButton(onClick = { viewModel.move(state.position + 1) }, enabled = !state.busy && state.position + 1 < count) { Text("다음") }
                        }
                        if (document.format == StudyFormat.PDF) {
                            Row {
                                TextButton(onClick = { viewModel.move(state.position, state.zoom - .25f) }, enabled = !state.busy && state.zoom > .75f) { Text("축소") }
                                Text("${(state.zoom * 100).toInt()}%")
                                TextButton(onClick = { viewModel.move(state.position, state.zoom + .25f) }, enabled = !state.busy && state.zoom < 3f) { Text("확대") }
                            }
                            LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                                item { state.page?.let { bitmap ->
                                    Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                                        Image(bitmap.asImageBitmap(), "PDF ${state.position + 1}페이지", Modifier.width((bitmap.width * .55f).dp).height((bitmap.height * .55f).dp))
                                    }
                                } }
                            }
                        } else {
                            Surface(Modifier.weight(1f).fillMaxWidth(), color = Color.White, contentColor = Color(0xff16243a)) {
                                LazyColumn(Modifier.fillMaxSize().padding(12.dp)) {
                                    item { document.blocks.getOrNull(state.position)?.let { StudyBlockView(it) } }
                                }
                            }
                        }
                        Row {
                            Checkbox(state.record?.progress?.completed == true, { viewModel.complete(it) }, enabled = !state.busy)
                            Text("자료 학습 완료 (자동 완료 아님)")
                        }
                        Text("열람 위치 ${(state.record?.progress?.fraction?.times(100) ?: 0f).toInt()}% · 원본 파일은 수정하지 않습니다.", style = MaterialTheme.typography.bodySmall)
                    }
                }
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("옆 메모 · 현재 파일/버전", fontWeight = FontWeight.Bold)
                    OutlinedTextField(noteText, { noteText = it }, label = { Text(if (editId == null) "새 텍스트 메모" else "메모 수정") }, modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp, max = 180.dp), enabled = state.selected != null && !state.busy)
                    Row {
                        Button(onClick = { viewModel.saveNote(editId, noteText) }, enabled = state.selected != null && noteText.isNotBlank() && !state.busy) { Text("저장") }
                        if (editId != null) TextButton(onClick = { editId = null; noteText = "" }) { Text("수정 취소") }
                    }
                    LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(state.record?.notes.orEmpty(), key = { it.id }) { note ->
                            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(10.dp)) {
                                Text(note.body)
                                Text("기록 위치 ${note.anchor ?: "없음"}", style = MaterialTheme.typography.bodySmall)
                                Row {
                                    TextButton(onClick = { editId = note.id; noteText = note.body }, enabled = !state.busy) { Text("수정") }
                                    TextButton(onClick = { viewModel.deleteNote(note.id) }, enabled = !state.busy) { Text("삭제") }
                                }
                            } }
                        }
                    }
                    TextButton(onClick = { confirmDelete = true }, enabled = state.selected != null && !state.busy) { Text("이 버전의 로컬 메모·진도 삭제") }
                }
            }
        }
    }
    if (confirmDelete && state.allowed) AlertDialog(
        onDismissRequest = { confirmDelete = false }, title = { Text("학습 기록 삭제") },
        text = { Text("현재 자료 버전의 로컬 메모와 진도를 삭제합니다. 원본 파일은 유지됩니다.") },
        confirmButton = { TextButton(onClick = { confirmDelete = false; noteText = ""; editId = null; viewModel.deleteLocalData() }) { Text("삭제") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("취소") } },
    )
}

@Composable
private fun StudyBlockView(block: StudyBlock) {
    when (block) {
        is StudyBlock.Paragraph -> Text(buildAnnotatedString {
            block.runs.forEach { run ->
                val color = run.color?.takeIf { it.matches(Regex("[0-9a-fA-F]{6}")) }?.let { Color(android.graphics.Color.parseColor("#$it")) } ?: Color.Unspecified
                withStyle(SpanStyle(fontWeight = if (run.bold) FontWeight.Bold else FontWeight.Normal,
                    fontStyle = if (run.italic) FontStyle.Italic else FontStyle.Normal,
                    textDecoration = if (run.underline) TextDecoration.Underline else null,
                    color = color, fontSize = (run.points?.coerceIn(8f, 48f) ?: 16f).sp)) { append(run.text) }
            }
        }, modifier = Modifier.padding(vertical = 4.dp))
        is StudyBlock.Table -> Column(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            block.rows.forEach { row -> Row { row.forEach { cell ->
                OutlinedCard(Modifier.width(220.dp)) { Column(Modifier.padding(8.dp)) { cell.forEach { StudyBlockView(it) } } }
            } } }
        }
        is StudyBlock.Picture -> {
            val bitmap by produceState<android.graphics.Bitmap?>(null, block) {
                value = withContext(Dispatchers.IO) {
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeByteArray(block.bytes, 0, block.bytes.size, bounds)
                    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) null else {
                        var sample = 1
                        while (bounds.outWidth.toLong() / sample * (bounds.outHeight / sample) > 4_000_000) sample *= 2
                        BitmapFactory.decodeByteArray(block.bytes, 0, block.bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
                    }
                }
            }
            bitmap?.let { Image(it.asImageBitmap(), block.description, Modifier.fillMaxWidth().heightIn(max = 360.dp)) }
                ?: Text("이미지를 읽는 중이거나 지원하지 않는 이미지입니다.")
        }
    }
}
