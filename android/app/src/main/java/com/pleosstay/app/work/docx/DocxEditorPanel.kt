package com.pleosstay.app.work.docx

import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pleosstay.app.ui.premium.*

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DocxEditorPanel(controller: DocxEditorController, onPicker: (DocxPickerKind, Int?) -> Unit, onClose: () -> Unit) {
    val liveState by controller.state.collectAsState()
    val state = featureInspection()?.docx ?: liveState
    val view = state.view ?: return
    var selected by remember(state.draft?.id) { mutableIntStateOf(0) }
    var selectedImage by remember(state.draft?.id) { mutableStateOf<Int?>(null) }
    val paragraph = view.paragraphs.firstOrNull { it.id == selected } ?: view.paragraphs.firstOrNull()
    var field by remember(state.draft?.id, paragraph?.id) { mutableStateOf(TextFieldValue(paragraph?.text ?: "")) }
    var tableDialog by remember { mutableStateOf(false) }
    var font by remember { mutableStateOf("Noto Sans CJK KR") }
    var size by remember { mutableStateOf("16") }
    var color by remember { mutableStateOf("000000") }
    var rows by remember { mutableStateOf("2") }
    var columns by remember { mutableStateOf("2") }
    var width by remember { mutableStateOf("240") }
    var height by remember { mutableStateOf("160") }
    var exportName by remember(state.draft?.id) { mutableStateOf(state.draft?.name ?: "문서.docx") }
    val editable = view.readOnlyReason == null
    LaunchedEffect(paragraph?.id) {
        paragraph?.runs?.firstOrNull()?.let { run -> font = run.font ?: "Noto Sans CJK KR"; size = (run.size ?: 16f).toString(); color = run.color ?: "000000" }
    }
    LaunchedEffect(selectedImage) {
        view.images.firstOrNull { it.id == selectedImage }?.let { image -> width = image.width.toString(); height = image.height.toString() }
    }
    LaunchedEffect(paragraph?.text, state.busy) {
        if (!state.busy && paragraph != null && field.text != paragraph.text) field = field.copy(text = paragraph.text,
            selection = TextRange(field.selection.start.coerceAtMost(paragraph.text.length), field.selection.end.coerceAtMost(paragraph.text.length)))
    }
    BackHandler { if (!state.busy) onClose() }
    fun range(): Pair<Int, Int> { val length = paragraph?.text?.length ?: 0; return if (field.selection.collapsed) 0 to length else field.selection.min.coerceAtMost(length) to field.selection.max.coerceAtMost(length) }
    fun format(style: DocxStyle) { paragraph?.let { p -> val (a, b) = range(); controller.edit { it.format(p.id, a, b, style) } } }
    val (a, b) = range()
    var offset = 0
    val selectedRuns = paragraph?.runs?.filter { run -> val from = offset; offset += run.text.length; (offset > a && from < b) || (a == b && run.text.isEmpty()) }.orEmpty()
    val formatEnabled = !state.busy && editable && paragraph?.editable == true
    var pane by rememberSaveable(state.draft?.id) { mutableIntStateOf(0) }
    var more by remember { mutableStateOf(false) }
    val tableParagraphIds = view.tables.flatMap { it.rows.flatten().flatten() }.toSet()
    val bodyParagraphs = view.paragraphs.filter { it.id !in tableParagraphIds }
    val documentScroll = rememberLazyListState()
    var revealItem by remember { mutableStateOf<Int?>(null) }
    BoxWithConstraints(Modifier.fillMaxSize().imePadding()) {
    val compact = maxWidth < 1098.dp
    LaunchedEffect(revealItem) {
        revealItem?.let { documentScroll.scrollToItem(it) }
        revealItem = null
    }
    Column(Modifier.fillMaxSize().padding(if (compact) 16.dp else 24.dp),
        verticalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 18.dp)) {
        Text("업무 · DOCX 편집기", fontSize = if (compact) 26.sp else 36.sp, fontWeight = FontWeight.Bold)
        if (compact) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(state.draft?.name ?: "DOCX", Modifier.weight(1f), maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                Box {
                    TextButton(onClick = { more = true }, enabled = !state.busy) { Text("더보기") }
                    DropdownMenu(expanded = more, onDismissRequest = { more = false }) {
                        DropdownMenuItem(text = { Text("DOCX 내보내기") }, onClick = { more = false; onPicker(DocxPickerKind.EXPORT, null) })
                        if (state.exportUri != null) DropdownMenuItem(text = { Text("저장 파일 열기") }, onClick = { more = false; controller.reopenExport() })
                        DropdownMenuItem(text = { Text("문서 목록") }, onClick = { more = false; onClose() })
                    }
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedButton(controller::undo, enabled = !state.busy && state.canUndo && editable,
                    modifier = Modifier.heightIn(min = 48.dp)) { Text("실행 취소") }
                OutlinedButton(controller::redo, enabled = !state.busy && state.canRedo && editable,
                    modifier = Modifier.heightIn(min = 48.dp)) { Text("다시 실행") }
                Button(controller::save, enabled = !state.busy, modifier = Modifier.heightIn(min = 48.dp).testTag("docx-save")) { Text("초안 저장") }
            }
            CompactPanelSelector(listOf("본문", "문단·구성", "서식·도구"), pane) { pane = it }
        } else
        Surface(shape = RoundedCornerShape(20.dp), color = StayColors.Surface.copy(alpha = .72f), border = BorderStroke(1.dp, StayColors.Border)) {
            Row(Modifier.fillMaxWidth().padding(16.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(state.draft?.name ?: "DOCX", fontSize = 24.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.widthIn(min = 180.dp, max = 350.dp))
                OutlinedButton(onClick = controller::undo, enabled = !state.busy && state.canUndo && editable, modifier = Modifier.heightIn(min = 64.dp)) { Text("실행 취소", fontSize = 20.sp) }
                OutlinedButton(onClick = controller::redo, enabled = !state.busy && state.canRedo && editable, modifier = Modifier.heightIn(min = 64.dp)) { Text("다시 실행", fontSize = 20.sp) }
                OutlinedButton(onClick = controller::save, enabled = !state.busy, modifier = Modifier.heightIn(min = 64.dp)) { Text("초안 저장", fontSize = 20.sp) }
                PremiumAction("DOCX 내보내기", { onPicker(DocxPickerKind.EXPORT, null) }, enabled = !state.busy)
                if (state.exportUri != null) TextButton(onClick = controller::reopenExport, enabled = !state.busy) { Text("저장 파일 열기") }
                TextButton(onClick = onClose, enabled = !state.busy) { Text("문서 목록") }
            }
        }
        view.readOnlyReason?.let { Text("읽기 전용 · $it", color = MaterialTheme.colorScheme.error) }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val structure: @Composable (Modifier) -> Unit = { modifier ->
                EditorPanel(modifier) {
                    Text("문단·구성", fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                    LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(view.paragraphs, key = { it.id }) { p ->
                            Surface(onClick = { selected = p.id; field = TextFieldValue(p.text); selectedImage = null
                                if (compact) {
                                    pane = 0
                                    val index = bodyParagraphs.indexOfFirst { it.id == p.id }
                                    if (index >= 0) revealItem = index
                                }
                            }, enabled = !state.busy,
                                shape = RoundedCornerShape(14.dp), color = if (paragraph?.id == p.id) StayColors.Selected else StayColors.Surface,
                                border = BorderStroke(1.dp, if (paragraph?.id == p.id) StayColors.SelectionOutline else StayColors.Border)) {
                                Text("${p.id + 1}. ${p.text.ifBlank { "빈 문단" }.take(50)}", Modifier.fillMaxWidth().padding(16.dp), fontSize = 18.sp)
                            }
                        }
                        items(view.tables, key = { "table-${it.id}" }) { table -> TextButton(onClick = {
                            table.rows.firstOrNull()?.firstOrNull()?.firstOrNull()?.let { id -> selected = id; view.paragraphs.firstOrNull { it.id == id }?.let { field = TextFieldValue(it.text) } }
                            if (compact) {
                                pane = 0
                                revealItem = bodyParagraphs.size + view.tables.indexOf(table)
                            }
                        }, enabled = !state.busy) { Text("표 ${table.id + 1}") } }
                        items(view.images, key = { "image-${it.id}" }) { image -> TextButton(onClick = {
                            selectedImage = image.id
                            if (compact) {
                                pane = 0
                                revealItem = bodyParagraphs.size + view.tables.size + view.images.indexOf(image)
                            }
                        }, enabled = !state.busy) { Text("이미지 ${image.id + 1}") } }
                    }
                    PremiumAction("+ 문단 추가", { controller.edit { it.addParagraph(paragraph?.id) } }, Modifier.fillMaxWidth(), !state.busy && editable, true)
                    PremiumAction("+ 표 삽입", { tableDialog = true }, Modifier.fillMaxWidth(), !state.busy && editable, true)
                }
            }
            val document: @Composable (Modifier) -> Unit = { modifier ->
                EditorPanel(modifier) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(paragraph?.let { "문단 ${it.id + 1}" } ?: "문서", Modifier.weight(1f), color = StayColors.Secondary)
                        Text(if (state.dirty) "편집 중" else "저장됨", color = StayColors.Blue)
                    }
                    Surface(Modifier.weight(1f).fillMaxWidth(), shape = RoundedCornerShape(10.dp), color = Color.White, contentColor = Color(0xFF273B63)) {
                        LazyColumn(Modifier.fillMaxSize(), state = documentScroll,
                            contentPadding = PaddingValues(if (compact) 12.dp else 28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            items(bodyParagraphs, key = { it.id }) { p ->
                                if (compact) Text("문단 ${p.id + 1}", color = Color(0xFF415675), fontWeight = FontWeight.SemiBold)
                                if (p.id == paragraph?.id) {
                                    OutlinedTextField(field, { next -> field = next; if (next.text != p.text) controller.setText(p.id, next.text) },
                                        modifier = Modifier.fillMaxWidth().heightIn(min = 150.dp).testTag("docx-body"), enabled = editable && p.editable,
                                        textStyle = TextStyle(color = Color(0xFF273B63), fontSize = 18.sp, textAlign = when (p.alignment) { "center" -> TextAlign.Center; "right" -> TextAlign.Right; else -> TextAlign.Left }),
                                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color(0xFF273B63), unfocusedTextColor = Color(0xFF273B63), disabledTextColor = Color(0xFF596780), cursorColor = Color(0xFF315B88), focusedBorderColor = Color(0xFF315B88), unfocusedBorderColor = Color(0xFFBDC9DE)),
                                        visualTransformation = VisualTransformation { input -> TransformedText(if (input.text == p.text) rich(p.runs) else input, OffsetMapping.Identity) })
                                    if (!p.editable) Text("이 문단은 읽기 전용입니다.", color = Color.DarkGray)
                                } else Text(if (p.text.isBlank()) AnnotatedString("빈 문단") else rich(p.runs), Modifier.fillMaxWidth().clickable(enabled = !state.busy) { selected = p.id; field = TextFieldValue(p.text); selectedImage = null }.then(if (compact) Modifier.border(1.dp, Color(0xFF738DA8), RoundedCornerShape(8.dp)) else Modifier)
                                    .padding(horizontal = if (compact) 12.dp else 0.dp, vertical = 12.dp),
                                    textAlign = when (p.alignment) { "center" -> TextAlign.Center; "right" -> TextAlign.Right; else -> TextAlign.Left })
                            }
                            items(view.tables, key = { "table-${it.id}" }) { table ->
                                Text("표 ${table.id + 1}", color = Color(0xFF273B63), fontWeight = FontWeight.SemiBold)
                                Column(Modifier.horizontalScroll(rememberScrollState())) {
                                    table.rows.forEach { row -> Row {
                                        row.forEach { cell ->
                                            val cellP = view.paragraphs.firstOrNull { it.id == cell.firstOrNull() }
                                            Surface(Modifier.width(180.dp), color = if (selected in cell) Color(0xFFEEEAFB) else Color.White, border = BorderStroke(1.dp, Color(0xFFBDC9DE))) {
                                                Text(cell.joinToString("\n") { id -> view.paragraphs.firstOrNull { it.id == id }?.text ?: "" }.ifBlank { "빈 셀" },
                                                    Modifier.clickable(enabled = !state.busy && cellP != null) { cellP?.let { selected = it.id; field = TextFieldValue(it.text); selectedImage = null } }.padding(14.dp), color = Color(0xFF273B63))
                                            }
                                        }
                                    } }
                                }
                                if (paragraph?.let { it.id in tableParagraphIds } == true && table.rows.flatten().any { selected in it }) {
                                    OutlinedTextField(field, { next -> field = next; paragraph?.let { if (next.text != it.text) controller.setText(it.id, next.text) } },
                                        Modifier.fillMaxWidth().heightIn(min = 120.dp).testTag("docx-body"), enabled = editable && paragraph?.editable == true,
                                        textStyle = TextStyle(color = Color(0xFF273B63), fontSize = 18.sp), label = { Text("선택한 셀", color = Color.DarkGray) },
                                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color(0xFF273B63), unfocusedTextColor = Color(0xFF273B63), cursorColor = Color(0xFF315B88)))
                                }
                            }
                            items(view.images, key = { "image-${it.id}" }) { image ->
                                val bitmap = remember(image.bytes) {
                                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                                    BitmapFactory.decodeByteArray(image.bytes, 0, image.bytes.size, bounds)
                                    var sample = 1
                                    while (bounds.outWidth.toLong() / sample * (bounds.outHeight / sample) > 2_000_000) sample *= 2
                                    BitmapFactory.decodeByteArray(image.bytes, 0, image.bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
                                }
                                bitmap?.let { Image(it.asImageBitmap(), "문서 이미지 ${image.id + 1}", Modifier.fillMaxWidth().heightIn(max = 260.dp).clickable { selectedImage = image.id }) }
                            }
                        }
                    }
                }
            }
            val inspector: @Composable (Modifier) -> Unit = { modifier ->
                EditorPanel(modifier) {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("선택한 문단", fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                        OutlinedTextField(font, { font = it.take(100) }, label = { Text("글꼴") }, enabled = formatEnabled, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(size, { size = it.take(4) }, label = { Text("크기 · pt") }, enabled = formatEnabled, modifier = Modifier.weight(1f), singleLine = true)
                            OutlinedButton(onClick = { format(DocxStyle(font = font, size = size.toFloatOrNull() ?: 16f, color = color)) }, enabled = formatEnabled, modifier = Modifier.heightIn(min = 64.dp)) { Text("적용") }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(selected = selectedRuns.isNotEmpty() && selectedRuns.all { it.bold }, onClick = { format(DocxStyle(bold = selectedRuns.isEmpty() || !selectedRuns.all { it.bold })) }, enabled = formatEnabled, modifier = Modifier.heightIn(min = 64.dp), label = { Text("B", fontSize = 20.sp, fontWeight = FontWeight.Bold) })
                            FilterChip(selected = selectedRuns.isNotEmpty() && selectedRuns.all { it.italic }, onClick = { format(DocxStyle(italic = selectedRuns.isEmpty() || !selectedRuns.all { it.italic })) }, enabled = formatEnabled, modifier = Modifier.heightIn(min = 64.dp), label = { Text("I", fontSize = 20.sp, fontStyle = FontStyle.Italic) })
                            FilterChip(selected = selectedRuns.isNotEmpty() && selectedRuns.all { it.underline }, onClick = { format(DocxStyle(underline = selectedRuns.isEmpty() || !selectedRuns.all { it.underline })) }, enabled = formatEnabled, modifier = Modifier.heightIn(min = 64.dp), label = { Text("U", fontSize = 20.sp, textDecoration = TextDecoration.Underline) })
                        }
                        Text("문단 정렬", color = StayColors.Secondary)
                        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("left" to "왼쪽", "center" to "가운데", "right" to "오른쪽").forEach { (value, label) ->
                                FilterChip(selected = paragraph?.alignment == value, onClick = { paragraph?.let { p -> controller.edit { it.align(p.id, value) } } }, enabled = formatEnabled, modifier = Modifier.heightIn(min = 64.dp), label = { Text(label, fontSize = 20.sp) })
                            }
                        }
                        OutlinedTextField(color, { color = it.take(6) }, label = { Text("글자 색상 · RGB") }, enabled = formatEnabled, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        TextButton(onClick = { format(DocxStyle(color = color)) }, enabled = formatEnabled) { Text("색상 적용") }
                        TextButton(onClick = { paragraph?.let { p -> controller.edit { it.deleteParagraph(p.id) } } }, enabled = !state.busy && editable && paragraph != null) { Text("문단 삭제") }
                        HorizontalDivider(color = StayColors.Border)
                        Text("문서 도구", fontSize = 23.sp, fontWeight = FontWeight.SemiBold)
                        view.tables.forEach { table ->
                            val selectedCell = table.rows.flatMapIndexed { r, row -> row.mapIndexedNotNull { c, ids -> if (selected in ids) r to c else null } }.firstOrNull()
                            if (selectedCell != null) {
                                Text("표 ${table.id + 1}", color = StayColors.Secondary)
                                Row {
                                    TextButton(onClick = { controller.edit { it.addRow(table.id) } }, enabled = !state.busy && editable) { Text("행 추가") }
                                    TextButton(onClick = { controller.edit { it.addColumn(table.id) } }, enabled = !state.busy && editable) { Text("열 추가") }
                                }
                                TextButton(onClick = { val (r, c) = selectedCell; controller.edit { it.shadeCell(table.id, r, c, "DDEEFF") } }, enabled = !state.busy && editable) { Text("셀 배경 · 연파랑") }
                            }
                        }
                        PremiumAction("이미지 삽입", { onPicker(DocxPickerKind.INSERT_IMAGE, null) }, Modifier.fillMaxWidth(), !state.busy && editable, true)
                        if (selectedImage != null && view.images.any { it.id == selectedImage }) {
                            Text("이미지 ${selectedImage!! + 1}", color = StayColors.Secondary)
                            OutlinedTextField(width, { width = it.take(4) }, label = { Text("너비 · 16~1600") }, enabled = !state.busy && editable, modifier = Modifier.fillMaxWidth())
                            OutlinedTextField(height, { height = it.take(4) }, label = { Text("높이 · 16~1600") }, enabled = !state.busy && editable, modifier = Modifier.fillMaxWidth())
                            PremiumAction("크기 적용", { selectedImage?.let { id -> controller.edit { it.resizeImage(id, width.toIntOrNull() ?: 240, height.toIntOrNull() ?: 160) } } }, Modifier.fillMaxWidth(), !state.busy && editable, true)
                            TextButton(onClick = { onPicker(DocxPickerKind.REPLACE_IMAGE, selectedImage) }, enabled = !state.busy && editable) { Text("이미지 교체") }
                        }
                    }
                }
            }
            if (maxWidth >= 1050.dp) Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                structure(Modifier.weight(.8f).fillMaxHeight()); document(Modifier.weight(2.6f).fillMaxHeight()); inspector(Modifier.weight(1f).fillMaxHeight())
            } else when (pane) {
                1 -> structure(Modifier.fillMaxSize())
                2 -> inspector(Modifier.fillMaxSize())
                else -> document(Modifier.fillMaxSize())
            }
        }
        if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        Text(if (state.busy) "저장 중…" else editorStatus(state.message), Modifier.testTag("docx-status"), color = StayColors.Secondary, fontSize = 18.sp)
    }
    }
 if(tableDialog)AlertDialog(onDismissRequest={tableDialog=false},title={Text("표 삽입")},text={Column{OutlinedTextField(rows,{rows=it.take(2)},label={Text("행 1~20")});OutlinedTextField(columns,{columns=it.take(2)},label={Text("열 1~10")})}},confirmButton={TextButton(onClick={controller.edit{it.addTable(rows.toIntOrNull() ?: 2,columns.toIntOrNull() ?: 2)};tableDialog=false}){Text("삽입")}},dismissButton={TextButton(onClick={tableDialog=false}){Text("취소")}})
 if(state.confirmDownloads)AlertDialog(onDismissRequest=controller::dismissDownloads,title={Text("다운로드 폴더에 DOCX 내보내기")},text={Column{
  Text("Download/StayPlus에 새 DOCX를 저장합니다. 앱 밖에서도 접근할 수 있으며 잠금·앱 삭제 후에도 남습니다. 원본은 덮어쓰지 않습니다.")
  OutlinedTextField(exportName,{exportName=it.take(80)},label={Text("DOCX 파일 이름")})
 }},confirmButton={TextButton(onClick={controller.exportDownloads(exportName)},enabled=!state.busy&&exportName.isNotBlank()){Text("공개 저장 위치 확인·내보내기")}},dismissButton={Row{TextButton(onClick={controller.dismissDownloads();controller.exportPrivate()},enabled=!state.busy){Text("앱 전용 임시 파일")};TextButton(onClick=controller::dismissDownloads){Text("취소")}}})
}
private fun rich(runs:List<DocxRun>)=buildAnnotatedString{runs.forEach{r->withStyle(SpanStyle(color=r.color?.takeIf{it.matches(Regex("[A-Fa-f0-9]{6}"))}?.let{Color(android.graphics.Color.parseColor("#$it"))} ?: Color.Black,fontSize=(r.size ?: 18f).coerceIn(8f,72f).sp,fontWeight=if(r.bold)FontWeight.Bold else FontWeight.Normal,fontStyle=if(r.italic)FontStyle.Italic else FontStyle.Normal,textDecoration=if(r.underline)TextDecoration.Underline else TextDecoration.None,fontFamily=if(r.font?.contains("Serif",true)==true)FontFamily.Serif else FontFamily.SansSerif)){append(r.text)}}}

@Composable
private fun EditorPanel(modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier, shape = RoundedCornerShape(22.dp), color = StayColors.Surface.copy(alpha = .72f), border = BorderStroke(1.dp, StayColors.Border)) {
        Column(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
    }
}
private fun editorStatus(message: String): String = when {
    message.startsWith("DOCX 구조 기반 편집") -> ""
    message.startsWith("자동 초안 저장됨") -> "초안 저장됨"
    message.startsWith("암호화 DOCX 초안 저장 완료") -> "초안 저장 완료"
    message.startsWith("변경 이력 복원") -> "변경 복원됨"
    message.startsWith("Download/StayPlus에 새 DOCX 저장") -> "Download/StayPlus에 저장 완료"
    message.startsWith("DOCX 파일 저장·해시") -> "DOCX 저장 완료"
    message.startsWith("이미지 변경") -> "이미지 저장됨"
    message.startsWith("앱 전용 임시 DOCX 생성") -> "앱 전용 임시 파일 저장 완료"
    message.startsWith("시스템 파일 선택기를 열 수 없습니다.") -> "파일 선택기를 열 수 없습니다. 초안은 보관됩니다."
    else -> message
}
