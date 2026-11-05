package com.pleosstay.app.pickup.tts

import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.constant.ErrorType
import ai.pleos.playground.tts.constant.Mode
import ai.pleos.playground.tts.listener.EventListener
import ai.pleos.playground.tts.listener.OnInitializeListener
import android.content.Context
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import java.util.Locale

enum class PickupSpeechEvent { DEPARTED, PROXIMITY_CANDIDATE, COMPLETED }

interface PickupTtsGateway : AutoCloseable {
    val supported: Boolean
    val verificationMessage: String get() = if (supported) "Pleos TTS 사용 가능 여부 확인 중" else "Pleos TTS SDK_VERIFY"
    suspend fun speakOnce(eventId: String, event: PickupSpeechEvent, displayName: String?): Result<Unit>
    override fun close() = Unit
}

/** Used only when the official SDK cannot be constructed in the runtime environment. */
class UnsupportedPickupTtsGateway(
    override val verificationMessage: String = "Pleos TTS SDK_VERIFY",
) : PickupTtsGateway {
    private val seen = mutableSetOf<String>()
    override val supported = false
    override suspend fun speakOnce(eventId: String, event: PickupSpeechEvent, displayName: String?): Result<Unit> {
        if (!seen.add(eventId)) return Result.success(Unit)
        return Result.failure(UnsupportedOperationException(verificationMessage))
    }
}

/**
 * Official Pleos TextToSpeech v3.3.1 adapter.
 *
 * ON_DEVICE deliberately avoids server registration and external credentials. The Maven/API
 * contract is compile-verified; service permission and emulator audio remain SDK_VERIFY until the
 * target Pleos image grants TTS_SERVICE and returns a successful initialization callback.
 */
class PleosPickupTtsGateway(context: Context) : PickupTtsGateway {
    private val engine = TextToSpeech(context.applicationContext, Mode.ON_DEVICE)
    private val initialized = CompletableDeferred<Result<Unit>>()
    private val speakMutex = Mutex()
    private val spoken = mutableSetOf<String>()
    @Volatile private var pending: CompletableDeferred<Unit>? = null
    @Volatile private var released = false

    private val listener = object : EventListener {
        override fun onReady() = Unit
        override fun onStart() = Unit
        override fun onUpdatedRms(rms: Double) = Unit
        override fun onDone() { pending?.complete(Unit) }
        override fun onStop() { pending?.completeExceptionally(IllegalStateException("TTS 재생이 중지되었습니다")) }
        override fun onError(errorType: ErrorType) {
            pending?.completeExceptionally(IllegalStateException("Pleos TTS 오류: $errorType"))
        }
    }

    init {
        engine.addEventListener(listener)
        engine.initialize(object : OnInitializeListener {
            override fun onSuccess() { initialized.complete(Result.success(Unit)) }
            override fun onFailure(errorType: ErrorType) {
                initialized.complete(Result.failure(IllegalStateException("Pleos TTS 초기화 실패: $errorType")))
            }
        })
    }

    override val supported: Boolean = true

    override suspend fun speakOnce(eventId: String, event: PickupSpeechEvent, displayName: String?): Result<Unit> = runCatching {
        speakMutex.withLock {
            check(!released) { "Pleos TTS가 해제되었습니다" }
            if (eventId in spoken) return@withLock
            withTimeout(10_000) { initialized.await() }.getOrThrow()
            engine.setLocale(Locale.KOREA)
            val completion = CompletableDeferred<Unit>()
            pending = completion
            try {
                engine.speak(pickupSpeechText(event, displayName))
                withTimeout(15_000) { completion.await() }
                spoken += eventId
            } finally {
                if (pending === completion) pending = null
            }
        }
    }

    override fun close() {
        if (released) return
        released = true
        pending?.cancel()
        engine.stop()
        engine.removeEventListener(listener)
        engine.release()
    }
}

internal fun pickupSpeechText(event: PickupSpeechEvent, displayName: String?): String {
    val guest = displayName?.trim()?.takeIf(String::isNotEmpty)
    return when (event) {
        PickupSpeechEvent.DEPARTED -> if (guest == null) "픽업 참가자가 출발했습니다." else "$guest 님이 출발했습니다."
        PickupSpeechEvent.PROXIMITY_CANDIDATE -> if (guest == null) "픽업 참가자가 가까이 도착했습니다. 화면에서 확인해 주세요." else "$guest 님이 가까이 도착했습니다. 화면에서 확인해 주세요."
        PickupSpeechEvent.COMPLETED -> if (guest == null) "픽업이 완료되었습니다." else "$guest 님과의 픽업이 완료되었습니다."
    }
}
