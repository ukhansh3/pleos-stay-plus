package com.pleosstay.app.work.docx

import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
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

@Composable fun DocxEditorPanel(controller:DocxEditorController,onPicker:(DocxPickerKind,Int?)->Unit,onClose:()->Unit){
 val state by controller.state.collectAsState();val view=state.view ?: return
 var selected by remember(state.draft?.id){mutableIntStateOf(0)}
 val paragraph=view.paragraphs.firstOrNull{it.id==selected} ?: view.paragraphs.firstOrNull()
 var field by remember(state.draft?.id,paragraph?.id){mutableStateOf(TextFieldValue(paragraph?.text ?: ""))}
 var styleDialog by remember{mutableStateOf(false)};var tableDialog by remember{mutableStateOf(false)};var imageDialog by remember{mutableStateOf(false)}
 var font by remember{mutableStateOf("Noto Sans CJK KR")};var size by remember{mutableStateOf("16")}
 var color by remember{mutableStateOf("000000")};var rows by remember{mutableStateOf("2")};var columns by remember{mutableStateOf("2")}
 var width by remember{mutableStateOf("240")};var height by remember{mutableStateOf("160")}
 var exportName by remember(state.draft?.id){mutableStateOf(state.draft?.name ?: "문서.docx")}
 val editable=view.readOnlyReason==null
 LaunchedEffect(paragraph?.text,state.busy){if(!state.busy&&paragraph!=null&&field.text!=paragraph.text)field=field.copy(text=paragraph.text,selection=TextRange(field.selection.start.coerceAtMost(paragraph.text.length),field.selection.end.coerceAtMost(paragraph.text.length)))}
 BackHandler{if(!state.busy)onClose()}
 fun range():Pair<Int,Int>{val length=paragraph?.text?.length ?: 0;return if(field.selection.collapsed)0 to length else field.selection.min.coerceAtMost(length) to field.selection.max.coerceAtMost(length)}
 fun format(s:DocxStyle){paragraph?.let{p->val(a,b)=range();controller.edit{it.format(p.id,a,b,s)}}}
 Column(Modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
  Text(state.draft?.name ?: "DOCX",style=MaterialTheme.typography.headlineSmall)
  Text(state.draft?.source?.let{"Realtime 원본 기준 v${it.versionNo} · 원본은 변경하지 않습니다."} ?: "로컬 DOCX · 현재 인증된 Work 프로필의 암호화 초안")
  Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)){
   Button(onClick=controller::save,enabled=!state.busy){Text("저장")}
   OutlinedButton(onClick={onPicker(DocxPickerKind.EXPORT,null)},enabled=!state.busy){Text("다른 이름으로 저장")}
   if(state.exportUri!=null)OutlinedButton(onClick=controller::reopenExport,enabled=!state.busy){Text("내보낸 DOCX 재열기")}
   OutlinedButton(onClick=controller::undo,enabled=!state.busy&&state.canUndo&&editable){Text("실행 취소")}
   OutlinedButton(onClick=controller::redo,enabled=!state.busy&&state.canRedo&&editable){Text("다시 실행")}
   OutlinedButton(onClick={styleDialog=true},enabled=!state.busy&&editable&&paragraph?.editable==true){Text("글꼴·서식")}
   OutlinedButton(onClick={tableDialog=true},enabled=!state.busy&&editable){Text("표")}
   OutlinedButton(onClick={imageDialog=true},enabled=!state.busy&&editable){Text("이미지")}
   TextButton(onClick=onClose,enabled=!state.busy){Text("문서 목록")}
  }
  view.readOnlyReason?.let{Text("읽기 전용: $it",color=MaterialTheme.colorScheme.error)}
  Row(Modifier.weight(1f).fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)){
   LazyColumn(Modifier.widthIn(min=140.dp,max=230.dp).fillMaxHeight()){
    item{Button(onClick={controller.edit{it.addParagraph(paragraph?.id)}},enabled=!state.busy&&editable){Text("문단 추가")}}
    items(view.paragraphs,key={it.id}){p->TextButton(onClick={selected=p.id;field=TextFieldValue(p.text)},enabled=!state.busy){Text("${p.id+1}. ${p.text.ifBlank{"(빈 문단 또는 이미지)"}.take(50)}")}}
   }
   Surface(Modifier.weight(1f).fillMaxHeight(),color=Color.White,contentColor=Color(0xFF182030)){
    Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)){
     if(paragraph!=null){
      Text("문단 ${paragraph.id+1} · 커서/텍스트 선택 후 서식 적용",color=Color.DarkGray)
      OutlinedTextField(field,{next->field=next;if(next.text!=paragraph.text)controller.setText(paragraph.id,next.text)},
       modifier=Modifier.fillMaxWidth().heightIn(min=180.dp).testTag("docx-body"),enabled=editable&&paragraph.editable,
       textStyle=TextStyle(color=Color.Black,fontSize=18.sp,textAlign=when(paragraph.alignment){"center"->TextAlign.Center;"right"->TextAlign.Right;else->TextAlign.Left}),
       visualTransformation=VisualTransformation{input->val rich=if(input.text==paragraph.text)rich(paragraph.runs)else input;TransformedText(rich,OffsetMapping.Identity)})
      if(!paragraph.editable)Text("개체·링크·책갈피를 포함한 이 문단은 텍스트 수정이 제한됩니다. 원본 구조는 보존됩니다.",color=Color.DarkGray)
      Text("선택 범위가 없으면 서식은 현재 문단 전체에 적용됩니다.",color=Color.DarkGray)
      OutlinedButton(onClick={controller.edit{it.deleteParagraph(paragraph.id)}},enabled=!state.busy&&editable){Text("선택 문단 삭제")}
     }
     view.tables.forEach{table->
      Text("표 ${table.id+1} · 셀 선택 후 위 입력란에서 편집",color=Color.Black)
      Column(Modifier.horizontalScroll(rememberScrollState())){table.rows.forEach{row->Row{row.forEach{cell->
       val cellP=view.paragraphs.firstOrNull{it.id==cell.firstOrNull()};OutlinedButton(onClick={cellP?.let{selected=it.id;field=TextFieldValue(it.text)}},enabled=!state.busy&&cellP!=null,modifier=Modifier.width(180.dp)){Text(cell.joinToString("\n"){id->view.paragraphs.firstOrNull{it.id==id}?.text ?: ""}.ifBlank{"(셀)"},color=Color.Black)}
      }}}}
      val selectedCell=table.rows.flatMapIndexed{r,row->row.mapIndexedNotNull{c,ids->if(selected in ids)r to c else null}}.firstOrNull()
      Row{TextButton(onClick={controller.edit{it.addRow(table.id)}},enabled=!state.busy&&editable){Text("행 추가")};TextButton(onClick={controller.edit{it.addColumn(table.id)}},enabled=!state.busy&&editable){Text("열 추가")};TextButton(onClick={selectedCell?.let{(r,c)->controller.edit{it.shadeCell(table.id,r,c,"DDEEFF")}}},enabled=!state.busy&&editable&&selectedCell!=null){Text("선택 셀 배경 · 연파랑")}}
     }
     view.images.forEach{image->
      val bitmap=remember(image.bytes){val b=BitmapFactory.Options().apply{inJustDecodeBounds=true};BitmapFactory.decodeByteArray(image.bytes,0,image.bytes.size,b);var sample=1;while(b.outWidth.toLong()/sample*(b.outHeight/sample)>2000000)sample*=2;BitmapFactory.decodeByteArray(image.bytes,0,image.bytes.size,BitmapFactory.Options().apply{inSampleSize=sample})}
      bitmap?.let{Image(it.asImageBitmap(),"문서 이미지 ${image.id+1}",Modifier.fillMaxWidth().heightIn(max=240.dp))}
      Text("이미지 ${image.id+1}: ${image.width} × ${image.height}",color=Color.Black)
      Row{TextButton(onClick={onPicker(DocxPickerKind.REPLACE_IMAGE,image.id)},enabled=!state.busy&&editable){Text("이미지 교체")};TextButton(onClick={controller.edit{it.resizeImage(image.id,width.toIntOrNull() ?: 240,height.toIntOrNull() ?: 160)}},enabled=!state.busy&&editable){Text("설정한 크기 적용")}}
     }
    }
   }
  }
  if(state.busy)LinearProgressIndicator(Modifier.fillMaxWidth())
  Text(if(state.busy)"변경 저장 중…" else state.message,modifier=Modifier.testTag("docx-status"))
  Text("재배치 편집 · Word의 고정 페이지/고급 배치는 재현하지 않습니다. PDF 편집·Realtime 업로드는 제외됩니다.",style=MaterialTheme.typography.bodySmall)
 }
 if(styleDialog)AlertDialog(onDismissRequest={styleDialog=false},title={Text("글꼴·문자·문단 서식")},text={Column(Modifier.verticalScroll(rememberScrollState())){
  OutlinedTextField(font,{font=it.take(100)},label={Text("글꼴 이름 (기기 미설치 시 대체 표시)")})
  OutlinedTextField(size,{size=it.take(4)},label={Text("글꼴 크기 8~72 pt")})
  OutlinedTextField(color,{color=it.take(6)},label={Text("글자 색상 RGB 6자리")})
  Row{TextButton(onClick={format(DocxStyle(font=font,size=size.toFloatOrNull() ?: 16f,color=color));styleDialog=false}){Text("적용")}}
  val(a,b)=range();var offset=0
  val selectedRuns=paragraph?.runs?.filter{r->val from=offset;offset+=r.text.length;(offset>a&&from<b)||(a==b&&r.text.isEmpty())}.orEmpty()
  Row{TextButton(onClick={format(DocxStyle(bold=selectedRuns.isEmpty()||!selectedRuns.all{it.bold}));styleDialog=false}){Text("굵게")};TextButton(onClick={format(DocxStyle(italic=selectedRuns.isEmpty()||!selectedRuns.all{it.italic}));styleDialog=false}){Text("기울임")};TextButton(onClick={format(DocxStyle(underline=selectedRuns.isEmpty()||!selectedRuns.all{it.underline}));styleDialog=false}){Text("밑줄")}}
  Row{listOf("left" to "왼쪽","center" to "가운데","right" to "오른쪽").forEach{(v,label)->TextButton(onClick={paragraph?.let{p->controller.edit{it.align(p.id,v)}};styleDialog=false}){Text(label)}}}
 }},confirmButton={TextButton(onClick={styleDialog=false}){Text("닫기")}})
 if(tableDialog)AlertDialog(onDismissRequest={tableDialog=false},title={Text("표 삽입")},text={Column{OutlinedTextField(rows,{rows=it.take(2)},label={Text("행 1~20")});OutlinedTextField(columns,{columns=it.take(2)},label={Text("열 1~10")})}},confirmButton={TextButton(onClick={controller.edit{it.addTable(rows.toIntOrNull() ?: 2,columns.toIntOrNull() ?: 2)};tableDialog=false}){Text("삽입")}},dismissButton={TextButton(onClick={tableDialog=false}){Text("취소")}})
 if(imageDialog)AlertDialog(onDismissRequest={imageDialog=false},title={Text("이미지 PNG/JPEG · 최대 4MiB")},text={Column{OutlinedTextField(width,{width=it.take(4)},label={Text("너비 16~1600")});OutlinedTextField(height,{height=it.take(4)},label={Text("높이 16~1600")});Text("크기는 이미지 아래 크기 적용 버튼으로 변경합니다.")}},confirmButton={TextButton(onClick={onPicker(DocxPickerKind.INSERT_IMAGE,null);imageDialog=false}){Text("이미지 선택·삽입")}},dismissButton={TextButton(onClick={imageDialog=false}){Text("닫기")}})
 if(state.confirmDownloads)AlertDialog(onDismissRequest=controller::dismissDownloads,title={Text("다운로드 폴더에 DOCX 내보내기")},text={Column{
  Text("이 차량의 시스템 파일 선택기는 미지원입니다. Download/StayPlus에 새 파일을 만듭니다. 앱 밖에서도 접근할 수 있으며 잠금·앱 삭제 후에도 남습니다. 원본 파일은 덮어쓰지 않습니다.")
  OutlinedTextField(exportName,{exportName=it.take(80)},label={Text("DOCX 파일 이름")})
 }},confirmButton={TextButton(onClick={controller.exportDownloads(exportName)},enabled=!state.busy&&exportName.isNotBlank()){Text("공개 저장 위치 확인·내보내기")}},dismissButton={Row{TextButton(onClick={controller.dismissDownloads();controller.exportPrivate()},enabled=!state.busy){Text("앱 전용 임시 파일")};TextButton(onClick=controller::dismissDownloads){Text("취소")}}})
}
private fun rich(runs:List<DocxRun>)=buildAnnotatedString{runs.forEach{r->withStyle(SpanStyle(color=r.color?.takeIf{it.matches(Regex("[A-Fa-f0-9]{6}"))}?.let{Color(android.graphics.Color.parseColor("#$it"))} ?: Color.Black,fontSize=(r.size ?: 18f).coerceIn(8f,72f).sp,fontWeight=if(r.bold)FontWeight.Bold else FontWeight.Normal,fontStyle=if(r.italic)FontStyle.Italic else FontStyle.Normal,textDecoration=if(r.underline)TextDecoration.Underline else TextDecoration.None,fontFamily=if(r.font?.contains("Serif",true)==true)FontFamily.Serif else FontFamily.SansSerif)){append(r.text)}}}
