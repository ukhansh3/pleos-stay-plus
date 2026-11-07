package com.pleosstay.app.rest

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import com.pleosstay.app.R

internal interface RestSoundPlayer {
    fun play(): Boolean
    fun stop()
}

internal class RestAudio(private val context: Context) : RestSoundPlayer {
    private val manager = context.getSystemService(AudioManager::class.java)
    private val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
    private var player: MediaPlayer? = null
    private var focus: AudioFocusRequest? = null

    override fun play(): Boolean {
        if (player != null) return true
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).setAudioAttributes(attributes)
            .setOnAudioFocusChangeListener { change ->
                when (change) {
                    AudioManager.AUDIOFOCUS_GAIN -> player?.start()
                    AudioManager.AUDIOFOCUS_LOSS -> stop()
                    else -> player?.pause()
                }
            }.build()
        if (manager.requestAudioFocus(request) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) return false
        focus = request
        return try {
            player = checkNotNull(MediaPlayer.create(context, R.raw.rest_rain, attributes, 0)).apply { isLooping = true }
            player?.start()
            true
        } catch (_: Exception) {
            stop()
            false
        }
    }

    override fun stop() {
        player?.release()
        player = null
        focus?.let(manager::abandonAudioFocusRequest)
        focus = null
    }
}
