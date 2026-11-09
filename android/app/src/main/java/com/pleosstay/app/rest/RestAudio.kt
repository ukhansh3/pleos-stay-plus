package com.pleosstay.app.rest

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import com.pleosstay.app.R
import com.pleosstay.app.audio.MediaFocusPlayback
import com.pleosstay.app.audio.PlaybackScreenState

internal interface RestSoundPlayer {
    fun play(): Boolean
    fun stop()
    fun setVolume(value: Float) {}
    fun setResumeAllowed(check: () -> Boolean) {}
    fun onPermanentLoss(action: () -> Unit) {}
}

internal class RestAudio(private val context: Context) : RestSoundPlayer {
    private val manager = context.getSystemService(AudioManager::class.java)
    private val screen = PlaybackScreenState(context)
    private val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
    private var player: MediaPlayer? = null
    private var focus: AudioFocusRequest? = null
    private var playback: MediaFocusPlayback? = null
    private var volume = 1f // MediaPlayer's legacy level; old presets preserve this.
    private var resumeAllowed: () -> Boolean = { false }
    private var permanentLoss: () -> Unit = {}
    override fun setResumeAllowed(check: () -> Boolean) { resumeAllowed = check }
    override fun onPermanentLoss(action: () -> Unit) { permanentLoss = action }

    override fun setVolume(value: Float) {
        volume = value.coerceIn(0f, 1f)
        try { playback?.setVolume(volume) } catch (_: IllegalStateException) { stop() }
    }

    private fun allowed() = resumeAllowed() && screen.isInteractive

    override fun play(): Boolean {
        if (!allowed()) { stop(); return false }
        if (player != null) return true
        return try {
            val session = MediaFocusPlayback(
                canResumeAfterTransientLoss = true,
                isAllowed = ::allowed,
                applyVolume = { player?.setVolume(it, it) },
                pause = { player?.pause() },
                resume = { player?.start() },
                interrupt = ::interrupted,
                initialVolume = volume,
            )
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(attributes)
                .setWillPauseWhenDucked(false)
                .setOnAudioFocusChangeListener({ change ->
                    if (playback === session) {
                        try { session.onFocusChange(change) } catch (_: RuntimeException) { interrupted() }
                    }
                }, Handler(Looper.getMainLooper())).build()
            if (manager.requestAudioFocus(request) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) return false
            focus = request
            playback = session
            screen.watch(::interrupted)
            if (!allowed()) { interrupted(); return false }
            val next = checkNotNull(MediaPlayer.create(context, R.raw.rest_rain, attributes, 0))
            player = next
            next.isLooping = true
            session.setVolume(volume)
            next.start()
            true
        } catch (_: Exception) { stop(); false }
    }

    private fun interrupted() {
        stop()
        permanentLoss()
    }

    override fun stop() {
        playback?.close()
        playback = null
        screen.stop()
        val previous = player
        player = null
        runCatching { previous?.release() }
        val request = focus
        focus = null
        if (request != null) runCatching { manager.abandonAudioFocusRequest(request) }
    }
}
