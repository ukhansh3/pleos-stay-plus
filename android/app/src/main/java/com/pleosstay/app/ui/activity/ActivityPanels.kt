package com.pleosstay.app.ui.activity

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.pleosstay.app.modes.*
import com.pleosstay.app.ui.premium.*

@Composable internal fun ActivityModal(title:String,busy:Boolean=false,onBack:()->Unit,content:@Composable ColumnScope.()->Unit) {
 Dialog(onDismissRequest={if(!busy)onBack()},properties=DialogProperties(usePlatformDefaultWidth=false,dismissOnClickOutside=false)) {
  FeaturePanel(Modifier.widthIn(max=760.dp).fillMaxWidth(.94f).fillMaxHeight(.9f).verticalScroll(rememberScrollState()).testTag("activity_modal")) {
   Text(title,fontSize=28.sp,color=StayColors.Text)
   if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
   content()
  }
 }
}
@Composable internal fun ActivityRecoveryPanel(message:String,busy:Boolean,retry:()->Unit,back:()->Unit) {
 FeaturePanel(Modifier.fillMaxWidth().testTag("activity_recovery")) {
  Text("저장을 완료하지 못했습니다",fontSize=25.sp,color=StayColors.Warning)
  Text(message,color=StayColors.Secondary)
  PremiumAction("저장 재시도",retry,enabled=!busy)
  PremiumAction("작업으로 돌아가기",back,enabled=!busy,secondary=true)
 }
}
@Composable internal fun ActivitySwitchPanel(source:ActivityMode,target:ActivityMode,busy:Boolean,cancel:()->Unit,confirm:()->Unit) {
 Text("현재 ${source.label()} 활동이 진행 중입니다. ${target.label()}로 전환하면 기존 활동이 종료되며, Waiting은 계속 유지됩니다.",color=StayColors.Secondary)
 PremiumAction("취소",cancel,enabled=!busy,secondary=true)
 PremiumAction("종료 후 전환",confirm,enabled=!busy)
}
@Composable internal fun ActivityPrompts(host:PremiumActivityHost,visible:Boolean,onRecoverWaiting:()->Unit) {
 val state by host.ui.state.collectAsState()
 val rest by host.rest.state.collectAsState()
 val study=host.studyOwner?.study?.state?.collectAsState()?.value
 val work=host.workOwner?.work?.state?.collectAsState()?.value
 if(!visible || state.preparing)return
 if(state.confirmation!=null)ActivityModal("${state.source?.label()}을 종료하시겠습니까?",state.busy,{host.ui.confirmSwitch(false)}) {
  ActivitySwitchPanel(checkNotNull(state.source),checkNotNull(state.target),state.busy,{host.ui.confirmSwitch(false)},{host.ui.confirmSwitch(true)})
 }
 else if(state.error!=null)ActivityModal("활동을 처리하지 못했습니다",state.busy,host.ui::dismissError) {
  Text(state.error.orEmpty(),color=StayColors.Warning)
  PremiumAction(state.retryLabel,host.ui::retry,enabled=!state.busy)
  PremiumAction("작업으로 돌아가기",host.ui::returnToWork,enabled=!state.busy,secondary=true)
 }
 else if(state.leavePrompt!=null)ActivityModal("초안을 보관하고 이동할까요?",state.busy,{host.ui.confirmLeaving(false)}) {
  Text("암호화 초안을 보관한 뒤 이동합니다. Work 타이머와 Waiting은 계속 유지됩니다.",color=StayColors.Secondary)
  PremiumAction("초안 보관 후 이동",{host.ui.confirmLeaving(true)},enabled=!state.busy)
  PremiumAction("작업으로 돌아가기",{host.ui.confirmLeaving(false)},enabled=!state.busy,secondary=true)
 }
 else if(state.endPrompt!=null)ActivityModal("${state.endPrompt?.label()} 종료",state.busy,{host.ui.confirmFinish(false)}) {
  Text("작성한 내용을 안전하게 보관한 뒤 종료합니다. Waiting은 계속 유지됩니다.",color=StayColors.Secondary)
  PremiumAction("저장 후 종료",{host.ui.confirmFinish(true)},enabled=!state.busy)
  PremiumAction("작업으로 돌아가기",{host.ui.confirmFinish(false)},enabled=!state.busy,secondary=true)
 }
 else if(state.input)ActivityModal("활동 시작 확인",state.busy,{host.ui.choose(WaitingChoice.BACK)}) {
  when {
   state.target==ActivityMode.REST && rest.alarmConsent!=null->{
    Text("앱을 화면에 유지하는 동안에만 종료 알람이 울립니다. 다른 앱으로 이동하거나 화면이 꺼지면 알람이 울리지 않을 수 있습니다.")
    PremiumAction("앱 실행 중 알람으로 시작",host.ui::consent,enabled=!state.busy)
   }
   state.target==ActivityMode.REST && rest.fallback!=null->{
    Text("대기 상태와 종료 예정 시각을 관리할 수 없습니다. 대기 기록을 복구하거나 독립 휴식을 선택해 주세요.")
    PremiumAction("대기 기록 복구",onRecoverWaiting,enabled=!state.busy,secondary=true)
    PremiumAction("독립 휴식 시작",host.ui::consent,enabled=!state.busy)
   }
   else->{
    val prompt=when(state.target){ActivityMode.REST->rest.decision?.prompt;ActivityMode.STUDY->study?.prompt;ActivityMode.WORK->work?.choice;else->null}
    Text("활동 시간이 남은 대기 시간보다 깁니다. 대기를 연장하거나 남은 시간만 사용할 수 있습니다.")
    PremiumAction("대기 연장",{host.ui.choose(WaitingChoice.EXTEND)},enabled=!state.busy && prompt!=null && prompt.extensionUnavailable==null)
    PremiumAction("남은 시간만",{host.ui.choose(WaitingChoice.SHORTEN)},enabled=!state.busy && prompt!=null && prompt.shortenUnavailable==null,secondary=true)
   }
  }
  PremiumAction("취소",{host.ui.choose(WaitingChoice.BACK)},enabled=!state.busy,secondary=true)
 }
}
