package com.pleosstay.app.entertainment.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import com.pleosstay.app.audio.MediaFocusPlayback
import com.pleosstay.app.audio.PlaybackScreenState
import com.pleosstay.app.entertainment.karaoke.KaraokeSong
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** Focus gain alone never resumes karaoke playback or recording after a pause. */
class AndroidKaraokeBackingPlayer(context: Context) : KaraokeBackingPlayer {
    private val manager = context.applicationContext.getSystemService(AudioManager::class.java)
    private val screen = PlaybackScreenState(context)
    private val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
    private var player: MediaPlayer? = null
    private var focus: AudioFocusRequest? = null
    private var playback: MediaFocusPlayback? = null
    override var onEvent: ((PlaybackEvent) -> Unit)? = null

    override suspend fun prepare(song: KaraokeSong) = withContext(Dispatchers.Main.immediate) {
        release()
        check(song.accompaniment.isFile && song.accompaniment.length() > 44) { "반주 파일이 없습니다." }
        val next = MediaPlayer()
        try {
            next.setAudioAttributes(attributes)
            next.setDataSource(song.accompaniment.absolutePath)
            next.prepare()
            check(kotlin.math.abs(next.duration.toLong() - song.durationMillis) < 250) {
                "반주와 곡 타임라인 길이가 다릅니다."
            }
            next.setOnCompletionListener {
                if (player === next && playback != null) onEvent?.invoke(PlaybackEvent.COMPLETED)
            }
            next.setOnErrorListener { _, _, _ ->
                if (player === next) signalInterruption(PlaybackEvent.ERROR)
                true
            }
            player = next
        } catch (error: Exception) {
            next.release()
            throw error
        }
    }

    override suspend fun seekTo(positionMillis: Long) = withContext(Dispatchers.Main.immediate) {
        val active = checkNotNull(player)
        val complete = CompletableDeferred<Unit>()
        active.setOnSeekCompleteListener { complete.complete(Unit) }
        try {
            active.seekTo(positionMillis, MediaPlayer.SEEK_CLOSEST)
            check(withTimeoutOrNull(3_000) { complete.await() } != null) { "반주 위치를 복원하지 못했습니다." }
        } finally {
            active.setOnSeekCompleteListener(null)
        }
    }

    override suspend fun play() = withContext(Dispatchers.Main.immediate) {
        check(screen.isInteractive) { "화면을 활성화한 뒤 다시 시작해 주세요." }
        val active = checkNotNull(player)
        val session = MediaFocusPlayback(
            canResumeAfterTransientLoss = false,
            isAllowed = { screen.isInteractive },
            applyVolume = { active.setVolume(it, it) },
            pause = { active.pause() },
            resume = { active.start() },
            interrupt = { signalInterruption(PlaybackEvent.INTERRUPTED) },
        )
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(attributes)
            .setWillPauseWhenDucked(false)
            .setOnAudioFocusChangeListener({ change ->
                if (playback === session) {
                    try { session.onFocusChange(change) } catch (_: RuntimeException) {
                        signalInterruption(PlaybackEvent.ERROR)
                    }
                }
            }, Handler(Looper.getMainLooper())).build()
        check(manager?.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            "오디오 포커스 요청이 거절되었습니다."
        }
        focus = request
        playback = session
        try {
            screen.watch { signalInterruption(PlaybackEvent.INTERRUPTED) }
            check(screen.isInteractive) { "화면을 활성화한 뒤 다시 시작해 주세요." }
            session.setVolume(1f)
            active.start()
        } catch (error: Exception) {
            interrupt()
            throw error
        }
    }

    private fun signalInterruption(event: PlaybackEvent) {
        interrupt()
        onEvent?.invoke(event)
    }

    override suspend fun pause() = withContext(Dispatchers.Main.immediate) { interrupt() }

    @Synchronized
    override fun positionMillis(): Long = runCatching { player?.currentPosition?.toLong() }.getOrNull() ?: 0L

    @Synchronized
    override fun interrupt() {
        playback?.close()
        playback = null
        screen.stop()
        runCatching { player?.let { if (it.isPlaying) it.pause() } }
        val previous = focus
        focus = null
        if (previous != null) runCatching { manager?.abandonAudioFocusRequest(previous) }
    }

    override suspend fun release() = withContext(Dispatchers.Main.immediate) {
        interrupt()
        val previous = player
        player = null
        previous?.release()
        Unit
    }
}
