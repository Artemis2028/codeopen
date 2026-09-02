package app.gridfix.android.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.gridfix.android.R

// ---------------------------------------------------------------------------
// Fonts (all SIL Open Font License, bundled in res/font by the build).
//   LabelFamily   - Saira Semi Condensed: every label, title and body line.
//   MonoFamily    - Fira Mono: grids, azimuths, distances, times - anything
//                   a soldier reads digit by digit.
//   NumeralFamily - Antonio: the oversized grid numerals of the Glance face
//                   (variable font; the weight is applied by Android 8+).
// ---------------------------------------------------------------------------
val LabelFamily = FontFamily(
    Font(R.font.saira_semi_condensed_regular, FontWeight.Normal),
    Font(R.font.saira_semi_condensed_medium, FontWeight.Medium),
    Font(R.font.saira_semi_condensed_semibold, FontWeight.SemiBold),
    Font(R.font.saira_semi_condensed_bold, FontWeight.Bold),
)

val MonoFamily = FontFamily(
    Font(R.font.fira_mono_regular, FontWeight.Normal),
    Font(R.font.fira_mono_medium, FontWeight.Medium),
    Font(R.font.fira_mono_bold, FontWeight.Bold),
)

val NumeralFamily = FontFamily(
    Font(R.font.antonio, FontWeight.Bold),
)

// ---------------------------------------------------------------------------
// Blackout palette (day): pure black, bone-white ink, a single amber accent,
// watch-lume green for "good" status. Rules instead of boxes wherever the
// layout allows; cards are barely lifted off the black.
// ---------------------------------------------------------------------------
private val Bone = Color(0xFFF5F5F0)
private val Amber = Color(0xFFFFB300)
private val Lume = Color(0xFFBFFF7A)
private val Muted = Color(0xFF7E8794)
private val Rule = Color(0xFF1F2630)

private val BlackoutColors = darkColorScheme(
    primary = Amber,
    onPrimary = Color(0xFF000000),
    primaryContainer = Color(0xFF3D2A00),
    onPrimaryContainer = Amber,
    secondary = Amber,
    onSecondary = Color(0xFF000000),
    secondaryContainer = Color(0xFF1A1F26),
    onSecondaryContainer = Bone,
    tertiary = Lume,
    onTertiary = Color(0xFF000000),
    tertiaryContainer = Color(0xFF1E2E10),
    onTertiaryContainer = Lume,
    background = Color(0xFF000000),
    onBackground = Bone,
    surface = Color(0xFF0B0D10),
    onSurface = Bone,
    surfaceVariant = Color(0xFF141920),
    onSurfaceVariant = Muted,
    surfaceContainerLowest = Color(0xFF000000),
    surfaceContainerLow = Color(0xFF0B0D10),
    surfaceContainer = Color(0xFF0F1216),
    surfaceContainerHigh = Color(0xFF141920),
    surfaceContainerHighest = Color(0xFF1A2028),
    outline = Rule,
    outlineVariant = Color(0xFF151A21),
    error = Color(0xFFFF6B61),
    onError = Color(0xFF000000),
)

// Night-vision palette: red on black to preserve dark adaptation
private val NightColors = darkColorScheme(
    primary = Color(0xFFFF3B30),
    onPrimary = Color(0xFF000000),
    primaryContainer = Color(0xFF3A0A06),
    onPrimaryContainer = Color(0xFFFF3B30),
    secondary = Color(0xFFB3251C),
    onSecondary = Color(0xFF000000),
    secondaryContainer = Color(0xFF1C0300),
    onSecondaryContainer = Color(0xFFFF3B30),
    tertiary = Color(0xFFFF3B30),
    onTertiary = Color(0xFF000000),
    tertiaryContainer = Color(0xFF3A0A06),
    onTertiaryContainer = Color(0xFFFF3B30),
    background = Color(0xFF000000),
    onBackground = Color(0xFFFF3B30),
    surface = Color(0xFF120000),
    onSurface = Color(0xFFFF3B30),
    surfaceVariant = Color(0xFF1C0300),
    onSurfaceVariant = Color(0xFFB3251C),
    surfaceContainerLowest = Color(0xFF000000),
    surfaceContainerLow = Color(0xFF0A0000),
    surfaceContainer = Color(0xFF120000),
    surfaceContainerHigh = Color(0xFF1C0300),
    surfaceContainerHighest = Color(0xFF260500),
    outline = Color(0xFF5A100A),
    outlineVariant = Color(0xFF3A0A06),
    error = Color(0xFFFF3B30),
    onError = Color(0xFF000000),
)

private fun Typography.withFamily(family: FontFamily): Typography = Typography(
    displayLarge = displayLarge.copy(fontFamily = family),
    displayMedium = displayMedium.copy(fontFamily = family),
    displaySmall = displaySmall.copy(fontFamily = family),
    headlineLarge = headlineLarge.copy(fontFamily = family),
    headlineMedium = headlineMedium.copy(fontFamily = family),
    headlineSmall = headlineSmall.copy(fontFamily = family),
    titleLarge = titleLarge.copy(fontFamily = family),
    titleMedium = titleMedium.copy(fontFamily = family, fontWeight = FontWeight.SemiBold),
    titleSmall = titleSmall.copy(fontFamily = family, fontWeight = FontWeight.SemiBold),
    bodyLarge = bodyLarge.copy(fontFamily = family),
    bodyMedium = bodyMedium.copy(fontFamily = family),
    bodySmall = bodySmall.copy(fontFamily = family),
    labelLarge = labelLarge.copy(fontFamily = family, fontWeight = FontWeight.Medium),
    labelMedium = labelMedium.copy(fontFamily = family, fontWeight = FontWeight.Medium),
    labelSmall = labelSmall.copy(fontFamily = family, fontWeight = FontWeight.Medium),
)

private val GridFixTypography = Typography().withFamily(LabelFamily)

// Equipment corners: near-square. Buttons keep Material's pill shape.
private val GridFixShapes = Shapes(
    extraSmall = RoundedCornerShape(2.dp),
    small = RoundedCornerShape(2.dp),
    medium = RoundedCornerShape(4.dp),
    large = RoundedCornerShape(6.dp),
    extraLarge = RoundedCornerShape(8.dp),
)

@Composable
fun GridFixTheme(
    nightMode: Boolean,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (nightMode) NightColors else BlackoutColors,
        typography = GridFixTypography,
        shapes = GridFixShapes,
        content = content,
    )
}
