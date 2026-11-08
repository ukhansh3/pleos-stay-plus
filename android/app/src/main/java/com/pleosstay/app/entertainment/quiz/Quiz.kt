package com.pleosstay.app.entertainment.quiz

import com.pleosstay.app.entertainment.data.*
import kotlin.random.Random

data class QuizQuestion(val id: String, val prompt: String, val options: List<String>, val answer: Int) {
    init { require(id.isNotBlank() && prompt.isNotBlank() && options.size in 2..8 && options.distinct().size == options.size && answer in options.indices) }
}
object GeneralKnowledgeQuestions {
    val all = listOf(
        QuizQuestion("q1", "태양계에서 가장 큰 행성은?", listOf("지구", "목성", "화성", "금성"), 1),
        QuizQuestion("q2", "물의 화학식은?", listOf("CO2", "O2", "H2O", "NaCl"), 2),
        QuizQuestion("q3", "삼각형 내각의 합은?", listOf("90도", "180도", "270도", "360도"), 1),
        QuizQuestion("q4", "한글을 창제한 왕은?", listOf("태조", "정조", "세종", "광해군"), 2),
        QuizQuestion("q5", "광합성에 주로 사용하는 에너지는?", listOf("빛", "바람", "소리", "자력"), 0),
        QuizQuestion("q6", "1킬로미터는 몇 미터인가?", listOf("10", "100", "1000", "10000"), 2),
        QuizQuestion("q7", "지구의 자연 위성은?", listOf("달", "태양", "화성", "토성"), 0),
        QuizQuestion("q8", "피아노에서 한 옥타브는 몇 반음인가?", listOf("5", "7", "10", "12"), 3),
        QuizQuestion("q9", "대한민국의 수도는?", listOf("부산", "서울", "대전", "인천"), 1),
        QuizQuestion("q10", "이진법이 사용하는 숫자는?", listOf("0과 1", "1과 2", "0부터 9", "1부터 9"), 0),
    )
}
data class QuizState(val questions: List<QuizQuestion>, val index: Int = 0, val answers: List<Int?> = List(5) { null }, val bestScore: Int = 0) {
    init {
        require(questions.size == 5 && questions.map { it.id }.distinct().size == 5)
        require(index in 0..5 && answers.size == 5 && bestScore in 0..100)
        require(answers.indices.all { i -> answers[i] == null || answers[i] in questions[i].options.indices })
        require((0 until index).all { answers[it] != null } && ((index + 1)..4).all { answers[it] == null })
    }
    val complete get() = index == 5
    val current get() = questions.getOrNull(index)
    val correctCount get() = answers.indices.count { answers[it] != null && answers[it] == questions[it].answer }
    val score get() = correctCount * 20
    val currentCorrect get() = if (complete || answers[index] == null) null else answers[index] == questions[index].answer
}
class QuizService(private val store: EntertainmentStore, private val random: Random = Random.Default,
    private val bank: List<QuizQuestion> = GeneralKnowledgeQuestions.all) {
    private var current: QuizState? = null
    init { require(bank.size >= 5 && bank.map { it.id }.distinct().size == bank.size) }
    @Synchronized fun resume(): QuizState? {
        current?.let { return it }
        val bytes = store.read("quiz") ?: return null
        return decodeRecord(bytes) {
            require(readInt() == 1)
            val index = readInt(); val best = readInt()
            val questions = List(5) {
                val id = readUTF(); val prompt = readUTF(); val count = readInt(); require(count in 2..8)
                QuizQuestion(id, prompt, List(count) { readUTF() }, readInt())
            }
            QuizState(questions, index, List(5) { readInt().let { if (it == -1) null else it } }, best)
        }.also { current = it }
    }
    @Synchronized fun start(): QuizState = commit(QuizState(bank.shuffled(random).take(5), bestScore = resume()?.bestScore ?: 0))
    @Synchronized fun submit(questionId: String, answer: Int): QuizState {
        val old = checkNotNull(resume()); check(!old.complete)
        require(old.current!!.id == questionId && answer in old.current!!.options.indices)
        if (old.answers[old.index] != null) return old
        return commit(old.copy(answers = old.answers.toMutableList().apply { this[old.index] = answer }.toList()))
    }
    @Synchronized fun next(): QuizState {
        val old = checkNotNull(resume()); if (old.complete) return old
        check(old.answers[old.index] != null) { "먼저 답변을 선택해 주세요." }
        val next = old.copy(index = old.index + 1)
        return commit(if (next.complete) next.copy(bestScore = maxOf(next.bestScore, next.score)) else next)
    }
    @Synchronized fun resetCorruptQuiz(): QuizState = commit(QuizState(bank.shuffled(random).take(5)))
    private fun commit(next: QuizState): QuizState {
        store.write("quiz", encodeRecord {
            writeInt(1); writeInt(next.index); writeInt(next.bestScore)
            next.questions.forEach { q -> writeUTF(q.id); writeUTF(q.prompt); writeInt(q.options.size); q.options.forEach(::writeUTF); writeInt(q.answer) }
            next.answers.forEach { writeInt(it ?: -1) }
        }); current = next; return next
    }
}
