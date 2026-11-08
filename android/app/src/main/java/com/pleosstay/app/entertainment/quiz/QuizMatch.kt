package com.pleosstay.app.entertainment.quiz

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.random.Random

enum class MatchCategory(val label: String) { ALL("전체"), SCIENCE("과학"), GEOGRAPHY("지리") }
data class BankQuestion(val question: QuizQuestion, val category: MatchCategory, val difficulty: Int,
    val source: String, val license: String = "CC0", val reviewStatus: String = "SOURCE_CHECKED_AGENT_REVIEWED_2026-11-08")
data class MatchConfig(val names: List<String>, val perPlayer: Int = 5, val category: MatchCategory = MatchCategory.ALL) {
    init {
        require(names.size in 2..4 && names.all { it.isNotBlank() && it.length <= 24 }) { "참가자 이름은 1~24자로 입력해 주세요." }
        require(perPlayer in listOf(5,10,15))
    }
}
data class MatchState(val config: MatchConfig, val questions: List<BankQuestion>, val turn: Int = 0,
    val answers: List<Int?> = List(questions.size) { null }) {
    val complete get() = turn == questions.size
    val player get() = turn % config.names.size
    val current get() = questions.getOrNull(turn)?.question
    val submitted get() = answers.getOrNull(turn) != null
    val correct get() = current?.let { answers.getOrNull(turn)?.let { answer -> answer == it.answer } }
    val scores get() = config.names.indices.map { player -> answers.indices.count { i ->
        i % config.names.size == player && answers[i] != null && answers[i] == questions[i].question.answer } * 10 }
    val winners get() = scores.indices.filter { scores[it] == scores.maxOrNull() }
}
/** In-memory only: explicit end/process death discard unfinished matches and names. */
class QuizMatchService(private val bank: List<BankQuestion> = ReviewedMatchBank.all, private val random: Random = Random.Default) {
    private val mutable = MutableStateFlow<MatchState?>(null)
    val state = mutable.asStateFlow()
    @Volatile private var accepting = false
    fun enter() { accepting = true }
    fun interrupt() { accepting = false }
    @Synchronized fun stop() { accepting = false; mutable.value = null }
    @Synchronized fun reset() { check(accepting); mutable.value = null }
    @Synchronized fun start(config: MatchConfig): MatchState {
        check(accepting) { "안전 상태와 활성 화면을 다시 확인해 주세요." }
        val groups = bank.filter { config.category == MatchCategory.ALL || it.category == config.category }
            .groupBy { it.category to it.difficulty }.toSortedMap(compareBy({ it.first.ordinal }, { it.second }))
            .mapValues { it.value.shuffled(random).toMutableList() }
        require(groups.isNotEmpty()) { "해당 카테고리의 문제가 없어요." }
        val keys = groups.keys.toList()
        val questions = mutableListOf<BankQuestion>()
        // Every player receives the same category/difficulty in each round.
        repeat(config.perPlayer) { round ->
            val pool = groups.getValue(keys[round % keys.size])
            require(pool.size >= config.names.size) { "문제가 부족해요. 인원·문제 수·카테고리를 바꿔 주세요." }
            repeat(config.names.size) { questions += pool.removeAt(pool.lastIndex) }
        }
        require(questions.map { it.question.id }.distinct().size == questions.size)
        check(accepting)
        return MatchState(config, questions).also { mutable.value = it }
    }
    @Synchronized fun submit(questionId: String, option: Int): MatchState {
        check(accepting)
        val old = checkNotNull(state.value); check(!old.complete)
        require(old.current!!.id == questionId && option in old.current!!.options.indices)
        if (old.submitted) return old
        return old.copy(answers = old.answers.toMutableList().apply { this[old.turn] = option }).also { mutable.value = it }
    }
    @Synchronized fun next(): MatchState {
        check(accepting)
        val old = checkNotNull(state.value)
        if (old.complete) return old
        check(old.submitted)
        return old.copy(turn = old.turn + 1).also { mutable.value = it }
    }
}
