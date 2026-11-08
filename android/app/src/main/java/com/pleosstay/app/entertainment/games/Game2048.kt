package com.pleosstay.app.entertainment.games

import com.pleosstay.app.entertainment.data.*
import kotlin.random.Random

enum class MoveDirection { LEFT, RIGHT, UP, DOWN }
data class Game2048State(val board: List<Int>, val score: Long = 0, val bestScore: Long = 0) {
    init {
        require(board.size == 16 && board.all { it == 0 || (it >= 2 && it <= (1 shl 30) && it and (it - 1) == 0) })
        require(score >= 0 && bestScore >= score)
    }
    val won get() = board.any { it >= 2048 }
    val gameOver get() = board.none { it == 0 } && (0..15).none { i ->
        (i % 4 < 3 && board[i] == board[i + 1] && board[i] < (1 shl 30)) || (i / 4 < 3 && board[i] == board[i + 4] && board[i] < (1 shl 30))
    }
    val inProgress get() = !gameOver
}
data class Slide2048(val board: List<Int>, val scoreGain: Long, val changed: Boolean)
class Game2048Engine(private val random: Random = Random.Default) {
    /** Inspect this pure result before the random tile is spawned. A tile merges once per move. */
    fun slide(board: List<Int>, direction: MoveDirection): Slide2048 {
        Game2048State(board)
        val output = board.toMutableList(); var gain = 0L
        for (line in 0..3) {
            val indices = (0..3).map { p -> when (direction) {
                MoveDirection.LEFT -> line * 4 + p
                MoveDirection.RIGHT -> line * 4 + 3 - p
                MoveDirection.UP -> p * 4 + line
                MoveDirection.DOWN -> (3 - p) * 4 + line
            } }
            val nonzero = indices.map { board[it] }.filter { it != 0 }
            val merged = mutableListOf<Int>(); var p = 0
            while (p < nonzero.size) {
                if (p + 1 < nonzero.size && nonzero[p] == nonzero[p + 1] && nonzero[p] < (1 shl 30)) {
                    val value = nonzero[p] * 2; merged += value; gain += value; p += 2
                } else { merged += nonzero[p]; p++ }
            }
            indices.forEachIndexed { index, cell -> output[cell] = merged.getOrElse(index) { 0 } }
        }
        return Slide2048(output.toList(), gain, output != board)
    }
    fun newGame(best: Long = 0): Game2048State = Game2048State(spawn(spawn(List(16) { 0 })), bestScore = best)
    fun move(state: Game2048State, direction: MoveDirection): Game2048State {
        if (state.gameOver) return state
        val slide = slide(state.board, direction)
        if (!slide.changed) return state
        val score = Math.addExact(state.score, slide.scoreGain)
        return Game2048State(spawn(slide.board), score, maxOf(score, state.bestScore))
    }
    private fun spawn(board: List<Int>): List<Int> {
        val free = board.indices.filter { board[it] == 0 }
        if (free.isEmpty()) return board
        return board.toMutableList().apply { this[free[random.nextInt(free.size)]] = if (random.nextInt(10) < 9) 2 else 4 }.toList()
    }
}
class Game2048Service(private val store: EntertainmentStore, private val engine: Game2048Engine = Game2048Engine()) {
    private var current: Game2048State? = null
    @Synchronized fun resume(): Game2048State? {
        current?.let { return it }
        val bytes = store.read("2048") ?: return null
        return decodeRecord(bytes) {
            require(readInt() == 1)
            val score = readLong(); val best = readLong()
            Game2048State(List(16) { readInt() }, score, best)
        }.also { current = it }
    }
    @Synchronized fun newGame(): Game2048State = commit(engine.newGame(resume()?.bestScore ?: 0))
    @Synchronized fun move(direction: MoveDirection): Game2048State = commit(engine.move(checkNotNull(resume()), direction))
    private fun commit(next: Game2048State): Game2048State {
        store.write("2048", encodeRecord { writeInt(1); writeLong(next.score); writeLong(next.bestScore); next.board.forEach(::writeInt) })
        current = next; return next
    }
    /** Explicit recovery after corrupt data; does not silently overwrite an unreadable record. */
    @Synchronized fun resetCorruptGame(): Game2048State = commit(engine.newGame())
}
