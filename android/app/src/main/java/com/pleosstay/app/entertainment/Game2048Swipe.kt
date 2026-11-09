package com.pleosstay.app.entertainment

import com.pleosstay.app.entertainment.games.MoveDirection
import kotlin.math.abs

internal fun gameSwipeDirection(dx: Float, dy: Float, minimumTravel: Float): MoveDirection? {
    val horizontal = abs(dx)
    val vertical = abs(dy)
    return when {
        horizontal >= minimumTravel && horizontal >= vertical * 1.2f ->
            if (dx > 0) MoveDirection.RIGHT else MoveDirection.LEFT
        vertical >= minimumTravel && vertical >= horizontal * 1.2f ->
            if (dy > 0) MoveDirection.DOWN else MoveDirection.UP
        else -> null
    }
}
