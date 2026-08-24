package app.gridfix.android.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

/** MIL-STD-2525-style affiliation colors. */
object Affiliations {
    val all = listOf("none", "friendly", "hostile", "neutral", "unknown")

    fun label(key: String): String = when (key) {
        "friendly" -> "Friendly"
        "hostile" -> "Hostile"
        "neutral" -> "Neutral"
        "unknown" -> "Unknown"
        else -> "None"
    }

    fun color(key: String, fallback: Color): Color = when (key) {
        "friendly" -> Color(0xFF5BC8F5)   // crystal blue
        "hostile" -> Color(0xFFFF6B60)    // salmon red
        "neutral" -> Color(0xFF8FE38F)    // bamboo green
        "unknown" -> Color(0xFFF0E060)    // light yellow
        else -> fallback
    }
}

/**
 * Renders a waypoint marker:
 * - NATO unit keys (nato_*) render the bundled MIL-STD-2525B symbol image
 * - tactical task keys (task_*) render drawn task glyphs or letter badges
 * - shape keys render plain drawn shapes
 * - everything else renders a material icon
 * Non-NATO markers get an affiliation frame (friendly rectangle, hostile diamond,
 * neutral square, unknown circle) except tasks, which stand alone per doctrine.
 */
@Composable
fun WaypointMarker(
    symbol: String,
    affiliation: String,
    modifier: Modifier = Modifier,
    size: Dp = 30.dp,
    echelon: String = "",
) {
    if (NatoSymbols.isNato(symbol)) {
        val res = NatoSymbols.resId(symbol)
        if (res != null) {
            Box(modifier.size(size)) {
                Image(
                    painter = painterResource(res),
                    contentDescription = NatoSymbols.label(symbol),
                    modifier = Modifier.fillMaxSize(),
                )
                EchelonMarks(
                    echelon = echelon,
                    color = Affiliations.color(affiliation, MaterialTheme.colorScheme.primary),
                    halo = MaterialTheme.colorScheme.background,
                )
            }
            return
        }
    }

    val color = Affiliations.color(affiliation, MaterialTheme.colorScheme.primary)
    val isShape = WaypointSymbols.isShape(symbol)
    val isTask = WaypointSymbols.isTask(symbol)
    val taskLetter = if (isTask) WaypointSymbols.taskLetter(symbol) else null

    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = this.size.width
            val h = this.size.height
            val frameStroke = Stroke(width = w * 0.07f)
            val glyphStroke = w * 0.08f

            if (!isTask) {
                when (affiliation) {
                    "friendly" -> drawRoundRect(
                        color = color,
                        topLeft = Offset(w * 0.04f, h * 0.20f),
                        size = Size(w * 0.92f, h * 0.60f),
                        cornerRadius = CornerRadius(w * 0.08f),
                        style = frameStroke,
                    )
                    "hostile" -> {
                        val p = Path().apply {
                            moveTo(w / 2f, h * 0.05f)
                            lineTo(w * 0.95f, h / 2f)
                            lineTo(w / 2f, h * 0.95f)
                            lineTo(w * 0.05f, h / 2f)
                            close()
                        }
                        drawPath(p, color, style = frameStroke)
                    }
                    "neutral" -> drawRect(
                        color = color,
                        topLeft = Offset(w * 0.10f, h * 0.10f),
                        size = Size(w * 0.80f, h * 0.80f),
                        style = frameStroke,
                    )
                    "unknown" -> drawCircle(
                        color = color,
                        radius = w * 0.44f,
                        style = frameStroke,
                    )
                }
            }

            if (isShape) {
                val s = w * 0.36f
                when (symbol) {
                    "dot" -> drawCircle(color, radius = s * 0.45f)
                    "square" -> drawRect(
                        color,
                        topLeft = Offset(w / 2f - s * 0.42f, h / 2f - s * 0.42f),
                        size = Size(s * 0.84f, s * 0.84f),
                    )
                    "triangle" -> {
                        val p = Path().apply {
                            moveTo(w / 2f, h / 2f - s * 0.55f)
                            lineTo(w / 2f + s * 0.58f, h / 2f + s * 0.42f)
                            lineTo(w / 2f - s * 0.58f, h / 2f + s * 0.42f)
                            close()
                        }
                        drawPath(p, color)
                    }
                    "diamond" -> {
                        val p = Path().apply {
                            moveTo(w / 2f, h / 2f - s * 0.6f)
                            lineTo(w / 2f + s * 0.6f, h / 2f)
                            lineTo(w / 2f, h / 2f + s * 0.6f)
                            lineTo(w / 2f - s * 0.6f, h / 2f)
                            close()
                        }
                        drawPath(p, color)
                    }
                    "cross" -> rotate(45f) {
                        val half = s * 0.6f
                        val t = s * 0.3f
                        drawLine(color, Offset(w / 2f - half, h / 2f), Offset(w / 2f + half, h / 2f), strokeWidth = t)
                        drawLine(color, Offset(w / 2f, h / 2f - half), Offset(w / 2f, h / 2f + half), strokeWidth = t)
                    }
                }
            }

            if (isTask && taskLetter == null) {
                when (symbol) {
                    "task_block" -> {
                        drawLine(color, Offset(w * 0.08f, h * 0.5f), Offset(w * 0.72f, h * 0.5f), glyphStroke)
                        drawLine(color, Offset(w * 0.72f, h * 0.16f), Offset(w * 0.72f, h * 0.84f), glyphStroke)
                    }
                    "task_ambush" -> {
                        drawLine(color, Offset(w * 0.30f, h * 0.5f), Offset(w * 0.86f, h * 0.5f), glyphStroke)
                        arrowHead(Offset(w * 0.92f, h * 0.5f), 0f, w * 0.16f, color, glyphStroke)
                        drawLine(color, Offset(w * 0.30f, h * 0.5f), Offset(w * 0.08f, h * 0.16f), glyphStroke)
                        drawLine(color, Offset(w * 0.30f, h * 0.5f), Offset(w * 0.04f, h * 0.5f), glyphStroke)
                        drawLine(color, Offset(w * 0.30f, h * 0.5f), Offset(w * 0.08f, h * 0.84f), glyphStroke)
                    }
                    "task_sbf" -> {
                        drawLine(color, Offset(w * 0.5f, h * 0.9f), Offset(w * 0.5f, h * 0.45f), glyphStroke)
                        drawLine(color, Offset(w * 0.5f, h * 0.45f), Offset(w * 0.22f, h * 0.18f), glyphStroke)
                        drawLine(color, Offset(w * 0.5f, h * 0.45f), Offset(w * 0.78f, h * 0.18f), glyphStroke)
                        arrowHead(Offset(w * 0.20f, h * 0.16f), 225f, w * 0.13f, color, glyphStroke)
                        arrowHead(Offset(w * 0.80f, h * 0.16f), 315f, w * 0.13f, color, glyphStroke)
                    }
                    "task_fix" -> {
                        drawLine(color, Offset(w * 0.06f, h * 0.5f), Offset(w * 0.22f, h * 0.5f), glyphStroke)
                        var x = w * 0.22f
                        var up = true
                        while (x < w * 0.66f) {
                            val nx = x + w * 0.11f
                            drawLine(
                                color,
                                Offset(x, if (up) h * 0.5f else h * 0.32f),
                                Offset(nx, if (up) h * 0.32f else h * 0.5f),
                                glyphStroke,
                            )
                            x = nx
                            up = !up
                        }
                        drawLine(color, Offset(x, h * 0.5f), Offset(w * 0.86f, h * 0.5f), glyphStroke)
                        arrowHead(Offset(w * 0.92f, h * 0.5f), 0f, w * 0.15f, color, glyphStroke)
                    }
                    "task_secure" -> {
                        drawCircle(color, radius = w * 0.34f, style = Stroke(glyphStroke))
                        arrowHead(Offset(w * 0.5f, h * 0.16f), 180f, w * 0.16f, color, glyphStroke)
                    }
                    "task_occupy" -> {
                        drawCircle(color, radius = w * 0.32f, style = Stroke(glyphStroke))
                        val cx = w * 0.24f
                        val cy = h * 0.76f
                        val a = w * 0.11f
                        drawLine(color, Offset(cx - a, cy - a), Offset(cx + a, cy + a), glyphStroke)
                        drawLine(color, Offset(cx - a, cy + a), Offset(cx + a, cy - a), glyphStroke)
                    }
                    "task_retain" -> {
                        drawCircle(color, radius = w * 0.28f, style = Stroke(glyphStroke))
                        for (i in 0 until 10) {
                            val ang = Math.toRadians(i * 36.0)
                            val x1 = w / 2f + (w * 0.28f) * cos(ang).toFloat()
                            val y1 = h / 2f + (w * 0.28f) * sin(ang).toFloat()
                            val x2 = w / 2f + (w * 0.40f) * cos(ang).toFloat()
                            val y2 = h / 2f + (w * 0.40f) * sin(ang).toFloat()
                            drawLine(color, Offset(x1, y1), Offset(x2, y2), glyphStroke * 0.75f)
                        }
                    }
                }
            }
        }
        if (!isShape && !isTask && symbol.isNotEmpty()) {
            Icon(
                WaypointSymbols.icon(symbol),
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(size * 0.5f),
            )
        }
        if (taskLetter != null) {
            Text(
                taskLetter,
                color = color,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = (size.value * 0.55f).sp,
            )
        }
        if (!isTask) {
            EchelonMarks(echelon = echelon, color = color, halo = MaterialTheme.colorScheme.background)
        }
    }
}

/**
 * MIL-STD-2525-style echelon marks drawn along the top edge of the marker:
 * team = slashed circle, squad/section/platoon = 1–3 dots,
 * company/battalion/regiment = 1–3 bars, brigade = X.
 */
@Composable
private fun EchelonMarks(echelon: String, color: Color, halo: Color) {
    if (echelon.isEmpty()) return
    Canvas(Modifier.fillMaxSize()) {
        val w = this.size.width
        val cy = w * 0.085f
        val s = w * 0.13f
        val stroke = s * 0.30f

        fun marks(count: Int, dot: Boolean) {
            val gap = if (dot) s * 0.78f else s * 0.62f
            val x0 = w / 2f - gap * (count - 1) / 2f
            for (i in 0 until count) {
                val x = x0 + gap * i
                if (dot) {
                    drawCircle(halo, radius = s * 0.30f + stroke * 0.5f, center = Offset(x, cy))
                    drawCircle(color, radius = s * 0.30f, center = Offset(x, cy))
                } else {
                    drawLine(halo, Offset(x, cy - s * 0.55f), Offset(x, cy + s * 0.55f), stroke * 1.9f)
                    drawLine(color, Offset(x, cy - s * 0.55f), Offset(x, cy + s * 0.55f), stroke)
                }
            }
        }

        when (echelon) {
            "tm" -> {
                drawCircle(halo, radius = s * 0.62f, center = Offset(w / 2f, cy), style = Stroke(stroke * 1.9f))
                drawCircle(color, radius = s * 0.62f, center = Offset(w / 2f, cy), style = Stroke(stroke))
                drawLine(
                    halo,
                    Offset(w / 2f - s * 0.85f, cy + s * 0.85f),
                    Offset(w / 2f + s * 0.85f, cy - s * 0.85f),
                    stroke * 1.9f,
                )
                drawLine(
                    color,
                    Offset(w / 2f - s * 0.85f, cy + s * 0.85f),
                    Offset(w / 2f + s * 0.85f, cy - s * 0.85f),
                    stroke,
                )
            }
            "sqd" -> marks(1, dot = true)
            "sec" -> marks(2, dot = true)
            "plt" -> marks(3, dot = true)
            "co" -> marks(1, dot = false)
            "bn" -> marks(2, dot = false)
            "rgt" -> marks(3, dot = false)
            "bde" -> {
                val r = s * 0.60f
                listOf(
                    Offset(w / 2f - r, cy - r) to Offset(w / 2f + r, cy + r),
                    Offset(w / 2f - r, cy + r) to Offset(w / 2f + r, cy - r),
                ).forEach { (a, b) ->
                    drawLine(halo, a, b, stroke * 1.9f)
                    drawLine(color, a, b, stroke)
                }
            }
        }
    }
}

/** Small open arrowhead at [tip], pointing along [angleDeg] (0° = +x, clockwise). */
private fun DrawScope.arrowHead(tip: Offset, angleDeg: Float, len: Float, color: Color, stroke: Float) {
    val a = Math.toRadians(angleDeg.toDouble())
    val back = 150.0 * Math.PI / 180.0
    val a1 = a + back
    val a2 = a - back
    drawLine(color, tip, Offset(tip.x + len * cos(a1).toFloat(), tip.y + len * sin(a1).toFloat()), stroke)
    drawLine(color, tip, Offset(tip.x + len * cos(a2).toFloat(), tip.y + len * sin(a2).toFloat()), stroke)
}
