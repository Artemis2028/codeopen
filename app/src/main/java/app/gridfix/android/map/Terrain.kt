package app.gridfix.android.map

import android.content.Context
import app.gridfix.android.data.GeoVertex
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Terrain analysis on the cached elevation data: line-of-sight between two
 * points and elevation profiles along routes. Uses the same Terrarium tiles
 * as the crosshair readout (~10-30 m source data), so anything works offline
 * once the area's elevation has been viewed or downloaded.
 *
 * Line of sight applies the standard earth-curvature + atmospheric-refraction
 * correction (effective earth radius x 4/3): terrain between the endpoints is
 * depressed by d1*d2 / (2 * Re), which is what makes ~1.7 m eyes see ~4.9 km
 * to a sea-level horizon.
 */
object Terrain {

    private const val EARTH_R = 6371008.8
    private const val EFFECTIVE_R = EARTH_R * 4.0 / 3.0

    data class Profile(
        val distancesM: FloatArray,   // cumulative along-path distance per sample
        val elevations: FloatArray,   // metres MSL; Float.NaN where no data
        val legEndIndex: IntArray,    // sample index where each leg ends (route profiles)
        val missing: Int,             // samples with no elevation data
    ) {
        val totalM: Float get() = if (distancesM.isEmpty()) 0f else distancesM.last()

        /** Total climb and descent, ignoring jitter under [threshold] metres. */
        fun gainLoss(threshold: Float = 1.5f): Pair<Float, Float> {
            var gain = 0f
            var loss = 0f
            var anchor = Float.NaN
            for (e in elevations) {
                if (e.isNaN()) continue
                if (anchor.isNaN()) {
                    anchor = e
                    continue
                }
                val d = e - anchor
                if (d >= threshold) {
                    gain += d
                    anchor = e
                } else if (d <= -threshold) {
                    loss -= d
                    anchor = e
                }
            }
            return gain to loss
        }
    }

    private fun haversineM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val h = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2) * sin(dLon / 2)
        return 2 * EARTH_R * atan2(sqrt(h), sqrt(1 - h))
    }

    /** Sample elevations along a multi-leg path, ~[stepM] apart, capped at [maxSamples]. */
    suspend fun profile(
        context: Context,
        points: List<GeoVertex>,
        stepM: Double = 25.0,
        maxSamples: Int = 600,
    ): Profile? {
        if (points.size < 2) return null
        val legLens = DoubleArray(points.size - 1)
        var total = 0.0
        for (i in 0 until points.size - 1) {
            legLens[i] = haversineM(points[i].lat, points[i].lon, points[i + 1].lat, points[i + 1].lon)
            total += legLens[i]
        }
        if (total <= 0.0) return null
        val step = max(stepM, total / maxSamples)

        val dists = ArrayList<Float>()
        val elevs = ArrayList<Float>()
        val legEnds = ArrayList<Int>()
        var missing = 0
        var walked = 0.0

        suspend fun sampleAt(lat: Double, lon: Double, dist: Double) {
            dists.add(dist.toFloat())
            val e = Elevation.elevationAt(context, lat, lon)
            if (e == null) {
                missing++
                elevs.add(Float.NaN)
            } else {
                elevs.add(e.toFloat())
            }
        }

        sampleAt(points[0].lat, points[0].lon, 0.0)
        for (i in 0 until points.size - 1) {
            val len = legLens[i]
            if (len > 0.0) {
                var d = step
                while (d < len) {
                    val t = d / len
                    sampleAt(
                        points[i].lat + (points[i + 1].lat - points[i].lat) * t,
                        points[i].lon + (points[i + 1].lon - points[i].lon) * t,
                        walked + d,
                    )
                    d += step
                }
            }
            walked += len
            sampleAt(points[i + 1].lat, points[i + 1].lon, walked)
            legEnds.add(dists.size - 1)
        }
        return Profile(
            distancesM = dists.toFloatArray(),
            elevations = elevs.toFloatArray(),
            legEndIndex = legEnds.toIntArray(),
            missing = missing,
        )
    }

    data class LosResult(
        val visible: Boolean,
        val profile: Profile,
        val observerElev: Float,      // ground MSL at observer
        val targetElev: Float,        // ground MSL at target
        val observerHeight: Float,    // metres above ground
        val targetHeight: Float,
        val blockIndex: Int,          // first blocking sample (-1 when visible)
        val blockDistM: Float,
        val blockLat: Double,
        val blockLon: Double,
        val clearObserverHeight: Float, // min observer height (m AGL) that would see the target
        /** Sight-line height (curvature-corrected frame) per sample, for drawing. */
        val sightLine: FloatArray,
        /** Terrain in the same curvature-corrected frame, for drawing. */
        val effectiveTerrain: FloatArray,
    )

    /**
     * Line of sight observer -> target. Heights are metres above ground.
     * Returns null when elevation data is missing at either endpoint.
     */
    suspend fun lineOfSight(
        context: Context,
        obsLat: Double, obsLon: Double, observerHeight: Float,
        tgtLat: Double, tgtLon: Double, targetHeight: Float,
    ): LosResult? {
        val prof = profile(
            context,
            listOf(GeoVertex(obsLat, obsLon), GeoVertex(tgtLat, tgtLon)),
            stepM = 20.0,
            maxSamples = 500,
        ) ?: return null
        val n = prof.elevations.size
        if (n < 2) return null
        val obsGround = prof.elevations.first()
        val tgtGround = prof.elevations.last()
        if (obsGround.isNaN() || tgtGround.isNaN()) return null
        val totalD = prof.totalM.toDouble()

        // Curvature-corrected frame: depress terrain by d1*d2 / (2 Re)
        val eff = FloatArray(n)
        for (i in 0 until n) {
            val d1 = prof.distancesM[i].toDouble()
            val bulge = d1 * (totalD - d1) / (2.0 * EFFECTIVE_R)
            eff[i] = if (prof.elevations[i].isNaN()) Float.NaN
            else (prof.elevations[i] - bulge).toFloat()
        }

        val a = obsGround + observerHeight       // endpoints carry no bulge
        val b = tgtGround + targetHeight
        val sight = FloatArray(n)
        val clearance = 0.5f
        var blockIdx = -1
        var required = 0f
        for (i in 0 until n) {
            val t = if (totalD == 0.0) 0f else (prof.distancesM[i] / prof.totalM)
            sight[i] = a + (b - a) * t
            if (i in 1 until n - 1 && !eff[i].isNaN()) {
                if (eff[i] + clearance > sight[i] && blockIdx == -1) {
                    blockIdx = i
                }
                // observer height that would clear this sample (target end fixed)
                if (t < 1f) {
                    val need = ((eff[i] + clearance - b * t) / (1f - t)) - obsGround
                    if (need > required) required = need
                }
            }
        }

        val blockT = if (blockIdx >= 0) prof.distancesM[blockIdx] / prof.totalM else 0f
        return LosResult(
            visible = blockIdx == -1,
            profile = prof,
            observerElev = obsGround,
            targetElev = tgtGround,
            observerHeight = observerHeight,
            targetHeight = targetHeight,
            blockIndex = blockIdx,
            blockDistM = if (blockIdx >= 0) prof.distancesM[blockIdx] else 0f,
            blockLat = obsLat + (tgtLat - obsLat) * blockT,
            blockLon = obsLon + (tgtLon - obsLon) * blockT,
            clearObserverHeight = max(required, 0f),
            sightLine = sight,
            effectiveTerrain = eff,
        )
    }
}
