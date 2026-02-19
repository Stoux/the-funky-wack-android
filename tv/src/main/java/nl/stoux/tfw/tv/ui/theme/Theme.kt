package nl.stoux.tfw.tv.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

@OptIn(ExperimentalTvMaterial3Api::class)
private val TvDarkColorScheme = darkColorScheme(
    primary = TvAccentPrimary,
    onPrimary = Color.Black,
    secondary = TvAccentSecondary,
    onSecondary = Color.Black,
    background = TvBlack,
    onBackground = TvWhite,
    surface = TvDarkGray,
    onSurface = TvWhite,
    surfaceVariant = TvSurfaceGray,
    onSurfaceVariant = TvLightGray,
)

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = TvDarkColorScheme,
        typography = TvTypography,
        content = content
    )
}
