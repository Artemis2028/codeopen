package app.gridfix.android.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

/**
 * Curated MIL-STD-2525B land unit symbols, bundled as 32px renders
 * (from the standard symbol set) in res/drawable-nodpi.
 * Key format: nato_<affiliation>_<function>, e.g. nato_h_armor.
 */
object NatoSymbols {

    val functions = listOf(
        "inf" to "Infantry",
        "mechinf" to "Mech infantry",
        "armor" to "Armor",
        "recon" to "Recon",
        "armcav" to "Armored cavalry",
        "sniper" to "Sniper team",
        "arty" to "Artillery",
        "mortar" to "Mortar",
        "rocket" to "Rocket artillery",
        "airdef" to "Air defense",
        "sam" to "SAM",
        "antiarmor" to "Anti-armor",
        "engineer" to "Engineer",
        "avn" to "Aviation",
        "atkavn" to "Attack aviation",
        "uav" to "UAV",
        "medical" to "Medical",
        "supply" to "Supply",
        "trans" to "Transportation",
        "maint" to "Maintenance",
        "signal" to "Signal",
        "ew" to "Electronic warfare",
        "intel" to "Mil intelligence",
        "mp" to "Military police",
        "eod" to "EOD",
        "cbrn" to "CBRN",
        "hq" to "Headquarters",
        "unit" to "Unit",
    )

    val affiliations = listOf(
        "f" to "Friendly",
        "h" to "Hostile",
        "n" to "Neutral",
        "u" to "Unknown",
    )

    fun isNato(key: String): Boolean = key.startsWith("nato_")

    fun keysFor(aff: String): List<String> = functions.map { "nato_${aff}_${it.first}" }

    fun label(key: String): String {
        val parts = key.split("_")
        if (parts.size != 3) return key
        val aff = affiliations.firstOrNull { it.first == parts[1] }?.second ?: parts[1]
        val func = functions.firstOrNull { it.first == parts[2] }?.second ?: parts[2]
        return "$aff $func"
    }

    /** Function-only label ("Infantry", "Air defense") for compact picker captions. */
    fun functionLabel(key: String): String {
        val parts = key.split("_")
        if (parts.size != 3) return key
        return functions.firstOrNull { it.first == parts[2] }?.second ?: parts[2]
    }

    // Symbol PNGs are looked up by name so the curated set can grow without a
    // hand-written map (112 as of the v0.7.8 doctrine pack). Ids are cached.
    private val idCache = HashMap<String, Int>()

    fun resId(context: Context, key: String): Int? {
        val cached = idCache[key]
        if (cached != null) return if (cached == 0) null else cached
        val id = context.resources.getIdentifier(key, "drawable", context.packageName)
        idCache[key] = id
        return if (id == 0) null else id
    }

    // The bundled renders have opaque black backgrounds; strip them to
    // transparent once per symbol so units sit directly on the map.
    private val bitmapCache = HashMap<Int, ImageBitmap>()

    fun bitmap(context: Context, resId: Int): ImageBitmap =
        bitmapCache.getOrPut(resId) {
            val src = BitmapFactory.decodeResource(context.resources, resId)
            val out = src.copy(Bitmap.Config.ARGB_8888, true)
            if (src !== out) src.recycle()
            val w = out.width
            val h = out.height
            val pixels = IntArray(w * h)
            out.getPixels(pixels, 0, w, 0, 0, w, h)
            for (i in pixels.indices) {
                val p = pixels[i]
                val r = (p shr 16) and 0xFF
                val g = (p shr 8) and 0xFF
                val b = p and 0xFF
                if (r < 40 && g < 40 && b < 40) {
                    pixels[i] = 0x00000000
                }
            }
            out.setPixels(pixels, 0, w, 0, 0, w, h)
            out.asImageBitmap()
        }
}
