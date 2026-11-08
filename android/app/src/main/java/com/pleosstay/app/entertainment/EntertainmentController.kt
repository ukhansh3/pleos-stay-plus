package com.pleosstay.app.entertainment

import com.pleosstay.app.entertainment.data.*
import com.pleosstay.app.entertainment.recommendation.EntertainmentContent
import com.pleosstay.app.waiting.safety.WaitingSafetyGateway
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** The future shared activity coordinator implements this. No permissive default is supplied. */
interface EntertainmentActivityAdmission {
    suspend fun acquire():String
    suspend fun release(permit:String)
}
interface EntertainmentContentSession {
    suspend fun enter()
    suspend fun pause()
    suspend fun stop()
    fun interrupt()
}
enum class EntertainmentPhase { IDLE, ACTIVE, PAUSED, EXTERNAL, BLOCKED, ERROR }
data class EntertainmentState(val active:Boolean=false,val content:EntertainmentContent?=null,
    val phase:EntertainmentPhase=EntertainmentPhase.IDLE,val message:String="")
data class EntertainmentActionResult(val success:Boolean,val state:EntertainmentState,val message:String="")
/** Only serializes Entertainment contents; does not pretend to enforce global Rest/Study/Work exclusivity. */
class EntertainmentController(private val admission:EntertainmentActivityAdmission,
    private val safety:WaitingSafetyGateway,private val sessions:Map<EntertainmentContent,EntertainmentContentSession>,
    private val store:EntertainmentStore,private val scope:CoroutineScope) {
    private val mutex=Mutex()
    private val mutable=MutableStateFlow(EntertainmentState())
    val state=mutable.asStateFlow()
    @Volatile private var visible=false
    private var permit:String?=null
    private var restored=false
    private val safetyJob=scope.launch {
        safety.state.collect{safe->
            if(!safe.canUse){
                sessions.values.forEach{runCatching{it.interrupt()}}
                mutex.withLock{
                    if(state.value.active){
                        try{state.value.content?.let{sessions.getValue(it).pause()};mutable.value=state.value.copy(phase=EntertainmentPhase.BLOCKED,message=safe.message);persist()}
                        catch(_:Exception){mutable.value=state.value.copy(phase=EntertainmentPhase.ERROR,message="안전 중단 정리를 완료하지 못했습니다. 재개할 수 없습니다.")}
                    }
                }
            }
        }
    }
    fun foreground()=visible
    fun canStart()=visible && safety.state.value.canUse
    suspend fun start():EntertainmentActionResult=command {
        check(canStart()){"안전 상태와 화면을 확인해 주세요."}
        if(state.value.active)return@command
        val granted=admission.acquire();check(granted.isNotBlank())
        permit=granted
        try{
            check(canStart())
            mutable.value=state.value.copy(active=true,phase=if(state.value.content==null)EntertainmentPhase.ACTIVE else EntertainmentPhase.PAUSED)
            persist()
        }catch(error:Exception){
            try{admission.release(granted);permit=null;mutable.value=state.value.copy(active=false)}catch(_:Exception){mutable.value=state.value.copy(active=true,phase=EntertainmentPhase.ERROR)}
            throw error
        }
    }
    suspend fun select(content:EntertainmentContent):EntertainmentActionResult=command {
        checkReady();check(state.value.phase!=EntertainmentPhase.ERROR){"이전 정리를 완료한 뒤 다시 시도해 주세요."}
        if(state.value.content==content && state.value.phase in setOf(EntertainmentPhase.ACTIVE,EntertainmentPhase.EXTERNAL))return@command
        val next=checkNotNull(sessions[content]){"지원하지 않는 콘텐츠입니다."}
        val old=state.value.content
        if(old!=null && old!=content)sessions.getValue(old).stop()
        // Keep a paused marker until the new content has really started; a failed enter never restores the old player.
        mutable.value=state.value.copy(content=content,phase=EntertainmentPhase.PAUSED,message="")
        persist();checkReady();next.enter();checkReady()
        mutable.value=state.value.copy(phase=if(content==EntertainmentContent.YOUTUBE || content==EntertainmentContent.NETFLIX)EntertainmentPhase.EXTERNAL else EntertainmentPhase.ACTIVE)
        persist()
    }
    /** Internal Home keeps the activity. External/OS Home additionally has host foreground=false. */
    suspend fun onInternalHome():EntertainmentActionResult { return hide("내부 홈으로 이동했습니다. 콘텐츠는 직접 재개해 주세요.") }
    suspend fun onHostForeground(value:Boolean):EntertainmentActionResult {
        if(!value)return hide("앱이 비활성 상태입니다. 콘텐츠는 직접 재개해 주세요.")
        visible=true
        return EntertainmentActionResult(true,state.value)
    }
    private suspend fun hide(message:String):EntertainmentActionResult {
        visible=false;sessions.values.forEach{runCatching{it.interrupt()}}
        return command {
            state.value.content?.let{sessions.getValue(it).pause()}
            if(state.value.active)mutable.value=state.value.copy(phase=EntertainmentPhase.PAUSED,message=message)
            persist()
        }
    }
    suspend fun end():EntertainmentActionResult=command {
        state.value.content?.let{sessions.getValue(it).stop()}
        // Release succeeds before active=false is reported. Waiting is never touched here.
        permit?.let{admission.release(it)};permit=null
        mutable.value=EntertainmentState();persist()
    }
    suspend fun restore():EntertainmentActionResult=command {
        if(restored || state.value.active)return@command
        val bytes=store.read("activity")
        if(bytes!=null){val content=decodeRecord(bytes){require(readInt()==1);val n=readInt();require(n in -1 until EntertainmentContent.entries.size);EntertainmentContent.entries.getOrNull(n)}
            mutable.value=EntertainmentState(content=content,phase=if(content==null)EntertainmentPhase.IDLE else EntertainmentPhase.PAUSED,message="복원된 콘텐츠입니다. 시작 승인과 직접 재개가 필요합니다.")}
        restored=true
    }
    /** Serializes game/quiz inputs with content changes and rechecks the real safety snapshot. */
    suspend fun <T> use(content:EntertainmentContent,action:suspend ()->T):T=mutex.withLock {
        checkReady();check(state.value.content==content && state.value.phase==EntertainmentPhase.ACTIVE)
        action()
    }
    suspend fun close():EntertainmentActionResult {
        val result=end();if(result.success)safetyJob.cancel();return result
    }
    private fun checkReady(){check(state.value.active && permit!=null && canStart()){"활동 승인·안전 상태·활성 화면을 확인해 주세요."}}
    private fun persist(){store.write("activity",encodeRecord{writeInt(1);writeInt(state.value.content?.ordinal?:-1)})}
    private suspend fun command(action:suspend ()->Unit):EntertainmentActionResult=mutex.withLock {
        try{action();EntertainmentActionResult(true,state.value)}
        catch(cancelled:CancellationException){sessions.values.forEach{runCatching{it.interrupt()}};throw cancelled}
        catch(error:Exception){
            sessions.values.forEach{runCatching{it.interrupt()}}
            mutable.value=state.value.copy(phase=EntertainmentPhase.ERROR,message=error.message?:"Entertainment 작업에 실패했습니다.")
            EntertainmentActionResult(false,state.value,state.value.message)
        }
    }
}
