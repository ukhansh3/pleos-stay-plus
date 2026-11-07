package com.pleosstay.app.rest

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.pleosstay.app.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Foreground-only alarm output. Focus loss stops it; it never auto-resumes unexpectedly. */
internal class RestAlarmPlayer(private val context: Context) {
    private val manager = context.getSystemService(AudioManager::class.java)
    private val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
    private val mutable = MutableStateFlow(RestPlayback())
    val state = mutable.asStateFlow()
    private var player: MediaPlayer? = null
    private var focus: AudioFocusRequest? = null

    private fun resource(style: RestAlert) = if (style == RestAlert.SOFT) R.raw.rest_soft else R.raw.rest_wake

    fun problem(style: RestAlert): String? = try {
        context.resources.openRawResourceFd(resource(style)).use { check(it.length > 0) }
        if (manager.isStreamMute(AudioManager.STREAM_ALARM) ||
            manager.getStreamVolume(AudioManager.STREAM_ALARM) == 0) "기기의 알람 음량을 확인해 주세요."
        else null
    } catch (_: Exception) { "알람 음원을 준비할 수 없어요." }

    fun play(style: RestAlert) {
        stop()
        problem(style)?.let { error(it) }
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(attributes)
            .setOnAudioFocusChangeListener({ change ->
                if (change != AudioManager.AUDIOFOCUS_GAIN) {
                    stop()
                    mutable.value = RestPlayback(error = "다른 오디오 요청으로 알람 재생이 중단됐어요.")
                    Log.i("RestAlarmAudio", "focus_lost; playback_stopped")
                }
            }, Handler(Looper.getMainLooper())).build()
        check(manager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            "알람 오디오 포커스를 확보하지 못했어요."
        }
        focus = request
        try {
            val next = checkNotNull(MediaPlayer.create(context, resource(style), attributes, 0)) {
                "알람 플레이어를 준비하지 못했어요."
            }
            player = next
            next.isLooping = true
            next.setOnErrorListener { _, _, _ ->
                stop()
                mutable.value = RestPlayback(error = "알람 음원 재생에 실패했어요.")
                true
            }
            next.start()
            check(next.isPlaying) { "알람 재생을 시작하지 못했어요." }
            mutable.value = RestPlayback(playing = true)
            Log.i("RestAlarmAudio", "playback_started style=" + style.name + " audioSession=" + next.audioSessionId)
        } catch (error: Exception) {
            stop()
            throw error
        }
    }

    fun stop() {
        val hadPlayer = player != null
        player?.release()
        player = null
        focus?.let(manager::abandonAudioFocusRequest)
        focus = null
        mutable.value = RestPlayback()
        if (hadPlayer) Log.i("RestAlarmAudio", "playback_stopped; resources_released")
    }
}
