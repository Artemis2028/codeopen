package app.gridfix.android.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

private val Context.wpStore by preferencesDataStore(name = "waypoints")

data class Waypoint(
    val id: String,
    val name: String,
    val lat: Double,
    val lon: Double,
    val createdAt: Long,
)

class WaypointRepository(private val context: Context) {

    private val listKey = stringPreferencesKey("list")
    private val selectedKey = stringPreferencesKey("selected")

    val waypoints: Flow<List<Waypoint>> = context.wpStore.data.map { p ->
        decode(p[listKey] ?: "[]")
    }

    val selectedId: Flow<String?> = context.wpStore.data.map { p -> p[selectedKey] }

    suspend fun add(name: String, lat: Double, lon: Double, nowMillis: Long) {
        context.wpStore.edit { p ->
            val current = decode(p[listKey] ?: "[]")
            val wp = Waypoint(
                id = UUID.randomUUID().toString(),
                name = name,
                lat = lat,
                lon = lon,
                createdAt = nowMillis,
            )
            p[listKey] = encode(current + wp)
            // Auto-select the new waypoint if nothing is selected yet
            if (p[selectedKey] == null) {
                p[selectedKey] = wp.id
            }
        }
    }

    suspend fun delete(id: String) {
        context.wpStore.edit { p ->
            val current = decode(p[listKey] ?: "[]")
            p[listKey] = encode(current.filterNot { it.id == id })
            if (p[selectedKey] == id) {
                p.remove(selectedKey)
            }
        }
    }

    suspend fun select(id: String) {
        context.wpStore.edit { p -> p[selectedKey] = id }
    }

    private fun decode(json: String): List<Waypoint> = runCatching {
        val arr = JSONArray(json)
        buildList {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                add(
                    Waypoint(
                        id = o.getString("id"),
                        name = o.getString("name"),
                        lat = o.getDouble("lat"),
                        lon = o.getDouble("lon"),
                        createdAt = o.optLong("createdAt"),
                    )
                )
            }
        }
    }.getOrDefault(emptyList())

    private fun encode(list: List<Waypoint>): String {
        val arr = JSONArray()
        for (w in list) {
            arr.put(
                JSONObject()
                    .put("id", w.id)
                    .put("name", w.name)
                    .put("lat", w.lat)
                    .put("lon", w.lon)
                    .put("createdAt", w.createdAt)
            )
        }
        return arr.toString()
    }
}
