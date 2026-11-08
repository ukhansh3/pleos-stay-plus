package com.pleosstay.app.rest

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.PowerManager
import com.pleosstay.app.R

internal interface RestSoundPlayer {
    fun play(): Boolean
    fun stop()
    fun setVolume(value: Float) {}
    fun setResumeAllowed(check: () -> Boolean) {}
    fun onPermanentLoss(action: () -> Unit) {}
}

internal class RestAudio(private val context: Context) : RestSoundPlayer {
    private val manager = context.getSystemService(AudioManager::class.java)
    private val power = context.getSystemService(PowerManager::class.java)
    private val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
    private var player: MediaPlayer? = null
    private var focus: AudioFocusRequest? = null
    private var volume = 1f // MediaPlayer's legacy level; old presets preserve this.
    private var resumeAllowed: () -> Boolean = { false }
    private var permanentLoss: () -> Unit = {}
    override fun setResumeAllowed(check: () -> Boolean) { resumeAllowed = check }
    override fun onPermanentLoss(action: () -> Unit) { permanentLoss = action }
    override fun setVolume(value: Float) {
        volume = value.coerceIn(0f, 1f)
        try { player?.setVolume(volume, volume) } catch (_: IllegalStateException) { stop() }
    }
    private fun allowed() = resumeAllowed() && power?.isInteractive != false
    override fun play(): Boolean {
        if (!allowed()) { stop(); return false }
        if (player != null) return true
        return try {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).setAudioAttributes(attributes)
                .setOnAudioFocusChangeListener { change ->
                    try {
                        when (change) {
                            AudioManager.AUDIOFOCUS_GAIN -> if (allowed()) player?.start() else stop()
                            AudioManager.AUDIOFOCUS_LOSS -> { stop(); permanentLoss() }
                            else -> player?.pause()
                        }
                    } catch (_: RuntimeException) { stop(); permanentLoss() }
                }.build()
            if (manager.requestAudioFocus(request) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) return false
            focus = request
            if (!allowed()) { stop(); return false }
            player = checkNotNull(MediaPlayer.create(context, R.raw.rest_rain, attributes, 0)).apply {
                isLooping = true; setVolume(volume, volume)
            }
            player?.start()
            true
        } catch (_: Exception) { stop(); false }
    }
    override fun stop() {
        val previous = player; player = null
        runCatching { previous?.release() }
        val request = focus; focus = null
        if (request != null) runCatching { manager.abandonAudioFocusRequest(request) }
    }
}
