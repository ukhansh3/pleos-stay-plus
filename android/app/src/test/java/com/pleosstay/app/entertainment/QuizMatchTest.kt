package com.pleosstay.app.entertainment

import com.pleosstay.app.entertainment.quiz.*
import kotlin.random.Random
import org.junit.Assert.*
import org.junit.Test

class QuizMatchTest {
    private fun service()=QuizMatchService(random=Random(123)).also{it.enter()}
    @Test fun everySupportedConfigurationHasEnoughUniqueBalancedQuestions() {
        for(players in 2..4) for(count in listOf(5,10,15)) for(category in MatchCategory.entries) {
            val s=service().start(MatchConfig(List(players){"참가자${it+1}"},count,category))
            assertEquals(players*count,s.questions.size)
            assertEquals(s.questions.size,s.questions.map{it.question.id}.distinct().size)
            for(player in 1 until players)assertEquals(
                s.questions.filterIndexed{i,_->i%players==0}.groupingBy{it.category to it.difficulty}.eachCount(),
                s.questions.filterIndexed{i,_->i%players==player}.groupingBy{it.category to it.difficulty}.eachCount())
        }
    }
    @Test fun correctAnswersScoreTenAndRepeatedSubmissionCannotScoreTwice() {
        val engine=service();val old=engine.start(MatchConfig(listOf("A","B")))
        val q=old.current!!;val one=engine.submit(q.id,q.answer);val twice=engine.submit(q.id,q.answer)
        assertEquals(listOf(10,0),one.scores);assertEquals(one,twice)
        assertEquals(1,engine.next().player)
    }
    @Test fun wrongAnswerScoresZero() {
        val engine=service();val q=engine.start(MatchConfig(listOf("A","B"))).current!!
        assertEquals(listOf(0,0),engine.submit(q.id,(q.answer+1)%4).scores)
    }
    @Test fun completedDrawHasJointWinnersAndResetKeepsNoNames() {
        val engine=service();engine.start(MatchConfig(listOf("A","B")))
        while(engine.state.value?.complete==false){val q=engine.state.value!!.current!!;engine.submit(q.id,q.answer);engine.next()}
        assertEquals(listOf(50,50),engine.state.value!!.scores);assertEquals(listOf(0,1),engine.state.value!!.winners)
        engine.reset();assertNull(engine.state.value)
    }
    @Test fun interruptionRejectsSubmitAndEnterResumesSameProcessState() {
        val engine=service();val old=engine.start(MatchConfig(listOf("A","B")));engine.interrupt()
        assertThrows(IllegalStateException::class.java){engine.submit(old.current!!.id,0)}
        assertEquals(old,engine.state.value);engine.enter();engine.submit(old.current!!.id,old.current!!.answer)
        engine.stop();assertNull(engine.state.value)
        assertNull(service().state.value) // No persistence resurrects names or unfinished matches.
    }
    @Test fun shortageIsReportedBeforeMatchStarts() {
        val engine=QuizMatchService(ReviewedMatchBank.all.take(5)).also{it.enter()}
        assertThrows(IllegalArgumentException::class.java){engine.start(MatchConfig(listOf("A","B"),15))}
        assertNull(engine.state.value)
    }
    @Test fun noAnswerCannotAdvanceAndStaleQuestionCannotBeSubmitted() {
        val engine=service();val q=engine.start(MatchConfig(listOf("A","B"))).current!!
        assertThrows(IllegalStateException::class.java){engine.next()}
        assertThrows(IllegalArgumentException::class.java){engine.submit("unknown",0)}
        engine.submit(q.id,q.answer);engine.next()
        assertThrows(IllegalArgumentException::class.java){engine.submit(q.id,q.answer)}
    }
    @Test fun bankContains120DistinctSourceCheckedFourOptionQuestions() {
        val bank=ReviewedMatchBank.all;assertEquals(120,bank.size)
        assertEquals(setOf(MatchCategory.SCIENCE,MatchCategory.GEOGRAPHY),bank.map{it.category}.toSet())
        assertTrue(bank.all{it.license=="CC0" && it.source.startsWith("https://www.wikidata.org/wiki/") && it.question.options.size==4})
        assertEquals(120,bank.map{it.question.prompt}.distinct().size)
    }
}
