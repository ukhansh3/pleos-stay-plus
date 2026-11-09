package com.pleosstay.app.audio

import android.media.AudioManager
import org.junit.Assert.assertEquals
import org.junit.Test

class MediaFocusPlaybackTest {
    private class Playback(resumeAfterTransient: Boolean = true) {
        var allowed = true
        var pauses = 0
        var starts = 0
        var interruptions = 0
        val volumes = mutableListOf<Float>()
        val session = MediaFocusPlayback(
            canResumeAfterTransientLoss = resumeAfterTransient,
            isAllowed = { allowed },
            applyVolume = { volumes += it },
            pause = { pauses++ },
            resume = { starts++ },
            interrupt = { interruptions++ },
        )

        fun focus(vararg changes: Int) = changes.forEach(session::onFocusChange)
    }

    @Test fun systemDuckingWithoutCallbackDoesNotChangePlayerGain() {
        val playback = Playback()
        playback.session.setVolume(0.7f)
        playback.focus(AudioManager.AUDIOFOCUS_GAIN)
        assertEquals(listOf(0.7f), playback.volumes)
        assertEquals(0, playback.starts)
    }

    @Test fun explicitDuckAppliesOnceAndRestoresUpdatedVolumeOnGain() {
        val playback = Playback()
        playback.session.setVolume(0.5f)
        playback.focus(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK)
        playback.session.setVolume(0.25f)
        playback.focus(AudioManager.AUDIOFOCUS_GAIN, AudioManager.AUDIOFOCUS_GAIN)
        assertEquals(listOf(0.5f, 0.1f, 0.05f, 0.25f), playback.volumes)
        assertEquals(0, playback.pauses)
        assertEquals(0, playback.starts)
    }

    @Test fun muteIsPreservedAcrossExplicitDuckAndGain() {
        val playback = Playback()
        playback.session.setVolume(0f)
        playback.focus(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK, AudioManager.AUDIOFOCUS_GAIN)
        assertEquals(listOf(0f, 0f, 0f), playback.volumes)
    }

    @Test fun restTransientPauseSurvivesRepeatedAndWeakerLossBeforeGain() {
        val playback = Playback()
        playback.focus(
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_GAIN,
            AudioManager.AUDIOFOCUS_GAIN,
        )
        assertEquals(listOf(0.2f, 1f), playback.volumes)
        assertEquals(1, playback.pauses)
        assertEquals(1, playback.starts)
    }

    @Test fun permanentLossOrUnsafeStateClosesPlaybackAndIgnoresLateGain() {
        val lost = Playback()
        lost.focus(AudioManager.AUDIOFOCUS_LOSS, AudioManager.AUDIOFOCUS_GAIN, AudioManager.AUDIOFOCUS_LOSS)
        assertEquals(1, lost.interruptions)
        assertEquals(0, lost.starts)

        val unsafe = Playback()
        unsafe.focus(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)
        unsafe.allowed = false
        unsafe.focus(AudioManager.AUDIOFOCUS_GAIN)
        unsafe.allowed = true
        unsafe.focus(AudioManager.AUDIOFOCUS_GAIN)
        assertEquals(1, unsafe.interruptions)
        assertEquals(0, unsafe.starts)
    }

    @Test fun userStopOrLifecycleStopIgnoresCallbacksFromPreviousRequest() {
        val playback = Playback()
        playback.focus(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)
        playback.session.close()
        playback.focus(AudioManager.AUDIOFOCUS_GAIN, AudioManager.AUDIOFOCUS_LOSS)
        assertEquals(0, playback.starts)
        assertEquals(0, playback.interruptions)
    }

    @Test fun karaokeCanDuckButCannotAutomaticallyResumeAfterInterruption() {
        val playback = Playback(resumeAfterTransient = false)
        playback.focus(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK, AudioManager.AUDIOFOCUS_GAIN)
        assertEquals(listOf(0.2f, 1f), playback.volumes)
        playback.focus(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT, AudioManager.AUDIOFOCUS_GAIN)
        assertEquals(1, playback.interruptions)
        assertEquals(0, playback.starts)
    }
}
