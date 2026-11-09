package com.pleosstay.app.entertainment.karaoke

import com.pleosstay.app.entertainment.audio.*
import com.pleosstay.app.entertainment.data.*
import com.pleosstay.app.waiting.safety.WaitingSafety
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class KaraokePhase { UNPREPARED, SELECTED, PREPARING, PLAYING, PAUSED, STOPPING, COMPLETED, USER_STOPPED, INPUT_ERROR, SAFETY_STOPPED }
data class KaraokeState(val phase:KaraokePhase=KaraokePhase.UNPREPARED,val songId:String?=null,
    val positionMillis:Long=0,val lyric:LyricCue?=null,val nextLyric:LyricCue?=null,val pitch:PitchEstimate?=null,
    val result:KaraokeScore?=null,val message:String="",val recordSaved:Boolean=false)
/** Host-owned scope: keep across internal navigation, explicitly close on activity-mode end. */
class KaraokeSession(private val microphone:KaraokeMicrophone,private val player:KaraokeBackingPlayer,
    private val history:KaraokeHistory,private val store:EntertainmentStore,private val scope:CoroutineScope,
    private val safety:()->WaitingSafety,private val foreground:()->Boolean,
    private val monotonicNanos:()->Long=System::nanoTime,private val wallClock:()->Long=System::currentTimeMillis,
    private val detector:YinPitchDetector=YinPitchDetector(),private val scorer:KaraokeScorer=KaraokeScorer(),
    val inputLatencyMillis:Long=0) {
    private val mutex=Mutex()
    private val mutable=MutableStateFlow(KaraokeState())
    val state=mutable.asStateFlow()
    private var song:KaraokeSong?=null
    private var sessionId=""
    private var frames=mutableListOf<PitchFrame>()
    private var capture:Job?=null
    private var generation=0L
    private var origin=0L
    private var prepared=false
    private var leakage=false
    @Volatile private var completing=false
    init {
        require(inputLatencyMillis in -5_000..5_000)
        player.onEvent = { event ->
            if (event == PlaybackEvent.COMPLETED) completing = true
            microphone.interrupt()
            scope.launch {
                try {
                    when (event) {
                        PlaybackEvent.COMPLETED -> complete()
                        PlaybackEvent.INTERRUPTED -> pause("오디오 포커스가 변경되었습니다. 직접 재개해 주세요.")
                        PlaybackEvent.ERROR -> inputFailed("반주 재생에 실패했습니다.")
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    mutable.value = state.value.copy(message = "오디오 정리 또는 결과 저장을 완료하지 못했습니다. 상태를 확인하고 다시 시도해 주세요.")
                }
            }
        }
    }
    fun microphoneSupport()=microphone.support()
    suspend fun select(value:KaraokeSong)=mutex.withLock {
        cleanup();song=value;sessionId=UUID.randomUUID().toString();frames=mutableListOf();leakage=false
        prepared=false;mutable.value=KaraokeState(KaraokePhase.SELECTED,value.id)
    }
    suspend fun startOrResume()=mutex.withLock {
        check(safety().canUse && foreground()){ "안전 상태와 활성 화면을 확인해 주세요." }
        check(state.value.phase in setOf(KaraokePhase.SELECTED,KaraokePhase.PAUSED,KaraokePhase.SAFETY_STOPPED)) { "재생 가능한 상태가 아닙니다." }
        check(microphone.support().available){microphone.support().reason}
        val selected=checkNotNull(song);val position=state.value.positionMillis
        mutable.value=state.value.copy(phase=KaraokePhase.PREPARING,message="")
        try {
            if(!prepared){player.prepare(selected);if(position>0)player.seekTo(position);prepared=true}
            check(safety().canUse && foreground())
            completing=false
            origin=monotonicNanos()-position*1_000_000
            val input = microphone.frames()
            player.play()
            check(safety().canUse && foreground())
            val token=++generation
            mutable.value=state.value.copy(phase=KaraokePhase.PLAYING)
            capture=scope.launch {
                try {
                    input.collect{chunk->
                        val estimate=detector.analyze(chunk.samples,chunk.sampleRate)
                        val rawStart=(chunk.capturedAtNanos-origin)/1_000_000
                        val duration=chunk.samples.size*1000L/chunk.sampleRate
                        mutex.withLock {
                            if(token!=generation || state.value.phase!=KaraokePhase.PLAYING)return@withLock
                            if(!safety().canUse || !foreground()){interrupt();return@withLock}
                            val start=maxOf(0,rawStart,frames.lastOrNull()?.endMillis?:0)
                            val end=minOf(selected.durationMillis,rawStart+duration)
                            if(end>start)frames+=PitchFrame(start,end,estimate)
                            check(frames.size<=20_000){"분석 프레임 한도를 초과했습니다."}
                            val at=player.positionMillis().coerceIn(0,selected.durationMillis)
                            mutable.value=state.value.copy(positionMillis=at,lyric=selected.lyricsAt(at),nextLyric=selected.nextLyrics(at),pitch=estimate)
                        }
                    }
                }catch(cancelled:CancellationException){throw cancelled}
                catch(_:Exception){if(token==generation && !completing)inputFailed("마이크 PCM 입력 또는 음정 분석에 실패했습니다.")}
            }
        }catch(cancelled:CancellationException){cleanup();throw cancelled}
        catch(error:Exception){cleanup();mutable.value=state.value.copy(phase=KaraokePhase.INPUT_ERROR,message="반주 또는 마이크 준비에 실패했습니다.");throw error}
    }
    /** Fast stop used before waiting for the serialized lifecycle command. */
    fun interrupt(){microphone.interrupt();player.interrupt()}
    suspend fun pause(message:String="직접 이어 부르기를 선택해 주세요.",safetyStop:Boolean=false)=mutex.withLock {
        if(state.value.phase !in setOf(KaraokePhase.PLAYING,KaraokePhase.PREPARING,KaraokePhase.PAUSED))return@withLock
        interrupt();generation++
        val at=if(prepared)player.positionMillis() else state.value.positionMillis;val oldCapture=capture;capture=null
        // A collector may be waiting for this mutex: cancel it before joining.
        oldCapture?.cancel();microphone.stop();player.pause();player.release()
        prepared = false
        mutable.value=state.value.copy(phase=if(safetyStop)KaraokePhase.SAFETY_STOPPED else KaraokePhase.PAUSED,positionMillis=at,message=message)
        checkpoint()
    }
    suspend fun stop()=mutex.withLock {
        mutable.value=state.value.copy(phase=KaraokePhase.STOPPING)
        cleanup()
        mutable.value=state.value.copy(phase=KaraokePhase.USER_STOPPED,message="노래를 중단했습니다.")
        checkpoint()
    }
    private suspend fun inputFailed(message:String)=mutex.withLock {
        cleanup();mutable.value=state.value.copy(phase=KaraokePhase.INPUT_ERROR,message=message,
            result=song?.let{scorer.score(it,frames,inputError=true)})
    }
    suspend fun complete()=mutex.withLock {
        if(state.value.phase!=KaraokePhase.PLAYING)return@withLock
        val selected=checkNotNull(song)
        // A completion callback is evidence only if playback reached the end.
        check(player.positionMillis()>=selected.durationMillis-100)
        cleanup()
        val result=scorer.score(selected,frames,inputLatencyMillis,leakageSuspected=leakage)
        mutable.value=state.value.copy(phase=KaraokePhase.COMPLETED,positionMillis=selected.durationMillis,result=result,recordSaved=false)
        saveCompletedResult()
        checkpoint()
    }
    suspend fun retrySave()=mutex.withLock {check(state.value.phase==KaraokePhase.COMPLETED);saveCompletedResult();checkpoint()}
    private fun saveCompletedResult(){
        val selected=checkNotNull(song);val result=state.value.result?:return
        if(result.status==KaraokeResultStatus.SCORED){
            try{history.save(KaraokeRecord(sessionId,selected.id,selected.title,checkNotNull(result.score),wallClock()))
                mutable.value=state.value.copy(recordSaved=true,message="점수 기록을 저장했습니다.")
            }catch(error:Exception){mutable.value=state.value.copy(message="점수 기록 저장에 실패했습니다. 다시 저장해 주세요.");throw error}
        }else mutable.value=state.value.copy(message=result.message)
    }
    suspend fun scorePreview():KaraokeScore=mutex.withLock {scorer.score(checkNotNull(song),frames,inputLatencyMillis,leakageSuspected=leakage)}
    suspend fun markLeakageSuspected()=mutex.withLock {leakage=true}
    private suspend fun cleanup()=withContext(NonCancellable){
        generation++;interrupt();capture?.cancel();capture=null
        var failure:Exception?=null
        try{microphone.stop()}catch(error:Exception){failure=error}
        try{player.release()}catch(error:Exception){if(failure==null)failure=error}
        prepared=false
        failure?.let{throw it}
    }
    private fun checkpoint(){
        val selected=song?:return
        store.write("karaoke-session",encodeRecord{
            writeInt(1);writeUTF(selected.id);writeInt(selected.version);writeUTF(sessionId);writeLong(state.value.positionMillis)
            writeBoolean(state.value.phase in setOf(KaraokePhase.PAUSED,KaraokePhase.SAFETY_STOPPED));writeBoolean(leakage)
            writeInt(frames.size);frames.forEach{f->writeLong(f.startMillis);writeLong(f.endMillis);writeDouble(f.estimate.hz?:0.0);writeDouble(f.estimate.confidence);writeInt(f.estimate.quality.ordinal)}
        })
    }
    /** Restores paused analysis only. Never starts a recorder or player on process restoration. */
    suspend fun restore(selected:KaraokeSong):Boolean=mutex.withLock {
        val bytes=store.read("karaoke-session")?:return@withLock false
        data class Saved(val id:String,val position:Long,val frames:List<PitchFrame>,val leakage:Boolean)
        val saved=decodeRecord(bytes){
            require(readInt()==1);require(readUTF()==selected.id && readInt()==selected.version)
            val id=readUTF();val position=readLong();require(position in 0..selected.durationMillis)
            val resumable=readBoolean();val leak=readBoolean();val count=readInt();require(count in 0..20_000)
            val data=List(count){val start=readLong();val end=readLong();val hz=readDouble();val confidence=readDouble();val quality=readInt()
                require(start>=0 && end>start && end<=selected.durationMillis && confidence.isFinite() && confidence in 0.0..1.0 && hz.isFinite() && hz>=0)
                require(quality in PitchQuality.entries.indices)
                PitchFrame(start,end,PitchEstimate(hz.takeIf{it>0},confidence,PitchQuality.entries[quality]))}
            require(data.zipWithNext().all{(a,b)->a.endMillis<=b.startMillis})
            if(resumable)Saved(id,position,data,leak) else null
        }?:return@withLock false
        cleanup();song=selected;sessionId=saved.id;frames=saved.frames.toMutableList();leakage=saved.leakage
        mutable.value=KaraokeState(KaraokePhase.PAUSED,selected.id,saved.position,selected.lyricsAt(saved.position),selected.nextLyrics(saved.position),message="저장된 노래입니다. 직접 이어 부르기를 선택해 주세요.")
        true
    }
}
