package com.pleosstay.app.ui.activity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pleosstay.app.modes.*
import com.pleosstay.app.rest.*
import com.pleosstay.app.waiting.safety.WaitingSafetyGateway
import com.pleosstay.app.workspace.DocumentSessionOwner
import com.pleosstay.app.realtime.FileTransferViewModel
import com.pleosstay.app.ui.premium.StayRoute
import kotlinx.coroutines.*

/** A missing binding denies entry; an active owner can never be silently replaced. */
internal class BoundActivityAdapter(override val mode:ActivityMode):ActivityAdapter {
    var delegate:ActivityAdapter?=null
        private set
    fun bind(next:ActivityAdapter):Boolean {
        require(next.mode==mode)
        if(delegate===next)return true
        if(delegate?.sessionId()!=null)return false
        delegate=next;return true
    }
    override fun sessionId()=delegate?.sessionId()
    override fun restored()=delegate?.restored() ?: true
    override suspend fun preflight()=delegate?.preflight() ?: ActivityOperation(false,"자료함 연결과 PIN을 확인해 주세요.")
    override suspend fun start()=delegate?.start() ?: ActivityOperation(false,"활동을 먼저 준비해 주세요.")
    override suspend fun resume()=delegate?.resume() ?: ActivityOperation(false,"활동을 먼저 준비해 주세요.")
    override suspend fun stop()=delegate?.stop() ?: ActivityOperation(false,"활동 상태를 확인할 수 없습니다.")
}

internal class PremiumActivityHost(val rest:RestController,
    val safety:WaitingSafetyGateway):ViewModel(),ActivityUiActions {
    private val studyBinding=BoundActivityAdapter(ActivityMode.STUDY)
    private val workBinding=BoundActivityAdapter(ActivityMode.WORK)
    var studyOwner:DocumentSessionOwner?=null;private set
    var workOwner:DocumentSessionOwner?=null;private set
    private var transfer:FileTransferViewModel?=null
    private var selectedPreset=RestPreset.defaults.first()
    var studyMinutes=30;private set
    private val choices=mutableMapOf<ActivityMode,WaitingChoice>()
    private var consent=false
    private var independent=false
    private var foreground=false
    var route=StayRoute.HOME;private set
    val coordinator=ActivityTransitionCoordinator(listOf(
        RestActivityAdapter(rest,{selectedPreset},{choices.remove(ActivityMode.REST)},{consent.also{consent=false}},{independent.also{independent=false}}),
        studyBinding,workBinding))
    val ui=ActivityUiController(coordinator,viewModelScope,
        documentsReady={documentsReady()},beforeStart={
            if(!foreground)ActivityOperation(false,"Stay+ 화면으로 돌아온 뒤 다시 선택해 주세요.")
            else ActivityOperation.Done},
        saveForNavigation={mode->guarded { when(mode){
            ActivityMode.STUDY->studyOwner?.study?.retainForNavigation() ?: ActivityOperation.Done
            ActivityMode.WORK->workOwner?.work?.docx?.flushForTransition() ?: ActivityOperation.Done
            else->ActivityOperation.Done }}},
        hide={mode->when(mode){ActivityMode.STUDY->studyOwner?.hide();ActivityMode.WORK->workOwner?.hide();else->Unit}},
        repair={mode->guarded { when(mode){
            ActivityMode.WORK->workOwner?.work?.docx?.retryRetention() ?: ActivityOperation(false,"Work를 다시 열어 주세요.")
            ActivityMode.STUDY->studyOwner?.study?.retainForNavigation() ?: ActivityOperation(false,"Study를 다시 열어 주세요.")
            else->ActivityOperation.Done }}},
        confirmLeave={workOwner?.work?.docx?.state?.value?.let{it.dirty || it.busy}==true},
        confirmEnd={mode->when(mode){ActivityMode.WORK->workOwner?.work?.docx?.state?.value?.let{it.dirty || it.busy}==true;ActivityMode.STUDY->studyOwner?.study?.state?.value?.noteText?.isNotBlank()==true;else->false}},
        setChoice={mode,choice->choices[mode]=choice},
        setConsent={mode->if(mode==ActivityMode.REST){if(rest.state.value.fallback!=null)independent=true else consent=true}},
        cancelInput={mode->when(mode){ActivityMode.REST->{rest.dismissAlarmConsent();rest.dismissFallback();if(rest.state.value.decision!=null)rest.resolve(WaitingChoice.BACK)};ActivityMode.STUDY->studyOwner?.study?.choose(WaitingChoice.BACK);ActivityMode.WORK->workOwner?.work?.resolve(WaitingChoice.BACK)}})
    init {
        if(!rest.state.value.ready)rest.restore()
        viewModelScope.launch { while(isActive){ui.refresh();delay(1000)} }
    }
    private suspend fun guarded(block:suspend()->ActivityOperation)=try{block()}catch(c:CancellationException){throw c}catch(_:Exception){ActivityOperation(false,"저장·정리를 완료하지 못했습니다. 현재 작업은 유지됩니다.")}
    private fun documentsReady()=transfer?.state?.value?.let{it.allowed&&it.unlocked}==true && studyOwner?.gateway?.access?.value?.allowed==true && workOwner?.gateway?.access?.value?.allowed==true
    fun canBind(study:DocumentSessionOwner,work:DocumentSessionOwner):Boolean =
        (studyOwner===study || studyBinding.sessionId()==null) && (workOwner===work || workBinding.sessionId()==null)
    fun bindDocuments(study:DocumentSessionOwner,work:DocumentSessionOwner,model:FileTransferViewModel):Boolean {
        if(!canBind(study,work))return false
        if(studyOwner!==study){studyOwner?.hide();check(studyBinding.bind(StudyActivityAdapter(checkNotNull(study.study),{studyMinutes},{choices.remove(ActivityMode.STUDY)})));studyOwner=study}
        if(workOwner!==work){workOwner?.hide();check(workBinding.bind(WorkActivityAdapter(checkNotNull(work.work),{choices.remove(ActivityMode.WORK)})));workOwner=work}
        transfer=model;return true
    }
    fun routeChanged(value:StayRoute){route=value}
    fun setStudyMinutes(value:Int){studyMinutes=value}
    fun setForeground(value:Boolean) {
        foreground=value
        if(!value){studyOwner?.hide();workOwner?.hide();transfer?.lock()}
    }
    fun prepareDocuments() {
        if(!ui.beginPreparation())return
        viewModelScope.launch {
            val result=guarded {
                check(foreground && safety.state.value.canUse)
                check(studyOwner?.gateway?.refreshAccess()?.allowed==true)
                check(workOwner?.gateway?.refreshAccess()?.allowed==true)
                ActivityOperation.Done
            }
            if(result.success)ui.prepared() else ui.preparationFailed("자료함 등록·PC 연결·PIN을 확인한 뒤 다시 시도해 주세요.")
        }
    }
    override fun startRest(preset:RestPreset){selectedPreset=preset;ui.open(ActivityMode.REST)}
    override fun startStudy(minutes:Int){studyMinutes=minutes;ui.open(ActivityMode.STUDY)}
    override fun startWork(){ui.open(ActivityMode.WORK)}
    fun open(mode:ActivityMode){ui.open(mode)}
    override fun finish(mode:ActivityMode){ui.finish(mode)}
    override fun home(){ui.leave(route,StayRoute.HOME)}
    override fun choose(mode:ActivityMode,choice:WaitingChoice) {
        if(coordinator.currentState().active==mode && !ui.state.value.input){
            when(mode){ActivityMode.REST->rest.resolve(choice);ActivityMode.WORK->workOwner?.work?.resolve(choice);else->Unit}
        } else ui.choose(choice)
    }
    override fun consentRest(){ui.consent()}
    override fun independentRest(){independent=true;ui.consent()}
    override fun retry(mode:ActivityMode){ui.repairOnly(mode)}
    override fun onCleared(){studyOwner?.hide();workOwner?.hide();transfer?.lock();super.onCleared()}
}
