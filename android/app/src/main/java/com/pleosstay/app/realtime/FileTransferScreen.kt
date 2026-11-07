package com.pleosstay.app.realtime
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pleosstay.app.ui.premium.*

@OptIn(ExperimentalLayoutApi::class)
@Composable fun FileTransferScreen(model: FileTransferViewModel) {
 var confirmSwitch by remember { mutableStateOf(false) }
 var selectedVersion by remember { mutableStateOf<org.json.JSONObject?>(null) }
 val picker=androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()){uri -> val version=selectedVersion; if(uri!=null && version!=null)model.uploadFakeWork(uri,version)}
 val state by model.state.collectAsState()
 var code by remember { mutableStateOf("") }
 if(!state.allowed || !state.unlocked) {
  NoticePanel("파일 화면 잠김", "정차 확인 및 사용자 인증이 필요합니다.", true)
  return
 }
 if(confirmSwitch) AlertDialog(onDismissRequest={confirmSwitch=false},title={Text("이전 사용자 연결 지우기")},text={Text("현재 PC 연결을 해제하고 차량의 파일·인증정보·PIN을 지웁니다. 다음 사용자는 다시 페어링해야 합니다.")},confirmButton={TextButton(onClick={confirmSwitch=false;model.switchUserAfterConfirmation()}){Text("지우고 사용자 전환")}},dismissButton={TextButton(onClick={confirmSwitch=false}){Text("취소")}})
 Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=28.dp,vertical=16.dp), verticalArrangement=Arrangement.spacedBy(22.dp)) {
  FeatureHeader("파일 작업", "차량과 PC 사이, 필요한 문서를 안전하게")
  GlassPanel(Modifier.fillMaxWidth()) {
   Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.SpaceBetween) {
    Text(if(state.connectionId!=null) "PC 연결 정보" else "PC 연결 준비",fontSize=28.sp,fontWeight=FontWeight.Bold)
    Text("수동 파일 전송 · TXT / PDF / DOCX",color=StayColors.Blue,fontSize=20.sp)
   }
   Text(state.message,fontSize=20.sp,color=StayColors.Secondary)
   FlowRow(horizontalArrangement=Arrangement.spacedBy(12.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
    PremiumAction("기기 등록 / 연결 복원",model::register,enabled=!state.busy,secondary=true)
    PremiumAction("페어링 코드 발급",model::issue,enabled=!state.busy,secondary=true)
    PremiumAction("목록 새로고침",model::refresh,enabled=!state.busy,secondary=true)
   }
   if(state.code.isNotBlank()) Text("발급 코드 · ${state.code}",fontSize=26.sp,color=StayColors.Blue)
   OutlinedTextField(code,{code=it},label={Text("상대 기기 코드")},modifier=Modifier.fillMaxWidth(),singleLine=true)
   FlowRow(horizontalArrangement=Arrangement.spacedBy(12.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
    PremiumAction("코드 입력",{model.claim(code)},enabled=!state.busy)
    PremiumAction("상대 확인",model::inspectPairing,enabled=!state.busy,secondary=true)
    PremiumAction("상대 승인",model::approve,enabled=!state.busy,secondary=true)
   }
   Text("서로 확인하고 승인한 연결만 파일을 전송할 수 있습니다. 자동 동기화·원격 데스크톱은 제공하지 않습니다.",color=StayColors.Secondary,fontSize=18.sp)
  }
  ResponsivePanels(leftWeight=1.15f,left={
   GlassPanel(Modifier.fillMaxWidth()) {
    Text("문서 목록",fontSize=28.sp,fontWeight=FontWeight.Bold)
    if(state.files.isEmpty()) Text("표시할 파일이 없습니다. PC 연결 후 목록을 새로고침해 주세요.",color=StayColors.Secondary,fontSize=20.sp)
    state.files.forEach { f ->
     Column(Modifier.fillMaxWidth().clickable(enabled=!state.busy){model.versions(f.getString("id"))}.padding(vertical=16.dp)) {
      Text(f.getString("name"),fontSize=24.sp,fontWeight=FontWeight.SemiBold)
      Text("버전 확인 ›",color=StayColors.Blue,fontSize=19.sp)
     }
     HorizontalDivider(color=StayColors.Border)
    }
   }
  },right={
   Column(verticalArrangement=Arrangement.spacedBy(22.dp)) {
    GlassPanel(Modifier.fillMaxWidth()) {
     Text("선택한 문서의 버전",fontSize=27.sp,fontWeight=FontWeight.Bold)
     if(state.versions.isEmpty()) Text("목록에서 문서를 선택해 주세요.",color=StayColors.Secondary,fontSize=20.sp)
     state.versions.forEach { v ->
      Text("v${v.getInt("versionNo")} · ${v.getString("fileName")}",fontSize=22.sp)
      Text("${v.getLong("size")} bytes",color=StayColors.Secondary)
      PremiumAction("이 버전 다운로드",{model.download(v)},Modifier.fillMaxWidth(),!state.busy)
      if(com.pleosstay.app.BuildConfig.DEBUG) TextButton(enabled=!state.busy,onClick={selectedVersion=v;picker.launch(arrayOf("text/plain","application/pdf","application/vnd.openxmlformats-officedocument.wordprocessingml.document"))}) {
       Text("개발 전용 Fake Work · 수정본 선택")
      }
     }
    }
    GlassPanel(Modifier.fillMaxWidth()) {
     Text("전송 상태",fontSize=27.sp,fontWeight=FontWeight.Bold)
     LinearProgressIndicator(progress={state.progress.coerceIn(0f,1f)},modifier=Modifier.fillMaxWidth())
     Text("진행률 ${(state.progress*100).toInt()}%",fontSize=24.sp,color=StayColors.Blue)
     PremiumAction("중단된 수정본 재시도",model::retryUpload,Modifier.fillMaxWidth(),!state.busy,true)
     PremiumAction("전송 취소",model::cancel,Modifier.fillMaxWidth(),secondary=true)
    }
   }
  })
  GlassPanel(Modifier.fillMaxWidth()) {
   Text("연결 및 보안",fontSize=26.sp,fontWeight=FontWeight.Bold)
   FlowRow(horizontalArrangement=Arrangement.spacedBy(12.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
    PremiumAction("연결 해제",model::disconnect,enabled=!state.busy,secondary=true)
    PremiumAction("로컬 파일 지우고 잠금",model::clearLocalFiles,enabled=!state.busy,secondary=true)
    PremiumAction("사용자 전환",{confirmSwitch=true},enabled=!state.busy,secondary=true)
    PremiumAction("화면 잠금",model::lock,secondary=true)
   }
  }
 }
}
