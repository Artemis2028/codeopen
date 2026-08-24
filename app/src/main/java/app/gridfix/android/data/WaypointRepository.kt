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

const val DEFAULT_FOLDER = "Waypoints"
const val DEFAULT_SYMBOL = "flag"

data class Waypoint(
    val id: String,
    val name: String,
    val lat: Double,
    val lon: Double,
    val createdAt: Long,
    val folder: String = DEFAULT_FOLDER,
    val symbol: String = DEFAULT_SYMBOL,
)

class WaypointRepository(private val context: Context) {

    private val listKey = stringPreferencesKey("list")
    private val selectedKey = stringPreferencesKey("selected")

    val waypoints: Flow<List<Waypoint>> = context.wpStore.data.map { p ->
        decode(p[listKey] ?: "[]")
    }

    val selectedId: Flow<String?> = context.wpStore.data.map { p -> p[selectedKey] }

    suspend fun add(
        name: String,
        lat: Double,
        lon: Double,
        folder: String,
        symbol: String,
        nowMillis: Long,
    ) {
        context.wpStore.edit { p ->
            val current = decode(p[listKey] ?: "[]")
            val wp = Waypoint(
                id = UUID.randomUUID().toString(),
                name = name,
                lat = lat,
                lon = lon,
                createdAt = nowMillis,
                folder = folder.ifBlank { DEFAULT_FOLDER },
                symbol = symbol.ifBlank { DEFAULT_SYMBOL },
            )
            p[listKey] = encode(current + wp)
            if (p[selectedKey] == null) {
                p[selectedKey] = wp.id
            }
        }
    }

    suspend fun update(
        id: String,
        name: String,
        lat: Double,
        lon: Double,
        folder: String,
        symbol: String,
    ) {
        context.wpStore.edit { p ->
            val current = decode(p[listKey] ?: "[]")
            p[listKey] = encode(
                current.map {
                    if (it.id == id) it.copy(
                        name = name,
                        lat = lat,
                        lon = lon,
                        folder = folder.ifBlank { DEFAULT_FOLDER },
                        symbol = symbol.ifBlank { DEFAULT_SYMBOL },
                    ) else it
                }
            )
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
                        folder = o.optString("folder", DEFAULT_FOLDER),
                        symbol = o.optString("symbol", DEFAULT_SYMBOL),
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
                    .put("folder", w.folder)
                    .put("symbol", w.symbol)
            )
        }
        return arr.toString()
    }
}
