package com.pleosstay.app.entertainment.recommendation

import com.pleosstay.app.waiting.data.WaitingRepository
import com.pleosstay.app.waiting.domain.*
import com.pleosstay.app.waiting.safety.WaitingSafety
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.*

enum class EntertainmentContent { GAME_2048, QUIZ, KARAOKE, YOUTUBE, NETFLIX }
enum class Availability { AVAILABLE, UNAVAILABLE, UNVERIFIED }
data class ContentAvailability(val status: Availability, val reason: String = "")
enum class WaitingRecommendationStatus { NO_WAITING, TIME_REMAINING, OVERTIME, SAFETY_BLOCKED }
data class EntertainmentRecommendations(val status: WaitingRecommendationStatus, val waitingId: String?,
    val remainingMillis: Long?, val overtimeMillis: Long = 0, val contents: List<EntertainmentContent> = emptyList())
class WaitingRecommendationEngine {
    fun recommend(sessions: List<WaitingSession>, now: Long, safety: WaitingSafety,
        availability: Map<EntertainmentContent, ContentAvailability>): EntertainmentRecommendations {
        val session = sessions.firstOrNull { it.status == WaitingStatus.ACTIVE }
        val time = session?.let { WaitingTime.calculate(it, now) }
        if (!safety.canUse) return EntertainmentRecommendations(WaitingRecommendationStatus.SAFETY_BLOCKED, session?.id, time?.remainingMillis, time?.overtimeMillis ?: 0)
        if (session == null) return EntertainmentRecommendations(WaitingRecommendationStatus.NO_WAITING, null, null)
        if (time!!.remainingMillis == 0L) return EntertainmentRecommendations(WaitingRecommendationStatus.OVERTIME, session.id, 0, time.overtimeMillis)
        val candidates = when {
            time.remainingMillis <= 5 * 60_000L -> listOf(EntertainmentContent.QUIZ, EntertainmentContent.GAME_2048)
            time.remainingMillis <= 15 * 60_000L -> listOf(EntertainmentContent.GAME_2048, EntertainmentContent.QUIZ, EntertainmentContent.KARAOKE)
            time.remainingMillis <= 30 * 60_000L -> listOf(EntertainmentContent.KARAOKE, EntertainmentContent.YOUTUBE, EntertainmentContent.NETFLIX)
            else -> EntertainmentContent.entries
        }
        return EntertainmentRecommendations(WaitingRecommendationStatus.TIME_REMAINING, session.id, time.remainingMillis,
            contents = candidates.filter { availability[it]?.status == Availability.AVAILABLE })
    }
}
/** Read-only adapter: no restore/start/end/extend calls and no second Waiting clock/store. */
class WaitingRecommendationFeed(private val waiting: WaitingRepository, private val safety: StateFlow<WaitingSafety>,
    private val availability: StateFlow<Map<EntertainmentContent, ContentAvailability>>, private val now: () -> Long = System::currentTimeMillis) {
    fun observe(): Flow<EntertainmentRecommendations> = combine(waiting.sessions, safety, availability, flow {
        while (currentCoroutineContext().isActive) { emit(now()); delay(1_000) }
    }) { sessions, safe, available, time -> WaitingRecommendationEngine().recommend(sessions, time, safe, available) }.distinctUntilChanged()
}
