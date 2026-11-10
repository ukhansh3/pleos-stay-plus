package com.pleosstay.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.pleosstay.app.ui.premium.StayDayPalette
import com.pleosstay.app.ui.premium.StayNightPalette
import kotlin.math.max
import kotlin.math.min
import org.junit.Assert.assertTrue
import org.junit.Test

class StayPaletteContrastTest {
    @Test
    fun textAndProgressMeetContrastTargetsInBothThemes() {
        for (palette in listOf(StayDayPalette, StayNightPalette)) {
            assertContrast(palette.Text, palette.Background, 4.5f)
            assertContrast(palette.Secondary, palette.Surface, 4.5f)
            assertContrast(palette.OnSelected, palette.Selected, 4.5f)
            assertContrast(palette.OnAction, palette.Action, 4.5f)
            assertContrast(palette.Error, palette.GlassEnd, 4.5f)
            assertContrast(palette.SelectionOutline, palette.Rail, 3f)

            assertContrast(palette.Ring, palette.PanelStart, 4.5f)
            assertContrast(palette.Ring, palette.PanelEnd, 4.5f)
            assertContrast(palette.Ring, palette.RingTrack, 4.5f)
        }
    }

    private fun assertContrast(foreground: Color, background: Color, minimum: Float) {
        val fg = foreground.luminance()
        val bg = background.luminance()
        val ratio = (max(fg, bg) + 0.05f) / (min(fg, bg) + 0.05f)
        assertTrue("Contrast of ${foreground} against ${background} is ${ratio}:1, expected at least ${minimum}:1", ratio >= minimum)
    }
}
