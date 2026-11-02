package com.pleosstay.app.waiting

import com.pleosstay.app.waiting.data.*
import com.pleosstay.app.waiting.domain.*
import com.pleosstay.app.waiting.safety.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

class MemoryWaitingStore : WaitingStore {
    val rows = linkedMapOf<String, WaitingSession>()
    var failReadAfterExtend = false
    var failReads = false
    var failWrites = false
    var endings = 0
    override fun all(): List<WaitingSession> { check(!failReads); return rows.values.toList() }
    override fun create(session: WaitingSession) {
        check(!failWrites) { "disk full" }
        check(rows.values.none { it.status == WaitingStatus.ACTIVE })
        rows[session.id] = session
    }
    override fun extend(session: WaitingSession, expectedRevision: Int) {
        check(!failWrites)
        check(rows[session.id]?.revision == expectedRevision)
        if (failReadAfterExtend) failReads = true
        rows[session.id] = session
    }
    override fun end(id: String, at: Long, reason: EndReason) {
        check(!failWrites) { "disk full" }
        val row = rows.getValue(id)
        if (row.status == WaitingStatus.ACTIVE) {
            rows[id] = WaitingPolicy().end(row, at, reason)
            endings++
        }
    }
}
class MemoryJournal : DrivingEndJournal {
    var command: PendingDrivingEnd? = null
    var fail = false
    override fun read() = command
    override fun write(command: PendingDrivingEnd) { check(!fail); this.command = command }
    override fun clear() { check(!fail); command = null }
}

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class WaitingRepositoryTest {
    @Test fun startExtendEndAndRestoreWithoutParkingOrGps() = runTest {
        val store = MemoryWaitingStore()
        val journal = MemoryJournal()
        var now = 1000L
        fun repository() = LocalWaitingRepository(store, journal, { WaitingSafety(VehicleMotion.PARKED, false) }, WaitingClock { now }, dispatcher = StandardTestDispatcher(testScheduler))
        val repo = repository()
        repo.start(30)
        val active = repo.sessions.value.single()
        assertNull(active.place)
        repo.extend(active.id, 15)
        assertEquals(2_701_000L, repo.sessions.value.single().plannedEndAt)
        now += 100_000
        val restored = repository()
        restored.restore()
        assertEquals(active.id, restored.sessions.value.single().id)
        restored.end(active.id)
        restored.end(active.id)
        assertEquals(1, store.endings)
        val ended = repository()
        ended.restore()
        assertEquals(WaitingStatus.ENDED, ended.sessions.value.single().status)
        assertEquals(now, ended.sessions.value.single().endedAt)
        assertEquals(EndReason.MANUAL, ended.sessions.value.single().endReason)
    }
    @Test fun concurrentStartsDoNotOverwriteSession() = runTest {
        val store = MemoryWaitingStore()
        val repo = LocalWaitingRepository(store, MemoryJournal(), { WaitingSafety(VehicleMotion.PARKED, false) }, dispatcher = StandardTestDispatcher(testScheduler))
        val results = List(10) { async { runCatching { repo.start(30) } } }.awaitAll()
        assertEquals(1, results.count { it.isSuccess })
        assertEquals(1, store.rows.size)
    }
    @Test fun unknownRestrictedOrDrivingCannotStart() = runTest {
        var safety = WaitingSafety()
        val repo = LocalWaitingRepository(MemoryWaitingStore(), MemoryJournal(), { safety }, dispatcher = StandardTestDispatcher(testScheduler))
        listOf(WaitingSafety(), WaitingSafety(VehicleMotion.PARKED, true), WaitingSafety(VehicleMotion.DRIVING, false), WaitingSafety(VehicleMotion.UNKNOWN, false)).forEach {
            safety = it
            assertTrue(runCatching { repo.start(15) }.isFailure)
        }
        assertTrue(repo.sessions.value.isEmpty())
    }
    @Test fun drivingFailureRestoresObservedTimeAndEndsOnlyOnce() = runTest {
        val store = MemoryWaitingStore()
        val journal = MemoryJournal()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repo = LocalWaitingRepository(store, journal, { WaitingSafety(VehicleMotion.PARKED, false) }, WaitingClock { 1000 }, dispatcher = dispatcher)
        repo.start(15)
        store.failWrites = true
        assertTrue(runCatching { repo.onDrivingConfirmed(21_000) }.isFailure)
        assertEquals(WaitingStatus.ACTIVE, repo.sessions.value.single().status)
        assertEquals(21_000L, journal.command!!.observedAt)
        store.failWrites = false
        val restarted = LocalWaitingRepository(store, journal, { WaitingSafety() }, dispatcher = dispatcher)
        restarted.restore()
        restarted.onDrivingConfirmed(99_000)
        assertEquals(1, store.endings)
        assertEquals(21_000L, restarted.sessions.value.single().endedAt)
        assertEquals(EndReason.DRIVING, restarted.sessions.value.single().endReason)
        assertNull(journal.command)
    }
    @Test fun failedWriteNeverPublishesSuccessAndJournalFailureBlocksMutation() = runTest {
        val store = MemoryWaitingStore()
        val journal = MemoryJournal()
        val repo = LocalWaitingRepository(store, journal, { WaitingSafety(VehicleMotion.PARKED, false) }, WaitingClock { 1000 }, dispatcher = StandardTestDispatcher(testScheduler))
        store.failWrites = true
        assertTrue(runCatching { repo.start(30) }.isFailure)
        assertTrue(repo.sessions.value.isEmpty())
        store.failWrites = false
        repo.start(30)
        journal.fail = true
        assertTrue(runCatching { repo.onDrivingConfirmed(2000) }.isFailure)
        assertTrue(runCatching { repo.extend(repo.sessions.value.single().id, 5) }.isFailure)
        journal.fail = false
        repo.retryPendingEnd()
        assertEquals(2000L, repo.sessions.value.single().endedAt)
    }
    @Test fun drivingReadFailurePersistsObservationAndDoesNotEndANewerSession() = runTest {
        val store = MemoryWaitingStore()
        val journal = MemoryJournal()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repo = LocalWaitingRepository(store, journal, { WaitingSafety(VehicleMotion.PARKED, false) }, WaitingClock { 1000 }, dispatcher = dispatcher)
        repo.start(15)
        store.failReads = true
        assertTrue(runCatching { repo.onDrivingConfirmed(2000) }.isFailure)
        assertEquals(2000L, journal.command!!.observedAt)
        store.failReads = false
        val restarted = LocalWaitingRepository(store, journal, { WaitingSafety() }, dispatcher = dispatcher)
        restarted.restore()
        assertEquals(2000L, restarted.sessions.value.single().endedAt)
        // A delayed duplicate observation predating the new session cannot end it.
        val newSession = WaitingPolicy().start(15, 3000)
        store.create(newSession)
        restarted.onDrivingConfirmed(2000)
        assertEquals(WaitingStatus.ACTIVE, store.rows.getValue(newSession.id).status)
        assertEquals(1, store.endings)
    }}




