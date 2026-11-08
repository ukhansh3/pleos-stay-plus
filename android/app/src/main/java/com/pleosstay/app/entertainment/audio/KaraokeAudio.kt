package com.pleosstay.app.entertainment.audio

import com.pleosstay.app.entertainment.karaoke.KaraokeSong
import kotlinx.coroutines.flow.Flow

/** Float mono PCM, capture timestamp in monotonic nanoseconds; raw samples are never persisted. */
data class PcmChunk(val samples:FloatArray,val sampleRate:Int,val capturedAtNanos:Long)
data class MicrophoneSupport(val available:Boolean,val reason:String,val sampleRate:Int?=null,val channels:Int?=null)
enum class PlaybackEvent { COMPLETED, INTERRUPTED, ERROR }
interface KaraokeMicrophone {
    fun support():MicrophoneSupport
    fun frames():Flow<PcmChunk>
    /** Immediate fail-closed interruption; stop() subsequently waits for read-loop cleanup. */
    fun interrupt()
    suspend fun stop()
}
interface KaraokeBackingPlayer {
    var onEvent:((PlaybackEvent)->Unit)?
    suspend fun prepare(song:KaraokeSong)
    suspend fun seekTo(positionMillis:Long)
    suspend fun play()
    suspend fun pause()
    fun positionMillis():Long
    fun interrupt()
    suspend fun release()
}
