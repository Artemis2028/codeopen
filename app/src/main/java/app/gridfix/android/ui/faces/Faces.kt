package app.gridfix.android.ui.faces

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.gridfix.android.coords.Coordinates
import app.gridfix.android.ui.theme.LabelFamily
import app.gridfix.android.ui.theme.MonoFamily
import app.gridfix.android.ui.theme.NumeralFamily
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The Position and Navigate screens come in three user-selectable faces.
 * Every face renders the same fix, heading and target; only the instrument
 * changes. Colors come from the theme, so night mode is automatic.
 */
object Face {
    const val GLANCE = 0     // two big numbers, one row of data
    const val LENSATIC = 1   // the issued M-1950 / Cammenga dial, grid on the glass
    const val DIAL = 2       // a clean compass card
    val names = listOf("Glance", "Lensatic", "Dial")
}

data class FacePalette(
    val ink: Color,
    val muted: Color,
    val line: Color,
    val accent: Color,
    val lume: Color,
    val night: Boolean,
)

@Composable
fun facePalette(night: Boolean): FacePalette {
    val cs = MaterialTheme.colorScheme
    return FacePalette(
        ink = cs.onBackground,
        muted = cs.onSurfaceVariant,
        line = cs.outline,
        accent = cs.primary,
        lume = cs.tertiary,
        night = night,
    )
}

/** Heading/azimuth expressed in the user's north reference: 0 true, 1 magnetic, 2 grid. */
fun toNorthRef(angleTrue: Float, northRef: Int, declination: Float, convergence: Float): Float = when (northRef) {
    1 -> (angleTrue - declination + 360f) % 360f
    2 -> (angleTrue - convergence + 360f) % 360f
    else -> angleTrue
}

fun northRefLetter(northRef: Int): String = when (northRef) {
    1 -> "M"
    2 -> "G"
    else -> "T"
}

/** "TURN RIGHT 12°" style steer for a signed deviation (target minus heading, -180..180). */
fun steerText(deviation: Float?, angleUnit: Int): String {
    if (deviation == null) return "NO HEADING"
    val mag = abs(deviation)
    if (mag <= 3f) return "ON COURSE"
    val amount = if (angleUnit == 1) "${(mag * 6400f / 360f).roundToInt()} mils" else "${mag.roundToInt()}°"
    return if (deviation > 0f) "TURN RIGHT $amount" else "TURN LEFT $amount"
}

// ---------------------------------------------------------------------------
// Fix quality: one word and five bars an operator can act on, instead of a
// satellite ratio. Horizontal accuracy (68 % radius) is what matters; the
// satellite count only caps it; a fix that is old or did not come from GNSS
// is graded down no matter how good its number looks. "Trust N-digit" = the
// finest MGRS precision whose cell still covers the error circle.
// ---------------------------------------------------------------------------
class FixQuality(
    val bars: Int,
    val word: String,
    val trustDigits: Int,
    val accuracyM: Float?,
    val ageSeconds: Long,
    val stale: Boolean,
    val network: Boolean,
)

const val FIX_STALE_AFTER_S = 30L

/** Seconds since the fix was produced, from the monotonic clock (immune to a wrong phone clock). */
fun fixAgeSeconds(loc: android.location.Location): Long =
    ((android.os.SystemClock.elapsedRealtimeNanos() - loc.elapsedRealtimeNanos) / 1_000_000_000L).coerceAtLeast(0L)

fun fixQuality(loc: android.location.Location?, satsUsed: Int): FixQuality {
    if (loc == null) return FixQuality(0, "NO FIX", 0, null, 0L, stale = false, network = false)
    val age = fixAgeSeconds(loc)
    val acc = if (loc.hasAccuracy()) loc.accuracy else 100f
    val network = loc.provider != android.location.LocationManager.GPS_PROVIDER
    var bars = when {
        acc <= 5f -> 5
        acc <= 10f -> 4
        acc <= 20f -> 3
        acc <= 50f -> 2
        else -> 1
    }
    if (satsUsed in 1..3) bars = minOf(bars, 1)
    else if (satsUsed in 4..5) bars = minOf(bars, 3)
    if (network) bars = minOf(bars, 2)
    val stale = age > FIX_STALE_AFTER_S
    if (stale) bars = 1
    var trust = when {
        acc <= 1f -> 10
        acc <= 5f -> 8
        acc <= 50f -> 6
        else -> 4
    }
    if (network) trust = minOf(trust, 6)
    if (stale) trust = 4
    val word = when {
        stale -> "STALE"
        network -> "NETWORK"
        bars == 5 -> "EXCELLENT"
        bars == 4 -> "GOOD"
        bars == 3 -> "FAIR"
        bars == 2 -> "POOR"
        else -> "DEGRADED"
    }
    return FixQuality(bars, word, trust, if (loc.hasAccuracy()) loc.accuracy else null, age, stale, network)
}

@Composable
fun fixColor(q: FixQuality, p: FacePalette): Color = when {
    q.bars >= 4 -> p.lume
    q.bars >= 2 -> p.accent
    q.bars == 1 -> MaterialTheme.colorScheme.error
    else -> p.muted
}

/** Five rising bars, filled to the quality level. */
@Composable
fun FixBars(q: FixQuality, p: FacePalette, modifier: Modifier = Modifier, barWidth: Dp = 4.dp, height: Dp = 14.dp) {
    val on = fixColor(q, p)
    val off = p.line
    Canvas(modifier.size(width = barWidth * 5 + 8.dp, height = height)) {
        val w = barWidth.toPx()
        val gap = 2.dp.toPx()
        for (i in 0 until 5) {
            val h = size.height * (0.4f + 0.15f * i)
            drawRect(
                color = if (i < q.bars) on else off,
                topLeft = Offset(i * (w + gap), size.height - h),
                size = Size(w, h),
            )
        }
    }
}

/**
 * Short status for the top of a face: "GOOD ±4 m · 12 sats", "STALE 42 s · ±4 m",
 * "ACQUIRING · 3 sats". With [showTrust] the satellite count gives way to the
 * precision the fix actually supports ("GOOD ±8 m · TRUST 8") — used when the
 * displayed grid is finer than that.
 */
fun fixSummary(q: FixQuality, satsUsed: Int, accuracyText: String?, showTrust: Boolean = false): String {
    val tail = if (showTrust) "TRUST ${q.trustDigits}" else "$satsUsed sats"
    return when {
        q.bars == 0 -> "ACQUIRING · $satsUsed sats"
        q.stale -> listOfNotNull("STALE ${q.ageSeconds} s", if (showTrust) tail else accuracyText).joinToString(" · ")
        q.network -> listOfNotNull("NETWORK", accuracyText, if (showTrust) tail else null).joinToString(" · ")
        else -> listOfNotNull(q.word + (accuracyText?.let { " $it" } ?: ""), tail).joinToString(" · ")
    }
}

/** True when the displayed precision claims more than the fix supports. */
fun overPrecise(q: FixQuality, mgrsDigits: Int): Boolean = q.bars > 0 && mgrsDigits > q.trustDigits

/** "42 s", "3 m 10 s", ">1 h" — how long ago a fix was produced. */
fun fixAgeText(seconds: Long): String = when {
    seconds < 60 -> "$seconds s"
    seconds < 3600 -> "${seconds / 60} m ${seconds % 60} s"
    else -> ">1 h"
}

// ---------------------------------------------------------------------------
// Shared furniture: three-up cells and hairline rows (rules instead of boxes)
// ---------------------------------------------------------------------------

/** Cell values shrink instead of wrapping, so "1234 mils" and "12.3 km/h" stay one number. */
private fun cellValueSize(value: String): TextUnit = when {
    value.length <= 8 -> 18.sp
    value.length <= 10 -> 15.sp
    else -> 13.sp
}

@Composable
fun FaceCells(cells: List<Pair<String, String>>, p: FacePalette, modifier: Modifier = Modifier) {
    val line = p.line
    Row(
        modifier
            .fillMaxWidth()
            .drawBehind {
                val w = 1.dp.toPx()
                drawLine(line, Offset(0f, 0f), Offset(size.width, 0f), w)
                drawLine(line, Offset(0f, size.height), Offset(size.width, size.height), w)
            },
    ) {
        cells.forEachIndexed { i, (lab, value) ->
            val last = i == cells.lastIndex
            Column(
                Modifier
                    .weight(1f)
                    .drawBehind {
                        if (!last) drawLine(line, Offset(size.width, 0f), Offset(size.width, size.height), 1.dp.toPx())
                    }
                    .padding(vertical = 12.dp, horizontal = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text(
                    lab.uppercase(),
                    fontFamily = LabelFamily,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 1.3.sp,
                    color = p.muted,
                    maxLines = 1,
                    softWrap = false,
                )
                Text(
                    value,
                    fontFamily = MonoFamily,
                    fontSize = cellValueSize(value),
                    color = p.ink,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}

/** The same cells stacked as label-left / value-right rows, for the narrow column of a landscape face. */
@Composable
fun StackedCells(cells: List<Pair<String, String>>, p: FacePalette, modifier: Modifier = Modifier) {
    val line = p.line
    Column(modifier.fillMaxWidth()) {
        cells.forEachIndexed { i, (lab, value) ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .drawBehind {
                        if (i > 0) drawLine(line, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx())
                    }
                    .padding(vertical = 9.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    lab.uppercase(),
                    fontFamily = LabelFamily,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 1.3.sp,
                    color = p.muted,
                    maxLines = 1,
                    softWrap = false,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    value,
                    fontFamily = MonoFamily,
                    fontSize = cellValueSize(value),
                    color = p.ink,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}

@Composable
fun FaceRow(
    left: String,
    right: String,
    p: FacePalette,
    rightInk: Boolean = false,
    labelFont: Boolean = false,
    rule: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val line = p.line
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 38.dp)
            .drawBehind {
                if (rule) drawLine(line, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx())
            },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val family = if (labelFont) LabelFamily else MonoFamily
        val fs = if (labelFont) 12.sp else 13.sp
        val track = if (labelFont) 1.2.sp else 0.sp
        Text(left, fontFamily = family, fontSize = fs, letterSpacing = track, color = p.muted, maxLines = 1)
        Text(right, fontFamily = family, fontSize = fs, letterSpacing = track, color = if (rightInk) p.ink else p.muted, maxLines = 1)
    }
}

// ---------------------------------------------------------------------------
// Glance: the two numbers and one row of data. Position fills the first
// screen; everything else scrolls up from below.
// ---------------------------------------------------------------------------

@Composable
fun GlancePositionFace(
    p: FacePalette,
    parts: Coordinates.MgrsParts?,
    acquiring: Boolean,
    quality: FixQuality,
    statusLine: String,
    precisionLabel: String,
    precisionWarn: Boolean,
    onCyclePrecision: () -> Unit,
    cells: List<Pair<String, String>>,
    utm: String,
    dtg: String,
    minHeight: Dp,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = minHeight)
            .padding(horizontal = 20.dp, vertical = 10.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            FixBars(quality, p)
            Spacer(Modifier.size(10.dp))
            Text(
                statusLine,
                fontFamily = MonoFamily,
                fontSize = 13.sp,
                letterSpacing = 0.5.sp,
                color = if (acquiring) p.muted else fixColor(quality, p),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            PrecisionTag(precisionLabel, p, precisionWarn, onCyclePrecision)
        }
        Spacer(Modifier.weight(1f))
        if (parts != null && parts.square.isEmpty()) {
            Text(parts.full, fontFamily = MonoFamily, fontSize = 34.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold, color = p.ink)
        } else {
            Text(
                if (parts != null) "${parts.gzd} ${parts.square}" else "—",
                fontFamily = MonoFamily,
                fontSize = 24.sp,
                letterSpacing = 4.sp,
                color = p.muted,
            )
            Spacer(Modifier.height(6.dp))
            val e = parts?.easting?.ifEmpty { "—" } ?: "-----"
            val n = parts?.northing?.ifEmpty { "—" } ?: "-----"
            BigNumeral(e, if (parts == null) p.muted else p.ink)
            BigNumeral(n, if (parts == null) p.muted else p.ink)
        }
        Spacer(Modifier.height(26.dp))
        FaceCells(cells, p)
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(utm, fontFamily = MonoFamily, fontSize = 13.sp, color = p.muted, maxLines = 1)
            Text(dtg, fontFamily = MonoFamily, fontSize = 13.sp, color = p.muted, maxLines = 1)
        }
        Spacer(Modifier.height(18.dp))
        Text(
            "SWIPE UP FOR LAT/LON · PHONETIC · MARK",
            fontFamily = MonoFamily,
            fontSize = 12.sp,
            letterSpacing = 0.8.sp,
            color = p.muted,
        )
        Spacer(Modifier.weight(1f))
    }
}

/**
 * Glance Position, worn sideways: the grid on one line across the left, the data
 * column on the right. Fills the viewport like the portrait face.
 */
@Composable
fun GlancePositionLandscape(
    p: FacePalette,
    parts: Coordinates.MgrsParts?,
    acquiring: Boolean,
    quality: FixQuality,
    statusLine: String,
    precisionLabel: String,
    precisionWarn: Boolean,
    trustLine: String,
    onCyclePrecision: () -> Unit,
    cells: List<Pair<String, String>>,
    utm: String,
    dtg: String,
    minHeight: Dp,
) {
    val line = p.line
    val rightWidth = 236.dp
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = minHeight)
            .drawBehind {
                val x = size.width - (16.dp + rightWidth).toPx()
                drawLine(line, Offset(x, 14.dp.toPx()), Offset(x, size.height - 14.dp.toPx()), 1.dp.toPx())
            }
            .padding(start = 20.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FixBars(quality, p)
                Spacer(Modifier.size(10.dp))
                Text(
                    statusLine,
                    fontFamily = MonoFamily,
                    fontSize = 13.sp,
                    letterSpacing = 0.5.sp,
                    color = if (acquiring) p.muted else fixColor(quality, p),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(8.dp))
            if (parts != null && parts.square.isEmpty()) {
                Text(parts.full, fontFamily = MonoFamily, fontSize = 34.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold, color = p.ink)
            } else {
                Text(
                    if (parts != null) "${parts.gzd} ${parts.square}" else "—",
                    fontFamily = MonoFamily,
                    fontSize = 20.sp,
                    letterSpacing = 4.sp,
                    color = p.muted,
                )
                val e = parts?.easting?.ifEmpty { "—" } ?: "-----"
                val n = parts?.northing?.ifEmpty { "—" } ?: "-----"
                FitNumeral("$e $n", if (parts == null) p.muted else p.ink, Modifier.fillMaxWidth())
            }
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .drawBehind { drawLine(line, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx()) }
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(utm, fontFamily = MonoFamily, fontSize = 13.sp, color = p.muted, maxLines = 1)
                Spacer(Modifier.width(8.dp))
                Text(dtg, fontFamily = MonoFamily, fontSize = 13.sp, color = p.muted, maxLines = 1)
            }
        }
        Column(Modifier.width(rightWidth).padding(start = 16.dp)) {
            StackedCells(cells, p)
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    trustLine,
                    fontFamily = LabelFamily,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 1.2.sp,
                    color = if (precisionWarn) p.accent else p.muted,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                PrecisionTag(precisionLabel, p, precisionWarn, onCyclePrecision)
            }
        }
    }
}

@Composable
private fun BigNumeral(text: String, color: Color) {
    Text(
        text,
        fontFamily = NumeralFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 96.sp,
        lineHeight = 92.sp,
        letterSpacing = 1.sp,
        color = color,
        maxLines = 1,
        softWrap = false,
    )
}

/** One-line numeral that shrinks from 96 sp until it fits the width it is given. */
@Composable
private fun FitNumeral(text: String, color: Color, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier) {
        val measurer = rememberTextMeasurer()
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.toPx() }
        val fitted = remember(text, widthPx, density.fontScale) {
            val style = TextStyle(fontFamily = NumeralFamily, fontWeight = FontWeight.Bold, fontSize = 96.sp, letterSpacing = 1.sp)
            val w = measurer.measure(AnnotatedString(text), style, softWrap = false).size.width.toFloat()
            if (w <= widthPx || w <= 0f) 96f else (96f * widthPx / w * 0.98f).coerceAtLeast(36f)
        }
        Text(
            text,
            fontFamily = NumeralFamily,
            fontWeight = FontWeight.Bold,
            fontSize = fitted.sp,
            lineHeight = (fitted * 0.96f).sp,
            letterSpacing = 1.sp,
            color = color,
            maxLines = 1,
            softWrap = false,
        )
    }
}

/** The precision switch: a 44 dp outlined tag; amber when the grid claims more than the fix supports. */
@Composable
private fun PrecisionTag(label: String, p: FacePalette, warn: Boolean, onClick: () -> Unit) {
    val line = if (warn) p.accent else p.line
    Box(
        Modifier
            .heightIn(min = 44.dp)
            .drawBehind {
                drawRect(line, style = Stroke(1.dp.toPx()))
            }
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            fontFamily = LabelFamily,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 1.2.sp,
            color = if (warn) p.accent else p.muted,
            maxLines = 1,
            softWrap = false,
        )
    }
}

/** The Glance target arrow: solid when it has a heading, an outline when it does not. */
@Composable
fun GlanceArrow(relBearing: Float?, p: FacePalette, arrowSize: Dp) {
    val hasHeading = relBearing != null
    val ink = p.ink
    val muted = p.muted
    Canvas(Modifier.size(arrowSize)) {
        rotate(relBearing ?: 0f) {
            val c = center
            val h = size.minDimension
            val path = Path().apply {
                moveTo(c.x, c.y - h * 0.42f)
                lineTo(c.x + h * 0.25f, c.y + h * 0.33f)
                lineTo(c.x, c.y + h * 0.16f)
                lineTo(c.x - h * 0.25f, c.y + h * 0.33f)
                close()
            }
            if (hasHeading) drawPath(path, ink)
            else drawPath(path, muted, style = Stroke(width = 3.dp.toPx()))
        }
    }
}

@Composable
fun GlanceNavigateFace(
    p: FacePalette,
    relBearing: Float?,
    distance: String,
    cells: List<Pair<String, String>>,
    headingLine: String,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        GlanceArrow(relBearing, p, 236.dp)
        Spacer(Modifier.height(4.dp))
        DistanceHero(distance, p, numeralSize = 106.sp, numeralLine = 100.sp, unitSize = 20.sp, display = true)
        Spacer(Modifier.height(18.dp))
        FaceCells(cells, p)
        Spacer(Modifier.height(14.dp))
        Text(headingLine, fontFamily = MonoFamily, fontSize = 13.sp, letterSpacing = 0.8.sp, color = p.muted, textAlign = TextAlign.Center)
    }
}

@Composable
fun DistanceHero(
    distance: String,
    p: FacePalette,
    numeralSize: TextUnit,
    numeralLine: TextUnit,
    unitSize: TextUnit,
    display: Boolean,
) {
    val number = distance.substringBeforeLast(' ')
    val unit = if (distance.contains(' ')) distance.substringAfterLast(' ') else ""
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            number,
            fontFamily = if (display) NumeralFamily else MonoFamily,
            fontWeight = FontWeight.Bold,
            fontSize = numeralSize,
            lineHeight = numeralLine,
            color = p.ink,
            maxLines = 1,
            softWrap = false,
        )
        if (unit.isNotEmpty()) {
            Text(
                unit,
                fontFamily = if (display) MonoFamily else LabelFamily,
                fontSize = unitSize,
                color = p.muted,
                modifier = Modifier.padding(bottom = 14.dp),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Dial faces
// ---------------------------------------------------------------------------

/** The Position dial with the grid on the glass; shared by the portrait and landscape layouts. */
@Composable
fun DialPositionInstrument(
    p: FacePalette,
    style: Int,
    dialSize: Dp,
    parts: Coordinates.MgrsParts?,
    acquiring: Boolean,
    quality: FixQuality,
    fixLine: String,
    precisionLabel: String,
    onCyclePrecision: () -> Unit,
    headingRef: Float?,
) {
    val lensatic = style == Face.LENSATIC
    // Type scales with the dial: 40 sp on the 360 dp lensatic, ~31 sp at the 280 dp landscape size
    val k = (dialSize.value / (if (lensatic) 360f else 330f)).coerceIn(0.6f, 1f)
    CompassDial(
        size = dialSize,
        style = style,
        p = p,
        rotation = if (lensatic) -(headingRef ?: 0f) else 0f,
        lumeMarkAt = if (!lensatic) headingRef else null,
        hasHeading = !lensatic || headingRef != null,
    ) {
        val numeralSize = ((if (lensatic) 40f else 56f) * k).sp
        val numeralLine = ((if (lensatic) 44f else 60f) * k).sp
        Column(
            Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onCyclePrecision,
            ),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (parts != null && parts.square.isEmpty()) {
                Text(parts.full, fontFamily = MonoFamily, fontSize = (22f * k).sp, fontWeight = FontWeight.Bold, color = p.ink, textAlign = TextAlign.Center)
            } else {
                Text(
                    if (parts != null) "${parts.gzd} ${parts.square}" else "—",
                    fontFamily = MonoFamily,
                    fontSize = if (lensatic) (13f * k).sp else (16f * k).sp,
                    letterSpacing = 3.sp,
                    color = p.muted,
                )
                val e = parts?.easting?.ifEmpty { "—" } ?: "-----"
                val n = parts?.northing?.ifEmpty { "—" } ?: "-----"
                val ink = if (parts == null) p.muted else p.ink
                Text(e, fontFamily = MonoFamily, fontWeight = FontWeight.Bold, fontSize = numeralSize, lineHeight = numeralLine, letterSpacing = 1.5.sp, color = ink, maxLines = 1, softWrap = false)
                Text(n, fontFamily = MonoFamily, fontWeight = FontWeight.Bold, fontSize = numeralSize, lineHeight = numeralLine, letterSpacing = 1.5.sp, color = ink, maxLines = 1, softWrap = false)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "$fixLine · $precisionLabel",
                fontFamily = LabelFamily,
                fontSize = (11f * k).coerceAtLeast(10f).sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.4.sp,
                color = if (acquiring) p.muted else fixColor(quality, p),
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

@Composable
fun DialPositionFace(
    p: FacePalette,
    style: Int,
    dialSize: Dp,
    parts: Coordinates.MgrsParts?,
    acquiring: Boolean,
    quality: FixQuality,
    fixLine: String,
    precisionLabel: String,
    onCyclePrecision: () -> Unit,
    headingRef: Float?,
    cells: List<Pair<String, String>>,
    rows: List<Triple<String, String, Boolean>>,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 15.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        DialPositionInstrument(p, style, dialSize, parts, acquiring, quality, fixLine, precisionLabel, onCyclePrecision, headingRef)
        Spacer(Modifier.height(14.dp))
        FaceCells(cells, p)
        Column(Modifier.padding(horizontal = 5.dp)) {
            rows.forEachIndexed { i, (l, r, label) ->
                FaceRow(l, r, p, rightInk = i == 0, labelFont = label, rule = i > 0)
            }
        }
    }
}

/** Dial Position worn sideways: the dial on the left, cells and rows filling the right. */
@Composable
fun DialPositionLandscape(
    p: FacePalette,
    style: Int,
    dialSize: Dp,
    parts: Coordinates.MgrsParts?,
    acquiring: Boolean,
    quality: FixQuality,
    fixLine: String,
    precisionLabel: String,
    onCyclePrecision: () -> Unit,
    headingRef: Float?,
    cells: List<Pair<String, String>>,
    rows: List<Triple<String, String, Boolean>>,
    minHeight: Dp,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = minHeight)
            .padding(start = 16.dp, end = 20.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DialPositionInstrument(p, style, dialSize, parts, acquiring, quality, fixLine, precisionLabel, onCyclePrecision, headingRef)
        Spacer(Modifier.width(20.dp))
        Column(Modifier.weight(1f)) {
            FaceCells(cells, p)
            rows.forEachIndexed { i, (l, r, label) ->
                FaceRow(l, r, p, rightInk = i == 0, labelFont = label, rule = i > 0)
            }
        }
    }
}

/** The Navigate dial: bezel set to the azimuth (lensatic) or a needle to the target (clean card). */
@Composable
fun DialNavigateInstrument(
    p: FacePalette,
    style: Int,
    dialSize: Dp,
    headingRef: Float?,
    targetRef: Float?,
    headingText: String,
    deviation: Float?,
    angleUnit: Int,
) {
    val lensatic = style == Face.LENSATIC
    val k = (dialSize.value / (if (lensatic) 360f else 330f)).coerceIn(0.6f, 1f)
    CompassDial(
        size = dialSize,
        style = style,
        p = p,
        rotation = -(headingRef ?: 0f),
        bezelLineAt = if (lensatic) targetRef?.let { -it } else null,
        needleAt = if (!lensatic && headingRef != null && targetRef != null) (targetRef - headingRef + 360f) % 360f else null,
        lubber = !lensatic,
        hasHeading = headingRef != null,
    ) {
        if (lensatic) {
            val steer = steerText(deviation, angleUnit)
            // "047° M" sits at 40 sp; "0836 mils M" would paint over the rings, so it drops to 28
            val headingSize = (if (headingText.length <= 6) 40f else 28f) * k
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("HEADING", fontFamily = LabelFamily, fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.6.sp, color = p.muted)
                Text(
                    headingText,
                    fontFamily = MonoFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = headingSize.sp,
                    lineHeight = (headingSize * 1.1f).sp,
                    color = if (headingRef != null) p.ink else p.muted,
                    maxLines = 1,
                    softWrap = false,
                )
                Text(
                    steer,
                    fontFamily = LabelFamily,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 1.6.sp,
                    color = when {
                        deviation == null -> p.muted
                        steer == "ON COURSE" -> p.lume
                        else -> p.accent
                    },
                    maxLines = 1,
                    softWrap = false,
                )
            }
        } else if (headingRef == null) {
            Text(
                "NO HEADING",
                fontFamily = LabelFamily,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.6.sp,
                color = p.muted,
            )
        }
    }
}

/** How to use the dial, in one line under the cells. */
fun dialNavigateHint(style: Int): String =
    if (style == Face.LENSATIC) "BEZEL IS SET TO THE AZIMUTH · TURN UNTIL THE NORTH ARROW SITS UNDER THE BEZEL LINE"
    else "CARD TURNS WITH YOU · NEEDLE POINTS AT THE TARGET"

@Composable
fun DialNavigateFace(
    p: FacePalette,
    style: Int,
    dialSize: Dp,
    headingRef: Float?,
    targetRef: Float?,
    headingText: String,
    deviation: Float?,
    angleUnit: Int,
    distance: String,
    cells: List<Pair<String, String>>,
) {
    val lensatic = style == Face.LENSATIC
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 15.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        DialNavigateInstrument(p, style, dialSize, headingRef, targetRef, headingText, deviation, angleUnit)
        Spacer(Modifier.height(if (lensatic) 12.dp else 6.dp))
        if (!lensatic) {
            Text(
                "HDG $headingText · ${steerText(deviation, angleUnit)}",
                fontFamily = MonoFamily,
                fontSize = 13.sp,
                letterSpacing = 0.8.sp,
                color = if (deviation != null && abs(deviation) <= 3f) p.lume else p.muted,
                maxLines = 1,
            )
            Spacer(Modifier.height(6.dp))
        }
        DistanceHero(distance, p, numeralSize = 56.sp, numeralLine = 60.sp, unitSize = 16.sp, display = false)
        Spacer(Modifier.height(12.dp))
        FaceCells(cells, p)
        Spacer(Modifier.height(12.dp))
        Text(
            dialNavigateHint(style),
            fontFamily = LabelFamily,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 1.2.sp,
            color = p.muted,
            textAlign = TextAlign.Center,
        )
    }
}

// ---------------------------------------------------------------------------
// The dial itself. Geometry is authored at the mock size (360 px lensatic,
// 330 px clean) and scaled; all ring labels are measured once per size.
// ---------------------------------------------------------------------------

private class DialLabel(
    val layout: TextLayoutResult,
    val angle: Float,      // dial bearing of the label
    val radius: Float,     // px from centre
    val color: Color,
    val radial: Boolean,   // true: text reads outward like a real dial; false: stays upright
)

/** The dial's fixed shapes, built once per size instead of on every frame. */
private class DialPaths(val arrow: Path, val needleHead: Path, val lubber: Path, val lumeMark: Path)

private fun dialPaths(px: Float, lensatic: Boolean): DialPaths {
    val c = Offset(px / 2f, px / 2f)
    val r = px / 2f
    val s = px / (if (lensatic) 360f else 330f)
    val big = r - 26f * s
    val arrow = Path().apply {
        moveTo(c.x, c.y - (big - 32f * s))
        lineTo(c.x - 7.5f * s, c.y - (big - 54f * s))
        lineTo(c.x + 7.5f * s, c.y - (big - 54f * s))
        close()
    }
    val needleHead = Path().apply {
        moveTo(c.x, c.y - 140f * s)
        lineTo(c.x - 9f * s, c.y - 116f * s)
        lineTo(c.x + 9f * s, c.y - 116f * s)
        close()
    }
    val lubber = Path().apply {
        moveTo(c.x, c.y - r + 18f * s)
        lineTo(c.x - 8f * s, c.y - r + 4f * s)
        lineTo(c.x + 8f * s, c.y - r + 4f * s)
        close()
    }
    val lumeMark = Path().apply {   // drawn translated to its spot on the ring
        moveTo(0f, -9f * s)
        lineTo(6f * s, 3f * s)
        lineTo(-6f * s, 3f * s)
        close()
    }
    return DialPaths(arrow, needleHead, lubber, lumeMark)
}

private class LensaticColors(
    val case: Color, val bezel: Color, val bezelTick: Color, val face: Color,
    val mils: Color, val degTick: Color, val red: Color,
)

private fun lensaticColors(night: Boolean) = if (night) LensaticColors(
    case = Color(0xFF2A0906), bezel = Color(0xFF0E0202), bezelTick = Color(0xFF6E170F), face = Color(0xFF000000),
    mils = Color(0xFF7A1A12), degTick = Color(0xFF7A1A12), red = Color(0xFF8F1D14),
) else LensaticColors(
    case = Color(0xFF4A5433), bezel = Color(0xFF1C1D1B), bezelTick = Color(0xFF8E938A), face = Color(0xFF050505),
    mils = Color(0xFFD9D4C8), degTick = Color(0xFFB5B0A4), red = Color(0xFFE8442F),
)

@Composable
fun CompassDial(
    size: Dp,
    style: Int,
    p: FacePalette,
    rotation: Float,
    bezelLineAt: Float? = null,
    needleAt: Float? = null,
    lumeMarkAt: Float? = null,
    lubber: Boolean = false,
    hasHeading: Boolean = true,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val density = LocalDensity.current
    val px = with(density) { size.toPx() }
    val lensatic = style == Face.LENSATIC
    val s = px / (if (lensatic) 360f else 330f)   // px per mock pixel
    val lc = lensaticColors(p.night)
    val measurer = rememberTextMeasurer()
    val paths = remember(px, style) { dialPaths(px, lensatic) }
    val labels = remember(px, style, p.ink, p.muted, p.lume, p.night) {
        val r = px / 2f
        val out = ArrayList<DialLabel>()
        fun ts(sizePx: Float, weight: FontWeight, color: Color) = TextStyle(
            fontFamily = LabelFamily,
            fontSize = with(density) { sizePx.toSp() },
            fontWeight = weight,
            color = color,
        )
        if (lensatic) {
            val big = r - 26f * s
            val milsStyle = ts(8.5f * s, FontWeight.Normal, lc.mils)
            for (k in 1 until 32) {
                val mils = k * 200
                out += DialLabel(measurer.measure(AnnotatedString((mils / 100).toString()), milsStyle), mils * 360f / 6400f, big - 16f * s, lc.mils, radial = true)
            }
            val degStyle = ts(10.5f * s, FontWeight.SemiBold, lc.red)
            for (d in 20 until 360 step 20) {
                out += DialLabel(measurer.measure(AnnotatedString(d.toString()), degStyle), d.toFloat(), big - 40f * s, lc.red, radial = true)
            }
            val cardinal = ts(17f * s, FontWeight.SemiBold, p.ink)
            out += DialLabel(measurer.measure(AnnotatedString("E"), cardinal), 90f, big - 52f * s, p.lume, radial = true)
            out += DialLabel(measurer.measure(AnnotatedString("S"), cardinal), 180f, big - 52f * s, p.ink, radial = true)
            out += DialLabel(measurer.measure(AnnotatedString("W"), cardinal), 270f, big - 52f * s, p.lume, radial = true)
        } else {
            val cardinal = ts(18f * s, FontWeight.SemiBold, p.ink)
            listOf("N" to 0f, "E" to 90f, "S" to 180f, "W" to 270f).forEach { (lab, ang) ->
                out += DialLabel(measurer.measure(AnnotatedString(lab), cardinal), ang, r - 36f * s, if (lab == "N") p.lume else p.ink, radial = false)
            }
        }
        out
    }

    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        // Without a heading the card would sit north-up with the index reading 000 and
        // look "on course"; it is dimmed until the compass reports.
        Canvas(Modifier.matchParentSize().alpha(if (hasHeading) 1f else 0.4f)) {
            val c = center
            val r = px / 2f
            if (lensatic) {
                drawLensatic(c, r, s, lc, p, rotation, bezelLineAt, labels, paths.arrow)
            } else {
                drawCleanDial(c, r, s, p, rotation, labels)
            }
            lumeMarkAt?.let { a ->
                val pos = polar(c, r - 22f * s, a)
                translate(pos.x, pos.y) {
                    rotate(a + 180f, pivot = Offset.Zero) {
                        drawPath(paths.lumeMark, p.lume)
                    }
                }
            }
            needleAt?.let { a ->
                rotate(a, pivot = c) {
                    drawLine(p.lume, Offset(c.x, c.y + 6f * s), Offset(c.x, c.y - 118f * s), 4f * s, cap = StrokeCap.Round)
                    drawPath(paths.needleHead, p.lume)
                }
                drawCircle(p.ink, radius = 5f * s, center = c)
            }
            if (lubber) drawPath(paths.lubber, p.ink)
        }
        content()
    }
}

private fun polar(c: Offset, radius: Float, bearingDeg: Float): Offset {
    val a = Math.toRadians((bearingDeg - 90f).toDouble())
    return Offset(c.x + radius * cos(a).toFloat(), c.y + radius * sin(a).toFloat())
}

private fun DrawScope.tick(c: Offset, bearingDeg: Float, from: Float, to: Float, color: Color, width: Float) {
    drawLine(color, polar(c, from, bearingDeg), polar(c, to, bearingDeg), width)
}

private fun DrawScope.drawLabels(c: Offset, labels: List<DialLabel>, rotation: Float) {
    for (l in labels) {
        val pos = polar(c, l.radius, l.angle)
        val w = l.layout.size.width.toFloat()
        val h = l.layout.size.height.toFloat()
        rotate(if (l.radial) l.angle else -rotation, pivot = pos) {
            drawText(l.layout, color = l.color, topLeft = Offset(pos.x - w / 2f, pos.y - h / 2f))
        }
    }
}

private fun DrawScope.drawLensatic(
    c: Offset, r: Float, s: Float, lc: LensaticColors, p: FacePalette,
    rotation: Float, bezelLineAt: Float?, labels: List<DialLabel>, arrow: Path,
) {
    val big = r - 26f * s
    drawCircle(lc.case, radius = r - 1f * s, center = c)
    drawCircle(lc.bezel, radius = r - 8f * s, center = c)
    for (i in 0 until 120) {
        tick(c, i * 3f, r - 22f * s, r - 11f * s, lc.bezelTick, (if (i % 10 == 0) 1.4f else 0.8f) * s)
    }
    bezelLineAt?.let { a ->
        drawLine(p.lume, polar(c, r - 25f * s, a), polar(c, r - 9f * s, a), 3.5f * s, cap = StrokeCap.Round)
    }
    drawCircle(lc.face, radius = big, center = c)
    drawCircle(p.line, radius = big, center = c, style = Stroke(1f * s))
    rotate(rotation, pivot = c) {
        for (k in 0 until 320) {
            val mils = k * 20
            val long = mils % 100 == 0
            tick(c, mils * 360f / 6400f, big - (if (long) 9f else 5f) * s, big - 1f * s, lc.mils, (if (long) 1.1f else 0.6f) * s)
        }
        for (d in 0 until 360 step 5) {
            val long = d % 10 == 0
            tick(c, d.toFloat(), big - 23f * s, big - (if (long) 30f else 27f) * s, lc.degTick, (if (long) 1f else 0.7f) * s)
        }
        // luminous north arrow (head prebuilt per size)
        val base = big - 54f * s
        val stem = big - 74f * s
        drawPath(arrow, p.lume)
        drawRect(p.lume, topLeft = Offset(c.x - 2.5f * s, c.y - base), size = Size(5f * s, base - stem))
        drawLabels(c, labels, rotation)
    }
    // fixed index line on the crystal
    drawLine(p.ink, Offset(c.x, c.y - r + 9f * s), Offset(c.x, c.y - r + 58f * s), 2f * s, cap = StrokeCap.Round)
}

private fun DrawScope.drawCleanDial(
    c: Offset, r: Float, s: Float, p: FacePalette, rotation: Float, labels: List<DialLabel>,
) {
    val bezel = if (p.night) p.line else Color(0xFF3A3A34)
    drawCircle(bezel, radius = r - 2f * s, center = c, style = Stroke(1.5f * s))
    rotate(rotation, pivot = c) {
        for (d in 0 until 360 step 10) {
            val big = d % 30 == 0
            tick(c, d.toFloat(), r - (if (big) 16f else 9f) * s, r - 3f * s, if (big) p.ink else p.muted, (if (big) 2f else 1f) * s)
        }
        drawLabels(c, labels, rotation)
    }
}
