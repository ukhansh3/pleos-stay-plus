package com.pleosstay.app.pickup

import com.pleosstay.app.pickup.tts.PickupSpeechEvent
import com.pleosstay.app.pickup.tts.UnsupportedPickupTtsGateway
import com.pleosstay.app.pickup.tts.pickupSpeechText
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PickupTtsGatewayTest {
    @Test fun unsupportedGatewayDoesNotPretendFirstPlaybackAndDeduplicatesEvent() = runTest {
        val gateway = UnsupportedPickupTtsGateway()
        assertFalse(gateway.supported)
        assertTrue(gateway.speakOnce("event-1", PickupSpeechEvent.DEPARTED, null).isFailure)
        assertTrue(gateway.speakOnce("event-1", PickupSpeechEvent.DEPARTED, null).isSuccess)
    }

    @Test fun speechTextIncludesParticipantName() {
        assertTrue(pickupSpeechText(PickupSpeechEvent.DEPARTED, "민수").contains("민수"))
        assertTrue(pickupSpeechText(PickupSpeechEvent.PROXIMITY_CANDIDATE, "민수").contains("화면에서 확인"))
        assertTrue(pickupSpeechText(PickupSpeechEvent.COMPLETED, "민수").contains("완료"))
    }
}
