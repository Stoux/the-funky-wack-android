package nl.stoux.tfw.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val AppDarkColorScheme = darkColorScheme(
    primary = AccentPrimary, // used for primary buttons/accents
    secondary = AccentSecondary,
    background = Black,
    surface = Black,
    surfaceVariant = DarkGray,
    onPrimary = Color.Black,
    onSecondary = Color.Black,
    onBackground = White,
    onSurface = White,
    onSurfaceVariant = LightGray,
)

@Composable
fun TheFunkyWackTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = AppDarkColorScheme,
        typography = Typography,
        content = content
    )
}