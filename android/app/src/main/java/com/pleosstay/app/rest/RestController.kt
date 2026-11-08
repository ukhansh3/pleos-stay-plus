package com.pleosstay.app.rest

import com.pleosstay.app.modes.ActivityMode
import com.pleosstay.app.modes.ModeStartManager
import com.pleosstay.app.modes.ModeStartRequest
import com.pleosstay.app.modes.ModeStartResult
import com.pleosstay.app.modes.WaitingChoice
import com.pleosstay.app.waiting.safety.VehicleMotion
import com.pleosstay.app.waiting.safety.WaitingSafetyGateway
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

internal data class RestDecision(val preset: RestPreset, val prompt: ModeStartResult.NeedsChoice, val replacing: RestSession?)
internal data class RestFallback(val preset: RestPreset, val error: ModeStartResult.StorageError)
internal data class RestState(
    val presets: List<RestPreset> = emptyList(),
    val session: RestSession? = null,
    val ev: EvReading = EvReading(),
    val ready: Boolean = false,
    val busy: Boolean = false,
    val message: String? = null,
    val decision: RestDecision? = null,
    val fallback: RestFallback? = null,
    val ringing: Boolean = false,
    val alarmConsent: RestPreset? = null,
    val alarmLimitation: String? = null,
)

internal class RestController(
    private val store: RestPersistence,
    private val alarm: RestAlerts,
    private val audio: RestSoundPlayer,
    private val ev: RestBattery,
    private val manager: ModeStartManager,
    private val safety: WaitingSafetyGateway,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val mutex = Mutex()
    private val mutable = MutableStateFlow(RestState())
    val state = mutable.asStateFlow()
    val safetyState = safety.state
    private var evJob: Job? = null
    private var previewEv = false
    private var evGeneration = 0
    private var foreground = false
    private var alarmReady = false
    private var selectedMode = RestAlarmMode.SYSTEM_EXACT
    private var visibilityGeneration = 0

    init {
        audio.setResumeAllowed { restSoundAllowed(state.value.session, foreground, safety.restState.value.canUse, now(), state.value.ringing) }
        audio.onPermanentLoss { pauseAfterFocusLoss() }
        alarm.playback?.let { playback ->
            scope.launch {
                playback.collect { result ->
                    if (state.value.session?.alarmMode == RestAlarmMode.FOREGROUND_ONLY) {
                        mutable.update { it.copy(ringing = result.playing,
                            message = result.error ?: it.message) }
                    }
                }
            }
        }
        scope.launch {
            safety.restState.collect { status ->
                if (!status.canUse) {
                    audio.stop()
                    alarm.silence()
                    mutable.update { it.copy(ringing = false) }
                }
                mutex.withLock {
                    runSafely {
                        val active = active() ?: return@runSafely
                        if (!status.canUse && (status.motion == VehicleMotion.DRIVING || status.motion == VehicleMotion.PARKED)) {
                            finish(active, RestEnd.SAFETY)
                        } else if (status.canUse) {
                            updateAudio(active)
                        }
                        observeEv()
                    }
                }
            }
        }
        scope.launch {
            while (true) {
                delay(1_000)
                mutex.withLock {
                    runSafely {
                        val active = active() ?: return@runSafely
                        if (alarmReady && canRun(active) && active.remaining(now()) == 0L && safety.restState.value.canUse) {
                            finish(active, RestEnd.TIME, ring = true)
                        }
                    }
                }
            }
        }
    }

    fun setForeground(value: Boolean) {
        if (foreground == value) return
        val generation = ++visibilityGeneration
        foreground = value
        if (!value) {
            audio.stop()
            if (state.value.session?.alarmMode == RestAlarmMode.FOREGROUND_ONLY) {
                stopAlarm()
                mutable.update { it.copy(alarmLimitation = "앱이 화면에서 벗어나 알람 보장이 제한됐어요. 복귀 시 남은 시간을 다시 확인합니다.") }
            }
        } else {
            alarmReady = false
            // Lifecycle restoration must not be dropped by the user-command busy guard.
            scope.launch {
                mutex.withLock {
                    if (generation == visibilityGeneration && foreground) runSafely { restoreSession() }
                }
            }
        }
    }

    fun restore() = command { restoreSession() }

    private suspend fun restoreSession() {
        alarmReady = false
        load()
        val active = active() ?: return
        if (active.remaining(now()) == 0L) {
            finish(active, RestEnd.TIME)
            if (active.alarmMode == RestAlarmMode.FOREGROUND_ONLY)
                mutable.update { it.copy(alarmLimitation = "앱을 사용하지 않는 동안 휴식 시간이 끝났어요. 종료 알람은 다시 울리지 않습니다.") }
        } else {
            if (active.alarmMode == RestAlarmMode.FOREGROUND_ONLY && !foreground) return
            alarm.schedule(active)
            alarmReady = true
            if (active.alarmMode == RestAlarmMode.FOREGROUND_ONLY)
                mutable.update { it.copy(alarmLimitation = "앱 실행 중 알람 · Stay+를 화면에 유지해 주세요. 앱 종료·차량 절전 중에는 보장되지 않습니다.") }
            updateAudio(active)
            observeEv()
        }
    }

    private suspend fun load() {
        if (state.value.ready) return
        val restored = withContext(io) { store.presets() to store.session() }
        mutable.update { it.copy(presets = restored.first, session = restored.second, ready = true) }
    }

    fun savePreset(value: RestPreset, editingId: String?) = command {
        require(value.valid) { "이름과 휴식 설정을 확인해 주세요." }
        check(state.value.ready)
        val updated = state.value.presets.filterNot { it.id == editingId } +
            value.copy(id = editingId ?: UUID.randomUUID().toString(), name = value.name.trim())
        withContext(io) { store.savePresets(updated) }
        mutable.update { it.copy(presets = updated, message = "프리셋을 저장했어요.") }
    }

    fun deletePreset(id: String) = command {
        check(state.value.ready)
        val updated = state.value.presets.filterNot { it.id == id }
        withContext(io) { store.savePresets(updated) }
        mutable.update { it.copy(presets = updated, message = "프리셋을 삭제했어요.") }
    }

    fun previewEv(enabled: Boolean) {
        previewEv = enabled
        observeEv()
    }

    fun retryEv() {
        stopEv()
        observeEv()
    }

    fun start(preset: RestPreset) = command {
        check(state.value.ready && active() == null) { "이미 휴식이 진행 중이거나 기록을 불러오는 중입니다." }
        check(state.value.decision == null && state.value.fallback == null && state.value.alarmConsent == null)
        checkStart(preset, checkAlarm = false)
        selectedMode = alarm.mode(preset.alert)
        when (selectedMode) {
            RestAlarmMode.FOREGROUND_ONLY -> mutable.update { it.copy(alarmConsent = preset) }
            RestAlarmMode.SYSTEM_EXACT -> startPrepared(preset)
            RestAlarmMode.UNAVAILABLE -> error(alarm.foregroundProblem(preset.alert) ?: "사용 가능한 알람 방식이 없어요.")
        }
    }

    fun dismissAlarmConsent() { mutable.update { it.copy(alarmConsent = null) } }

    fun acceptForegroundAlarm() = command {
        val preset = state.value.alarmConsent ?: return@command
        check(active() == null)
        selectedMode = RestAlarmMode.FOREGROUND_ONLY
        checkStart(preset)
        mutable.update { it.copy(alarmConsent = null) }
        startPrepared(preset)
    }

    private suspend fun startPrepared(preset: RestPreset) {
        checkStart(preset)
        apply(preset, manager.start(ModeStartRequest(ActivityMode.REST, preset.minutes)), null)
    }

    fun resolve(choice: WaitingChoice) = command {
        val decision = state.value.decision ?: return@command
        if (choice != WaitingChoice.BACK) {
            if (decision.replacing == null) checkStart(decision.preset)
            else checkAlarmAndSafety(decision.preset)
        }
        mutable.update { it.copy(decision = null) }
        if (decision.replacing != null && active()?.alarmToken != decision.replacing.alarmToken) return@command
        val previous = decision.replacing
        val fixedEnd = decision.prompt.requestedEnd
        if (choice == WaitingChoice.EXTEND && previous != null && fixedEnd != null) {
            prepareReplacement(previous, fixedEnd) { manager.resolve(decision.prompt, choice) }
        } else {
            apply(decision.preset, manager.resolve(decision.prompt, choice), previous)
        }
    }

    fun alarmSettingsUnavailable() {
        message("이 기기에서 정확한 알람 권한 설정을 열 수 없어요. 차량의 앱 권한 지원을 확인해 주세요.")
    }

    fun dismissFallback() { mutable.update { it.copy(fallback = null) } }

    fun independent() = command {
        val fallback = state.value.fallback ?: return@command
        check(active() == null)
        checkStart(fallback.preset)
        mutable.update { it.copy(fallback = null) }
        apply(fallback.preset, manager.continueWithoutWaiting(fallback.error), null)
    }

    fun extend(minutes: Int) = command {
        val current = active() ?: return@command
        checkAlarmAndSafety(current.preset)
        require(current.remaining(now()) > 0 && minutes in 1..720) { "연장 시간을 확인해 주세요." }
        val target = current.endsAt + minutes * REST_MINUTE
        require(target - current.startedAt <= 720 * REST_MINUTE) { "총 휴식 시간은 720분까지 설정할 수 있어요." }
        val waitingId = current.waitingId
        if (waitingId == null) {
            replaceAlarm(current.copy(endsAt = target, alarmToken = UUID.randomUUID().toString()), current)
        } else {
            prepareReplacement(current, target) { manager.adjustEnd(ActivityMode.REST, waitingId, target) }
        }
    }

    fun end() = command { active()?.let { finish(it, RestEnd.MANUAL) } }

    fun transitionSessionId(): String? = state.value.session?.takeIf { it.end==null || state.value.ringing }?.alarmToken
    fun transitionPreflight(preset:RestPreset):com.pleosstay.app.modes.ActivityOperation = try {
        check(state.value.ready && !state.value.busy)
        checkStart(preset,checkAlarm=false)
        com.pleosstay.app.modes.ActivityOperation.Done
    } catch(_:Exception) { com.pleosstay.app.modes.ActivityOperation(false,"휴식 설정과 안전 상태를 확인해 주세요.") }
    suspend fun startForTransition(preset:RestPreset,choice:WaitingChoice?=null,acceptForeground:Boolean=false,withoutWaiting:Boolean=false):com.pleosstay.app.modes.ActivityOperation {
        val ready=transitionPreflight(preset)
        if(!ready.success) return ready
        when {
            state.value.fallback!=null -> if(withoutWaiting) independent() else return com.pleosstay.app.modes.ActivityOperation(false,"대기 기록 복구 또는 독립 휴식 선택이 필요합니다.",true)
            state.value.alarmConsent!=null -> if(acceptForeground) acceptForegroundAlarm() else return com.pleosstay.app.modes.ActivityOperation(false,"전경 알람 사용 동의가 필요합니다.",true)
            state.value.decision!=null -> if(choice!=null) resolve(choice) else return com.pleosstay.app.modes.ActivityOperation(false,"대기 시간 선택이 필요합니다.",true)
            else -> start(preset)
        }
        state.first { !it.busy }
        return com.pleosstay.app.modes.ActivityOperation(active()!=null,state.value.message ?: "휴식 시작 동의·시간 선택을 확인해 주세요.",state.value.alarmConsent!=null || state.value.decision!=null || state.value.fallback!=null)
    }

    /** Awaitable boundary for activity switches; includes an already completed ringing session. */
    suspend fun stopForTransition(): com.pleosstay.app.modes.ActivityOperation = mutex.withLock {
        try {
            if(state.value.busy) return@withLock com.pleosstay.app.modes.ActivityOperation(false,"휴식 작업 완료 후 다시 시도해 주세요.")
            audio.stop()
            stopAlarm()
            state.value.session?.let { session ->
                // Cancel before committing end so a failed cancel remains retryable.
                alarm.cancel(session)
                if (session.end == null) persist(session.copy(end = RestEnd.MANUAL, targetPrompt = false))
            }
            previewEv = false
            stopEv()
            mutable.update { it.copy(decision = null, fallback = null, alarmConsent = null, ringing = false) }
            com.pleosstay.app.modes.ActivityOperation.Done
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) {
            message("휴식 종료 정리를 완료하지 못했습니다. 다시 시도해 주세요.")
            com.pleosstay.app.modes.ActivityOperation(false, state.value.message.orEmpty())
        }
    }

    private fun pauseAfterFocusLoss() {
        val token = active()?.alarmToken ?: return
        // A focus event must not be dropped by the user-command busy guard.
        scope.launch {
            mutex.withLock {
                runSafely {
                    val current = active()?.takeIf { it.alarmToken == token } ?: return@runSafely
                    val paused = current.copy(soundPaused = true)
                    mutable.update { it.copy(session = paused) }
                    updateAudio(paused)
                    withContext(io) { store.saveSession(paused) }
                }
            }
        }
    }

    fun pauseSound(paused: Boolean) = command {
        val current = active() ?: return@command
        val updated = current.copy(soundPaused = paused)
        persist(updated)
        updateAudio(updated)
    }

    /** Changes only the active session. Presets are persisted explicitly via saveCurrentSoundPreset. */
    fun changeSound(sound: RestSound? = null, volume: Int? = null, muted: Boolean? = null) {
        scope.launch {
            mutex.withLock {
                runSafely {
                    val current = active() ?: return@runSafely
                    check(safety.restState.value.canUse && foreground)
                    require(volume == null || volume in 0..100)
                    val preset = current.preset.copy(sound = sound ?: current.preset.sound,
                        volume = volume ?: current.preset.volume, muted = muted ?: current.preset.muted)
                    val updated = current.copy(preset = preset)
                    // Apply session changes immediately; persistence failure is reported honestly.
                    mutable.update { it.copy(session = updated) }
                    updateAudio(updated)
                    withContext(io) { store.saveSession(updated) }
                }
            }
        }
    }

    fun saveCurrentSoundPreset() {
        val current = active() ?: return
        savePreset(current.preset, current.preset.id)
    }

    fun stopAlarm() {
        alarm.silence()
        mutable.update { it.copy(ringing = false) }
    }

    fun answerTarget(keepResting: Boolean) = command {
        val current = active()?.takeIf { it.targetPrompt } ?: return@command
        stopAlarm()
        if (keepResting) {
            val updated = current.copy(targetPrompt = false)
            persist(updated)
            updateAudio(updated)
        } else {
            finish(current, RestEnd.TARGET)
        }
    }

    fun receive(action: String?, token: String, done: () -> Unit) {
        scope.launch {
            try {
                if (action == RestAlarm.ACTION_TIME) {
                    safety.start()
                    withTimeoutOrNull(4_000) { safety.restState.first { it.canUse || it.motion == VehicleMotion.DRIVING } }
                }
                mutex.withLock {
                    runSafely {
                        load()
                        val session = state.value.session?.takeIf { it.alarmToken == token } ?: return@runSafely
                        if (session.alarmMode != RestAlarmMode.SYSTEM_EXACT) return@runSafely
                        when (action) {
                            RestAlarm.ACTION_STOP -> stopAlarm()
                            RestAlarm.ACTION_TIME -> if (session.end == null && session.remaining(now()) == 0L) {
                                finish(session, if (safety.restState.value.canUse) RestEnd.TIME else RestEnd.SAFETY,
                                    ring = safety.restState.value.canUse)
                            }
                        }
                    }
                }
            } finally { done() }
        }
    }

    private suspend fun prepareReplacement(
        current: RestSession,
        target: Long,
        request: suspend () -> ModeStartResult,
    ) {
        val candidate = current.copy(endsAt = target, alarmToken = UUID.randomUUID().toString())
        alarm.schedule(candidate)
        try {
            apply(current.preset, request(), current, candidate)
        } finally {
            if (state.value.session?.alarmToken != candidate.alarmToken) alarm.cancel(candidate)
        }
    }

    private suspend fun apply(
        preset: RestPreset,
        result: ModeStartResult,
        replacing: RestSession?,
        scheduled: RestSession? = null,
    ) {
        when (result) {
            is ModeStartResult.Ready -> {
                checkAlarmAndSafety(preset)
                val start = now()
                val deadline = result.endsAt ?: start + result.durationMillis
                check(deadline > start && (replacing == null || deadline > replacing.endsAt)) { "남은 대기 시간으로 휴식을 연장할 수 없습니다." }
                val reserved = scheduled?.takeIf { it.endsAt == deadline }
                val next = reserved ?: replacing?.copy(endsAt = deadline, alarmToken = UUID.randomUUID().toString())
                    ?: RestSession(preset, start, deadline, result.waiting?.id, alarmMode = selectedMode)
                replaceAlarm(next, replacing, alreadyScheduled = reserved != null)
            }
            is ModeStartResult.NeedsChoice -> mutable.update { it.copy(decision = RestDecision(preset, result, replacing)) }
            is ModeStartResult.StorageError -> mutable.update {
                it.copy(message = result.message, fallback = if (replacing == null) RestFallback(preset, result) else null)
            }
            is ModeStartResult.SafetyBlocked -> message(result.message)
            is ModeStartResult.RecoveryRequired -> message(result.message)
            is ModeStartResult.WaitingChanged -> message("연결된 대기 세션이 변경됐습니다. 다시 확인해 주세요.")
            is ModeStartResult.InvalidDuration -> message("허용 시간은 ${result.allowedMinutes.first}~${result.allowedMinutes.last}분입니다.")
            is ModeStartResult.Cancelled, is ModeStartResult.DecisionAlreadyHandled -> Unit
        }
    }

    private suspend fun replaceAlarm(next: RestSession, previous: RestSession?, alreadyScheduled: Boolean = false) {
        if (!alreadyScheduled) alarm.schedule(next)
        try {
            checkAlarmAndSafety(next.preset)
            persist(next)
        } catch (error: Exception) {
            alarm.cancel(next)
            throw error
        }
        previous?.let(alarm::cancel)
        alarmReady = true
        mutable.update { it.copy(alarmLimitation = if (next.alarmMode == RestAlarmMode.FOREGROUND_ONLY)
            "앱 실행 중 알람 · Stay+를 화면에 유지해 주세요. 앱 종료·차량 절전 중에는 보장되지 않습니다." else null) }
        stopAlarm()
        if (safety.restState.value.canUse) updateAudio(next)
        observeEv()
    }

    private suspend fun finish(session: RestSession, reason: RestEnd, ring: Boolean = false) {
        audio.stop()
        val justAlerted = reason == RestEnd.TIME && session.recentlyAlertedTarget(now())
        if (!justAlerted) stopAlarm()
        persist(session.copy(end = reason, targetPrompt = false))
        alarm.cancel(session)
        mutable.update { it.copy(decision = null) }
        observeEv()
        if (ring && !justAlerted && safety.restState.value.canUse) ring(session, reason)
    }

    private suspend fun reached(session: RestSession) {
        audio.stop()
        persist(session.copy(targetReached = true, targetPrompt = true, targetAlertAt = now()))
        if (safety.restState.value.canUse) ring(session, RestEnd.TARGET)
    }

    private fun ring(session: RestSession, reason: RestEnd) {
        if (!canRun(session)) return
        val problem = if (session.alarmMode == RestAlarmMode.FOREGROUND_ONLY)
            alarm.foregroundProblem(session.preset.alert) else alarm.soundProblem(session.preset.alert)
        if (problem != null) message(problem)
        else {
            alarm.sound(session, reason)
            mutable.update { it.copy(ringing = true) }
        }
    }

    private fun observeEv() {
        val needed = (previewEv || active()?.preset?.ev == true) &&
            (safety.state.value.canUse || active()?.preset?.ev == true && safety.restState.value.canUse)
        if (!needed) {
            stopEv()
            return
        }
        if (evJob != null) return
        val generation = ++evGeneration
        evJob = scope.launch {
            try {
                ev.readings().collect { reading ->
                    if (generation != evGeneration) return@collect
                    mutable.update { it.copy(ev = reading) }
                    mutex.withLock {
                        runSafely {
                            val current = active()
                            val percent = reading.percent
                            if (generation == evGeneration && safety.restState.value.canUse && current != null && canRun(current) &&
                                percent != null && current.canReachTarget(percent, now())) reached(current)
                        }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (generation == evGeneration) mutable.update { it.copy(ev = EvReading(message = error.message.orEmpty())) }
            }
        }
    }

    private fun stopEv() {
        if (evJob == null) return
        evGeneration++
        evJob?.cancel()
        evJob = null
        mutable.update { it.copy(ev = EvReading()) }
    }

    private fun checkStart(preset: RestPreset, checkAlarm: Boolean = true) {
        require(preset.valid) { "프리셋 이름, 시간 또는 목표 충전율을 확인해 주세요." }
        check(safety.state.value.canUse) { safety.state.value.message }
        if (checkAlarm) checkAlarmAndSafety(preset)
        if (preset.ev) {
            val reading = state.value.ev
            val percent = checkNotNull(reading.percent) { reading.message }
            check(percent < preset.target) { "이미 목표 충전율에 도달했어요. 목표를 바꾸거나 일반 휴식을 선택해 주세요." }
        }
    }

    private fun checkAlarmAndSafety(preset: RestPreset) {
        check(safety.state.value.canUse) { safety.state.value.message }
        val mode = active()?.alarmMode ?: selectedMode
        check(mode != RestAlarmMode.UNAVAILABLE) { "사용 가능한 알람 방식이 없어요." }
        if (mode == RestAlarmMode.FOREGROUND_ONLY) check(foreground) { "Stay+ 화면에서 다시 시도해 주세요." }
        val problem = if (mode == RestAlarmMode.FOREGROUND_ONLY) alarm.foregroundProblem(preset.alert)
            else alarm.problem(preset.alert)
        check(problem == null) { problem.orEmpty() }
    }

    private fun canRun(session: RestSession) =
        session.alarmMode == RestAlarmMode.SYSTEM_EXACT || (session.alarmMode == RestAlarmMode.FOREGROUND_ONLY && foreground && alarmReady)

    private fun updateAudio(session: RestSession) {
        audio.setVolume(session.preset.volume / 100f)
        if (!restSoundAllowed(session, foreground, safety.restState.value.canUse, now(), state.value.ringing)) audio.stop()
        else if (!audio.play()) message("오디오 사용 권한을 확보하지 못했어요. 사운드 재개를 다시 시도해 주세요.")
    }

    private suspend fun persist(session: RestSession) {
        withContext(io) { store.saveSession(session) }
        mutable.update { it.copy(session = session) }
    }

    private fun active() = state.value.session?.takeIf { it.end == null }
    private fun message(value: String) { mutable.update { it.copy(message = value) } }

    private fun command(block: suspend () -> Unit) {
        if (state.value.busy) return
        mutable.update { it.copy(busy = true, message = null) }
        scope.launch {
            try { mutex.withLock { runSafely(block) } }
            finally { mutable.update { it.copy(busy = false) } }
        }
    }

    private suspend fun runSafely(block: suspend () -> Unit) {
        try { block() }
        catch (error: CancellationException) { throw error }
        catch (error: Exception) { message(error.message ?: "휴식 처리에 실패했어요.") }
    }
}
