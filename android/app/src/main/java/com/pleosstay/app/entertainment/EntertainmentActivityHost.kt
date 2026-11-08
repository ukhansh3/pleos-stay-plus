package com.pleosstay.app.entertainment

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pleosstay.app.entertainment.launcher.VerifiedVideoTarget
import com.pleosstay.app.modes.*
import com.pleosstay.app.waiting.data.WaitingRepository
import com.pleosstay.app.waiting.safety.WaitingSafetyGateway
import java.util.UUID
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withContext

private class ActivityLease(val owner: Any, val token:String):AbstractCoroutineContextElement(Key) {
    companion object Key:CoroutineContext.Key<ActivityLease>
}

/** Only adapter.start can mint an Admission; never calls back into the coordinator. */
class CoordinatedEntertainmentAdmission:EntertainmentActivityAdmission {
    @Volatile private var held:String?=null
    override suspend fun acquire():String {
        val lease=currentCoroutineContext()[ActivityLease]
        check(currentCoroutineContext()[ActivityExecution]?.mode==ActivityMode.ENTERTAINMENT && lease?.owner===this && held==null){"공통 활동 전환 승인이 필요합니다."}
        return checkNotNull(lease).token.also { held=it }
    }
    override suspend fun release(permit:String) { check(held==permit);held=null }
    internal suspend fun start(controller:EntertainmentController):EntertainmentActionResult =
        withContext(ActivityLease(this,UUID.randomUUID().toString())) { controller.start() }
    internal fun sessionId()=held
}

class EntertainmentActivityAdapter(private val runtime:EntertainmentRuntime,
    private val admission:CoordinatedEntertainmentAdmission):ActivityAdapter {
    override val mode=ActivityMode.ENTERTAINMENT
    override fun sessionId()=admission.sessionId()
    override suspend fun preflight()=ActivityOperation(runtime.controller.canStart(),"정차 상태와 활성 화면을 확인해 주세요.")
    override suspend fun start()=admission.start(runtime.controller).let { ActivityOperation(it.success,it.message) }
    override suspend fun resume()=runtime.controller.onHostForeground(true).let { ActivityOperation(it.success,it.message) }
    override suspend fun stop()=runtime.controller.end().let { ActivityOperation(it.success,it.message) }
}

/** Obtain once from the host ViewModelStore, not from a screen's rememberCoroutineScope. */
class EntertainmentActivityHost(context:Context,safety:WaitingSafetyGateway,waiting:WaitingRepository,
    targets:List<VerifiedVideoTarget>):ViewModel() {
    private val admission=CoordinatedEntertainmentAdmission()
    val runtime=EntertainmentRuntime.create(context.applicationContext,viewModelScope,safety,waiting,admission,targets)
    val adapter=EntertainmentActivityAdapter(runtime,admission)
    init { viewModelScope.launch { runtime.controller.restore() } }
    suspend fun internalHome()=runtime.controller.onInternalHome()
    suspend fun foreground(value:Boolean)=runtime.controller.onHostForeground(value)
    fun close() { runtime.karaoke.interrupt();viewModelScope.cancel() }
    override fun onCleared() {
        // Immediate interruption does not depend on a scope that ViewModelStore is cancelling.
        runtime.karaoke.interrupt()
        super.onCleared()
    }
}
