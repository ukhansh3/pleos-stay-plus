package com.pleosstay.app.ui

import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.pleosstay.app.MainActivity
import org.junit.Rule
import org.junit.Test

/** Production routes only: no test identity, simulated safety, pairing or Pickup creation. */
class PickupRealtimeEntryTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()

    @Test fun pickupAndRealtimeRemainReachableThroughTheRail() {
        for (route in listOf("PICKUP", "FILES", "PICKUP", "HOME")) {
            ui.onNodeWithTag("nav_$route").performScrollTo().performClick()
            ui.waitUntil(timeoutMillis = 10_000) {
                runCatching { ui.onNodeWithTag("nav_$route").assertIsSelected(); true }.getOrDefault(false)
            }
        }
    }
}