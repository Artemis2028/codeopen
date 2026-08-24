package app.gridfix.android.map

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Point
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.Projection
import org.osmdroid.views.overlay.Overlay
import kotlin.math.max

/**
 * Draws recorded movement: the live recording (amber) and one viewed saved
 * track (green). Long tracks are decimated for drawing; the stored log keeps
 * every point.
 */
class TracksOverlay(private val density: Float) : Overlay() {

    var nightMode = false
    var activePoints: List<Pair<Double, Double>> = emptyList()
    var viewedPoints: List<Pair<Double, Double>> = emptyList()

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f * density
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    private val halo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 5.5f * density
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    private val gp = GeoPoint(0.0, 0.0)
    private val pt = Point()
    private val path = Path()

    override fun draw(canvas: Canvas, projection: Projection) {
        if (viewedPoints.isNotEmpty()) {
            drawTrack(
                canvas, projection, viewedPoints,
                if (nightMode) Color.rgb(196, 45, 36) else Color.rgb(70, 160, 90),
            )
        }
        if (activePoints.isNotEmpty()) {
            drawTrack(
                canvas, projection, activePoints,
                if (nightMode) Color.rgb(255, 59, 48) else Color.rgb(224, 150, 40),
            )
        }
    }

    private fun drawTrack(canvas: Canvas, projection: Projection, pts: List<Pair<Double, Double>>, color: Int) {
        val stride = max(1, pts.size / 1500)
        path.reset()
        var first = true
        var i = 0
        while (i < pts.size) {
            val (lat, lon) = pts[i]
            gp.setCoords(lat, lon)
            projection.toPixels(gp, pt)
            if (first) {
                path.moveTo(pt.x.toFloat(), pt.y.toFloat())
                first = false
            } else {
                path.lineTo(pt.x.toFloat(), pt.y.toFloat())
            }
            // always include the final point
            i = if (i + stride >= pts.size && i != pts.size - 1) pts.size - 1 else i + stride
        }
        halo.color = if (nightMode) Color.BLACK else Color.WHITE
        halo.alpha = 110
        canvas.drawPath(path, halo)
        paint.color = color
        paint.alpha = 230
        canvas.drawPath(path, paint)
        // end marker
        if (pts.isNotEmpty()) {
            val (lat, lon) = pts.last()
            gp.setCoords(lat, lon)
            projection.toPixels(gp, pt)
            paint.style = Paint.Style.FILL
            canvas.drawCircle(pt.x.toFloat(), pt.y.toFloat(), 4f * density, paint)
            paint.style = Paint.Style.STROKE
        }
    }
}
