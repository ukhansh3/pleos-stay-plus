package com.pleosstay.app.entertainment

import com.pleosstay.app.entertainment.games.MoveDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class Game2048SwipeTest {
    @Test fun dominantAxisDeterminesAllFourDirections() {
        assertEquals(MoveDirection.RIGHT, gameSwipeDirection(60f, -15f, 24f))
        assertEquals(MoveDirection.LEFT, gameSwipeDirection(-60f, 15f, 24f))
        assertEquals(MoveDirection.DOWN, gameSwipeDirection(15f, 60f, 24f))
        assertEquals(MoveDirection.UP, gameSwipeDirection(-15f, -60f, 24f))
    }

    @Test fun shortDragAndTapDoNotMoveTiles() {
        assertNull(gameSwipeDirection(23f, -2f, 24f))
        assertNull(gameSwipeDirection(0f, 0f, 24f))
        assertEquals(MoveDirection.UP, gameSwipeDirection(0f, -24f, 24f))
    }

    @Test fun diagonalWithNoClearAxisDoesNotMoveTiles() {
        assertNull(gameSwipeDirection(70f, 70f, 24f))
        assertNull(gameSwipeDirection(-70f, 65f, 24f))
        assertEquals(MoveDirection.LEFT, gameSwipeDirection(-80f, 60f, 24f))
    }
}
