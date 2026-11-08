package com.pleosstay.app.entertainment

import android.content.Context
import com.pleosstay.app.entertainment.audio.*
import com.pleosstay.app.entertainment.data.*
import com.pleosstay.app.entertainment.games.*
import com.pleosstay.app.entertainment.quiz.*
import com.pleosstay.app.entertainment.karaoke.*
import com.pleosstay.app.entertainment.launcher.*
import com.pleosstay.app.entertainment.recommendation.*
import com.pleosstay.app.waiting.data.WaitingRepository
import com.pleosstay.app.waiting.safety.WaitingSafetyGateway
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File

/** Create once in a host-owned scope after common-mode integration, not inside a screen composition. */
class EntertainmentRuntime(val controller:EntertainmentController,val game:Game2048Service,
    val quiz:QuizService,val karaoke:KaraokeSession,val history:KaraokeHistory,val video:VideoAppLauncher,
    val songs:List<KaraokeSong>,val recommendations:WaitingRecommendationFeed,
    private val availability:MutableStateFlow<Map<EntertainmentContent,ContentAvailability>>,
    val match:QuizMatchService=QuizMatchService()) {
    suspend fun select2048()=controller.select(EntertainmentContent.GAME_2048)
    suspend fun new2048()=controller.use(EntertainmentContent.GAME_2048){game.newGame()}
    suspend fun move2048(direction:MoveDirection)=controller.use(EntertainmentContent.GAME_2048){game.move(direction)}
    suspend fun selectQuiz()=controller.select(EntertainmentContent.QUIZ)
    suspend fun newQuiz()=controller.use(EntertainmentContent.QUIZ){quiz.start()}
    suspend fun answerQuiz(questionId:String,answer:Int)=controller.use(EntertainmentContent.QUIZ){quiz.submit(questionId,answer)}
    suspend fun nextQuiz()=controller.use(EntertainmentContent.QUIZ){quiz.next()}
    suspend fun selectMatch()=controller.select(EntertainmentContent.QUIZ_MATCH)
    suspend fun startMatch(config:MatchConfig)=controller.use(EntertainmentContent.QUIZ_MATCH){match.start(config)}
    suspend fun answerMatch(id:String,option:Int)=controller.use(EntertainmentContent.QUIZ_MATCH){match.submit(id,option)}
    suspend fun nextMatch()=controller.use(EntertainmentContent.QUIZ_MATCH){match.next()}
    suspend fun resetMatch()=controller.use(EntertainmentContent.QUIZ_MATCH){match.reset()}
    suspend fun selectKaraoke()=controller.select(EntertainmentContent.KARAOKE)
    suspend fun chooseSong(id:String)=controller.use(EntertainmentContent.KARAOKE){karaoke.select(songs.first{it.id==id})}
    suspend fun startKaraoke()=controller.use(EntertainmentContent.KARAOKE){karaoke.startOrResume()}
    suspend fun pauseKaraoke()=controller.use(EntertainmentContent.KARAOKE){karaoke.pause()}
    suspend fun stopKaraoke()=controller.use(EntertainmentContent.KARAOKE){karaoke.stop()}
    suspend fun karaokeScorePreview()=controller.use(EntertainmentContent.KARAOKE){karaoke.scorePreview()}
    suspend fun retryKaraokeSave()=controller.use(EntertainmentContent.KARAOKE){karaoke.retrySave()}
    suspend fun launchVideo(app:VideoApp)=controller.select(if(app==VideoApp.YOUTUBE)EntertainmentContent.YOUTUBE else EntertainmentContent.NETFLIX)
    /** Unknown full-duplex PCM capability stays unverified until an actual device probe proves it. */
    fun refreshAvailability(karaokeDeviceValidated:Boolean=false) {
        val values=EntertainmentContent.entries.associateWith{content->when(content){
            EntertainmentContent.GAME_2048,EntertainmentContent.QUIZ,EntertainmentContent.QUIZ_MATCH->ContentAvailability(Availability.AVAILABLE)
            EntertainmentContent.KARAOKE-> {
                val mic=karaoke.microphoneSupport()
                if(!mic.available)ContentAvailability(Availability.UNAVAILABLE,mic.reason)
                else if(karaokeDeviceValidated)ContentAvailability(Availability.AVAILABLE)
                else ContentAvailability(Availability.UNVERIFIED,"실제 PCM·반주 동시 사용 검증 필요")
            }
            else->{val result=video.availability(if(content==EntertainmentContent.YOUTUBE)VideoApp.YOUTUBE else VideoApp.NETFLIX)
                ContentAvailability(when(result.status){LaunchStatus.AVAILABLE->Availability.AVAILABLE;LaunchStatus.INSTALLATION_UNKNOWN->Availability.UNVERIFIED;else->Availability.UNAVAILABLE},result.message)}
        }};availability.value=values
    }
    companion object {
        fun create(context:Context,scope:CoroutineScope,safety:WaitingSafetyGateway,waiting:WaitingRepository,
            admission:EntertainmentActivityAdmission,verifiedVideoTargets:List<VerifiedVideoTarget>):EntertainmentRuntime {
            val directory=File(context.noBackupFilesDir,"entertainment-v1")
            val store=FileEntertainmentStore(directory)
            val game=Game2048Service(store);val quiz=QuizService(store);val match=QuizMatchService();val history=KaraokeHistory(store)
            val songs=listOf(OriginalExercise.song(OriginalBackingTrack.create(File(directory,"backing"))))
            val video=VideoAppLauncher(AndroidVideoAppPlatform(context),verifiedVideoTargets){safety.state.value}
            lateinit var controller:EntertainmentController
            val karaoke=KaraokeSession(AndroidKaraokeMicrophone(context),AndroidKaraokeBackingPlayer(context),history,store,scope,
                {safety.state.value},{controller.foreground() && controller.state.value.active && controller.state.value.content==EntertainmentContent.KARAOKE})
            val sessions=mutableMapOf<EntertainmentContent,EntertainmentContentSession>()
            // Game and quiz mutations are persisted by their services before returning. No audio to release.
            sessions[EntertainmentContent.GAME_2048]=object:EntertainmentContentSession {
                override suspend fun enter(){if(game.resume()==null)game.newGame()}
                override suspend fun pause(){game.resume()}
                override suspend fun stop(){game.resume()}
                override fun interrupt()=Unit
            }
            sessions[EntertainmentContent.QUIZ]=object:EntertainmentContentSession {
                override suspend fun enter(){if(quiz.resume()==null)quiz.start()}
                override suspend fun pause(){quiz.resume()}
                override suspend fun stop(){quiz.resume()}
                override fun interrupt()=Unit
            }
            sessions[EntertainmentContent.QUIZ_MATCH]=object:EntertainmentContentSession {
                override suspend fun enter(){match.enter()}
                override suspend fun pause(){match.interrupt()}
                override suspend fun stop(){match.stop()}
                override fun interrupt(){match.interrupt()}
            }
            sessions[EntertainmentContent.KARAOKE]=object:EntertainmentContentSession {
                override suspend fun enter(){
                    if(karaoke.state.value.phase in setOf(KaraokePhase.UNPREPARED,KaraokePhase.USER_STOPPED,KaraokePhase.COMPLETED,KaraokePhase.INPUT_ERROR)){
                        if(!karaoke.restore(songs.first()))karaoke.select(songs.first())
                    }
                    // Selecting the catalogue never starts a microphone. The UI calls startKaraoke explicitly.
                }
                override suspend fun pause(){karaoke.pause(safetyStop=!safety.state.value.canUse)}
                override suspend fun stop(){karaoke.stop()}
                override fun interrupt(){karaoke.interrupt()}
            }
            VideoApp.entries.forEach{app->sessions[if(app==VideoApp.YOUTUBE)EntertainmentContent.YOUTUBE else EntertainmentContent.NETFLIX]=object:EntertainmentContentSession{
                override suspend fun enter(){val result=video.launch(app);check(result.status==LaunchStatus.LAUNCHED){result.message}}
                override suspend fun pause()=Unit // Stay+ does not own the external player's lifecycle.
                override suspend fun stop()=Unit
                override fun interrupt()=Unit
            }}
            controller=EntertainmentController(admission,safety,sessions,store,scope,onEnded=match::stop)
            val availability=MutableStateFlow<Map<EntertainmentContent,ContentAvailability>>(emptyMap())
            val result=EntertainmentRuntime(controller,game,quiz,karaoke,history,video,songs,
                WaitingRecommendationFeed(waiting,safety.state,availability),availability,match)
            result.refreshAvailability();return result
        }
    }
}
