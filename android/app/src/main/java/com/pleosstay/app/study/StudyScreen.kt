package com.pleosstay.app.study

import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
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
import com.pleosstay.app.ui.premium.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Presentation only; authorization, mode transitions and records remain in the ViewModel. */
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
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("공부 · Study", Modifier.weight(1f), fontSize = 38.sp, fontWeight = FontWeight.Bold)
            if (state.active) Text(studyDuration(state.remainingMillis), fontSize = 28.sp, color = StayColors.Blue)
            TextButton(onClick = onOpenFiles) { Text("자료함 연결") }
        }
        if (!state.allowed) {
            NoticePanel("학습자료 잠김", "정차 상태와 자료함 PIN을 확인해 주세요.", true)
            return@Column
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (!state.active) {
                OutlinedTextField(minutes, { minutes = it.filter(Char::isDigit).take(3) }, label = { Text("학습 시간 · 분") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.width(180.dp), enabled = !state.busy, singleLine = true)
                PremiumAction("학습 시작", { viewModel.start(minutes.toIntOrNull() ?: 0) }, enabled = !state.busy)
            } else PremiumAction("학습 종료", viewModel::end, enabled = !state.busy, secondary = true)
            TextButton(onClick = viewModel::refreshFiles, enabled = state.active && !state.busy) { Text("새로고침") }
            TextButton(onClick = viewModel::restoreLast, enabled = state.active && !state.busy) { Text("이전 학습") }
        }
        studyMessage(state.message)?.let { Text(it, color = StayColors.Secondary, fontSize = 18.sp) }
        if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val library: @Composable (Modifier) -> Unit = { modifier ->
                StudyPanel(modifier) {
                    Text("학습 자료", fontSize = 25.sp, fontWeight = FontWeight.SemiBold)
                    HorizontalDivider(color = StayColors.Border)
                    LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (state.files.isEmpty()) item { Text(if (state.active) "자료가 없습니다." else "학습을 시작해 주세요.", color = StayColors.Secondary) }
                        items(state.files, key = { it.fileId }) { file ->
                            val selected = state.selected?.fileId == file.fileId
                            Surface(onClick = { viewModel.selectFile(file) }, enabled = !state.busy && state.active,
                                shape = RoundedCornerShape(16.dp), color = if (selected) StayColors.Purple.copy(alpha = .28f) else StayColors.Surface,
                                border = BorderStroke(1.dp, if (selected) StayColors.Purple else StayColors.Border)) {
                                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(file.name, fontWeight = FontWeight.SemiBold, fontSize = 19.sp)
                                    Text(file.name.substringAfterLast('.', "문서").uppercase(), color = StayColors.Secondary, fontSize = 14.sp, maxLines = 1)
                                }
                            }
                        }
                        if (state.versions.isNotEmpty()) item { Text("버전 선택", color = StayColors.Secondary) }
                        items(state.versions, key = { it.versionId }) { ref ->
                            OutlinedButton(onClick = { viewModel.open(ref) }, enabled = !state.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("v${ref.versionNo} · ${(ref.sizeBytes + 1023) / 1024} KB") }
                        }
                    }
                }
            }
            val reader: @Composable (Modifier) -> Unit = { modifier ->
                StudyPanel(modifier) {
                    Text(state.selected?.name ?: "문서 열람", fontSize = 25.sp, fontWeight = FontWeight.SemiBold)
                    val document = state.document
                    if (document == null) {
                        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { Text("자료를 선택해 주세요.", color = StayColors.Secondary) }
                    } else {
                        state.record?.progress?.fraction?.let { fraction ->
                            LinearProgressIndicator(progress = { fraction.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth(), color = StayColors.Purple)
                        }
                        val count = document.pageCount ?: document.blocks.size
                        if (document.format == StudyFormat.PDF) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text("${state.position + 1} / $count 페이지", Modifier.weight(1f), color = StayColors.Secondary)
                                TextButton(onClick = { viewModel.move(state.position, state.zoom - .25f) }, enabled = !state.busy && state.zoom > .75f) { Text("−") }
                                Text("${(state.zoom * 100).toInt()}%", color = StayColors.Secondary)
                                TextButton(onClick = { viewModel.move(state.position, state.zoom + .25f) }, enabled = !state.busy && state.zoom < 3f) { Text("+") }
                            }
                        }
                        Surface(Modifier.weight(1f).fillMaxWidth(), shape = RoundedCornerShape(10.dp), color = Color.White, contentColor = Color(0xff16243a)) {
                            LazyColumn(Modifier.fillMaxSize().padding(18.dp)) {
                                item {
                                    if (document.format == StudyFormat.PDF) {
                                        state.page?.let { bitmap -> Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                                            Image(bitmap.asImageBitmap(), "PDF ${state.position + 1}페이지", Modifier.width((bitmap.width * .55f).dp).height((bitmap.height * .55f).dp))
                                        } }
                                    } else document.blocks.getOrNull(state.position)?.let { StudyBlockView(it) }
                                }
                            }
                        }
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            OutlinedButton(onClick = { viewModel.move(state.position - 1) }, enabled = !state.busy && state.position > 0, modifier = Modifier.heightIn(min = 56.dp)) { Text("이전") }
                            if (document.format != StudyFormat.PDF) Text("${state.position + 1} / $count 구간", color = StayColors.Secondary)
                            OutlinedButton(onClick = { viewModel.move(state.position + 1) }, enabled = !state.busy && state.position + 1 < count, modifier = Modifier.heightIn(min = 56.dp)) { Text("다음") }
                        }
                    }
                }
            }
            val notes: @Composable (Modifier) -> Unit = { modifier ->
                StudyPanel(modifier) {
                    Text("옆 메모", fontSize = 25.sp, fontWeight = FontWeight.SemiBold)
                    LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(state.record?.notes.orEmpty(), key = { it.id }) { note ->
                            Surface(color = StayColors.Surface, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, StayColors.Border)) {
                                Column(Modifier.fillMaxWidth().padding(14.dp)) {
                                    Text(note.body, fontSize = 18.sp)
                                    Row {
                                        TextButton(onClick = { editId = note.id; noteText = note.body }, enabled = !state.busy) { Text("수정") }
                                        TextButton(onClick = { viewModel.deleteNote(note.id) }, enabled = !state.busy) { Text("삭제") }
                                    }
                                }
                            }
                        }
                        item {
                            OutlinedTextField(noteText, { noteText = it }, label = { Text(if (editId == null) "새 메모" else "메모 수정") },
                                modifier = Modifier.fillMaxWidth().heightIn(min = 180.dp, max = 260.dp), enabled = state.selected != null && !state.busy)
                            Spacer(Modifier.height(12.dp))
                            PremiumAction("메모 저장", { viewModel.saveNote(editId, noteText) }, Modifier.fillMaxWidth(), state.selected != null && noteText.isNotBlank() && !state.busy)
                            if (editId != null) TextButton(onClick = { editId = null; noteText = "" }) { Text("수정 취소") }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(state.record?.progress?.completed == true, { viewModel.complete(it) }, enabled = state.selected != null && !state.busy)
                        Text("학습 완료", fontSize = 18.sp)
                    }
                    TextButton(onClick = { confirmDelete = true }, enabled = state.selected != null && !state.busy) { Text("학습 기록 삭제") }
                }
            }
            if (maxWidth >= 1000.dp) Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                library(Modifier.weight(.85f).fillMaxHeight()); reader(Modifier.weight(2.3f).fillMaxHeight()); notes(Modifier.weight(1.05f).fillMaxHeight())
            } else Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                library(Modifier.fillMaxWidth().height(400.dp)); reader(Modifier.fillMaxWidth().height(700.dp)); notes(Modifier.fillMaxWidth().height(650.dp))
            }
        }
    }
    state.prompt?.takeIf { state.allowed }?.let { prompt -> AlertDialog(
        onDismissRequest = { viewModel.choose(WaitingChoice.BACK) }, title = { Text("대기 시간 확인") },
        text = { Text("학습 시간을 맞추려면 대기 시간을 ${prompt.extensionMinutes}분 연장해 주세요.") },
        confirmButton = { TextButton(onClick = { viewModel.choose(WaitingChoice.EXTEND) }, enabled = !state.busy && prompt.extensionUnavailable == null) { Text("대기 연장") } },
        dismissButton = { Row { TextButton(onClick = { viewModel.choose(WaitingChoice.SHORTEN) }, enabled = !state.busy && prompt.shortenUnavailable == null) { Text("남은 시간만") }; TextButton(onClick = { viewModel.choose(WaitingChoice.BACK) }, enabled = !state.busy) { Text("취소") } } }) }
    if (confirmDelete && state.allowed) AlertDialog(
        onDismissRequest = { confirmDelete = false }, title = { Text("학습 기록 삭제") },
        text = { Text("이 버전의 메모와 진도를 삭제합니다. 원본 파일은 유지됩니다.") },
        confirmButton = { TextButton(onClick = { confirmDelete = false; noteText = ""; editId = null; viewModel.deleteLocalData() }) { Text("삭제") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("취소") } })
}

@Composable
private fun StudyPanel(modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier, shape = RoundedCornerShape(24.dp), color = StayColors.Surface.copy(alpha = .72f), border = BorderStroke(1.dp, StayColors.Border)) {
        Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
    }
}
private fun studyDuration(millis: Long) = "%02d:%02d".format(millis / 60000, millis / 1000 % 60)
private fun studyMessage(message: String): String? = if (message.startsWith("DOCX 재배치 열람:")) "일부 문서 서식은 다르게 표시될 수 있습니다." else when (message) {
    "학습 시간을 선택하고 시작해 주세요.", "자료함에서 자료를 선택해 주세요.", "파일과 버전을 선택해 주세요.", "열 버전을 선택해 주세요.", "자료를 열었습니다.", "자료함이 비어 있습니다." -> null
    "학습 기록을 저장했습니다." -> "저장 완료"
    "학습을 종료했습니다. 기존 Waiting은 유지됩니다." -> "학습 종료 · 대기는 계속됩니다."
    else -> message.replace("Waiting", "대기").takeIf { it.isNotBlank() }
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
