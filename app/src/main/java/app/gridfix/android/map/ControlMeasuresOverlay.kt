package app.gridfix.android.map

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Point
import android.graphics.Typeface
import app.gridfix.android.data.GeoVertex
import app.gridfix.android.data.GraphicTypes
import app.gridfix.android.data.TacGraphic
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.Projection
import org.osmdroid.views.overlay.Overlay
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Draws tactical control-measure graphics: phase lines, boundaries, axes of advance,
 * directions of attack, objectives, assembly areas, and routes — in doctrine-style
 * presentation (MIL-STD-2525 / APP-6 line graphics, simplified for a phone screen).
 *
 * The overlay renders whatever is in [graphics] (already filtered to visible folders
 * by the caller) plus an in-progress [draftPoints] polyline while draw mode is active.
 * Colors follow the waypoint affiliation palette; night mode forces the red-on-black
 * scheme like the rest of the app.
 */
class ControlMeasuresOverlay(private val density: Float) : Overlay() {

    var graphics: List<TacGraphic> = emptyList()
    var selectedId: String? = null
    var nightMode = false
    var lightLines = false

    // In-progress drawing (draw mode)
    var draftActive = false
    var draftType: String = "phase_line"
    var draftAffiliation: String = "none"
    var draftPoints: List<GeoVertex> = emptyList()

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.6f * density
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6.5f * density
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val textFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    }
    private val textHalo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        style = Paint.Style.STROKE
        strokeWidth = 3f * density
    }
    private val dash = DashPathEffect(floatArrayOf(9f * density, 6f * density), 0f)

    private val gp = GeoPoint(0.0, 0.0)
    private val pt = Point()
    private val path = Path()
    private val xs = FloatArray(MAX_PTS)
    private val ys = FloatArray(MAX_PTS)

    override fun draw(canvas: Canvas, projection: Projection) {
        val haloColor = if (nightMode || lightLines) Color.BLACK else Color.WHITE
        for (g in graphics) {
            val n = project(projection, g.points)
            if (n == 0) continue
            drawGraphic(canvas, g.type, colorFor(g.affiliation), haloColor, n, g.name, g.id == selectedId, dashed = false)
        }
        if (draftActive && draftPoints.isNotEmpty()) {
            val n = project(projection, draftPoints)
            drawGraphic(canvas, draftType, colorFor(draftAffiliation), haloColor, n, "", selected = false, dashed = true)
            // vertex handles so each placed point is visible while drawing
            handlePaint.color = haloColor
            for (i in 0 until n) {
                canvas.drawCircle(xs[i], ys[i], 4.5f * density, handlePaint)
            }
            handlePaint.color = colorFor(draftAffiliation)
            for (i in 0 until n) {
                canvas.drawCircle(xs[i], ys[i], 3f * density, handlePaint)
            }
        }
    }

    /** Screen-space distance from (x,y) to the nearest segment of [g], in pixels. */
    fun distanceToGraphic(projection: Projection, g: TacGraphic, x: Float, y: Float): Float {
        val n = project(projection, g.points)
        if (n == 0) return Float.MAX_VALUE
        if (n == 1) return hypot(x - xs[0], y - ys[0])
        var best = Float.MAX_VALUE
        val closed = GraphicTypes.isArea(g.type) && n >= 3
        val last = if (closed) n else n - 1
        for (i in 0 until last) {
            val j = (i + 1) % n
            best = min(best, segmentDistance(x, y, xs[i], ys[i], xs[j], ys[j]))
        }
        return best
    }

    private fun colorFor(affiliation: String): Int {
        if (nightMode) return Color.rgb(255, 59, 48)
        return when (affiliation) {
            "friendly" -> Color.rgb(45, 120, 200)
            "hostile" -> Color.rgb(210, 50, 40)
            "neutral" -> Color.rgb(60, 150, 60)
            "unknown" -> Color.rgb(200, 170, 40)
            else -> if (lightLines) Color.WHITE else Color.rgb(20, 22, 26)
        }
    }

    private fun project(projection: Projection, points: List<GeoVertex>, ): Int {
        val n = min(points.size, MAX_PTS)
        for (i in 0 until n) {
            gp.setCoords(points[i].lat, points[i].lon)
            projection.toPixels(gp, pt)
            xs[i] = pt.x.toFloat()
            ys[i] = pt.y.toFloat()
        }
        return n
    }

    private fun drawGraphic(
        canvas: Canvas,
        type: String,
        color: Int,
        haloColor: Int,
        n: Int,
        name: String,
        selected: Boolean,
        dashed: Boolean,
    ) {
        linePaint.color = color
        linePaint.alpha = 235
        linePaint.pathEffect = if (dashed) dash else null
        glowPaint.color = haloColor
        glowPaint.alpha = 120

        when (type) {
            "axis" -> if (n >= 2) {
                buildAxisPath(n)
                if (selected) canvas.drawPath(path, glowPaint)
                canvas.drawPath(path, linePaint)
                label(canvas, name, xs[0], ys[0], color, haloColor, above = true)
            }
            "doa" -> if (n >= 2) {
                buildPolyline(n, closed = false)
                if (selected) canvas.drawPath(path, glowPaint)
                canvas.drawPath(path, linePaint)
                drawSolidArrowHead(canvas, n, color)
                label(canvas, name, xs[0], ys[0], color, haloColor, above = true)
            }
            "objective", "aa" -> if (n >= 2) {
                buildPolyline(n, closed = n >= 3)
                fillPaint.color = color
                fillPaint.alpha = 26
                if (n >= 3) canvas.drawPath(path, fillPaint)
                if (selected) canvas.drawPath(path, glowPaint)
                canvas.drawPath(path, linePaint)
                var cx = 0f
                var cy = 0f
                for (i in 0 until n) {
                    cx += xs[i]; cy += ys[i]
                }
                label(
                    canvas,
                    GraphicTypes.labelPrefix(type) + name.uppercase(Locale.US),
                    cx / n,
                    cy / n,
                    color,
                    haloColor,
                    above = false,
                    centered = true,
                )
            }
            "route" -> if (n >= 2) {
                buildPolyline(n, closed = false)
                if (selected) canvas.drawPath(path, glowPaint)
                canvas.drawPath(path, linePaint)
                handlePaint.color = color
                for (i in 0 until n) {
                    canvas.drawCircle(xs[i], ys[i], 3f * density, handlePaint)
                }
                val mid = n / 2
                label(canvas, name, xs[mid], ys[mid], color, haloColor, above = true)
            }
            else -> if (n >= 2) {          // phase_line, boundary
                buildPolyline(n, closed = false)
                if (selected) canvas.drawPath(path, glowPaint)
                canvas.drawPath(path, linePaint)
                val text = GraphicTypes.labelPrefix(type) + name.uppercase(Locale.US)
                if (type == "phase_line") {
                    label(canvas, text, xs[0], ys[0], color, haloColor, above = true)
                    label(canvas, text, xs[n - 1], ys[n - 1], color, haloColor, above = true)
                } else {
                    val mid = n / 2
                    label(canvas, text, xs[mid], ys[mid], color, haloColor, above = true)
                }
            }
        }
    }

    private fun buildPolyline(n: Int, closed: Boolean) {
        path.reset()
        path.moveTo(xs[0], ys[0])
        for (i in 1 until n) path.lineTo(xs[i], ys[i])
        if (closed) path.close()
    }

    /**
     * Doctrine-style axis-of-advance: an outlined broad arrow along the centerline,
     * open at the rear. Shaft edges are the centerline offset by ± half width with
     * miter directions at interior vertices; the head is twice the shaft width.
     */
    private fun buildAxisPath(n: Int) {
        var total = 0f
        for (i in 0 until n - 1) total += hypot(xs[i + 1] - xs[i], ys[i + 1] - ys[i])
        val w = (total * 0.13f).coerceIn(14f * density, 64f * density)
        val half = w / 2f
        val headLen = min(w * 1.25f, total * 0.45f)

        // Tip is the last vertex; neck sits headLen back along the centerline.
        var remaining = headLen
        var neckSeg = n - 2
        var neckX = xs[n - 2]
        var neckY = ys[n - 2]
        for (i in n - 2 downTo 0) {
            val segLen = hypot(xs[i + 1] - xs[i], ys[i + 1] - ys[i])
            if (segLen >= remaining || i == 0) {
                val t = if (segLen == 0f) 0f else (1f - remaining / segLen).coerceIn(0f, 1f)
                neckX = xs[i] + (xs[i + 1] - xs[i]) * t
                neckY = ys[i] + (ys[i + 1] - ys[i]) * t
                neckSeg = i
                break
            }
            remaining -= segLen
        }

        // Shaft vertices: rear .. neck
        val sx = FloatArray(neckSeg + 2)
        val sy = FloatArray(neckSeg + 2)
        for (i in 0..neckSeg) {
            sx[i] = xs[i]; sy[i] = ys[i]
        }
        sx[neckSeg + 1] = neckX
        sy[neckSeg + 1] = neckY
        val m = neckSeg + 2

        val lx = FloatArray(m)
        val ly = FloatArray(m)
        val rx = FloatArray(m)
        val ry = FloatArray(m)
        for (i in 0 until m) {
            val (nxv, nyv) = miterNormal(sx, sy, m, i)
            lx[i] = sx[i] + nxv * half
            ly[i] = sy[i] + nyv * half
            rx[i] = sx[i] - nxv * half
            ry[i] = sy[i] - nyv * half
        }

        val ang = atan2((ys[n - 1] - neckY).toDouble(), (xs[n - 1] - neckX).toDouble())
        val nx = -sin(ang).toFloat()
        val ny = cos(ang).toFloat()

        path.reset()
        path.moveTo(lx[0], ly[0])
        for (i in 1 until m) path.lineTo(lx[i], ly[i])
        path.lineTo(neckX + nx * w, neckY + ny * w)          // head left barb
        path.lineTo(xs[n - 1], ys[n - 1])                    // tip
        path.lineTo(neckX - nx * w, neckY - ny * w)          // head right barb
        for (i in m - 1 downTo 0) path.lineTo(rx[i], ry[i])  // back along right edge; rear stays open
    }

    /** Unit normal at vertex [i] of the polyline, averaging adjacent segment normals. */
    private fun miterNormal(px: FloatArray, py: FloatArray, m: Int, i: Int): Pair<Float, Float> {
        var dx = 0f
        var dy = 0f
        if (i > 0) {
            dx += px[i] - px[i - 1]
            dy += py[i] - py[i - 1]
        }
        if (i < m - 1) {
            dx += px[i + 1] - px[i]
            dy += py[i + 1] - py[i]
        }
        val len = hypot(dx, dy)
        if (len == 0f) return 0f to 0f
        return (-dy / len) to (dx / len)
    }

    private fun drawSolidArrowHead(canvas: Canvas, n: Int, color: Int) {
        val ang = atan2((ys[n - 1] - ys[n - 2]).toDouble(), (xs[n - 1] - xs[n - 2]).toDouble())
        val len = 11f * density
        val spread = Math.toRadians(150.0)
        path.reset()
        path.moveTo(xs[n - 1], ys[n - 1])
        path.lineTo(
            xs[n - 1] + len * cos(ang + spread).toFloat(),
            ys[n - 1] + len * sin(ang + spread).toFloat(),
        )
        path.lineTo(
            xs[n - 1] + len * cos(ang - spread).toFloat(),
            ys[n - 1] + len * sin(ang - spread).toFloat(),
        )
        path.close()
        fillPaint.color = color
        fillPaint.alpha = 235
        canvas.drawPath(path, fillPaint)
    }

    private fun label(
        canvas: Canvas,
        text: String,
        x: Float,
        y: Float,
        color: Int,
        haloColor: Int,
        above: Boolean,
        centered: Boolean = false,
    ) {
        if (text.isBlank()) return
        val size = 11.5f * density
        textFill.textSize = size
        textHalo.textSize = size
        textFill.color = color
        textFill.alpha = 245
        textHalo.color = haloColor
        textHalo.alpha = 200
        val tw = textFill.measureText(text)
        val tx = if (centered) x - tw / 2f else x + 6f * density
        val ty = if (above) y - 7f * density else y + size / 3f
        canvas.drawText(text, tx, ty, textHalo)
        canvas.drawText(text, tx, ty, textFill)
    }

    private fun segmentDistance(px: Float, py: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
        val dx = bx - ax
        val dy = by - ay
        val len2 = dx * dx + dy * dy
        val t = if (len2 == 0f) 0f else ((px - ax) * dx + (py - ay) * dy) / len2
        val tc = max(0f, min(1f, t))
        return hypot(px - (ax + tc * dx), py - (ay + tc * dy))
    }

    companion object {
        const val MAX_PTS = 64
    }
}
