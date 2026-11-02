package com.pleosstay.app.waiting.data

import android.content.Context
import androidx.room.Room
import com.pleosstay.app.waiting.domain.*

class RoomWaitingStore(private val database: WaitingDatabase) : WaitingStore {
    private val dao = database.waitingDao()
    override fun all(): List<WaitingSession> = dao.all().map { row ->
        WaitingSession(row.id, row.startedAt, row.plannedEndAt, row.endedAt,
            WaitingStatus.valueOf(row.status), row.endReason?.let(EndReason::valueOf),
            if (row.parkingPlaceId == null && row.placeName == null) null else WaitingPlace(row.parkingPlaceId, row.placeName), row.revision)
    }
    override fun create(session: WaitingSession) {
        dao.insert(WaitingEntity().apply {
            id = session.id; startedAt = session.startedAt; plannedEndAt = session.plannedEndAt
            status = session.status.name; revision = session.revision; activeSlot = 1
            parkingPlaceId = session.place?.parkingPlaceId; placeName = session.place?.name
        })
    }
    override fun extend(session: WaitingSession, expectedRevision: Int) {
        check(dao.extend(session.id, session.plannedEndAt, expectedRevision) == 1) { "세션이 변경되었습니다. 다시 조회해 주세요" }
    }
    override fun end(id: String, at: Long, reason: EndReason) {
        database.runInTransaction {
            val row = dao.get(id) ?: error("세션을 찾을 수 없습니다")
            if (row.status == WaitingStatus.ACTIVE.name) {
                check(dao.end(id, at, reason.name) == 1) { "종료 기록 저장 실패" }
            }
        }
    }
    companion object {
        fun open(context: Context): RoomWaitingStore = RoomWaitingStore(
            Room.databaseBuilder(context.applicationContext, WaitingDatabase::class.java, "waiting.db")
                .build()
        )
    }
}

class PreferencesDrivingEndJournal(context: Context) : DrivingEndJournal {
    private val preferences = context.applicationContext.getSharedPreferences("waiting_pending_end", Context.MODE_PRIVATE)
    override fun read(): PendingDrivingEnd? {
        val id = preferences.getString("sessionId", null) ?: return null
        return PendingDrivingEnd(id, preferences.getLong("observedAt", 0))
    }
    override fun write(command: PendingDrivingEnd) {
        check(preferences.edit().putString("sessionId", command.sessionId)
            .putLong("observedAt", command.observedAt).commit()) { "주행 종료 복구 정보 저장 실패" }
    }
    override fun clear() {
        check(preferences.edit().clear().commit()) { "종료 복구 정보 정리 실패" }
    }
}
