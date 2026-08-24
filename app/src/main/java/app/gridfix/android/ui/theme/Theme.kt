package app.gridfix.android.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Tactical dark palette (default)
private val TacticalColors = darkColorScheme(
    primary = Color(0xFF7BC67E),
    onPrimary = Color(0xFF0B0E13),
    secondary = Color(0xFFE0B458),
    onSecondary = Color(0xFF0B0E13),
    background = Color(0xFF0B0E13),
    onBackground = Color(0xFFE8EDF2),
    surface = Color(0xFF151B24),
    onSurface = Color(0xFFE8EDF2),
    surfaceVariant = Color(0xFF1D2530),
    onSurfaceVariant = Color(0xFF9AA7B5),
    outline = Color(0xFF3A4654),
    error = Color(0xFFE57373),
    onError = Color(0xFF0B0E13),
)

// Night-vision palette: red on black to preserve dark adaptation
private val NightColors = darkColorScheme(
    primary = Color(0xFFFF3B30),
    onPrimary = Color(0xFF000000),
    secondary = Color(0xFFB3251C),
    onSecondary = Color(0xFF000000),
    background = Color(0xFF000000),
    onBackground = Color(0xFFFF3B30),
    surface = Color(0xFF120000),
    onSurface = Color(0xFFFF3B30),
    surfaceVariant = Color(0xFF1C0300),
    onSurfaceVariant = Color(0xFFB3251C),
    outline = Color(0xFF5A100A),
    error = Color(0xFFFF3B30),
    onError = Color(0xFF000000),
)

@Composable
fun GridFixTheme(
    nightMode: Boolean,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (nightMode) NightColors else TacticalColors,
        content = content,
    )
}
