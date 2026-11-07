package com.pleosstay.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.graphics.Color
import com.pleosstay.app.ui.premium.StayColors

private val PremiumColors = darkColorScheme(
    primary = StayColors.Blue, onPrimary = StayColors.Background,
    secondary = Color(0xFFBED2EE), tertiary = StayColors.Purple,
    background = StayColors.Background, onBackground = StayColors.Text,
    surface = StayColors.Surface, onSurface = StayColors.Text,
    surfaceVariant = StayColors.Rail, onSurfaceVariant = StayColors.Secondary,
    outline = StayColors.Border, error = Color(0xFFFF8B91),
)

@Composable
fun PleosStayPlusTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = PremiumColors, typography = Typography) {
        CompositionLocalProvider(LocalContentColor provides StayColors.Text, content = content)
    }
}
