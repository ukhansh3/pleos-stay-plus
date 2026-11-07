package com.pleosstay.app.ui

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.pleosstay.app.MainActivity
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Real production entry/routes. Never creates Pickup, pairing, documents or Waiting sessions. */
class PremiumProductionEntryTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()

    @Test fun realEntryShowsTheHomeAndKeepsEveryCurrentFeatureReachable() {
        ui.onNodeWithTag("nav_HOME").assertIsSelected()
        ui.onNodeWithTag("home_PARKING").performScrollTo().assertExists()
        // Save a visual proof ONLY if there is no active Waiting/place to expose.
        val empty = ui.onAllNodesWithText("대기 세션 없음").fetchSemanticsNodes().isNotEmpty()
        if (empty) {
            val target = InstrumentationRegistry.getInstrumentation().targetContext
            val directory = File(target.cacheDir, "premium-ui-proof").apply { mkdirs() }
            ui.onRoot().captureToImage().asAndroidBitmap().let { image ->
                File(directory, "home-v2.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG,100,it) }
            }
        }
        for (name in listOf("PARKING","PICKUP","FILES")) {
            ui.onNodeWithTag("nav_$name").performScrollTo().performClick()
            ui.onNodeWithTag("nav_$name").assertIsSelected()
        }
        ui.onNodeWithTag("nav_MODES").performScrollTo().performClick()
        ui.onNodeWithTag("mode_STUDY").performScrollTo().performClick()
        ui.onNodeWithTag("nav_MODES").assertIsSelected()
        ui.onNodeWithTag("back").performClick()
        ui.onNodeWithTag("mode_WORK").performScrollTo().performClick()
        ui.onNodeWithTag("nav_MODES").assertIsSelected()
        ui.onNodeWithTag("back").performClick()
        ui.onNodeWithTag("mode_REST").performScrollTo().performClick()
        ui.onNodeWithTag("nav_MODES").assertIsSelected()
        ui.onNodeWithTag("nav_HOME").performScrollTo().performClick()
        ui.onNodeWithTag("nav_HOME").assertIsSelected()
    }
}
