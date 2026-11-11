package com.pleosstay.app.ui.activity

import com.pleosstay.app.modes.*
import com.pleosstay.app.ui.premium.StayRoute
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

internal fun ActivityMode.route()=when(this) {
    ActivityMode.REST->StayRoute.REST;ActivityMode.STUDY->StayRoute.STUDY
    ActivityMode.WORK->StayRoute.WORK
}
internal fun StayRoute.activityMode():ActivityMode?=when(this) {
    StayRoute.REST->ActivityMode.REST;StayRoute.STUDY->ActivityMode.STUDY
    StayRoute.WORK->ActivityMode.WORK
    else->null
}
internal fun ActivityMode.label()=when(this){ActivityMode.REST->"휴식";ActivityMode.STUDY->"공부";ActivityMode.WORK->"업무"}
internal fun ActivityMode.document()=this==ActivityMode.STUDY || this==ActivityMode.WORK

internal data class ActivityUiState(
    val active:ActivityMode?=null,val busy:Boolean=false,val target:ActivityMode?=null,
    val confirmation:String?=null,val source:ActivityMode?=null,val input:Boolean=false,
    val preparing:Boolean=false,val preparationError:String?=null,
    val leavePrompt:StayRoute?=null,val endPrompt:ActivityMode?=null,
    val error:String?=null,val recovery:ActivityMode?=null,val destination:StayRoute?=null,val retryLabel:String="다시 시도",
) {
    val modal get()=confirmation!=null || input || preparing || leavePrompt!=null || endPrompt!=null || error!=null
    val documentLease get()=preparing || target?.document()==true || (modal && source?.document()==true) || destination?.activityMode()?.document()==true
}

/** UI orchestration only. All activity admission and transition decisions stay in the existing coordinator. */
internal class ActivityUiController(
    private val coordinator:ActivityTransitionCoordinator,private val scope:CoroutineScope,
    private val documentsReady:()->Boolean,
    private val beforeStart:suspend(ActivityMode)->ActivityOperation,
    private val saveForNavigation:suspend(ActivityMode)->ActivityOperation,
    private val hide:suspend(ActivityMode)->Unit,
    private val repair:suspend(ActivityMode)->ActivityOperation,
    private val confirmLeave:()->Boolean,
    private val confirmEnd:(ActivityMode)->Boolean,
    private val setChoice:(ActivityMode,WaitingChoice)->Unit,
    private val setConsent:(ActivityMode)->Unit,
    private val cancelInput:(ActivityMode)->Unit,
) {
    private val mutable=MutableStateFlow(ActivityUiState())
    val state=mutable.asStateFlow()
    private var permit:StayRoute?=null
    private sealed interface Retry {
        data class Switch(val target:ActivityMode,val cleanup:Boolean):Retry
        data class End(val source:ActivityMode):Retry
        data class Leave(val route:StayRoute,val source:ActivityMode):Retry
        data class Repair(val mode:ActivityMode):Retry
    }
    private var retry:Retry?=null
    private var retryAfterPreparation=false
    fun refresh(){mutable.update{it.copy(active=coordinator.currentState().active)}}
    fun takeNavigationPermit(route:StayRoute):Boolean {
        if(permit!=route)return false
        permit=null;mutable.update{it.copy(destination=null)};return true
    }
    fun handledNavigation(){permit=null;mutable.update{it.copy(destination=null)}}
    private fun navigate(route:StayRoute) { permit=route;mutable.value=ActivityUiState(active=coordinator.currentState().active,destination=route) }
    private fun needsDocuments(target:ActivityMode)= (target.document() || coordinator.currentState().active?.document()==true) && !documentsReady()
    private fun run(block:suspend()->Unit) {
        if(state.value.busy)return
        mutable.update{it.copy(busy=true)}
        scope.launch {
            try { block() }
            catch(c:CancellationException){throw c}
            catch(_:Exception){
                val intent=retry
                if(intent!=null)fail("활동을 처리하지 못했습니다. 현재 작업을 확인해 주세요.",intent)
                else mutable.update{it.copy(error="활동을 처리하지 못했습니다. 현재 작업을 확인해 주세요.",preparing=false,input=false,confirmation=null)}
            }
            finally { mutable.update{it.copy(busy=false,active=coordinator.currentState().active)} }
        }
    }
    fun open(target:ActivityMode) {
        if(state.value.busy || state.value.modal)return
        mutable.value=ActivityUiState(active=coordinator.currentState().active,target=target,source=coordinator.currentState().active)
        if(needsDocuments(target)) mutable.update{it.copy(preparing=true)} else submit(target)
    }
    fun beginPreparation():Boolean {
        if(!state.value.preparing || state.value.busy)return false
        mutable.update{it.copy(busy=true,preparationError=null)};return true
    }
    fun preparationFailed(message:String){mutable.update{it.copy(busy=false,preparationError=message)}}
    fun prepared() {
        if(!documentsReady()){preparationFailed("자료함 연결과 PIN을 확인해 주세요.");return}
        val target=state.value.target ?: state.value.source ?: return
        mutable.update{it.copy(preparing=false,busy=false,preparationError=null)}
        if(retryAfterPreparation){retryAfterPreparation=false;retry()}else submit(target)
    }
    private fun submit(target:ActivityMode)=run {
        retry=Retry.Switch(target,false)
        val ready=beforeStart(target)
        if(!ready.success){fail(ready.message,Retry.Switch(target,false));return@run}
        handle(coordinator.request(target),target)
    }
    private fun handle(result:ActivityTransitionResult,target:ActivityMode) {
        when(result.status) {
            TransitionStatus.STARTED,TransitionStatus.RESUMED->{retry=null;navigate(target.route())}
            TransitionStatus.CONFIRMATION_REQUIRED->mutable.update{it.copy(confirmation=result.state.confirmation,source=result.state.active,input=false,error=null)}
            TransitionStatus.NEEDS_INPUT->mutable.update{it.copy(input=true,confirmation=null,error=null)}
            TransitionStatus.CANCELLED->{retry=null;mutable.value=ActivityUiState(active=coordinator.currentState().active)}
            TransitionStatus.BUSY->Unit
            else->fail(result.message.ifBlank{if(result.status==TransitionStatus.STALE)"전환을 다시 확인해야 해요. 시작할 활동을 다시 선택해 주세요." else "활동을 시작하지 못했습니다. 현재 작업과 권한을 확인해 주세요."},Retry.Switch(target,result.status==TransitionStatus.STOP_FAILED))
        }
    }
    fun confirmSwitch(accepted:Boolean) {
        val token=state.value.confirmation ?: return
        val target=state.value.target ?: return
        run {
            retry=Retry.Switch(target,false)
            val result=coordinator.confirm(token,accepted)
            if(!accepted && result.status==TransitionStatus.CANCELLED){retry=null;mutable.value=ActivityUiState(active=coordinator.currentState().active)}
            else {mutable.update{it.copy(confirmation=null)};handle(result,target)}
        }
    }
    fun choose(choice:WaitingChoice) {
        if(state.value.busy)return
        val target=state.value.target ?: return
        if(choice==WaitingChoice.BACK){cancelInput(target);retry=null;mutable.value=ActivityUiState(active=coordinator.currentState().active);return}
        setChoice(target,choice);mutable.update{it.copy(input=false)};submit(target)
    }
    fun consent() {
        if(state.value.busy)return
        val target=state.value.target ?: return
        setConsent(target);mutable.update{it.copy(input=false)};submit(target)
    }
    fun openDocumentConnection() {
        if (!state.value.preparing || state.value.busy) return
        val source = state.value.source
        cancelPreparation()
        // Existing document activities retain the normal save/leave guard.
        if (source?.document() == true) leave(source.route(), StayRoute.FILES)
        else navigate(StayRoute.FILES)
    }
    fun cancelPreparation(){if(!state.value.busy){retryAfterPreparation=false;retry=null;mutable.value=ActivityUiState(active=coordinator.currentState().active)}}
    fun finish(mode:ActivityMode) {
        if(state.value.busy || state.value.modal || coordinator.currentState().active!=mode)return
        mutable.value=ActivityUiState(active=mode,source=mode)
        if(confirmEnd(mode))mutable.update{it.copy(endPrompt=mode)} else end(mode)
    }
    fun confirmFinish(accepted:Boolean) {
        val mode=state.value.endPrompt ?: return
        if(!accepted){mutable.value=ActivityUiState(active=coordinator.currentState().active);return}
        mutable.update{it.copy(endPrompt=null)};end(mode)
    }
    private fun end(source:ActivityMode)=run {
        retry=Retry.End(source)
        val result=coordinator.end()
        if(result.status==TransitionStatus.STOPPED){hide(source);retry=null;navigate(source.route())}
        else fail(result.message.ifBlank{"작업을 안전하게 종료하지 못했습니다."},Retry.End(source))
    }
    fun leave(from:StayRoute,to:StayRoute) {
        if(state.value.busy || state.value.modal)return
        val source=from.activityMode()
        if(source==null){navigate(to);return}
        mutable.value=ActivityUiState(active=coordinator.currentState().active,source=source)
        if(source==ActivityMode.WORK && confirmLeave())mutable.update{it.copy(leavePrompt=to)} else leaveSaved(source,to)
    }
    fun confirmLeaving(accepted:Boolean) {
        val route=state.value.leavePrompt ?: return
        val source=state.value.source ?: return
        if(!accepted){mutable.value=ActivityUiState(active=coordinator.currentState().active);return}
        mutable.update{it.copy(leavePrompt=null)};leaveSaved(source,route)
    }
    private fun leaveSaved(source:ActivityMode,to:StayRoute)=run {
        retry=Retry.Leave(to,source)
        val saved=saveForNavigation(source)
        if(!saved.success){fail(saved.message,Retry.Leave(to,source));return@run}
        hide(source);retry=null;navigate(to)
    }
    private fun fail(message:String,intent:Retry) {
        retry=intent
        val label=when(intent){is Retry.Switch->"저장 후 전환 재시도";is Retry.End->"저장 후 종료 재시도";is Retry.Leave->"초안 보관 후 이동 재시도";is Retry.Repair->"저장 재시도"}
        mutable.update{it.copy(error=message.ifBlank{"저장 상태를 확인해 주세요."},confirmation=null,input=false,preparing=false,recovery=coordinator.currentState().active ?: it.source,retryLabel=label)}
    }
    fun retry() {
        if(state.value.busy)return
        val intent=retry ?: return
        val source=state.value.recovery
        if((source?.document()==true || (intent is Retry.Switch && intent.target.document())) && !documentsReady()) {
            retryAfterPreparation=true;mutable.update{it.copy(error=null,preparing=true,target=(intent as? Retry.Switch)?.target ?: source)};return
        }
        mutable.update{it.copy(error=null)}
        when(intent) {
            is Retry.Repair->run {val result=repair(intent.mode);if(!result.success)fail(result.message,intent)else{retry=null;mutable.update{it.copy(error=null,recovery=null)}}}
            is Retry.Leave->run {val result=repair(intent.source);if(!result.success){fail(result.message,intent);return@run};val saved=saveForNavigation(intent.source);if(saved.success){hide(intent.source);retry=null;navigate(intent.route)}else fail(saved.message,intent)}
            is Retry.End->run {val fixed=repair(intent.source);if(!fixed.success){fail(fixed.message,intent);return@run};val result=coordinator.end();if(result.status==TransitionStatus.STOPPED){hide(intent.source);retry=null;navigate(intent.source.route())}else fail(result.message,intent)}
            is Retry.Switch->run {
                if(intent.cleanup){source?.let{val fixed=repair(it);if(!fixed.success){fail(fixed.message,intent);return@run}};val stopped=coordinator.end();if(stopped.status!=TransitionStatus.STOPPED){fail(stopped.message,intent);return@run}}
                val ready=beforeStart(intent.target);if(!ready.success){fail(ready.message,intent);return@run}
                handle(coordinator.request(intent.target),intent.target)
            }
        }
    }
    fun returnToWork() {
        if(state.value.busy)return
        val source=state.value.recovery ?: state.value.source
        if(source==null){dismissError();return}
        permit=source.route();mutable.update{it.copy(error=null,target=null,confirmation=null,input=false,preparing=false,destination=source.route())}
    }
    fun dismissError(){if(!state.value.busy)mutable.update{it.copy(error=null,target=null,confirmation=null,input=false,preparing=false)}}
    fun repairOnly(mode:ActivityMode)=run {val result=repair(mode);if(!result.success)fail(result.message,Retry.Repair(mode))}
}
