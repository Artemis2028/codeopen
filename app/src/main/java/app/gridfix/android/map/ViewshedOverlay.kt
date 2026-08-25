package app.gridfix.android.map

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Point
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.Projection
import org.osmdroid.views.overlay.Overlay
import kotlin.math.hypot

/**
 * Draws a computed [Terrain.Viewshed] raster over the map: green where the
 * observer sees the ground, amber where only a standing target would show
 * (partial defilade), red where a 3 m target is hidden. The bitmap is placed
 * with an affine corner fit, so it stays glued under pan, zoom, and rotation.
 */
class ViewshedOverlay : Overlay() {

    var data: Terrain.Viewshed? = null

    private val bmpPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = Color.DKGRAY
        alpha = 160
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.DKGRAY
        alpha = 220
    }
    private val matrix = Matrix()
    private val gp = GeoPoint(0.0, 0.0)
    private val pt = Point()
    private val srcPts = FloatArray(6)
    private val dstPts = FloatArray(6)

    override fun draw(canvas: Canvas, projection: Projection) {
        val d = data ?: return
        val w = d.bitmap.width.toFloat()
        val h = d.bitmap.height.toFloat()

        gp.setCoords(d.latN, d.lonW)
        projection.toPixels(gp, pt)
        dstPts[0] = pt.x.toFloat(); dstPts[1] = pt.y.toFloat()
        gp.setCoords(d.latN, d.lonE)
        projection.toPixels(gp, pt)
        dstPts[2] = pt.x.toFloat(); dstPts[3] = pt.y.toFloat()
        gp.setCoords(d.latS, d.lonW)
        projection.toPixels(gp, pt)
        dstPts[4] = pt.x.toFloat(); dstPts[5] = pt.y.toFloat()

        srcPts[0] = 0f; srcPts[1] = 0f
        srcPts[2] = w; srcPts[3] = 0f
        srcPts[4] = 0f; srcPts[5] = h
        matrix.setPolyToPoly(srcPts, 0, dstPts, 0, 3)
        canvas.drawBitmap(d.bitmap, matrix, bmpPaint)

        // observer dot + radius ring
        gp.setCoords(d.obsLat, d.obsLon)
        projection.toPixels(gp, pt)
        val ox = pt.x.toFloat()
        val oy = pt.y.toFloat()
        gp.setCoords(d.obsLat, d.lonE)
        projection.toPixels(gp, pt)
        val rPx = hypot(pt.x - ox, pt.y - oy)
        canvas.drawCircle(ox, oy, rPx, ringPaint)
        canvas.drawCircle(ox, oy, 6f, dotPaint)
    }
}
