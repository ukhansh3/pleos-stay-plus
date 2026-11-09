package com.pleosstay.app.modes

import com.pleosstay.app.waiting.data.LocalWaitingRepository
import com.pleosstay.app.waiting.data.WaitingRecoveryRequiredException
import com.pleosstay.app.waiting.data.WaitingRefreshException
import com.pleosstay.app.waiting.data.WaitingSafetyException
import com.pleosstay.app.waiting.data.WaitingWriteUncertainException
import com.pleosstay.app.waiting.domain.WaitingPlace
import com.pleosstay.app.waiting.domain.WaitingSession
import com.pleosstay.app.waiting.domain.WaitingTime
import com.pleosstay.app.waiting.safety.WaitingSafetyGateway
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException

enum class ActivityMode { REST, STUDY, WORK }

data class ModeStartRequest(val mode: ActivityMode, val minutes: Int, val place: WaitingPlace? = null)

enum class WaitingChoice { EXTEND, SHORTEN, BACK }
enum class WaitingUse { CREATED, REUSED, EXTENDED, SHORTENED, NONE }
enum class WaitingChoiceUnavailable { TOTAL_TIME_LIMIT, NO_TIME_REMAINING }

sealed interface ModeStartResult {
    data class Ready(
        val request: ModeStartRequest,
        val waiting: WaitingSession?,
        val use: WaitingUse,
        val durationMillis: Long,
        val endsAt: Long?,
    ) : ModeStartResult

    class NeedsChoice internal constructor(
        val request: ModeStartRequest,
        val waitingId: String,
        val remainingMillis: Long,
        val extensionMinutes: Long,
        val extensionUnavailable: WaitingChoiceUnavailable?,
        val shortenUnavailable: WaitingChoiceUnavailable?,
        val rejectedChoice: WaitingChoiceUnavailable? = null,
        internal val requestedEnd: Long? = null,
    ) : ModeStartResult {
        private val handled = AtomicBoolean()
        internal fun claim(): Boolean = handled.compareAndSet(false, true)
    }

    data class DecisionAlreadyHandled(val request: ModeStartRequest) : ModeStartResult
    data class Cancelled(val request: ModeStartRequest) : ModeStartResult
    data class WaitingChanged(val request: ModeStartRequest) : ModeStartResult
    data class InvalidDuration(val allowedMinutes: IntRange) : ModeStartResult
    data class SafetyBlocked(val message: String) : ModeStartResult
    data class RecoveryRequired(val message: String) : ModeStartResult

    class StorageError internal constructor(
        val request: ModeStartRequest,
        val message: String,
        val refreshRequired: Boolean,
    ) : ModeStartResult
}

class ModeStartManager(
    private val waiting: LocalWaitingRepository,
    private val safety: WaitingSafetyGateway,
) {
    suspend fun start(request: ModeStartRequest): ModeStartResult = prepare(request)

    /** Re-evaluates a running activity's deadline against its original Waiting. */
    suspend fun adjustEnd(mode: ActivityMode, waitingId: String, endsAt: Long): ModeStartResult =
        prepare(ModeStartRequest(mode, waiting.modeDurationRange.first), waitingId, requestedEnd = endsAt)

    suspend fun resolve(prompt: ModeStartResult.NeedsChoice, choice: WaitingChoice): ModeStartResult {
        if (!prompt.claim()) return ModeStartResult.DecisionAlreadyHandled(prompt.request)
        return if (choice == WaitingChoice.BACK) ModeStartResult.Cancelled(prompt.request)
        else prepare(prompt.request, prompt.waitingId, choice, prompt.requestedEnd)
    }

    /** Call only after explicit confirmation and only for a mode that supports independent operation. */
    fun continueWithoutWaiting(error: ModeStartResult.StorageError): ModeStartResult =
        safetyBlock() ?: ModeStartResult.Ready(
            error.request, null, WaitingUse.NONE, error.request.minutes * MINUTE_MILLIS, endsAt = null,
        )

    private suspend fun prepare(
        request: ModeStartRequest,
        expectedId: String? = null,
        choice: WaitingChoice? = null,
        requestedEnd: Long? = null,
    ): ModeStartResult {
        safetyBlock()?.let { return it }
        val range = waiting.modeDurationRange
        if (request.minutes !in range) return ModeStartResult.InvalidDuration(range)
        return try {
            val result = waiting.withModeWaiting {
                val session = active
                val now = now()
                val targetEnd = requestedEnd ?: Math.addExact(now, request.minutes * MINUTE_MILLIS)
                val duration = targetEnd - now
                if (duration <= 0 || duration > range.last * MINUTE_MILLIS) {
                    return@withModeWaiting ModeStartResult.InvalidDuration(range)
                }
                when {
                    expectedId != null && expectedId != session?.id -> ModeStartResult.WaitingChanged(request)
                    session == null -> {
                        val created = create(request.minutes, request.place)
                        ModeStartResult.Ready(request, created, WaitingUse.CREATED, duration, created.plannedEndAt)
                    }
                    targetEnd <= session.plannedEndAt ->
                        ModeStartResult.Ready(request, session, WaitingUse.REUSED, duration, targetEnd)
                    else -> {
                        val remaining = WaitingTime.calculate(session, now).remainingMillis
                        // Extend from the previous deadline, including time already spent in overtime.
                        val gap = targetEnd - session.plannedEndAt
                        val extension = (gap - 1) / MINUTE_MILLIS + 1
                        val available = range.last - (session.plannedEndAt - session.startedAt) / MINUTE_MILLIS
                        val extensionUnavailable = WaitingChoiceUnavailable.TOTAL_TIME_LIMIT.takeIf { extension > available }
                        val shortenUnavailable = WaitingChoiceUnavailable.NO_TIME_REMAINING.takeIf { remaining == 0L }
                        when {
                            choice == WaitingChoice.EXTEND && extensionUnavailable == null ->
                                ModeStartResult.Ready(request, extend(session, extension.toInt()), WaitingUse.EXTENDED, duration, targetEnd)
                            choice == WaitingChoice.SHORTEN && shortenUnavailable == null ->
                                ModeStartResult.Ready(request, session, WaitingUse.SHORTENED, remaining, session.plannedEndAt)
                            else -> ModeStartResult.NeedsChoice(
                                request, session.id, remaining, extension, extensionUnavailable, shortenUnavailable,
                                when (choice) {
                                    WaitingChoice.EXTEND -> extensionUnavailable
                                    WaitingChoice.SHORTEN -> shortenUnavailable
                                    else -> null
                                },
                                requestedEnd,
                            )
                        }
                    }
                }
            }
            safetyBlock() ?: result
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (blocked: WaitingSafetyException) {
            ModeStartResult.SafetyBlocked(blocked.message.orEmpty())
        } catch (recovery: WaitingRecoveryRequiredException) {
            safetyBlock() ?: ModeStartResult.RecoveryRequired(recovery.message.orEmpty())
        } catch (error: Exception) {
            safetyBlock() ?: ModeStartResult.StorageError(
                request, error.message ?: "대기 저장소 오류",
                refreshRequired = error is WaitingRefreshException || error is WaitingWriteUncertainException,
            )
        }
    }

    private fun safetyBlock(): ModeStartResult.SafetyBlocked? {
        val state = safety.state.value
        return if (state.canUse) null else ModeStartResult.SafetyBlocked(state.message)
    }

    private companion object {
        const val MINUTE_MILLIS = 60_000L
    }
}
