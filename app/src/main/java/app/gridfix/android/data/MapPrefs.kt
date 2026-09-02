package app.gridfix.android.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.mapStore by preferencesDataStore(
    name = "map",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

/**
 * Map screen preferences.
 * baseLayer is "streets", "topo", "sat", or "mbtiles:<filename>".
 */
data class MapPrefsData(
    val baseLayer: String = "topo",
    val gridEnabled: Boolean = true,
    val lastLat: Double = 24.4539,   // sensible first-run default: UAE
    val lastLon: Double = 54.3773,
    val lastZoom: Double = 6.5,
    val hillshadeOverlay: Boolean = false,
    val contourOverlay: Boolean = false,
)

class MapPrefs(private val context: Context) {

    private object Keys {
        val BASE_LAYER = stringPreferencesKey("base_layer")
        val GRID_ENABLED = booleanPreferencesKey("grid_enabled")
        val LAST_LAT = doublePreferencesKey("last_lat")
        val LAST_LON = doublePreferencesKey("last_lon")
        val LAST_ZOOM = doublePreferencesKey("last_zoom")
        val HILLSHADE_OVERLAY = booleanPreferencesKey("hillshade_overlay")
        val CONTOUR_OVERLAY = booleanPreferencesKey("contour_overlay")
    }

    val prefs: Flow<MapPrefsData> = context.mapStore.data.map { p ->
        MapPrefsData(
            baseLayer = p[Keys.BASE_LAYER] ?: "topo",
            gridEnabled = p[Keys.GRID_ENABLED] ?: true,
            lastLat = p[Keys.LAST_LAT] ?: 24.4539,
            lastLon = p[Keys.LAST_LON] ?: 54.3773,
            lastZoom = p[Keys.LAST_ZOOM] ?: 6.5,
            hillshadeOverlay = p[Keys.HILLSHADE_OVERLAY] ?: false,
            contourOverlay = p[Keys.CONTOUR_OVERLAY] ?: false,
        )
    }

    suspend fun setBaseLayer(value: String) {
        context.mapStore.edit { it[Keys.BASE_LAYER] = value }
    }

    suspend fun setGridEnabled(value: Boolean) {
        context.mapStore.edit { it[Keys.GRID_ENABLED] = value }
    }

    suspend fun setHillshadeOverlay(value: Boolean) {
        context.mapStore.edit { it[Keys.HILLSHADE_OVERLAY] = value }
    }

    suspend fun setContourOverlay(value: Boolean) {
        context.mapStore.edit { it[Keys.CONTOUR_OVERLAY] = value }
    }

    suspend fun setCamera(lat: Double, lon: Double, zoom: Double) {
        context.mapStore.edit {
            it[Keys.LAST_LAT] = lat
            it[Keys.LAST_LON] = lon
            it[Keys.LAST_ZOOM] = zoom
        }
    }
}
