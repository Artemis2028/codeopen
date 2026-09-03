package app.gridfix.android.location

import android.hardware.GeomagneticField
import android.location.Location
import android.os.Build
import app.gridfix.android.data.AppSettings
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Magnetic declination (east positive, degrees). The phone's World Magnetic
 * Model is the default; an operator who has been given a G-M angle from the
 * map sheet or the order can pin it in Settings, and every screen follows.
 */
object Declination {
    fun model(lat: Double, lon: Double, altitudeM: Double = 0.0, time: Long = System.currentTimeMillis()): Float =
        GeomagneticField(lat.toFloat(), lon.toFloat(), altitudeM.toFloat(), time).declination

    fun at(settings: AppSettings, lat: Double, lon: Double, altitudeM: Double = 0.0, time: Long = System.currentTimeMillis()): Float =
        settings.declinationOverride ?: model(lat, lon, altitudeM, time)

    fun at(settings: AppSettings, loc: Location?): Float {
        settings.declinationOverride?.let { return it }
        if (loc == null) return 0f
        return model(loc.latitude, loc.longitude, if (loc.hasAltitude()) loc.altitude else 0.0, loc.time)
    }

    /** "3.2° E" / "0.5° W"; in mils "57 mils E". */
    fun format(declination: Float, angleUnit: Int): String {
        val ew = if (declination >= 0f) "E" else "W"
        return if (angleUnit == 1) "${(abs(declination) * 6400f / 360f).roundToInt()} mils $ew"
        else String.format(Locale.US, "%.1f° %s", abs(declination), ew)
    }
}

/** Height above mean sea level when the phone can convert it (Android 14+), else the GPS ellipsoid height. */
fun Location.bestAltitude(): Double? = when {
    Build.VERSION.SDK_INT >= 34 && hasMslAltitude() -> mslAltitudeMeters
    hasAltitude() -> altitude
    else -> null
}

fun Location.altitudeIsMsl(): Boolean = Build.VERSION.SDK_INT >= 34 && hasMslAltitude()
