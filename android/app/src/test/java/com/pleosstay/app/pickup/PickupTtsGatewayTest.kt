package com.pleosstay.app.pickup

import com.pleosstay.app.pickup.tts.PickupSpeechEvent
import com.pleosstay.app.pickup.tts.UnsupportedPickupTtsGateway
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
}
