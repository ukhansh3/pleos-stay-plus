package com.pleosstay.app.waiting

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pleosstay.app.waiting.data.*
import com.pleosstay.app.waiting.domain.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class WaitingDatabaseTest {
    @Test fun persistedActiveAndEndedSessionsSurviveReopenAndDuplicateSlotIsRejected() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "waiting-test-${UUID.randomUUID()}.db"
        fun open() = Room.databaseBuilder(context, WaitingDatabase::class.java, name).build()
        var db = open()
        try {
            val initial = WaitingPolicy().start(30, 1000)
            RoomWaitingStore(db).create(initial)
            assertThrows(Exception::class.java) { RoomWaitingStore(db).create(WaitingPolicy().start(15, 2000)) }
            db.close()
            db = open()
            val restored = RoomWaitingStore(db)
            assertEquals(initial, restored.all().single())
            restored.end(initial.id, 61_000, EndReason.MANUAL)
            restored.end(initial.id, 99_000, EndReason.DRIVING)
            db.close()
            db = open()
            val ended = RoomWaitingStore(db).all().single()
            assertEquals(WaitingStatus.ENDED, ended.status)
            assertEquals(61_000L, ended.endedAt)
            assertEquals(EndReason.MANUAL, ended.endReason)
            RoomWaitingStore(db).create(WaitingPolicy().start(5, 100_000))
            assertEquals(2, RoomWaitingStore(db).all().size)
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }
}
