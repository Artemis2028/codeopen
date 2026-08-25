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

private val Context.graphicsStore by preferencesDataStore(name = "graphics")

/** A simple lat/lon vertex for tactical graphics. */
data class GeoVertex(val lat: Double, val lon: Double)

/**
 * A tactical control-measure graphic drawn on the map.
 * Types: phase_line, boundary, axis, doa, objective, aa, route.
 * Area types (objective, aa) are closed automatically when rendered.
 */
data class TacGraphic(
    val id: String,
    val name: String,
    val type: String,
    val points: List<GeoVertex>,
    val folder: String = DEFAULT_FOLDER,
    val affiliation: String = "none",
    val createdAt: Long = 0L,
)

object GraphicTypes {
    /** type key -> (label, minimum vertices, isArea) */
    val all = listOf(
        Triple("phase_line", "Phase line", 2),
        Triple("boundary", "Boundary", 2),
        Triple("axis", "Axis of advance", 2),
        Triple("doa", "Direction of attack", 2),
        Triple("objective", "Objective", 3),
        Triple("aa", "Assembly area", 3),
        Triple("route", "Route", 2),
        Triple("lz", "Landing zone", 3),
        Triple("pz", "Pickup zone", 3),
        Triple("bp", "Battle position", 3),
        Triple("ea", "Engagement area", 3),
        Triple("nai", "NAI", 3),
        Triple("tai", "TAI", 3),
        Triple("area", "Area (sketch)", 3),
        Triple("screen_l", "Screen line", 2),
        Triple("guard_l", "Guard line", 2),
        Triple("cover_l", "Cover line", 2),
        Triple("ring", "Range ring", 2),
        Triple("sector", "Sector of fire", 3),
        Triple("trp", "Target ref point", 1),
        Triple("checkpoint", "Checkpoint", 1),
        Triple("dp", "Decision point", 1),
        Triple("text", "Text label", 1),
    )

    fun label(type: String): String = all.firstOrNull { it.first == type }?.second ?: type

    fun minPoints(type: String): Int = all.firstOrNull { it.first == type }?.third ?: 2

    fun isArea(type: String): Boolean = type in setOf(
        "objective", "aa", "lz", "pz", "area", "bp", "ea", "nai", "tai",
    )

    /** Security line graphics: letter box mid-line, arrows outward both ends. */
    fun securityLetter(type: String): String? = when (type) {
        "screen_l" -> "S"
        "guard_l" -> "G"
        "cover_l" -> "C"
        else -> null
    }

    /**
     * Types placed with a fixed number of taps finish themselves — no Done press.
     * Ring: center then range edge. Sector: apex, left limit, right limit.
     */
    fun fixedPoints(type: String): Int? = when (type) {
        "ring" -> 2
        "sector" -> 3
        "trp", "checkpoint", "dp", "text" -> 1
        else -> null
    }

    /** Placement hint shown when the type is picked. */
    fun placeHint(type: String): String? = when (type) {
        "ring" -> "Tap the center, then the range edge"
        "sector" -> "Tap the apex, then left limit, then right limit"
        "trp", "checkpoint", "dp" -> "Tap the map to place it"
        "text" -> "Tap the map where the text goes"
        "screen_l", "guard_l", "cover_l" -> "Tap along the security line, then Done"
        else -> null
    }

    /** Display prefix used in the on-map label, per doctrine shorthand. */
    fun labelPrefix(type: String): String = when (type) {
        "phase_line" -> "PL "
        "objective" -> "OBJ "
        "aa" -> "AA "
        "lz" -> "LZ "
        "pz" -> "PZ "
        "bp" -> "BP "
        "ea" -> "EA "
        "nai" -> "NAI "
        "tai" -> "TAI "
        "trp" -> "TRP "
        "checkpoint" -> "CP "
        "dp" -> "DP "
        else -> ""
    }
}

class GraphicsRepository(private val context: Context) {

    private val listKey = stringPreferencesKey("list")

    val graphics: Flow<List<TacGraphic>> = context.graphicsStore.data.map { p ->
        decode(p[listKey] ?: "[]")
    }

    suspend fun add(
        name: String,
        type: String,
        points: List<GeoVertex>,
        folder: String,
        affiliation: String,
        nowMillis: Long,
    ): String {
        val g = TacGraphic(
            id = UUID.randomUUID().toString(),
            name = name.trim(),
            type = type,
            points = points,
            folder = folder.ifBlank { DEFAULT_FOLDER },
            affiliation = affiliation,
            createdAt = nowMillis,
        )
        context.graphicsStore.edit { p ->
            p[listKey] = encode(decode(p[listKey] ?: "[]") + g)
        }
        return g.id
    }

    suspend fun rename(id: String, name: String, folder: String, affiliation: String) {
        context.graphicsStore.edit { p ->
            p[listKey] = encode(
                decode(p[listKey] ?: "[]").map {
                    if (it.id == id) it.copy(
                        name = name.trim(),
                        folder = folder.ifBlank { DEFAULT_FOLDER },
                        affiliation = affiliation,
                    ) else it
                }
            )
        }
    }

    /** Replace a graphic's vertices — the map's edit-points mode saves through this. */
    suspend fun updatePoints(id: String, points: List<GeoVertex>) {
        context.graphicsStore.edit { p ->
            p[listKey] = encode(
                decode(p[listKey] ?: "[]").map {
                    if (it.id == id) it.copy(points = points) else it
                }
            )
        }
    }

    suspend fun delete(id: String) {
        context.graphicsStore.edit { p ->
            p[listKey] = encode(decode(p[listKey] ?: "[]").filterNot { it.id == id })
        }
    }

    /** Remove every graphic in [folder] — "clear the sketch" in one go. */
    suspend fun deleteFolder(folder: String) {
        context.graphicsStore.edit { p ->
            p[listKey] = encode(decode(p[listKey] ?: "[]").filterNot { it.folder == folder })
        }
    }

    private fun decode(json: String): List<TacGraphic> = runCatching {
        val arr = JSONArray(json)
        buildList {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val pts = o.getJSONArray("points")
                val vertices = buildList {
                    for (j in 0 until pts.length()) {
                        val v = pts.getJSONArray(j)
                        add(GeoVertex(v.getDouble(0), v.getDouble(1)))
                    }
                }
                add(
                    TacGraphic(
                        id = o.getString("id"),
                        name = o.getString("name"),
                        type = o.getString("type"),
                        points = vertices,
                        folder = o.optString("folder", DEFAULT_FOLDER),
                        affiliation = o.optString("affiliation", "none"),
                        createdAt = o.optLong("createdAt"),
                    )
                )
            }
        }
    }.getOrDefault(emptyList())

    private fun encode(list: List<TacGraphic>): String {
        val arr = JSONArray()
        for (g in list) {
            val pts = JSONArray()
            for (v in g.points) {
                pts.put(JSONArray().put(v.lat).put(v.lon))
            }
            arr.put(
                JSONObject()
                    .put("id", g.id)
                    .put("name", g.name)
                    .put("type", g.type)
                    .put("points", pts)
                    .put("folder", g.folder)
                    .put("affiliation", g.affiliation)
                    .put("createdAt", g.createdAt)
            )
        }
        return arr.toString()
    }
}
