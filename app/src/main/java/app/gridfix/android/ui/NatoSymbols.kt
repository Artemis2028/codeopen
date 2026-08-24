package app.gridfix.android.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import app.gridfix.android.R

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
        "arty" to "Artillery",
        "airdef" to "Air defense",
        "antiarmor" to "Anti-armor",
        "engineer" to "Engineer",
        "avn" to "Aviation",
        "medical" to "Medical",
        "supply" to "Supply",
        "signal" to "Signal",
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

    private val resMap: Map<String, Int> = mapOf(
        "nato_f_inf" to R.drawable.nato_f_inf,
        "nato_f_mechinf" to R.drawable.nato_f_mechinf,
        "nato_f_armor" to R.drawable.nato_f_armor,
        "nato_f_recon" to R.drawable.nato_f_recon,
        "nato_f_arty" to R.drawable.nato_f_arty,
        "nato_f_airdef" to R.drawable.nato_f_airdef,
        "nato_f_antiarmor" to R.drawable.nato_f_antiarmor,
        "nato_f_engineer" to R.drawable.nato_f_engineer,
        "nato_f_avn" to R.drawable.nato_f_avn,
        "nato_f_medical" to R.drawable.nato_f_medical,
        "nato_f_supply" to R.drawable.nato_f_supply,
        "nato_f_signal" to R.drawable.nato_f_signal,
        "nato_f_unit" to R.drawable.nato_f_unit,
        "nato_h_inf" to R.drawable.nato_h_inf,
        "nato_h_mechinf" to R.drawable.nato_h_mechinf,
        "nato_h_armor" to R.drawable.nato_h_armor,
        "nato_h_recon" to R.drawable.nato_h_recon,
        "nato_h_arty" to R.drawable.nato_h_arty,
        "nato_h_airdef" to R.drawable.nato_h_airdef,
        "nato_h_antiarmor" to R.drawable.nato_h_antiarmor,
        "nato_h_engineer" to R.drawable.nato_h_engineer,
        "nato_h_avn" to R.drawable.nato_h_avn,
        "nato_h_medical" to R.drawable.nato_h_medical,
        "nato_h_supply" to R.drawable.nato_h_supply,
        "nato_h_signal" to R.drawable.nato_h_signal,
        "nato_h_unit" to R.drawable.nato_h_unit,
        "nato_n_inf" to R.drawable.nato_n_inf,
        "nato_n_mechinf" to R.drawable.nato_n_mechinf,
        "nato_n_armor" to R.drawable.nato_n_armor,
        "nato_n_recon" to R.drawable.nato_n_recon,
        "nato_n_arty" to R.drawable.nato_n_arty,
        "nato_n_airdef" to R.drawable.nato_n_airdef,
        "nato_n_antiarmor" to R.drawable.nato_n_antiarmor,
        "nato_n_engineer" to R.drawable.nato_n_engineer,
        "nato_n_avn" to R.drawable.nato_n_avn,
        "nato_n_medical" to R.drawable.nato_n_medical,
        "nato_n_supply" to R.drawable.nato_n_supply,
        "nato_n_signal" to R.drawable.nato_n_signal,
        "nato_n_unit" to R.drawable.nato_n_unit,
        "nato_u_inf" to R.drawable.nato_u_inf,
        "nato_u_mechinf" to R.drawable.nato_u_mechinf,
        "nato_u_armor" to R.drawable.nato_u_armor,
        "nato_u_recon" to R.drawable.nato_u_recon,
        "nato_u_arty" to R.drawable.nato_u_arty,
        "nato_u_airdef" to R.drawable.nato_u_airdef,
        "nato_u_antiarmor" to R.drawable.nato_u_antiarmor,
        "nato_u_engineer" to R.drawable.nato_u_engineer,
        "nato_u_avn" to R.drawable.nato_u_avn,
        "nato_u_medical" to R.drawable.nato_u_medical,
        "nato_u_supply" to R.drawable.nato_u_supply,
        "nato_u_signal" to R.drawable.nato_u_signal,
        "nato_u_unit" to R.drawable.nato_u_unit,
    )

    fun resId(key: String): Int? = resMap[key]

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
