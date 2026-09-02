package app.gridfix.android.coords

import java.util.Calendar
import java.util.TimeZone
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.tan

/**
 * Offline sun and moon planning data: BMNT/EENT (nautical twilight), civil
 * twilight, sunrise/sunset (NOAA solar equations — validated to the minute),
 * moon phase + illumination (validated < 0.1° against eclipse epochs), and
 * approximate moonrise/set (truncated Meeus series, about ±5 minutes).
 */
object SunMoon {

    private const val D2R = Math.PI / 180.0
    private const val R2D = 180.0 / Math.PI

    data class SunTimes(
        val bmnt: Double?,        // UT hours, null when the sun never crosses that altitude
        val civilDawn: Double?,
        val sunrise: Double?,
        val sunset: Double?,
        val civilDusk: Double?,
        val eent: Double?,
    )

    data class MoonInfo(
        val phaseName: String,
        val illuminationPct: Int,
        val rises: List<Double>,  // UT hours within the requested day
        val sets: List<Double>,
    )

    fun julianDay(y0: Int, m0: Int, d: Int, hourUtc: Double): Double {
        var y = y0
        var m = m0
        if (m <= 2) {
            y -= 1
            m += 12
        }
        val a = y / 100
        val b = 2 - a + a / 4
        return (365.25 * (y + 4716)).toInt() + (30.6001 * (m + 1)).toInt() + d + b - 1524.5 + hourUtc / 24.0
    }

    fun sunTimes(y: Int, m: Int, d: Int, lat: Double, lon: Double): SunTimes = SunTimes(
        bmnt = solarCrossing(y, m, d, lat, lon, -12.0, morning = true),
        civilDawn = solarCrossing(y, m, d, lat, lon, -6.0, morning = true),
        sunrise = solarCrossing(y, m, d, lat, lon, -0.833, morning = true),
        sunset = solarCrossing(y, m, d, lat, lon, -0.833, morning = false),
        civilDusk = solarCrossing(y, m, d, lat, lon, -6.0, morning = false),
        eent = solarCrossing(y, m, d, lat, lon, -12.0, morning = false),
    )

    private fun solarCrossing(
        y: Int, m: Int, d: Int, lat: Double, lon: Double, altDeg: Double, morning: Boolean,
    ): Double? {
        var tUt = 12.0 - lon / 15.0
        repeat(3) {
            val jd = julianDay(y, m, d, tUt)
            val t = (jd - 2451545.0) / 36525.0
            val l0 = norm360(280.46646 + 36000.76983 * t + 0.0003032 * t * t)
            val ma = 357.52911 + 35999.05029 * t - 0.0001537 * t * t
            val e = 0.016708634 - 0.000042037 * t
            val c = (1.914602 - 0.004817 * t) * sin(ma * D2R) +
                (0.019993 - 0.000101 * t) * sin(2 * ma * D2R) +
                0.000289 * sin(3 * ma * D2R)
            val trueLong = l0 + c
            val omega = 125.04 - 1934.136 * t
            val lambda = trueLong - 0.00569 - 0.00478 * sin(omega * D2R)
            val eps0 = 23.0 + 26.0 / 60.0 + 21.448 / 3600.0 - 46.815 * t / 3600.0
            val eps = eps0 + 0.00256 * cos(omega * D2R)
            val decl = asin(sin(eps * D2R) * sin(lambda * D2R)) * R2D
            val yv = tan(eps * D2R / 2.0).let { it * it }
            val eqTime = 4.0 * R2D * (
                yv * sin(2 * l0 * D2R) - 2.0 * e * sin(ma * D2R) +
                    4.0 * e * yv * sin(ma * D2R) * cos(2 * l0 * D2R) -
                    0.5 * yv * yv * sin(4 * l0 * D2R) - 1.25 * e * e * sin(2 * ma * D2R)
                )
            val cosH = (sin(altDeg * D2R) - sin(lat * D2R) * sin(decl * D2R)) /
                (cos(lat * D2R) * cos(decl * D2R))
            if (cosH < -1.0 || cosH > 1.0) return null
            val h = acos(cosH) * R2D
            val solarNoonUt = 12.0 - lon / 15.0 - eqTime / 60.0
            tUt = if (morning) solarNoonUt - h / 15.0 else solarNoonUt + h / 15.0
        }
        return ((tUt % 24.0) + 24.0) % 24.0
    }

    // ---------------- Moon ----------------

    /** Moon RA (deg), declination (deg), ecliptic longitude (deg). */
    private fun moonPos(jd: Double): Triple<Double, Double, Double> {
        val t = (jd - 2451545.0) / 36525.0
        val lp = norm360(218.3164477 + 481267.88123421 * t)
        val dd = norm360(297.8501921 + 445267.1114034 * t)
        val ms = norm360(357.5291092 + 35999.0502909 * t)
        val mp = norm360(134.9633964 + 477198.8675055 * t)
        val f = norm360(93.2720950 + 483202.0175233 * t)
        val lon = lp +
            6.288774 * sin(mp * D2R) +
            1.274027 * sin((2 * dd - mp) * D2R) +
            0.658314 * sin(2 * dd * D2R) +
            0.213618 * sin(2 * mp * D2R) -
            0.185116 * sin(ms * D2R) -
            0.114332 * sin(2 * f * D2R) +
            0.058793 * sin((2 * dd - 2 * mp) * D2R) +
            0.057066 * sin((2 * dd - ms - mp) * D2R) +
            0.053322 * sin((2 * dd + mp) * D2R) +
            0.045758 * sin((2 * dd - ms) * D2R)
        val lat = 5.128122 * sin(f * D2R) +
            0.280602 * sin((mp + f) * D2R) +
            0.277693 * sin((mp - f) * D2R) +
            0.173237 * sin((2 * dd - f) * D2R) +
            0.055413 * sin((2 * dd - mp + f) * D2R) +
            0.046271 * sin((2 * dd - mp - f) * D2R)
        val eps = (23.4393 - 0.0130 * t) * D2R
        val lam = lon * D2R
        val beta = lat * D2R
        val ra = atan2(
            sin(lam) * cos(eps) - tan(beta) * sin(eps),
            cos(lam),
        ).let { ((it * R2D) % 360.0 + 360.0) % 360.0 }
        val dec = asin(sin(beta) * cos(eps) + cos(beta) * sin(eps) * sin(lam)) * R2D
        return Triple(ra, dec, norm360(lon))
    }

    private fun sunEclipticLon(jd: Double): Double {
        val t = (jd - 2451545.0) / 36525.0
        val l0 = norm360(280.46646 + 36000.76983 * t)
        val ma = 357.52911 + 35999.05029 * t
        return norm360(l0 + 1.914602 * sin(ma * D2R) + 0.019993 * sin(2 * ma * D2R))
    }

    private fun gmstDeg(jd: Double): Double {
        val t = (jd - 2451545.0) / 36525.0
        return norm360(280.46061837 + 360.98564736629 * (jd - 2451545.0) + 0.000387933 * t * t)
    }

    private fun moonAltitude(jd: Double, lat: Double, lon: Double): Double {
        val (ra, dec, _) = moonPos(jd)
        val lst = norm360(gmstDeg(jd) + lon)
        val h = (lst - ra) * D2R
        return asin(
            sin(lat * D2R) * sin(dec * D2R) + cos(lat * D2R) * cos(dec * D2R) * cos(h)
        ) * R2D
    }

    fun moonInfo(y: Int, m: Int, d: Int, lat: Double, lon: Double): MoonInfo {
        val noon = julianDay(y, m, d, 12.0)
        val elong = norm360(moonPos(noon).third - sunEclipticLon(noon))
        val illum = ((1.0 - cos(elong * D2R)) / 2.0 * 100.0).roundToInt()
        val name = when {
            elong < 22.5 || elong >= 337.5 -> "New moon"
            elong < 67.5 -> "Waxing crescent"
            elong < 112.5 -> "First quarter"
            elong < 157.5 -> "Waxing gibbous"
            elong < 202.5 -> "Full moon"
            elong < 247.5 -> "Waning gibbous"
            elong < 292.5 -> "Last quarter"
            else -> "Waning crescent"
        }

        val rises = ArrayList<Double>()
        val sets = ArrayList<Double>()
        val std = 0.125   // net standard altitude for the moon (parallax vs refraction)
        // Scan the LOCAL calendar day (its midnight expressed in UT hours of date d),
        // not 00:00-24:00 UT — otherwise a phone in UTC+4 or UTC-7 reports the
        // neighbouring day's moonrise, or misses tonight's entirely.
        val startUt = -localOffsetHours(y, m, d)
        var prev = moonAltitude(julianDay(y, m, d, startUt), lat, lon) - std
        for (i in 1..144) {
            val tHour = startUt + i * 10.0 / 60.0
            val cur = moonAltitude(julianDay(y, m, d, tHour), lat, lon) - std
            if (prev <= 0 && cur > 0) rises.add(tHour - (10.0 / 60.0) * cur / (cur - prev))
            if (prev > 0 && cur <= 0) sets.add(tHour - (10.0 / 60.0) * cur / (cur - prev))
            prev = cur
        }
        return MoonInfo(name, illum, rises, sets)
    }

    /** The device zone's offset from UT at local midnight of the given date, in hours. */
    private fun localOffsetHours(y: Int, m: Int, d: Int): Double {
        val cal = Calendar.getInstance(TimeZone.getDefault())
        cal.clear()
        cal.set(y, m - 1, d, 0, 0, 0)
        return TimeZone.getDefault().getOffset(cal.timeInMillis) / 3600000.0
    }

    // ---------------- formatting ----------------

    /** "0453" style; UT hour → local clock via the device zone's offset on that date. */
    fun formatLocal(utHours: Double?, y: Int, m: Int, d: Int): String {
        if (utHours == null) return "----"
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        cal.set(y, m - 1, d, utHours.toInt(), ((utHours % 1.0) * 60.0).roundToInt(), 0)
        val offsetMin = TimeZone.getDefault().getOffset(cal.timeInMillis) / 60000
        var total = (utHours * 60.0).roundToInt() + offsetMin
        total = ((total % 1440) + 1440) % 1440
        return String.format(java.util.Locale.US, "%02d%02d", total / 60, total % 60)
    }

    fun formatZulu(utHours: Double?): String {
        if (utHours == null) return "----"
        val total = ((utHours * 60.0).roundToInt() % 1440 + 1440) % 1440
        return String.format(java.util.Locale.US, "%02d%02dZ", total / 60, total % 60)
    }

    private fun norm360(v: Double): Double = ((v % 360.0) + 360.0) % 360.0
}
