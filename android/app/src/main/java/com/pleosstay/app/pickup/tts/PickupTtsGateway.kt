package com.pleosstay.app.pickup.tts

enum class PickupSpeechEvent { DEPARTED, PROXIMITY_CANDIDATE, COMPLETED }

interface PickupTtsGateway {
    val supported: Boolean
    suspend fun speakOnce(eventId: String, event: PickupSpeechEvent, displayName: String?): Result<Unit>
}

/** SDK_VERIFY: official Pleos TextToSpeech Maven/API/permission has not been confirmed. */
class UnsupportedPickupTtsGateway : PickupTtsGateway {
    private val seen = mutableSetOf<String>()
    override val supported = false
    override suspend fun speakOnce(eventId: String, event: PickupSpeechEvent, displayName: String?): Result<Unit> {
        if (!seen.add(eventId)) return Result.success(Unit)
        return Result.failure(UnsupportedOperationException("Pleos TextToSpeech SDK_VERIFY"))
    }
}
