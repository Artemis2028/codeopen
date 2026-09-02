package app.gridfix.android.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration

/**
 * Worn-device layouts. Phones ride sideways on body armour, so every main
 * screen has a landscape arrangement, and the orientation can be pinned
 * regardless of the phone's own auto-rotate setting.
 */
object ScreenOrientation {
    const val AUTO = 0
    const val PORTRAIT = 1
    const val LANDSCAPE = 2
    const val LANDSCAPE_FLIPPED = 3
    val names = listOf("Auto", "Portrait", "Landscape", "Flipped")
    val menuNames = listOf("AUTO", "PORTRAIT", "LANDSCAPE", "LANDSCAPE FLIPPED")

    fun toActivityInfo(value: Int): Int = when (value) {
        PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        LANDSCAPE_FLIPPED -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
        else -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }
}

/** True while the window is wider than tall — the phone is sideways, or pinned that way. */
@Composable
fun isLandscape(): Boolean =
    LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

/** The Activity hosting this context (PaywallScreen has its own private finder). */
fun Context.hostActivity(): Activity? {
    var c: Context = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}
