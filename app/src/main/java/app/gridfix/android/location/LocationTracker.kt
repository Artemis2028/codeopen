package app.gridfix.android.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.GnssStatus
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class FixData(
    val location: Location? = null,
    val satellitesUsed: Int = 0,
    val satellitesVisible: Int = 0,
    val gpsEnabled: Boolean = true,
)

/**
 * Thin wrapper over the platform LocationManager.
 * Uses GPS as the primary provider with a network-location fallback,
 * and reports GNSS satellite counts.
 */
class LocationTracker(context: Context) {

    private val appContext = context.applicationContext
    private val locationManager =
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    // Android 14+ can convert the GPS ellipsoid height to height above mean sea
    // level (what the map's contours use). The geoid lookup does disk I/O the
    // first time, so it runs off the main thread and the fix is re-emitted when done.
    private val mslExecutor = Executors.newSingleThreadExecutor { r -> Thread(r, "msl-altitude").apply { isDaemon = true } }

    private fun addMslAltitude(loc: Location) {
        if (Build.VERSION.SDK_INT < 34 || !loc.hasAltitude()) return
        mslExecutor.execute {
            if (MslConverter.add(appContext, loc)) {
                // Same fix, now with MSL fields: a copy so the state flow sees a new value
                _fix.update { cur -> if (cur.location === loc) cur.copy(location = Location(loc)) else cur }
            }
        }
    }

    private val _fix = MutableStateFlow(FixData())
    val fix: StateFlow<FixData> = _fix.asStateFlow()

    private var started = false

    // Full interface implementation (not a SAM lambda): on API 26–29 devices the
    // framework still calls the legacy callbacks, which have no platform defaults there.
    private val listener = object : LocationListener {
        override fun onLocationChanged(loc: Location) {
            _fix.update { current ->
                val old = current.location
                val accept = old == null ||
                    loc.provider == LocationManager.GPS_PROVIDER ||
                    old.provider != LocationManager.GPS_PROVIDER ||
                    loc.elapsedRealtimeNanos - old.elapsedRealtimeNanos > 10_000_000_000L
                if (accept) current.copy(location = loc) else current
            }
            addMslAltitude(loc)
        }

        @Deprecated("Legacy callback, required for API 26-29 devices")
        override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {
        }

        override fun onProviderEnabled(provider: String) {
            if (provider == LocationManager.GPS_PROVIDER) {
                _fix.update { it.copy(gpsEnabled = true) }
            }
        }

        override fun onProviderDisabled(provider: String) {
            if (provider == LocationManager.GPS_PROVIDER) {
                _fix.update { it.copy(gpsEnabled = false) }
            }
        }
    }

    private val gnssCallback = object : GnssStatus.Callback() {
        override fun onSatelliteStatusChanged(status: GnssStatus) {
            var used = 0
            for (i in 0 until status.satelliteCount) {
                if (status.usedInFix(i)) used++
            }
            _fix.update { it.copy(satellitesUsed = used, satellitesVisible = status.satelliteCount) }
        }
    }

    @SuppressLint("MissingPermission")
    fun start() {
        if (started) return
        started = true
        val looper = Looper.getMainLooper()
        // Each provider is requested independently: an "Approximate" (coarse-only)
        // grant makes the GPS request throw, which must not also cost us the
        // network provider and the GNSS status callback.
        var any = false
        val gpsEnabled = runCatching {
            locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        }.getOrDefault(false)
        if (gpsEnabled) {
            runCatching {
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER, 1000L, 0f, listener, looper
                )
                any = true
            }
        }
        runCatching {
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER, 5000L, 0f, listener, looper
                )
                any = true
            }
        }
        runCatching { locationManager.registerGnssStatusCallback(gnssCallback, Handler(looper)) }
        runCatching {
            val last = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            _fix.update { it.copy(location = last ?: it.location, gpsEnabled = gpsEnabled) }
            last?.let { addMslAltitude(it) }
        }
        if (!any) started = false
    }

    fun stop() {
        if (!started) return
        started = false
        try {
            locationManager.removeUpdates(listener)
            locationManager.unregisterGnssStatusCallback(gnssCallback)
        } catch (_: SecurityException) {
        }
    }
}

/** Kept in its own class so devices below Android 14 never load the converter type. */
@androidx.annotation.RequiresApi(34)
private object MslConverter {
    private val converter = android.location.altitude.AltitudeConverter()

    /** True when [loc] now carries an MSL altitude. */
    fun add(context: Context, loc: Location): Boolean {
        if (loc.hasMslAltitude()) return true
        return runCatching { converter.addMslAltitudeToLocation(context, loc) }.isSuccess && loc.hasMslAltitude()
    }
}
