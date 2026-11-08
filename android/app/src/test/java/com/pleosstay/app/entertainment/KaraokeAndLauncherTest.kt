package com.pleosstay.app.entertainment

import com.pleosstay.app.entertainment.karaoke.*
import com.pleosstay.app.entertainment.launcher.*
import com.pleosstay.app.waiting.safety.*
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.*
import kotlin.random.Random
import org.junit.Assert.*
import org.junit.Test

internal fun pcm(hz:Double,rate:Int=44_100,amplitude:Double=0.5,duration:Double=0.06)=FloatArray((rate*duration).toInt()){(amplitude*sin(2*PI*hz*it/rate)).toFloat()}
internal fun song()=OriginalExercise.song(File("test-only.wav"))
class YinPitchTest {
    private val detector=YinPitchDetector()
    @Test fun knownFrequencies(){listOf(220.0,440.0,880.0).forEach{hz->val result=detector.analyze(pcm(hz),44_100);assertEquals(PitchQuality.VOICED,result.quality);assertEquals(hz,result.hz!!,2.0);assertTrue(result.confidence>0.95)}}
    @Test fun changedSampleRates(){listOf(8_000,16_000,48_000).forEach{rate->assertEquals(440.0,detector.analyze(pcm(440.0,rate),rate).hz!!,4.0)}}
    @Test fun centsSignAndOctave(){assertEquals(0.0,YinPitchDetector.cents(440.0,440.0),0.001);assertEquals(1200.0,YinPitchDetector.cents(880.0,440.0),0.001);assertTrue(YinPitchDetector.cents(430.0,440.0)<0)}
    @Test fun silenceAndQuiet(){assertEquals(PitchQuality.SILENCE,detector.analyze(FloatArray(3000),44_100).quality);assertEquals(PitchQuality.TOO_QUIET,detector.analyze(pcm(440.0,amplitude=0.00001),44_100).quality)}
    @Test fun noiseIsNotVoiced(){val r=Random(55);val result=detector.analyze(FloatArray(3000){r.nextFloat()-0.5f},44_100);assertEquals(PitchQuality.NOISY,result.quality)}
    @Test fun clippedInputRejected(){assertEquals(PitchQuality.CLIPPED,detector.analyze(FloatArray(3000){if(it%100<50)1f else -1f},44_100).quality)}
    @Test fun shortAndMalformedInput(){assertEquals(PitchQuality.TOO_SHORT,detector.analyze(FloatArray(10),44_100).quality);assertEquals(PitchQuality.INVALID,detector.analyze(floatArrayOf(Float.NaN),44_100).quality);assertEquals(PitchQuality.INVALID,detector.analyze(FloatArray(3000),0).quality)}
    @Test fun changedPitchAcrossFrames(){val a=detector.analyze(pcm(220.0),44_100);val b=detector.analyze(pcm(440.0),44_100);assertTrue(b.hz!!>a.hz!!*1.9)}
    @Test fun dcInputDoesNotPretendVoice(){assertEquals(PitchQuality.NOISY,detector.analyze(FloatArray(3000){0.2f},44_100).quality)}
}
class KaraokeScoreTest {
    private val song=song();private val scorer=KaraokeScorer()
    private fun matching(multiplier:Double=1.0)=song.melody.filter{it.midi!=null}.map{PitchFrame(it.startMillis,it.endMillis,PitchEstimate(it.hz!!*multiplier,0.99,PitchQuality.VOICED))}
    @Test fun perfectPitchAndWrongOctave(){assertEquals(100,scorer.score(song,matching()).score);assertEquals(0,scorer.score(song,matching(2.0)).score)}
    @Test fun actualPcmFeedsScore(){val detector=YinPitchDetector();val frames=song.melody.filter{it.midi!=null}.map{PitchFrame(it.startMillis,it.endMillis,detector.analyze(pcm(it.hz!!),44_100))};assertTrue(scorer.score(song,frames).score!!>=99)}
    @Test fun shorterCoverageCannotGetFullScore(){val result=scorer.score(song,matching().take(1));assertEquals(27,result.score);assertTrue(result.coverage<0.3)}
    @Test fun insufficientAndSilenceHaveNoScore(){assertNull(scorer.score(song,emptyList()).score);assertEquals(KaraokeResultStatus.INSUFFICIENT_INPUT,scorer.score(song,listOf(PitchFrame(0,7500,PitchEstimate(null,0.0,PitchQuality.SILENCE)))).status)}
    @Test fun errorsAndLeakageHaveNoScore(){assertEquals(KaraokeResultStatus.INPUT_ERROR,scorer.score(song,matching(),inputError=true).status);assertNull(scorer.score(song,matching(),leakageSuspected=true).score)}
    @Test fun overlapAndDuplicatesRejected(){assertThrows(IllegalArgumentException::class.java){scorer.score(song,matching()+matching())}}
    @Test fun timeAlignmentAndLatency(){val delayed=matching().map{it.copy(startMillis=it.startMillis+100,endMillis=it.endMillis+100)};assertEquals(100,scorer.score(song,delayed,inputLatencyMillis=100).score);assertTrue(scorer.score(song,delayed).score!!<100)}
    @Test fun reorderedMelodyIsNotAnAveragePitchScore(){val frames=matching().mapIndexed{i,f->f.copy(estimate=f.estimate.copy(hz=matching().reversed()[i].estimate.hz))};assertTrue(scorer.score(song,frames).score!!<50)}
    @Test fun invalidConfidenceCannotScore(){assertNull(scorer.score(song,matching().map{it.copy(estimate=it.estimate.copy(confidence=Double.NaN))}).score)}
}
class KaraokeContentAndHistoryTest {
    @Test fun lyricAndRestTimelineBoundaries(){val s=song();assertEquals("잠시 쉬어",s.lyricsAt(1999)?.text);assertEquals("숨을 고르고",s.lyricsAt(2000)?.text);assertEquals("숨을 고르고",s.nextLyrics(0)?.text);assertNull(s.lyricsAt(8000));assertNull(s.noteAt(7500)?.midi)}
    @Test fun originalBackingIsActualPcmWave(){val bytes=OriginalBackingTrack.wavBytes();assertEquals("RIFF",String(bytes,0,4,Charsets.US_ASCII));val b=ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);assertEquals(16000,b.getInt(24));assertEquals(256000,b.getInt(40));assertEquals(256044,bytes.size);assertTrue(bytes.drop(44).any{it!=0.toByte()})}
    @Test fun recordsBestAndIdempotency(){val store=MemoryEntertainmentStore();val h=KaraokeHistory(store);h.save(KaraokeRecord("s1","song","제목",91,1));h.save(KaraokeRecord("s1","song","제목",91,1));h.save(KaraokeRecord("s2","song","제목",50,2));assertEquals(2,h.records().size);assertEquals(91,KaraokeHistory(store).bestScores()["song"])}
    @Test fun lifetimeBestSurvivesBoundedHistory(){val h=KaraokeHistory(MemoryEntertainmentStore());h.save(KaraokeRecord("best","song","제목",100,1));repeat(201){h.save(KaraokeRecord("s$it","song","제목",10,it.toLong()))};assertEquals(200,h.records().size);assertEquals(100,h.bestScores()["song"])}
    @Test fun saveFailureDoesNotClaimSuccess(){val store=MemoryEntertainmentStore();val h=KaraokeHistory(store);store.fail=true;assertThrows(IllegalStateException::class.java){h.save(KaraokeRecord("s","song","제목",90,0))};assertTrue(h.records().isEmpty())}
    @Test fun malformedHistoryFailsClearly(){val store=MemoryEntertainmentStore();store.records["karaoke-history"]=byteArrayOf(0);assertThrows(com.pleosstay.app.entertainment.data.CorruptEntertainmentData::class.java){KaraokeHistory(store).records()}}
}
class VideoAppLauncherTest {
    private class Platform:VideoAppPlatform {
        var status=LaunchStatus.AVAILABLE;var launchStatus=LaunchStatus.LAUNCHED;var launches=0
        override fun inspect(target:VerifiedVideoTarget)=LaunchResult(status,"test")
        override fun launch(target:VerifiedVideoTarget):LaunchResult{launches++;return LaunchResult(launchStatus,"test")}
    }
    private val target=VerifiedVideoTarget(VideoApp.YOUTUBE,"test.verified.youtube","unit-test target only")
    @Test fun unknownTargetDoesNotLaunch(){val p=Platform();val service=VideoAppLauncher(p,emptyList()){WaitingSafety(VehicleMotion.PARKED,false)};assertEquals(LaunchStatus.INSTALLATION_UNKNOWN,service.launch(VideoApp.YOUTUBE).status);assertEquals(0,p.launches)}
    @Test fun unsafeBlocksBeforePlatform(){val p=Platform();val service=VideoAppLauncher(p,listOf(target)){WaitingSafety()};assertEquals(LaunchStatus.SAFETY_BLOCKED,service.launch(VideoApp.YOUTUBE).status);assertEquals(0,p.launches)}
    @Test fun everyNonAvailableStatusBlocksLaunch(){val p=Platform();val service=VideoAppLauncher(p,listOf(target)){WaitingSafety(VehicleMotion.PARKED,false)};listOf(LaunchStatus.NOT_INSTALLED,LaunchStatus.INSTALLATION_UNKNOWN,LaunchStatus.NO_ENTRY_POINT,LaunchStatus.POLICY_BLOCKED,LaunchStatus.ERROR).forEach{p.status=it;assertEquals(it,service.launch(VideoApp.YOUTUBE).status)};assertEquals(0,p.launches)}
    @Test fun actualPlatformFailurePropagates(){val p=Platform();p.launchStatus=LaunchStatus.ERROR;val service=VideoAppLauncher(p,listOf(target)){WaitingSafety(VehicleMotion.PARKED,false)};assertEquals(LaunchStatus.ERROR,service.launch(VideoApp.YOUTUBE).status);assertEquals(1,p.launches)}
    @Test fun netflixIsSeparateTarget(){val p=Platform();val service=VideoAppLauncher(p,listOf(target)){WaitingSafety(VehicleMotion.PARKED,false)};assertEquals(LaunchStatus.INSTALLATION_UNKNOWN,service.availability(VideoApp.NETFLIX).status)}
    @Test fun safetyRecheckedBeforeLaunch(){val p=Platform();var calls=0;val service=VideoAppLauncher(p,listOf(target)){calls++;if(calls==1)WaitingSafety(VehicleMotion.PARKED,false)else WaitingSafety()};assertEquals(LaunchStatus.SAFETY_BLOCKED,service.launch(VideoApp.YOUTUBE).status);assertEquals(0,p.launches)}
}
