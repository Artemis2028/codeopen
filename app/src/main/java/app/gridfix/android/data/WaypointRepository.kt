package app.gridfix.android.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
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
    val affiliation: String = "none",
    val echelon: String = "",       // "", tm, sqd, sec, plt, co, bn, rgt, bde
    val designation: String = "",   // free-text unit designation amplifier
    val kind: String = KIND_WP,     // KIND_WP (navigation point) or KIND_UNIT (plotted unit)
    val rotation: Float = 0f,       // degrees clockwise from north; direction of fire for task symbols
)

const val KIND_WP = "wp"
const val KIND_UNIT = "unit"

/** Everything needed to create or update a waypoint. */
data class WaypointDraft(
    val name: String,
    val lat: Double,
    val lon: Double,
    val folder: String,
    val symbol: String,
    val affiliation: String,
    val echelon: String = "",
    val designation: String = "",
    val kind: String = KIND_WP,
    val rotation: Float = 0f,
)

/** A waypoint folder ("overlay"): can exist empty, and can be toggled visible/hidden. */
data class FolderInfo(val name: String, val visible: Boolean = true)

class WaypointRepository(private val context: Context) {

    private val listKey = stringPreferencesKey("list")
    private val selectedKey = stringPreferencesKey("selected")
    private val foldersKey = stringPreferencesKey("folders")

    val waypoints: Flow<List<Waypoint>> = context.wpStore.data.map { p ->
        decode(p[listKey] ?: "[]")
    }

    val selectedId: Flow<String?> = context.wpStore.data.map { p -> p[selectedKey] }

    /** Stored folders unioned with any folder referenced by a waypoint, sorted by name. */
    val folders: Flow<List<FolderInfo>> = context.wpStore.data.map { p ->
        val stored = decodeFolders(p[foldersKey] ?: "[]")
        val referenced = decode(p[listKey] ?: "[]").map { it.folder }
        val names = (stored.map { it.name } + referenced + DEFAULT_FOLDER).distinct()
        names.map { n -> stored.firstOrNull { it.name == n } ?: FolderInfo(n) }
            .sortedBy { it.name.lowercase(Locale.US) }
    }

    suspend fun addFolder(name: String) {
        val clean = name.trim()
        if (clean.isEmpty()) return
        context.wpStore.edit { p ->
            p[foldersKey] = encodeFolders(
                upsertFolder(decodeFolders(p[foldersKey] ?: "[]"), clean, null)
            )
        }
    }

    suspend fun setFolderVisible(name: String, visible: Boolean) {
        context.wpStore.edit { p ->
            p[foldersKey] = encodeFolders(
                upsertFolder(decodeFolders(p[foldersKey] ?: "[]"), name, visible)
            )
        }
    }

    suspend fun add(draft: WaypointDraft, nowMillis: Long): String {
        val newId = UUID.randomUUID().toString()
        context.wpStore.edit { p ->
            val current = decode(p[listKey] ?: "[]")
            val wp = Waypoint(
                id = newId,
                name = draft.name,
                lat = draft.lat,
                lon = draft.lon,
                createdAt = nowMillis,
                folder = draft.folder.ifBlank { DEFAULT_FOLDER },
                symbol = draft.symbol.ifBlank { DEFAULT_SYMBOL },
                affiliation = draft.affiliation,
                echelon = draft.echelon,
                designation = draft.designation,
                kind = draft.kind,
                rotation = draft.rotation,
            )
            p[listKey] = encode(current + wp)
            p[foldersKey] = encodeFolders(
                upsertFolder(decodeFolders(p[foldersKey] ?: "[]"), wp.folder, null)
            )
            if (p[selectedKey] == null) {
                p[selectedKey] = wp.id
            }
        }
        return newId
    }

    /** Bulk add (imports): one datastore write however many points arrive. */
    suspend fun addAll(drafts: List<WaypointDraft>, nowMillis: Long) {
        if (drafts.isEmpty()) return
        context.wpStore.edit { p ->
            val current = decode(p[listKey] ?: "[]")
            var folders = decodeFolders(p[foldersKey] ?: "[]")
            val added = drafts.map { draft ->
                folders = upsertFolder(folders, draft.folder.ifBlank { DEFAULT_FOLDER }, null)
                Waypoint(
                    id = UUID.randomUUID().toString(),
                    name = draft.name,
                    lat = draft.lat,
                    lon = draft.lon,
                    createdAt = nowMillis,
                    folder = draft.folder.ifBlank { DEFAULT_FOLDER },
                    symbol = draft.symbol.ifBlank { DEFAULT_SYMBOL },
                    affiliation = draft.affiliation,
                    echelon = draft.echelon,
                    designation = draft.designation,
                    kind = draft.kind,
                    rotation = draft.rotation,
                )
            }
            p[listKey] = encode(current + added)
            p[foldersKey] = encodeFolders(folders)
        }
    }

    suspend fun update(id: String, draft: WaypointDraft) {
        context.wpStore.edit { p ->
            val current = decode(p[listKey] ?: "[]")
            p[listKey] = encode(
                current.map {
                    if (it.id == id) it.copy(
                        name = draft.name,
                        lat = draft.lat,
                        lon = draft.lon,
                        folder = draft.folder.ifBlank { DEFAULT_FOLDER },
                        symbol = draft.symbol.ifBlank { DEFAULT_SYMBOL },
                        affiliation = draft.affiliation,
                        echelon = draft.echelon,
                        designation = draft.designation,
                        kind = draft.kind,
                        rotation = draft.rotation,
                    ) else it
                }
            )
            p[foldersKey] = encodeFolders(
                upsertFolder(
                    decodeFolders(p[foldersKey] ?: "[]"),
                    draft.folder.ifBlank { DEFAULT_FOLDER },
                    null,
                )
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
                        affiliation = o.optString("affiliation", "none"),
                        echelon = o.optString("echelon", ""),
                        designation = o.optString("designation", ""),
                        kind = o.optString(
                            "kind",
                            // migrate pre-0.5 data: NATO symbols were always units
                            if (o.optString("symbol", "").startsWith("nato_")) KIND_UNIT else KIND_WP,
                        ),
                        rotation = o.optDouble("rotation", 0.0).toFloat(),
                    )
                )
            }
        }
    }.getOrDefault(emptyList())

    /** Add or update a folder entry; visible == null keeps the existing visibility. */
    private fun upsertFolder(list: List<FolderInfo>, name: String, visible: Boolean?): List<FolderInfo> {
        val existing = list.firstOrNull { it.name == name }
        return when {
            existing == null -> list + FolderInfo(name, visible ?: true)
            visible == null -> list
            else -> list.map { if (it.name == name) it.copy(visible = visible) else it }
        }
    }

    private fun decodeFolders(json: String): List<FolderInfo> = runCatching {
        val arr = JSONArray(json)
        buildList {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                add(FolderInfo(o.getString("name"), o.optBoolean("visible", true)))
            }
        }
    }.getOrDefault(emptyList())

    private fun encodeFolders(list: List<FolderInfo>): String {
        val arr = JSONArray()
        for (f in list) {
            arr.put(JSONObject().put("name", f.name).put("visible", f.visible))
        }
        return arr.toString()
    }

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
                    .put("affiliation", w.affiliation)
                    .put("echelon", w.echelon)
                    .put("designation", w.designation)
                    .put("kind", w.kind)
                    .put("rotation", w.rotation.toDouble())
            )
        }
        return arr.toString()
    }
}
