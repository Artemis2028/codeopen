package app.gridfix.android.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.GnssStatus
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Handler
import android.os.Looper
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

    private val locationManager =
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

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
        try {
            val gpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
            if (gpsEnabled) {
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER, 1000L, 0f, listener, looper
                )
            }
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER, 5000L, 0f, listener, looper
                )
            }
            locationManager.registerGnssStatusCallback(gnssCallback, Handler(looper))

            val last = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            _fix.update { it.copy(location = last ?: it.location, gpsEnabled = gpsEnabled) }
        } catch (_: SecurityException) {
            started = false
        } catch (_: IllegalArgumentException) {
            // A provider does not exist on this device; ignore
        }
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
