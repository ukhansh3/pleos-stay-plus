package com.pleosstay.app.rest

import java.util.UUID

internal enum class RestAlarmMode { SYSTEM_EXACT, FOREGROUND_ONLY, UNAVAILABLE }
internal data class RestPlayback(val playing: Boolean = false, val error: String? = null)

internal const val REST_MINUTE = 60_000L
internal enum class RestDisplay(val label: String) { DARK("어두운 화면"), MINIMAL("최소 정보"), CALM("차분한 화면") }
internal enum class RestSound(val label: String) { SILENT("무음"), RAIN("빗소리") }
internal enum class RestAlert(val label: String) { SOFT("부드러운 알림"), WAKE("기상 알람") }
internal enum class RestEnd(val label: String) { TIME("휴식 시간이 끝났어요"), MANUAL("휴식을 종료했어요"), SAFETY("차량 안전 조건으로 휴식을 중단했어요"), TARGET("목표 충전율에 도달했어요") }

internal data class RestPreset(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val minutes: Int = 10,
    val display: RestDisplay = RestDisplay.DARK,
    val sound: RestSound = RestSound.SILENT,
    val alert: RestAlert = RestAlert.SOFT,
    val ev: Boolean = false,
    val target: Int = 80,
) {
    val valid get() = name.isNotBlank() && name.length <= 40 && minutes in 5..720 && target in 1..100

    companion object {
        val defaults = listOf(
            RestPreset("quick", "잠깐 쉬어가기"),
            RestPreset("nap", "파워냅", 20, RestDisplay.MINIMAL, alert = RestAlert.WAKE),
            RestPreset("calm", "여유로운 휴식", 30, RestDisplay.CALM, RestSound.RAIN),
            RestPreset("ev", "충전하며 휴식", 60, RestDisplay.CALM, ev = true),
        )
    }
}

internal data class RestSession(
    val preset: RestPreset,
    val startedAt: Long,
    val endsAt: Long,
    val waitingId: String?,
    val alarmToken: String = UUID.randomUUID().toString(),
    val targetReached: Boolean = false,
    val targetPrompt: Boolean = false,
    val soundPaused: Boolean = false,
    val end: RestEnd? = null,
    val targetAlertAt: Long? = null,
    val alarmMode: RestAlarmMode = RestAlarmMode.SYSTEM_EXACT,
) {
    fun remaining(now: Long) = (endsAt - now).coerceAtLeast(0)
    fun canReachTarget(percent: Int, now: Long) =
        end == null && preset.ev && !targetReached && percent in preset.target..100 && now < endsAt

    fun recentlyAlertedTarget(now: Long) = targetAlertAt?.let { now - it in 0..10_000L } == true
}

internal data class EvReading(val percent: Int? = null, val message: String = "배터리 정보를 확인 중입니다")
