package com.pleosstay.app.waiting.domain

import java.util.UUID

enum class WaitingStatus { ACTIVE, ENDED }
enum class EndReason { MANUAL, DRIVING }

// Coordinates are deliberately absent. Local ID remains stable across future sharing.
data class WaitingPlace(val parkingPlaceId: String?, val name: String?)
// All persisted timestamps are UTC epoch milliseconds; DRIVING endedAt is observation time, not inferred departure time.
data class WaitingSession(
    val id: String,
    val startedAt: Long,
    val plannedEndAt: Long,
    val endedAt: Long? = null,
    val status: WaitingStatus = WaitingStatus.ACTIVE,
    val endReason: EndReason? = null,
    val place: WaitingPlace? = null,
    val revision: Int = 0,
)

data class WaitingPolicy(
    val minimumMinutes: Int = 5,
    val maximumMinutes: Int = 720,
    val presets: List<Int> = listOf(15, 30, 60, 120),
) {
    fun start(minutes: Int, now: Long, place: WaitingPlace? = null): WaitingSession {
        require(minutes in minimumMinutes..maximumMinutes) { "대기시간은 ${minimumMinutes}~${maximumMinutes}분이어야 합니다" }
        return WaitingSession(UUID.randomUUID().toString(), now, Math.addExact(now, minutes * 60_000L), place = place)
    }

    fun extend(session: WaitingSession, minutes: Int): WaitingSession {
        require(session.status == WaitingStatus.ACTIVE) { "종료한 대기는 연장할 수 없습니다" }
        require(minutes > 0 && minutes <= maximumMinutes) { "연장 시간은 양수이며 최대 ${maximumMinutes}분입니다" }
        val end = Math.addExact(session.plannedEndAt, minutes * 60_000L)
        require(end - session.startedAt <= maximumMinutes * 60_000L) { "총 예정시간은 ${maximumMinutes}분을 초과할 수 없습니다" }
        return session.copy(plannedEndAt = end, revision = session.revision + 1)
    }

    fun end(session: WaitingSession, at: Long, reason: EndReason): WaitingSession {
        if (session.status == WaitingStatus.ENDED) return session
        require(at >= session.startedAt) { "종료 시각이 시작보다 이릅니다. 기기 시각을 확인해 주세요" }
        return session.copy(status = WaitingStatus.ENDED, endedAt = at, endReason = reason, revision = session.revision + 1)
    }
}

data class WaitingTime(val elapsedMillis: Long, val remainingMillis: Long, val overtimeMillis: Long) {
    companion object {
        fun calculate(session: WaitingSession, now: Long): WaitingTime {
            val reference = session.endedAt ?: now
            return WaitingTime(
                (reference - session.startedAt).coerceAtLeast(0),
                (session.plannedEndAt - reference).coerceAtLeast(0),
                (reference - session.plannedEndAt).coerceAtLeast(0),
            )
        }
    }
}

fun interface WaitingClock { fun now(): Long }

