package app.gridfix.android.coords

import mil.nga.grid.features.Point
import mil.nga.mgrs.MGRS
import mil.nga.mgrs.grid.GridType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

object Coordinates {

    data class MgrsParts(
        val gzd: String,
        val square: String,
        val easting: String,
        val northing: String,
        val full: String,
    )

    /** Lat/lon (WGS84) to MGRS at the requested precision (4/6/8/10 digits). */
    fun mgrs(lat: Double, lon: Double, digits: Int): MgrsParts? = runCatching {
        val mgrs = MGRS.from(Point.point(lon, lat))
        val gridType = when (digits) {
            4 -> GridType.KILOMETER
            6 -> GridType.HUNDRED_METER
            8 -> GridType.TEN_METER
            else -> GridType.METER
        }
        val full = mgrs.coordinate(gridType).uppercase(Locale.US)
        val match = Regex("^(\\d{1,2}[A-Z])([A-Z]{2})(\\d*)$").find(full)
        if (match != null) {
            val (gzd, square, num) = match.destructured
            val half = num.length / 2
            MgrsParts(gzd, square, num.substring(0, half), num.substring(half), full)
        } else {
            // Polar regions (no zone number) or unexpected shape: show as-is
            MgrsParts(full, "", "", "", full)
        }
    }.getOrNull()

    data class UtmCoord(val zone: Int, val hemisphere: Char, val easting: Long, val northing: Long)

    /** Lat/lon (WGS84) to standard UTM. Valid for lat -80..84. */
    fun utm(lat: Double, lon: Double): UtmCoord? {
        if (lat < -80.0 || lat > 84.0) return null

        val zone = utmZone(lat, lon)

        val a = 6378137.0
        val f = 1.0 / 298.257223563
        val k0 = 0.9996
        val e2 = f * (2.0 - f)
        val ep2 = e2 / (1.0 - e2)

        val latRad = Math.toRadians(lat)
        val lonOrigin = Math.toRadians(((zone - 1) * 6 - 180 + 3).toDouble())
        val lonRad = Math.toRadians(lon)

        val n = a / sqrt(1.0 - e2 * sin(latRad).pow(2))
        val t = tan(latRad).pow(2)
        val c = ep2 * cos(latRad).pow(2)
        val bigA = cos(latRad) * (lonRad - lonOrigin)

        val m = a * (
            (1.0 - e2 / 4.0 - 3.0 * e2 * e2 / 64.0 - 5.0 * e2 * e2 * e2 / 256.0) * latRad -
                (3.0 * e2 / 8.0 + 3.0 * e2 * e2 / 32.0 + 45.0 * e2 * e2 * e2 / 1024.0) * sin(2.0 * latRad) +
                (15.0 * e2 * e2 / 256.0 + 45.0 * e2 * e2 * e2 / 1024.0) * sin(4.0 * latRad) -
                (35.0 * e2 * e2 * e2 / 3072.0) * sin(6.0 * latRad)
            )

        val easting = k0 * n * (
            bigA + (1.0 - t + c) * bigA.pow(3) / 6.0 +
                (5.0 - 18.0 * t + t * t + 72.0 * c - 58.0 * ep2) * bigA.pow(5) / 120.0
            ) + 500000.0

        var northing = k0 * (
            m + n * tan(latRad) * (
                bigA.pow(2) / 2.0 +
                    (5.0 - t + 9.0 * c + 4.0 * c * c) * bigA.pow(4) / 24.0 +
                    (61.0 - 58.0 * t + t * t + 600.0 * c - 330.0 * ep2) * bigA.pow(6) / 720.0
                )
            )
        if (lat < 0) northing += 10000000.0

        return UtmCoord(
            zone = zone,
            hemisphere = if (lat >= 0) 'N' else 'S',
            easting = easting.roundToLong(),
            northing = northing.roundToLong(),
        )
    }

    fun formatUtm(u: UtmCoord?): String =
        if (u == null) "—" else "${u.zone}${u.hemisphere} ${u.easting}E ${u.northing}N"

    /** Lat/lon in the chosen format: 0 = DD, 1 = DDM, 2 = DMS. */
    fun formatLatLon(lat: Double, lon: Double, format: Int): String {
        fun one(value: Double, positive: Char, negative: Char): String {
            val hemi = if (value >= 0) positive else negative
            val v = abs(value)
            return when (format) {
                0 -> String.format(Locale.US, "%.5f° %c", v, hemi)
                2 -> {
                    val d = v.toInt()
                    val mFull = (v - d) * 60.0
                    val mm = mFull.toInt()
                    val s = (mFull - mm) * 60.0
                    String.format(Locale.US, "%d° %02d' %04.1f\" %c", d, mm, s, hemi)
                }
                else -> {
                    val d = v.toInt()
                    val mFull = (v - d) * 60.0
                    String.format(Locale.US, "%d° %06.3f' %c", d, mFull, hemi)
                }
            }
        }
        return one(lat, 'N', 'S') + "   " + one(lon, 'E', 'W')
    }

    /** Military date-time group in Zulu, e.g. 241435Z AUG 26 */
    fun dtg(timeMillis: Long): String {
        val sdf = SimpleDateFormat("ddHHmm'Z' MMM yy", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date(timeMillis)).uppercase(Locale.US)
    }

    fun formatAltitude(meters: Double, units: Int): String = when (units) {
        1 -> "${(meters * 3.28084).roundToLong()} ft"
        else -> "${meters.roundToLong()} m"
    }

    fun formatAccuracy(meters: Float, units: Int): String = when (units) {
        1 -> "±${(meters * 3.28084).roundToLong()} ft"
        else -> "±${meters.roundToLong()} m"
    }

    fun formatSpeed(metersPerSecond: Float, units: Int): String = when (units) {
        1 -> String.format(Locale.US, "%.1f mph", metersPerSecond * 2.23694)
        2 -> String.format(Locale.US, "%.1f kn", metersPerSecond * 1.94384)
        else -> String.format(Locale.US, "%.1f km/h", metersPerSecond * 3.6)
    }

    private fun utmZone(lat: Double, lon: Double): Int {
        var zone = (floor((lon + 180.0) / 6.0) + 1.0).toInt().coerceIn(1, 60)
        // Norway exception
        if (lat in 56.0..64.0 && lon in 3.0..12.0) zone = 32
        // Svalbard exceptions
        if (lat in 72.0..84.0) {
            zone = when {
                lon in 0.0..9.0 -> 31
                lon in 9.0..21.0 -> 33
                lon in 21.0..33.0 -> 35
                lon in 33.0..42.0 -> 37
                else -> zone
            }
        }
        return zone
    }

    /** Grid convergence angle (degrees): grid north minus true north for this UTM zone. */
    fun gridConvergence(lat: Double, lon: Double): Double {
        val zone = utmZone(lat, lon)
        val lonOrigin = ((zone - 1) * 6 - 180 + 3).toDouble()
        return Math.toDegrees(
            atan(tan(Math.toRadians(lon - lonOrigin)) * sin(Math.toRadians(lat)))
        )
    }

    data class NavInfo(val distanceMeters: Float, val bearingTrue: Float)

    /** Geodesic distance and initial true bearing between two points. */
    fun navInfo(fromLat: Double, fromLon: Double, toLat: Double, toLon: Double): NavInfo {
        val results = FloatArray(2)
        android.location.Location.distanceBetween(fromLat, fromLon, toLat, toLon, results)
        return NavInfo(results[0], (results[1] + 360f) % 360f)
    }

    fun formatDistance(meters: Float, units: Int): String = when (units) {
        1 -> {
            val feet = meters * 3.28084f
            if (feet < 1000f) String.format(Locale.US, "%.0f ft", feet)
            else String.format(Locale.US, "%.2f mi", meters / 1609.344f)
        }
        2 -> {
            if (meters < 1852f) String.format(Locale.US, "%.0f m", meters)
            else String.format(Locale.US, "%.2f NM", meters / 1852f)
        }
        else -> {
            if (meters < 1000f) String.format(Locale.US, "%.0f m", meters)
            else if (meters < 10000f) String.format(Locale.US, "%.2f km", meters / 1000f)
            else String.format(Locale.US, "%.1f km", meters / 1000f)
        }
    }

    /** Format an angle in degrees (0..360) as degrees or NATO mils per the angle-unit setting. */
    fun formatAngle(degrees: Float, angleUnit: Int): String = when (angleUnit) {
        1 -> String.format(Locale.US, "%.0f mils", (degrees * 6400f / 360f + 6400f) % 6400f)
        else -> String.format(Locale.US, "%03.0f°", (degrees + 360f) % 360f)
    }

    /** Parse an MGRS string (spaces allowed, case-insensitive) to lat/lon. Null if invalid. */
    fun parseMgrs(text: String): Pair<Double, Double>? = runCatching {
        val cleaned = text.trim().uppercase(Locale.US).replace(" ", "")
        if (cleaned.isEmpty()) return null
        val point = MGRS.parse(cleaned).toPoint()
        point.latitude to point.longitude
    }.getOrNull()
}
