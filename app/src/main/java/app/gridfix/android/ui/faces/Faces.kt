package app.gridfix.android.ui.faces

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
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

class FacePalette(
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
// satellite ratio. Horizontal accuracy is what matters; the satellite count
// only caps it. "Trust N-digit" = the MGRS precision whose cell is at least
// as big as the error circle.
// ---------------------------------------------------------------------------
class FixQuality(val bars: Int, val word: String, val trustDigits: Int, val accuracyM: Float?)

fun fixQuality(loc: android.location.Location?, satsUsed: Int): FixQuality {
    if (loc == null) return FixQuality(0, "NO FIX", 0, null)
    val acc = if (loc.hasAccuracy()) loc.accuracy else 100f
    var bars = when {
        acc <= 5f -> 5
        acc <= 10f -> 4
        acc <= 20f -> 3
        acc <= 50f -> 2
        else -> 1
    }
    if (satsUsed in 1..3) bars = minOf(bars, 1)
    else if (satsUsed in 4..5) bars = minOf(bars, 3)
    val word = when (bars) {
        5 -> "EXCELLENT"
        4 -> "GOOD"
        3 -> "FAIR"
        2 -> "POOR"
        else -> "DEGRADED"
    }
    val trust = when {
        acc <= 2f -> 10
        acc <= 15f -> 8
        acc <= 150f -> 6
        else -> 4
    }
    return FixQuality(bars, word, trust, if (loc.hasAccuracy()) loc.accuracy else null)
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

/** "GOOD ±4 m · 9 sats · trust 8-digit" */
fun fixSummary(q: FixQuality, satsUsed: Int, accuracyText: String?): String = when {
    q.bars == 0 -> "acquiring · $satsUsed sats"
    else -> listOfNotNull(
        q.word.lowercase().replaceFirstChar { it.uppercase() },
        accuracyText,
        "$satsUsed sats",
        "trust ${q.trustDigits}-digit",
    ).joinToString(" · ")
}

// ---------------------------------------------------------------------------
// Shared furniture: three-up cells and hairline rows (rules instead of boxes)
// ---------------------------------------------------------------------------

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
                )
                Text(
                    value,
                    fontFamily = MonoFamily,
                    fontSize = 18.sp,
                    color = p.ink,
                    maxLines = 1,
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
                modifier = Modifier.weight(1f),
            )
            PrecisionTag(precisionLabel, p, onCyclePrecision)
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

@Composable
private fun PrecisionTag(label: String, p: FacePalette, onClick: () -> Unit) {
    val line = p.line
    Box(
        Modifier
            .heightIn(min = 32.dp)
            .drawBehind {
                drawRect(line, style = Stroke(1.dp.toPx()))
            }
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontFamily = LabelFamily, fontSize = 12.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.2.sp, color = p.muted)
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
        val arrowColor = if (relBearing != null) p.ink else p.muted
        Canvas(Modifier.size(236.dp)) {
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
                drawPath(path, arrowColor)
            }
        }
        Spacer(Modifier.height(4.dp))
        DistanceHero(distance, p, numeralSize = 106.sp, numeralLine = 100.sp, unitSize = 20.sp, display = true)
        Spacer(Modifier.height(18.dp))
        FaceCells(cells, p)
        Spacer(Modifier.height(14.dp))
        Text(headingLine, fontFamily = MonoFamily, fontSize = 13.sp, letterSpacing = 0.8.sp, color = p.muted)
    }
}

@Composable
private fun DistanceHero(
    distance: String,
    p: FacePalette,
    numeralSize: androidx.compose.ui.unit.TextUnit,
    numeralLine: androidx.compose.ui.unit.TextUnit,
    unitSize: androidx.compose.ui.unit.TextUnit,
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
    val lensatic = style == Face.LENSATIC
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 15.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CompassDial(
            size = dialSize,
            style = style,
            p = p,
            rotation = if (lensatic) -(headingRef ?: 0f) else 0f,
            lumeMarkAt = if (!lensatic) headingRef else null,
        ) {
            val numeralSize = if (lensatic) 40.sp else 56.sp
            val numeralLine = if (lensatic) 44.sp else 60.sp
            Column(
                Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onCyclePrecision,
                ),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (parts != null && parts.square.isEmpty()) {
                    Text(parts.full, fontFamily = MonoFamily, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = p.ink, textAlign = TextAlign.Center)
                } else {
                    Text(
                        if (parts != null) "${parts.gzd} ${parts.square}" else "—",
                        fontFamily = MonoFamily,
                        fontSize = if (lensatic) 13.sp else 16.sp,
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
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 1.4.sp,
                    color = if (acquiring) p.muted else fixColor(quality, p),
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        FaceCells(cells, p)
        Column(Modifier.padding(horizontal = 5.dp)) {
            rows.forEachIndexed { i, (l, r, label) ->
                FaceRow(l, r, p, rightInk = i == 0, labelFont = label, rule = i > 0)
            }
        }
    }
}

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
        CompassDial(
            size = dialSize,
            style = style,
            p = p,
            rotation = -(headingRef ?: 0f),
            bezelLineAt = if (lensatic) targetRef?.let { -it } else null,
            needleAt = if (!lensatic && headingRef != null && targetRef != null) (targetRef - headingRef + 360f) % 360f else null,
            lubber = !lensatic,
        ) {
            if (lensatic) {
                val steer = steerText(deviation, angleUnit)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("HEADING", fontFamily = LabelFamily, fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.6.sp, color = p.muted)
                    Text(headingText, fontFamily = MonoFamily, fontWeight = FontWeight.Bold, fontSize = 40.sp, lineHeight = 44.sp, color = p.ink, maxLines = 1)
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
                    )
                }
            }
        }
        Spacer(Modifier.height(if (lensatic) 12.dp else 6.dp))
        if (!lensatic) {
            Text(
                "HDG $headingText · ${steerText(deviation, angleUnit)}",
                fontFamily = MonoFamily,
                fontSize = 13.sp,
                letterSpacing = 0.8.sp,
                color = if (deviation != null && abs(deviation) <= 3f) p.lume else p.muted,
            )
            Spacer(Modifier.height(6.dp))
        }
        DistanceHero(distance, p, numeralSize = 56.sp, numeralLine = 60.sp, unitSize = 16.sp, display = false)
        Spacer(Modifier.height(12.dp))
        FaceCells(cells, p)
        Spacer(Modifier.height(12.dp))
        Text(
            if (lensatic) "BEZEL IS SET TO THE AZIMUTH · TURN UNTIL THE NORTH ARROW SITS UNDER THE BEZEL LINE"
            else "CARD TURNS WITH YOU · NEEDLE POINTS AT THE TARGET",
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
    content: @Composable BoxScope.() -> Unit = {},
) {
    val density = LocalDensity.current
    val px = with(density) { size.toPx() }
    val lensatic = style == Face.LENSATIC
    val s = px / (if (lensatic) 360f else 330f)   // px per mock pixel
    val lc = lensaticColors(p.night)
    val measurer = rememberTextMeasurer()
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
        Canvas(Modifier.matchParentSize()) {
            val c = center
            val r = px / 2f
            if (lensatic) {
                drawLensatic(c, r, s, lc, p, rotation, bezelLineAt, labels)
            } else {
                drawCleanDial(c, r, s, p, rotation, labels)
            }
            lumeMarkAt?.let { a ->
                val pos = polar(c, r - 22f * s, a)
                rotate(a + 180f, pivot = pos) {
                    val path = Path().apply {
                        moveTo(pos.x, pos.y - 9f * s)
                        lineTo(pos.x + 6f * s, pos.y + 3f * s)
                        lineTo(pos.x - 6f * s, pos.y + 3f * s)
                        close()
                    }
                    drawPath(path, p.lume)
                }
            }
            needleAt?.let { a ->
                rotate(a, pivot = c) {
                    drawLine(p.lume, Offset(c.x, c.y + 6f * s), Offset(c.x, c.y - 118f * s), 4f * s, cap = StrokeCap.Round)
                    val head = Path().apply {
                        moveTo(c.x, c.y - 140f * s)
                        lineTo(c.x - 9f * s, c.y - 116f * s)
                        lineTo(c.x + 9f * s, c.y - 116f * s)
                        close()
                    }
                    drawPath(head, p.lume)
                }
                drawCircle(p.ink, radius = 5f * s, center = c)
            }
            if (lubber) {
                val path = Path().apply {
                    moveTo(c.x, c.y - r + 18f * s)
                    lineTo(c.x - 8f * s, c.y - r + 4f * s)
                    lineTo(c.x + 8f * s, c.y - r + 4f * s)
                    close()
                }
                drawPath(path, p.ink)
            }
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
    rotation: Float, bezelLineAt: Float?, labels: List<DialLabel>,
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
        // luminous north arrow
        val tip = big - 32f * s
        val base = big - 54f * s
        val stem = big - 74f * s
        val arrow = Path().apply {
            moveTo(c.x, c.y - tip)
            lineTo(c.x - 7.5f * s, c.y - base)
            lineTo(c.x + 7.5f * s, c.y - base)
            close()
        }
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
