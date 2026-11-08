package com.pleosstay.app.entertainment.audio

import android.content.Context
import android.media.*
import android.os.Handler
import android.os.Looper
import com.pleosstay.app.entertainment.karaoke.KaraokeSong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Lifecycle owner calls pause/release; focus gain alone never resumes playback or recording. */
class AndroidKaraokeBackingPlayer(context:Context):KaraokeBackingPlayer {
    private val manager=context.applicationContext.getSystemService(AudioManager::class.java)
    private val attributes=AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
    private var player:MediaPlayer?=null
    private var focus:AudioFocusRequest?=null
    private var playbackEpoch=0L
    override var onEvent:((PlaybackEvent)->Unit)?=null
    override suspend fun prepare(song:KaraokeSong)=withContext(Dispatchers.Main.immediate) {
        release()
        check(song.accompaniment.isFile && song.accompaniment.length()>44){"반주 파일이 없습니다."}
        val next=MediaPlayer()
        try {
            next.setAudioAttributes(attributes);next.setDataSource(song.accompaniment.absolutePath);next.prepare()
            check(kotlin.math.abs(next.duration.toLong()-song.durationMillis)<250){"반주와 곡 타임라인 길이가 다릅니다."}
            next.setOnCompletionListener{if(player===next)onEvent?.invoke(PlaybackEvent.COMPLETED)}
            next.setOnErrorListener{_,_,_->if(player===next){interrupt();onEvent?.invoke(PlaybackEvent.ERROR)};true}
            player=next
        } catch(error:Exception){next.release();throw error}
    }
    override suspend fun seekTo(positionMillis:Long)=withContext(Dispatchers.Main.immediate){ checkNotNull(player).seekTo(positionMillis,MediaPlayer.SEEK_CLOSEST) }
    override suspend fun play()=withContext(Dispatchers.Main.immediate) {
        val active=checkNotNull(player)
        val epoch=++playbackEpoch
        val request=AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).setAudioAttributes(attributes)
            .setOnAudioFocusChangeListener({change->
                if(epoch==playbackEpoch && change!=AudioManager.AUDIOFOCUS_GAIN){interrupt();onEvent?.invoke(PlaybackEvent.INTERRUPTED)}
            },Handler(Looper.getMainLooper())).build()
        check(manager?.requestAudioFocus(request)==AudioManager.AUDIOFOCUS_REQUEST_GRANTED){"오디오 포커스 요청이 거절되었습니다."}
        focus=request
        try{active.start()}catch(error:Exception){interrupt();throw error}
    }
    override suspend fun pause()=withContext(Dispatchers.Main.immediate){interrupt()}
    @Synchronized override fun positionMillis():Long=player?.currentPosition?.toLong()?:0L
    @Synchronized override fun interrupt(){
        runCatching{player?.let{if(it.isPlaying)it.pause()}}
        focus?.let{manager?.abandonAudioFocusRequest(it)};focus=null
    }
    override suspend fun release()=withContext(Dispatchers.Main.immediate){
        playbackEpoch++;interrupt();player?.release();player=null
    }
}
