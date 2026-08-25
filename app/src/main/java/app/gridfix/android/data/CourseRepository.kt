package app.gridfix.android.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.courseStore by preferencesDataStore(name = "course")

/** A practice land-nav course in progress: ordered waypoints + split times. */
data class CourseState(
    val name: String,
    val waypointIds: List<String>,
    val startedAt: Long,
    val foundAt: List<Long>,   // one entry per point already found, in order
) {
    val nextIndex: Int get() = foundAt.size
    val done: Boolean get() = foundAt.size >= waypointIds.size
}

/** A finished course, kept for the practice log. */
data class CourseResult(
    val name: String,
    val points: Int,
    val startedAt: Long,
    val totalMillis: Long,
    val splitsMillis: List<Long>,
)

/**
 * Practice course engine state. The course itself is ordinary waypoints (in
 * their own folder); this store tracks order, progress, and the results log.
 */
class CourseRepository(private val context: Context) {

    private object Keys {
        val ACTIVE = stringPreferencesKey("active")
        val HISTORY = stringPreferencesKey("history")
    }

    val active: Flow<CourseState?> = context.courseStore.data.map { p ->
        decodeActive(p[Keys.ACTIVE] ?: "")
    }

    val history: Flow<List<CourseResult>> = context.courseStore.data.map { p ->
        decodeHistory(p[Keys.HISTORY] ?: "[]")
    }

    suspend fun start(name: String, waypointIds: List<String>, nowMillis: Long) {
        val o = JSONObject()
            .put("name", name)
            .put("ids", JSONArray(waypointIds))
            .put("started", nowMillis)
            .put("found", JSONArray())
        context.courseStore.edit { it[Keys.ACTIVE] = o.toString() }
    }

    /** Record the next point as found; returns the updated state. */
    suspend fun markFound(nowMillis: Long) {
        context.courseStore.edit { p ->
            val cur = decodeActive(p[Keys.ACTIVE] ?: "") ?: return@edit
            if (cur.done) return@edit
            val o = JSONObject()
                .put("name", cur.name)
                .put("ids", JSONArray(cur.waypointIds))
                .put("started", cur.startedAt)
                .put("found", JSONArray(cur.foundAt + nowMillis))
            p[Keys.ACTIVE] = o.toString()
        }
    }

    /** Move the finished course into the results log and clear the active one. */
    suspend fun finish() {
        context.courseStore.edit { p ->
            val cur = decodeActive(p[Keys.ACTIVE] ?: "") ?: return@edit
            if (cur.foundAt.isNotEmpty()) {
                val splits = buildList {
                    var prev = cur.startedAt
                    for (t in cur.foundAt) {
                        add(t - prev)
                        prev = t
                    }
                }
                val result = JSONObject()
                    .put("name", cur.name)
                    .put("points", cur.waypointIds.size)
                    .put("started", cur.startedAt)
                    .put("total", cur.foundAt.last() - cur.startedAt)
                    .put("splits", JSONArray(splits))
                val hist = JSONArray(p[Keys.HISTORY] ?: "[]")
                hist.put(result)
                // keep the latest 30 results
                while (hist.length() > 30) hist.remove(0)
                p[Keys.HISTORY] = hist.toString()
            }
            p[Keys.ACTIVE] = ""
        }
    }

    suspend fun abandon() {
        context.courseStore.edit { it[Keys.ACTIVE] = "" }
    }

    private fun decodeActive(json: String): CourseState? = runCatching {
        if (json.isBlank()) return null
        val o = JSONObject(json)
        val ids = o.getJSONArray("ids")
        val found = o.getJSONArray("found")
        CourseState(
            name = o.getString("name"),
            waypointIds = buildList { for (i in 0 until ids.length()) add(ids.getString(i)) },
            startedAt = o.getLong("started"),
            foundAt = buildList { for (i in 0 until found.length()) add(found.getLong(i)) },
        )
    }.getOrNull()

    private fun decodeHistory(json: String): List<CourseResult> = runCatching {
        val arr = JSONArray(json)
        buildList {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val splits = o.getJSONArray("splits")
                add(
                    CourseResult(
                        name = o.getString("name"),
                        points = o.getInt("points"),
                        startedAt = o.getLong("started"),
                        totalMillis = o.getLong("total"),
                        splitsMillis = buildList {
                            for (j in 0 until splits.length()) add(splits.getLong(j))
                        },
                    )
                )
            }
        }.reversed()   // newest first
    }.getOrDefault(emptyList())
}
