package com.pleosstay.app.ui.activity

import android.content.Context
import androidx.compose.runtime.*
import androidx.compose.material3.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pleosstay.app.documents.DocumentClientAccess
import com.pleosstay.app.realtime.*
import com.pleosstay.app.workspace.rememberDocumentSession
import com.pleosstay.app.modes.*
import com.pleosstay.app.study.StudyScreen
import com.pleosstay.app.work.WorkScreen
import com.pleosstay.app.work.docx.rememberDocxPicker
import com.pleosstay.app.ui.premium.*

/** Owners and picker registration are outside presentation; sensitive content stays inside the real PIN gate. */
@Composable internal fun rememberPremiumDocuments(context:Context,model:FileTransferViewModel,endpoint:String,
 host:PremiumActivityHost,manager:ModeStartManager,route:StayRoute,foreground:Boolean,
 navigate:(StayRoute)->Unit,onRecoverWaiting:()->Unit):@Composable ()->Unit {
 val study=rememberDocumentSession(context,model,endpoint,manager,host.safety,true)
 val work=rememberDocumentSession(context,model,endpoint,manager,host.safety,false)
 val transfer by model.state.collectAsState()
 val ui by host.ui.state.collectAsState()
 val bound=host.canBind(study,work)
 val lease=foreground && bound && (route.activityMode()?.document()==true || ui.documentLease)
 SideEffect {
  host.bindDocuments(study,work,model)
  val client=DocumentClientAccess(transfer.allowed,transfer.unlocked,lease)
  study.client.value=client;work.client.value=client
  study.study?.setForeground(lease);work.work?.setForeground(lease)
 }
 LaunchedEffect(study,work,lease,transfer.allowed,transfer.unlocked){study.gateway.refreshAccess();work.gateway.refreshAccess()}
 val workModel=checkNotNull(work.work)
 val picker=rememberDocxPicker(workModel)
 DisposableEffect(study,work){onDispose{study.hide();work.hide()}}
 return {
  if(!bound)NoticePanel("진행 중인 문서 활동이 있습니다","기존 연결의 작업을 종료한 뒤 다른 연결을 사용할 수 있습니다.",true)
  else if(ui.preparing) { Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(16.dp)) {
   Text("자료함 연결과 PIN을 확인한 뒤 활동을 시작합니다.")
   if(ui.target==ActivityMode.STUDY && host.coordinator.currentState().active!=ActivityMode.STUDY) {
    var minutes by remember { mutableStateOf(host.studyMinutes.toString()) }
    OutlinedTextField(minutes,{minutes=it.filter(Char::isDigit).take(3);host.setStudyMinutes(minutes.toIntOrNull() ?: 0)},label={Text("학습 시간 · 분")},singleLine=true)
   }
   ui.preparationError?.let{Text(it,color=StayColors.Warning)}
   PremiumAction("연결 확인 후 계속",host::prepareDocuments,enabled=!ui.busy)
   Box(Modifier.fillMaxWidth().height(640.dp)){FileTransferScreen(model)}
  }} else when(route) {
   StayRoute.STUDY->StudyScreen(checkNotNull(study.study),{navigate(StayRoute.FILES)})
   StayRoute.WORK->WorkScreen(workModel,{navigate(StayRoute.FILES)},onRecoverWaiting,host::home,picker)
   else->FileTransferScreen(model)
  }
 }
}
