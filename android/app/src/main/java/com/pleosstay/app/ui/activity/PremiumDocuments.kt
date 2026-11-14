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
 LaunchedEffect(study,work,lease,transfer.allowed,transfer.unlocked,transfer.connectionId,ui.preparing,ui.target) {
  if(lease && transfer.allowed && transfer.unlocked && transfer.connectionId!=null && ui.preparing) {
   host.prepareDocuments()
  } else { study.gateway.refreshAccess();work.gateway.refreshAccess() }
 }
 val workModel=checkNotNull(work.work)
 val picker=rememberDocxPicker(workModel)
 val studyModel=checkNotNull(study.study)
 val studyState by studyModel.state.collectAsState()
 val workState by workModel.state.collectAsState()
 LaunchedEffect(route,foreground,ui.preparing,studyState.allowed,studyState.active,workState.allowed,workState.session?.id) {
  if(foreground && !ui.preparing) when(route) {
   StayRoute.STUDY->if(studyState.allowed && studyState.active)studyModel.refreshFiles()
   StayRoute.WORK->if(workState.allowed && workState.session!=null)workModel.refresh()
   else->Unit
  }
 }
 DisposableEffect(study,work){onDispose{study.hide();work.hide()}}
 return {
  if(!bound)NoticePanel("진행 중인 문서 활동이 있습니다","기존 연결의 작업을 종료한 뒤 다른 연결을 사용할 수 있습니다.",true)
  else if(ui.preparing) {
   if(transfer.connectionId==null)FileTransferScreen(model)
   else Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(16.dp)) {
    Text(if(ui.preparationError==null) "문서를 불러오고 있어요." else "자료함 연결을 확인해 주세요.")
   }
  } else when(route) {
   StayRoute.STUDY->StudyScreen(checkNotNull(study.study),{navigate(StayRoute.FILES)})
   StayRoute.WORK->WorkScreen(workModel,{navigate(StayRoute.FILES)},onRecoverWaiting,host::home,picker)
   else->FileTransferScreen(model)
  }
 }
}
