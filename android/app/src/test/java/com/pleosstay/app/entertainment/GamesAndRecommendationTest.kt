package com.pleosstay.app.entertainment

import com.pleosstay.app.entertainment.data.*
import com.pleosstay.app.entertainment.games.*
import com.pleosstay.app.entertainment.quiz.*
import com.pleosstay.app.entertainment.recommendation.*
import com.pleosstay.app.waiting.domain.*
import com.pleosstay.app.waiting.safety.*
import java.io.File
import java.nio.file.Files
import java.util.concurrent.Executors
import kotlin.random.Random
import org.junit.Assert.*
import org.junit.Test

internal class MemoryEntertainmentStore : EntertainmentStore {
    val records = mutableMapOf<String, ByteArray>()
    var fail = false
    override fun read(key: String) = records[key]?.copyOf()
    override fun write(key: String, bytes: ByteArray) { check(!fail) { "disk failure" }; records[key] = bytes.copyOf() }
}
class Game2048Test {
    private val engine = Game2048Engine(Random(1))
    private fun board(vararg row: Int) = row.toList() + List(16 - row.size) { 0 }
    @Test fun initialTilesAndNewGame() { val s = engine.newGame(100); assertEquals(2, s.board.count { it != 0 }); assertEquals(100L, s.bestScore); assertEquals(0L, s.score) }
    @Test fun noDoubleMergeAndActualScore() {
        val a = engine.slide(board(2,2,2,2), MoveDirection.LEFT)
        assertEquals(board(4,4,0,0), a.board); assertEquals(8L,a.scoreGain)
        assertEquals(board(8,0,0,0),engine.slide(a.board,MoveDirection.LEFT).board)
        assertEquals(board(4,2,0,0),engine.slide(board(2,2,2,0),MoveDirection.LEFT).board)
    }
    @Test fun allDirections() {
        val b = board(2,0,0,0,2)
        assertEquals(4,engine.slide(b,MoveDirection.UP).board[0])
        assertEquals(4,engine.slide(b,MoveDirection.DOWN).board[12])
        assertEquals(2,engine.slide(b,MoveDirection.RIGHT).board[3])
        assertEquals(2,engine.slide(b,MoveDirection.LEFT).board[0])
    }
    @Test fun invalidMoveDoesNotSpawn() { val s=Game2048State(board(2)); assertEquals(s,engine.move(s,MoveDirection.LEFT)) }
    @Test fun validMoveSpawnsOneTile() { val s=Game2048State(board(2,2)); val n=engine.move(s,MoveDirection.RIGHT); assertEquals(2,n.board.count{it!=0}); assertEquals(4L,n.score) }
    @Test fun winAndGameOver() {
        assertTrue(Game2048State(board(2048)).won)
        val full=Game2048State(listOf(2,4,2,4,4,2,4,2,2,4,2,4,4,2,4,2))
        assertTrue(full.gameOver); assertEquals(full,engine.move(full,MoveDirection.DOWN))
        assertFalse(Game2048State(List(16){2}).gameOver)
    }
    @Test fun processRestoreAndBestSurvivesRestart() {
        val store=MemoryEntertainmentStore(); val service=Game2048Service(store,engine)
        var state=service.newGame()
        repeat(60) { state=service.move(MoveDirection.entries[it%4]) }
        assertEquals(state,Game2048Service(store).resume())
        assertEquals(state.bestScore,service.newGame().bestScore)
    }
    @Test fun corruptRecordRequiresExplicitReset() {
        val store=MemoryEntertainmentStore(); store.records["2048"]=byteArrayOf(1)
        val s=Game2048Service(store); assertThrows(CorruptEntertainmentData::class.java){s.resume()}; assertNotNull(s.resetCorruptGame())
    }
    @Test fun failedSaveDoesNotPublishMove() {
        val store=MemoryEntertainmentStore(); val s=Game2048Service(store,engine); val old=s.newGame(); store.fail=true
        assertThrows(IllegalStateException::class.java){s.newGame()}; assertEquals(old,s.resume())
    }
    @Test fun concurrentMovesSerializeAndPersist() {
        val store=MemoryEntertainmentStore(); val s=Game2048Service(store,engine); s.newGame()
        val pool=Executors.newFixedThreadPool(4)
        try { pool.invokeAll((0..39).map { java.util.concurrent.Callable { s.move(MoveDirection.entries[it%4]) } }).forEach{it.get()} }
        finally { pool.shutdownNow() }
        assertEquals(s.resume(),Game2048Service(store).resume())
    }
}
class QuizTest {
    @Test fun fiveDistinctQuestionsFromLargerBank() { val q=QuizService(MemoryEntertainmentStore(),Random(2)).start(); assertEquals(5,q.questions.map{it.id}.distinct().size); assertTrue(GeneralKnowledgeQuestions.all.size>5) }
    @Test fun noAnswerCannotAdvance() { val s=QuizService(MemoryEntertainmentStore()); s.start(); assertThrows(IllegalStateException::class.java){s.next()} }
    @Test fun correctWrongDuplicateAndCompletion() {
        val s=QuizService(MemoryEntertainmentStore(),Random(2)); var q=s.start()
        repeat(5) { i -> val question=q.current!!; val answer=if(i<3)question.answer else (question.answer+1)%question.options.size
            val submitted=s.submit(question.id,answer)
            assertEquals(i<3,submitted.currentCorrect)
            assertEquals(submitted,s.submit(question.id,(answer+1)%question.options.size))
            q=s.next()
        }
        assertTrue(q.complete); assertEquals(60,q.score); assertEquals(60,q.bestScore); assertNull(q.current); assertEquals(q,s.next())
        assertEquals(60,s.start().bestScore)
    }
    @Test fun restoreDoesNotDoubleScore() {
        val store=MemoryEntertainmentStore(); val s=QuizService(store); val q=s.start(); val expected=s.submit(q.current!!.id,q.current!!.answer)
        val restored=QuizService(store); assertEquals(expected,restored.resume()); assertEquals(expected,restored.submit(q.current!!.id,0))
    }
    @Test fun staleQuestionCannotAnswerNext() {
        val s=QuizService(MemoryEntertainmentStore()); val q=s.start(); s.submit(q.current!!.id,0);s.next()
        assertThrows(IllegalArgumentException::class.java){s.submit(q.current!!.id,0)}
    }
    @Test fun corruptAndFailedSave() {
        val store=MemoryEntertainmentStore();store.records["quiz"]=byteArrayOf(0)
        val s=QuizService(store);assertThrows(CorruptEntertainmentData::class.java){s.resume()};val q=s.resetCorruptQuiz();store.fail=true
        assertThrows(IllegalStateException::class.java){s.submit(q.current!!.id,0)};assertEquals(q,s.resume())
    }
}
class WaitingRecommendationTest {
    private val engine=WaitingRecommendationEngine()
    private val safe=WaitingSafety(VehicleMotion.PARKED,false)
    private val all=EntertainmentContent.entries.associateWith{ContentAvailability(Availability.AVAILABLE)}
    private fun at(ms:Long, available:Map<EntertainmentContent,ContentAvailability> = all)=engine.recommend(listOf(WaitingSession("w",0,ms)),0,safe,available)
    @Test fun exactBoundaries() {
        assertEquals(2,at(1).contents.size); assertEquals(2,at(300_000).contents.size)
        assertEquals(3,at(300_001).contents.size);assertTrue(at(900_000).contents.contains(EntertainmentContent.QUIZ))
        assertFalse(at(900_001).contents.contains(EntertainmentContent.QUIZ));assertEquals(3,at(1_800_000).contents.size)
        assertEquals(6,at(1_800_001).contents.size);assertTrue(at(1_800_001).contents.contains(EntertainmentContent.QUIZ_MATCH))
    }
    @Test fun absentExpiredEndedAndUnsafeAreDistinct() {
        assertEquals(WaitingRecommendationStatus.NO_WAITING,engine.recommend(emptyList(),0,safe,all).status)
        val active=WaitingSession("w",0,10)
        val over=engine.recommend(listOf(active),20,safe,all);assertEquals(WaitingRecommendationStatus.OVERTIME,over.status);assertEquals(10L,over.overtimeMillis)
        assertEquals(WaitingRecommendationStatus.NO_WAITING,engine.recommend(listOf(active.copy(status=WaitingStatus.ENDED)),20,safe,all).status)
        assertEquals(WaitingRecommendationStatus.SAFETY_BLOCKED,engine.recommend(listOf(active),0,WaitingSafety(),all).status)
    }
    @Test fun availabilityUnknownIsNotAvailable() { assertEquals(listOf(EntertainmentContent.GAME_2048),at(100, mapOf(EntertainmentContent.GAME_2048 to ContentAvailability(Availability.AVAILABLE),EntertainmentContent.QUIZ to ContentAvailability(Availability.UNVERIFIED))).contents) }
}
class FileEntertainmentStoreTest {
    @Test fun roundtripCorruptionAndKeyIsolation() {
        val dir=Files.createTempDirectory("ent-test").toFile()
        try {
            val store=FileEntertainmentStore(dir);store.write("2048",byteArrayOf(1,2,3));store.write("quiz",byteArrayOf(4))
            assertArrayEquals(byteArrayOf(1,2,3),FileEntertainmentStore(dir).read("2048"));assertArrayEquals(byteArrayOf(4),store.read("quiz"))
            File(dir,"2048.ent").appendBytes(byteArrayOf(0));assertThrows(CorruptEntertainmentData::class.java){store.read("2048")}
            assertThrows(IllegalArgumentException::class.java){store.read("../waiting")}
        } finally { dir.deleteRecursively() }
    }
}
