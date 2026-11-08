package com.pleosstay.app.entertainment.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.concurrent.atomic.AtomicBoolean

/** Permission denied/revoked, including a race between the check and a framework call. */
class MicrophonePermissionException(cause: SecurityException? = null) :
    IllegalStateException("마이크 권한이 없거나 회수되었습니다. 권한을 확인한 뒤 다시 시작해 주세요.", cause)

/** Narrow microphone-only seams allow denial/revocation and cleanup tests without real recording. */
internal interface MicrophonePlatform {
    val sampleRate: Int
    fun hasPermission(): Boolean
    fun support(): MicrophoneSupport
    fun createRecorder(): MicrophoneRecorder
}

internal interface MicrophoneRecorder {
    val initialized: Boolean
    val recording: Boolean
    fun start()
    fun read(buffer: ShortArray, offset: Int, size: Int): Int
    fun frameEndNanos(framesRead: Long): Long?
    fun stop()
    fun release()
}

/** Standard Android API only. RECORD_AUDIO is requested by the host's explicit user flow. */
class AndroidKaraokeMicrophone internal constructor(
    private val platform: MicrophonePlatform,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : KaraokeMicrophone {
    constructor(context: Context, sampleRate: Int = 44_100) : this(AndroidMicrophonePlatform(context, sampleRate))

    private val running = AtomicBoolean(false)
    @Volatile private var recorder: MicrophoneRecorder? = null
    @Volatile private var reader: Job? = null

    override fun support(): MicrophoneSupport = try {
        platform.support()
    } catch (_: SecurityException) {
        MicrophoneSupport(false, "마이크 권한이 없거나 회수되었습니다.")
    }

    override fun frames(): Flow<PcmChunk> = flow {
        try {
            requirePermission()
            val support = platform.support()
            check(support.available) { support.reason }
            check(running.compareAndSet(false, true)) { "마이크가 이미 사용 중입니다." }
            val job = currentCoroutineContext()[Job]!!
            reader = job
            var local: MicrophoneRecorder? = null
            try {
                val sampleRate = platform.sampleRate
                val frameSize = (sampleRate * 0.05).toInt()
                requirePermission()
                local = platform.createRecorder()
                recorder = local
                check(local.initialized) { "마이크 초기화 실패" }
                if (!running.get()) return@flow
                requirePermission()
                local.start()
                check(local.recording) { "마이크 녹음 시작 실패" }
                val buffer = ShortArray(frameSize)
                var frameCounter = 0L
                while (currentCoroutineContext().isActive && running.get()) {
                    var read = 0
                    while (read < frameSize && running.get()) {
                        requirePermission()
                        val count = local.read(buffer, read, frameSize - read)
                        check(count > 0) { "PCM 입력 오류: $count" }
                        read += count
                    }
                    if (read == frameSize && running.get()) {
                        // Never emit samples captured across a detected permission revocation.
                        requirePermission()
                        frameCounter += read
                        val end = local.frameEndNanos(frameCounter) ?: System.nanoTime()
                        emit(PcmChunk(FloatArray(read) { buffer[it] / 32768f }, sampleRate,
                            end - read * 1_000_000_000L / sampleRate))
                    }
                }
            } finally {
                running.set(false)
                try {
                    // Stop may itself fail after revocation; release must still be attempted.
                    runCatching { local?.stop() }
                    local?.release()
                } finally {
                    if (recorder === local) recorder = null
                    if (reader === job) reader = null
                }
            }
        } catch (error: SecurityException) {
            throw MicrophonePermissionException(error)
        }
    }.flowOn(dispatcher)

    private fun requirePermission() {
        if (!platform.hasPermission()) throw MicrophonePermissionException()
    }

    override fun interrupt() {
        running.set(false)
        runCatching { recorder?.stop() }
    }

    override suspend fun stop() {
        interrupt()
        reader?.let { if (it != currentCoroutineContext()[Job]) it.cancelAndJoin() }
    }
}

private class AndroidMicrophonePlatform(context: Context, override val sampleRate: Int) : MicrophonePlatform {
    private val context = context.applicationContext
    override fun hasPermission() =
        context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    override fun support(): MicrophoneSupport {
        if (!hasPermission()) return MicrophoneSupport(false, "RECORD_AUDIO 선언 및 사용자 권한 동의가 필요합니다.")
        val manager = context.getSystemService(AudioManager::class.java)
        if (manager == null || manager.getDevices(AudioManager.GET_DEVICES_INPUTS).isEmpty())
            return MicrophoneSupport(false, "입력 장치를 확인할 수 없습니다.")
        if (manager.isMicrophoneMute) return MicrophoneSupport(false, "시스템 마이크가 음소거 상태입니다.")
        val size = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        return if (size > 0) MicrophoneSupport(true, "구성 후보 지원 · 실제 비무음 PCM과 차량 라우팅은 미검증", sampleRate, 1)
        else MicrophoneSupport(false, "요청한 입력 구성을 지원하지 않습니다.")
    }

    override fun createRecorder(): MicrophoneRecorder {
        // Keep this check at the Android API call site so Lint can prove the permission guard.
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
            throw MicrophonePermissionException()
        val minimum = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val frameSize = (sampleRate * 0.05).toInt()
        val record = AudioRecord.Builder().setAudioSource(MediaRecorder.AudioSource.MIC)
            .setAudioFormat(AudioFormat.Builder().setSampleRate(sampleRate).setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
            .setBufferSizeInBytes(maxOf(minimum, frameSize * 4)).build()
        return AndroidMicrophoneRecorder(context, record, sampleRate)
    }
}

private class AndroidMicrophoneRecorder(
    private val context: Context,
    private val record: AudioRecord,
    private val sampleRate: Int,
) : MicrophoneRecorder {
    override val initialized get() = record.state == AudioRecord.STATE_INITIALIZED
    override val recording get() = record.recordingState == AudioRecord.RECORDSTATE_RECORDING

    override fun start() {
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
            throw MicrophonePermissionException()
        record.startRecording()
    }

    override fun read(buffer: ShortArray, offset: Int, size: Int) = record.read(buffer, offset, size, AudioRecord.READ_BLOCKING)
    override fun frameEndNanos(framesRead: Long): Long? {
        val timestamp = AudioTimestamp()
        return if (record.getTimestamp(timestamp, AudioTimestamp.TIMEBASE_MONOTONIC) == AudioRecord.SUCCESS)
            timestamp.nanoTime + (framesRead - timestamp.framePosition) * 1_000_000_000L / sampleRate
        else null
    }
    override fun stop() = record.stop()
    override fun release() = record.release()
}
