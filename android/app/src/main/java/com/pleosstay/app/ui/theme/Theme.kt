package com.pleosstay.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.pleosstay.app.ui.premium.LocalStayPalette
import com.pleosstay.app.ui.premium.StayDayPalette
import com.pleosstay.app.ui.premium.StayNightPalette
import com.pleosstay.app.ui.premium.StayPalette

private fun StayPalette.materialColors(base: ColorScheme) = base.copy(
    primary = Blue,
    onPrimary = if (isDark) Background else OnAction,
    primaryContainer = Selected,
    onPrimaryContainer = OnSelected,
    secondary = Secondary,
    onSecondary = if (isDark) Background else OnAction,
    secondaryContainer = Surface,
    onSecondaryContainer = Text,
    tertiary = Accent,
    onTertiary = if (isDark) Background else OnAction,
    tertiaryContainer = Selected,
    onTertiaryContainer = OnSelected,
    background = Background,
    onBackground = Text,
    surface = Surface,
    onSurface = Text,
    surfaceVariant = Rail,
    onSurfaceVariant = Secondary,
    surfaceDim = Background,
    surfaceBright = PanelStart,
    surfaceContainerLowest = Background,
    surfaceContainerLow = PanelEnd,
    surfaceContainer = GlassStart,
    surfaceContainerHigh = PanelStart,
    surfaceContainerHighest = PanelEnd,
    inverseSurface = Text,
    inverseOnSurface = Background,
    inversePrimary = if (isDark) StayDayPalette.Blue else StayNightPalette.Blue,
    outline = Border,
    outlineVariant = Border,
    error = Error,
    onError = if (isDark) Background else OnAction,
    errorContainer = Surface,
    onErrorContainer = Error,
)

private val dayColors = StayDayPalette.materialColors(lightColorScheme())
private val nightColors = StayNightPalette.materialColors(darkColorScheme())

@Composable
fun PleosStayPlusTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val palette = if (darkTheme) StayNightPalette else StayDayPalette
    CompositionLocalProvider(LocalStayPalette provides palette) {
        MaterialTheme(colorScheme = if (darkTheme) nightColors else dayColors, typography = Typography) {
            CompositionLocalProvider(LocalContentColor provides palette.Text, content = content)
        }
    }
}
