package com.pleosstay.app.entertainment.karaoke

import java.io.File

data class LyricCue(val startMillis:Long,val endMillis:Long,val text:String) {
    init { require(startMillis>=0 && endMillis>startMillis && text.isNotBlank()) }
}
data class MelodyNote(val startMillis:Long,val endMillis:Long,val midi:Int?) {
    init { require(startMillis>=0 && endMillis>startMillis && (midi==null || midi in 24..96)) }
    val hz:Double? get()=midi?.let{440.0*Math.pow(2.0,(it-69)/12.0)}
}
data class KaraokeSong(val id:String,val title:String,val durationMillis:Long,val accompaniment:File,
    val lyrics:List<LyricCue>,val melody:List<MelodyNote>,val version:Int,val rights:String,val testContent:Boolean=false) {
    init {
        require(id.isNotBlank() && title.isNotBlank() && durationMillis in 1..600_000 && version>0 && rights.isNotBlank())
        require(melody.isNotEmpty() && melody.any{it.midi!=null})
        require(lyrics.all{it.endMillis<=durationMillis} && melody.all{it.endMillis<=durationMillis})
        require(lyrics.zipWithNext().all{(a,b)->a.endMillis<=b.startMillis})
        require(melody.zipWithNext().all{(a,b)->a.endMillis<=b.startMillis})
    }
    fun lyricsAt(positionMillis:Long)=lyrics.firstOrNull{positionMillis>=it.startMillis && positionMillis<it.endMillis}
    fun nextLyrics(positionMillis:Long)=lyrics.firstOrNull{it.startMillis>positionMillis}
    fun noteAt(positionMillis:Long)=melody.firstOrNull{positionMillis>=it.startMillis && positionMillis<it.endMillis}
}
/** Original 8-second exercise. Backing chords omit the scored lead melody. Not a commercial catalogue. */
object OriginalExercise {
    fun song(wav:File)=KaraokeSong("stay-exercise-v1","Stay+ 음정 연습",8_000,wav,
        listOf(LyricCue(0,2_000,"잠시 쉬어"),LyricCue(2_000,4_000,"숨을 고르고"),LyricCue(4_000,6_000,"천천히 노래해"),LyricCue(6_000,8_000,"다시 힘내요")),
        listOf(MelodyNote(0,2_000,60),MelodyNote(2_000,4_000,62),MelodyNote(4_000,6_000,64),MelodyNote(6_000,7_500,67),MelodyNote(7_500,8_000,null)),
        1,"Stay+ original generated chord accompaniment, original exercise lyrics and reference notes; no third-party samples.",true)
}
