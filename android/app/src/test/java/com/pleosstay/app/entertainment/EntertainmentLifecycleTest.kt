package com.pleosstay.app.entertainment

import com.pleosstay.app.entertainment.audio.*
import com.pleosstay.app.entertainment.karaoke.*
import com.pleosstay.app.entertainment.recommendation.*
import com.pleosstay.app.entertainment.data.*
import com.pleosstay.app.waiting.data.*
import com.pleosstay.app.waiting.domain.*
import com.pleosstay.app.waiting.safety.*
import com.pleosstay.app.waiting.MemoryWaitingStore
import com.pleosstay.app.waiting.MemoryJournal
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

internal class EntertainmentSafety:WaitingSafetyGateway {
    override val state=MutableStateFlow(WaitingSafety(VehicleMotion.PARKED,false))
    override val drivingEvents=DrivingEventBuffer()
    override fun start()=Unit
    override fun stop()=Unit
    override fun close()=Unit
}
internal class Admission:EntertainmentActivityAdmission {
    var acquired=0;var released=0;var refuse=false;var failRelease=false
    override suspend fun acquire():String {check(!refuse){"another activity active"};acquired++;return "permit-$acquired"}
    override suspend fun release(permit:String){check(!failRelease);released++}
}
internal class Content:EntertainmentContentSession {
    var starts=0;var pauses=0;var stops=0;var interrupts=0;var failStop=false;var failStart=false
    override suspend fun enter(){check(!failStart);starts++}
    override suspend fun pause(){pauses++}
    override suspend fun stop(){check(!failStop);stops++}
    override fun interrupt(){interrupts++}
}
@OptIn(ExperimentalCoroutinesApi::class)
class EntertainmentControllerTest {
    @Test fun catalogueKeepsMatchButExplicitEndDiscardsNames()=runTest {
        val match=com.pleosstay.app.entertainment.quiz.QuizMatchService();match.enter()
        val original=match.start(com.pleosstay.app.entertainment.quiz.MatchConfig(listOf("A","B")))
        val content=Content()
        val controller=EntertainmentController(Admission(),EntertainmentSafety(),mapOf(EntertainmentContent.QUIZ_MATCH to content),MemoryEntertainmentStore(),backgroundScope,onEnded=match::stop)
        controller.onHostForeground(true);assertTrue(controller.start().success)
        assertTrue(controller.select(EntertainmentContent.QUIZ_MATCH).success)
        assertTrue(controller.catalogue().success);assertEquals(original,match.state.value)
        assertTrue(controller.end().success);assertNull(match.state.value)
    }
    @Test fun admissionRequiredAndSameContentIsIdempotent()=runTest {
        val admission=Admission();val c=Content();val controller=EntertainmentController(admission,EntertainmentSafety(),mapOf(EntertainmentContent.GAME_2048 to c),MemoryEntertainmentStore(),backgroundScope)
        controller.onHostForeground(true);admission.refuse=true;assertFalse(controller.start().success)
        admission.refuse=false;assertTrue(controller.start().success);assertTrue(controller.start().success)
        assertTrue(controller.select(EntertainmentContent.GAME_2048).success);assertTrue(controller.select(EntertainmentContent.GAME_2048).success)
        assertEquals(1,admission.acquired);assertEquals(1,c.starts);controller.close()
    }
    @Test fun stopFailurePreventsNextContent()=runTest {
        val old=Content();val next=Content();val controller=EntertainmentController(Admission(),EntertainmentSafety(),mapOf(EntertainmentContent.KARAOKE to old,EntertainmentContent.GAME_2048 to next),MemoryEntertainmentStore(),backgroundScope)
        controller.onHostForeground(true);controller.start();controller.select(EntertainmentContent.KARAOKE);old.failStop=true
        assertFalse(controller.select(EntertainmentContent.GAME_2048).success);assertEquals(0,next.starts);assertEquals(EntertainmentContent.KARAOKE,controller.state.value.content)
        old.failStop=false;assertTrue(controller.end().success)
    }
    @Test fun startFailureDoesNotRestoreEndedContent()=runTest {
        val old=Content();val next=Content().apply{failStart=true};val controller=EntertainmentController(Admission(),EntertainmentSafety(),mapOf(EntertainmentContent.KARAOKE to old,EntertainmentContent.QUIZ to next),MemoryEntertainmentStore(),backgroundScope)
        controller.onHostForeground(true);controller.start();controller.select(EntertainmentContent.KARAOKE)
        assertFalse(controller.select(EntertainmentContent.QUIZ).success);assertEquals(1,old.stops);assertEquals(1,old.starts);assertEquals(EntertainmentPhase.ERROR,controller.state.value.phase);controller.close()
    }
    @Test fun internalHomeKeepsActivityButNeedsManualResume()=runTest {
        val a=Admission();val c=Content();val store=MemoryEntertainmentStore();val controller=EntertainmentController(a,EntertainmentSafety(),mapOf(EntertainmentContent.QUIZ to c),store,backgroundScope)
        controller.onHostForeground(true);controller.start();controller.select(EntertainmentContent.QUIZ);controller.onInternalHome()
        assertTrue(controller.state.value.active);assertEquals(0,a.released);assertEquals(1,c.pauses)
        controller.onHostForeground(true);assertEquals(1,c.starts);controller.select(EntertainmentContent.QUIZ);assertEquals(2,c.starts);controller.close()
    }
    @Test fun drivingAndUnknownInterruptWithoutAutoResume()=runTest {
        val safe=EntertainmentSafety();val c=Content();val controller=EntertainmentController(Admission(),safe,mapOf(EntertainmentContent.KARAOKE to c),MemoryEntertainmentStore(),backgroundScope)
        controller.onHostForeground(true);controller.start();controller.select(EntertainmentContent.KARAOKE);runCurrent()
        safe.state.value=WaitingSafety();runCurrent();assertEquals(EntertainmentPhase.BLOCKED,controller.state.value.phase);assertTrue(c.interrupts>0)
        assertFails{controller.use(EntertainmentContent.KARAOKE){true}}
        safe.state.value=WaitingSafety(VehicleMotion.PARKED,false);runCurrent();assertEquals(1,c.starts)
        controller.select(EntertainmentContent.KARAOKE);assertEquals(2,c.starts);controller.close()
    }
    @Test fun duplicateEndsAndReleaseFailure()=runTest {
        val a=Admission();val c=Content();val controller=EntertainmentController(a,EntertainmentSafety(),mapOf(EntertainmentContent.QUIZ to c),MemoryEntertainmentStore(),backgroundScope)
        controller.onHostForeground(true);controller.start();controller.select(EntertainmentContent.QUIZ);a.failRelease=true
        assertFalse(controller.end().success);assertTrue(controller.state.value.active);a.failRelease=false
        assertTrue(controller.end().success);assertTrue(controller.end().success);assertEquals(1,a.released);controller.close()
    }
    @Test fun concurrentSelectionsAreSerialized()=runTest {
        val game=Content();val quiz=Content();val controller=EntertainmentController(Admission(),EntertainmentSafety(),mapOf(EntertainmentContent.QUIZ to quiz,EntertainmentContent.GAME_2048 to game),MemoryEntertainmentStore(),backgroundScope)
        controller.onHostForeground(true);controller.start()
        (0..19).map{async{controller.select(if(it%2==0)EntertainmentContent.QUIZ else EntertainmentContent.GAME_2048)}}.awaitAll()
        assertEquals(20,game.starts+quiz.starts);assertEquals(19,game.stops+quiz.stops);controller.close()
    }
    @Test fun processRestoreNeverAutoStartsContentOrActivity()=runTest {
        val store=MemoryEntertainmentStore();val a=Admission();val content=Content();val safe=EntertainmentSafety()
        val first=EntertainmentController(a,safe,mapOf(EntertainmentContent.QUIZ to content),store,backgroundScope)
        first.onHostForeground(true);first.start();first.select(EntertainmentContent.QUIZ);first.onInternalHome()
        val restored=EntertainmentController(Admission(),safe,mapOf(EntertainmentContent.QUIZ to Content()),store,backgroundScope)
        assertTrue(restored.restore().success);assertFalse(restored.state.value.active);assertEquals(EntertainmentPhase.PAUSED,restored.state.value.phase)
        assertFalse(restored.select(EntertainmentContent.QUIZ).success);first.close();restored.close()
    }
    @Test fun realWaitingRepositoryRemainsUnchangedByRecommendationAndModeExit()=runTest {
        val safe=EntertainmentSafety();val storage=MemoryWaitingStore();val waiting=LocalWaitingRepository(storage,MemoryJournal(),{safe.state.value},WaitingClock{0},dispatcher=StandardTestDispatcher(testScheduler))
        waiting.restore();waiting.start(15);val before=waiting.sessions.value
        val availability=MutableStateFlow(EntertainmentContent.entries.associateWith{ContentAvailability(Availability.AVAILABLE)})
        val feed=WaitingRecommendationFeed(waiting,safe.state,availability){1_000}
        assertEquals(before.single().id,feed.observe().first().waitingId)
        val controller=EntertainmentController(Admission(),safe,emptyMap(),MemoryEntertainmentStore(),backgroundScope)
        controller.onHostForeground(true);controller.start();controller.end();assertEquals(before,waiting.sessions.value);controller.close()
    }
    @Test fun waitingFeedUpdatesFromRealSessionWithoutStoppingContent()=runTest {
        val safe=EntertainmentSafety();val storage=MemoryWaitingStore();val waiting=LocalWaitingRepository(storage,MemoryJournal(),{safe.state.value},WaitingClock{0},dispatcher=StandardTestDispatcher(testScheduler))
        waiting.restore();waiting.start(15)
        var now=0L
        val available=MutableStateFlow(EntertainmentContent.entries.associateWith{ContentAvailability(Availability.AVAILABLE)})
        val feed=WaitingRecommendationFeed(waiting,safe.state,available){now}
        val seen=mutableListOf<EntertainmentRecommendations>()
        val collector=backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)){feed.observe().collect{seen+=it}}
        val content=Content();val controller=EntertainmentController(Admission(),safe,mapOf(EntertainmentContent.GAME_2048 to content),MemoryEntertainmentStore(),backgroundScope)
        controller.onHostForeground(true);controller.start();controller.select(EntertainmentContent.GAME_2048)
        now=11*60_000;advanceTimeBy(1001);runCurrent();assertEquals(2,seen.last().contents.size)
        now=16*60_000;advanceTimeBy(1001);runCurrent();assertEquals(WaitingRecommendationStatus.OVERTIME,seen.last().status)
        assertEquals(0,content.stops);assertEquals(WaitingStatus.ACTIVE,waiting.sessions.value.single().status)
        collector.cancel();controller.close()
    }
    private suspend fun assertFails(block:suspend()->Unit){var failed=false;try{block()}catch(_:IllegalStateException){failed=true};assertTrue(failed)}
}
internal class TestMicrophone:KaraokeMicrophone {
    var allowed=true;var interrupts=0;var stops=0
    val input=MutableSharedFlow<PcmChunk>()
    override fun support()=MicrophoneSupport(allowed,if(allowed)"test-only PCM" else "permission missing",44_100,1)
    override fun frames():Flow<PcmChunk> = input
    override fun interrupt(){interrupts++}
    override suspend fun stop(){stops++}
}
internal class TestBacking:KaraokeBackingPlayer {
    override var onEvent:((PlaybackEvent)->Unit)?=null
    var position=0L;var plays=0;var releases=0;var prepared=0;var interrupted=0
    override suspend fun prepare(song:KaraokeSong){prepared++}
    override suspend fun seekTo(positionMillis:Long){position=positionMillis}
    override suspend fun play(){plays++}
    override suspend fun pause()=Unit
    override fun positionMillis()=position
    override fun interrupt(){interrupted++}
    override suspend fun release(){releases++}
}
@OptIn(ExperimentalCoroutinesApi::class)
class KaraokeSessionTest {
    @Test fun permissionFailureDoesNotStartPlayback()=runTest {
        val mic=TestMicrophone().apply{allowed=false};val player=TestBacking();val store=MemoryEntertainmentStore()
        val session=KaraokeSession(mic,player,KaraokeHistory(store),store,backgroundScope,{WaitingSafety(VehicleMotion.PARKED,false)},{true})
        session.select(song());var failed=false;try{session.startOrResume()}catch(_:IllegalStateException){failed=true};assertTrue(failed);assertEquals(0,player.plays)
    }
    @Test fun pcmAnalysisPauseRestoreAndExplicitResume()=runTest {
        val mic=TestMicrophone();val player=TestBacking();val store=MemoryEntertainmentStore();val safe=WaitingSafety(VehicleMotion.PARKED,false)
        val session=KaraokeSession(mic,player,KaraokeHistory(store),store,backgroundScope,{safe},{true},{1_000_000_000L})
        session.select(song());session.startOrResume();runCurrent();player.position=50
        mic.input.emit(PcmChunk(pcm(440.0),44_100,1_000_000_000));runCurrent();assertEquals(PitchQuality.VOICED,session.state.value.pitch!!.quality)
        session.pause();assertEquals(KaraokePhase.PAUSED,session.state.value.phase);assertEquals(1,player.plays)
        val restoredPlayer=TestBacking();val restored=KaraokeSession(TestMicrophone(),restoredPlayer,KaraokeHistory(store),store,backgroundScope,{safe},{true})
        assertTrue(restored.restore(song()));assertEquals(0,restoredPlayer.plays);assertEquals(50L,restored.state.value.positionMillis)
        restored.startOrResume();assertEquals(1,restoredPlayer.plays);assertEquals(50L,restoredPlayer.position);restored.stop();session.stop()
    }
    @Test fun completionScoresActualProvidedPcmAndSavesOnce()=runTest {
        val mic=TestMicrophone();val player=TestBacking();val store=MemoryEntertainmentStore();val h=KaraokeHistory(store);val safe=WaitingSafety(VehicleMotion.PARKED,false)
        val s=KaraokeSession(mic,player,h,store,backgroundScope,{safe},{true},{0})
        val song=song();s.select(song);s.startOrResume();runCurrent()
        for(note in song.melody.filter{it.midi!=null}){
            var at=note.startMillis
            while(at+50<=note.endMillis){player.position=at;mic.input.emit(PcmChunk(pcm(note.hz!!,duration=0.05),44_100,at*1_000_000));at+=50;runCurrent()}
        }
        player.position=8000;s.complete();assertEquals(KaraokePhase.COMPLETED,s.state.value.phase);assertTrue(s.state.value.result!!.score!!>=99);assertTrue(s.state.value.recordSaved)
        s.retrySave();assertEquals(1,h.records().size)
    }
    @Test fun silenceCompletesWithoutScoreRecord()=runTest {
        val mic=TestMicrophone();val player=TestBacking();val store=MemoryEntertainmentStore();val history=KaraokeHistory(store)
        val s=KaraokeSession(mic,player,history,store,backgroundScope,{WaitingSafety(VehicleMotion.PARKED,false)},{true},{0})
        s.select(song());s.startOrResume();runCurrent();mic.input.emit(PcmChunk(FloatArray(3000),44_100,0));runCurrent()
        player.position=8000;s.complete();assertNull(s.state.value.result!!.score);assertTrue(history.records().isEmpty())
    }
    @Test fun focusLossAndSafetyNeverAutoResume()=runTest {
        val mic=TestMicrophone();val player=TestBacking();val store=MemoryEntertainmentStore()
        val s=KaraokeSession(mic,player,KaraokeHistory(store),store,backgroundScope,{WaitingSafety(VehicleMotion.PARKED,false)},{true})
        s.select(song());s.startOrResume();runCurrent();player.onEvent!!.invoke(PlaybackEvent.INTERRUPTED);runCurrent()
        assertEquals(KaraokePhase.PAUSED,s.state.value.phase);assertEquals(1,player.plays);assertTrue(mic.stops>0);s.startOrResume();s.pause(safetyStop=true)
        assertEquals(KaraokePhase.SAFETY_STOPPED,s.state.value.phase);s.stop()
    }
    @Test fun duplicateStartRejectedAndStopIsSafeToRepeat()=runTest {
        val store=MemoryEntertainmentStore();val p=TestBacking();val s=KaraokeSession(TestMicrophone(),p,KaraokeHistory(store),store,backgroundScope,{WaitingSafety(VehicleMotion.PARKED,false)},{true})
        s.select(song());s.startOrResume();var failed=false;try{s.startOrResume()}catch(_:IllegalStateException){failed=true};assertTrue(failed);assertEquals(1,p.plays);s.stop();s.stop();assertEquals(KaraokePhase.USER_STOPPED,s.state.value.phase)
    }
}
