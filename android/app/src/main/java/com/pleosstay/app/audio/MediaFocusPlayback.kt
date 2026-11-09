package com.pleosstay.app.audio

import android.media.AudioManager

/** Handles focus callbacks for a single active request. Android's automatic duck has no callback. */
internal class MediaFocusPlayback(
    private val canResumeAfterTransientLoss: Boolean,
    private val isAllowed: () -> Boolean,
    private val applyVolume: (Float) -> Unit,
    private val pause: () -> Unit,
    private val resume: () -> Unit,
    private val interrupt: () -> Unit,
    initialVolume: Float = 1f,
) {
    private enum class State { PLAYING, DUCKED, PAUSED, CLOSED }

    private var state = State.PLAYING
    private var volume = initialVolume

    fun setVolume(value: Float) {
        volume = value
        if (state != State.CLOSED) applyVolume(effectiveVolume())
    }

    fun onFocusChange(change: Int) {
        if (state == State.CLOSED) return
        if (!isAllowed() || change == AudioManager.AUDIOFOCUS_LOSS) {
            stopPlayback()
            return
        }
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                if (state == State.PLAYING) {
                    state = State.DUCKED
                    applyVolume(effectiveVolume())
                }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                if (!canResumeAfterTransientLoss) {
                    stopPlayback()
                } else if (state != State.PAUSED) {
                    state = State.PAUSED
                    pause()
                }
            }
            AudioManager.AUDIOFOCUS_GAIN -> when (state) {
                State.DUCKED -> {
                    state = State.PLAYING
                    applyVolume(volume)
                }
                State.PAUSED -> {
                    state = State.PLAYING
                    applyVolume(volume)
                    resume()
                }
                else -> Unit
            }
        }
    }

    fun close() {
        state = State.CLOSED
    }

    private fun stopPlayback() {
        close()
        interrupt()
    }

    // Linear player gain. The platform owns attenuation when it does not dispatch CAN_DUCK.
    private fun effectiveVolume() = if (state == State.DUCKED) volume * 0.2f else volume
}
