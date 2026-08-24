package app.gridfix.android.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Adjust
import androidx.compose.material.icons.outlined.Cabin
import androidx.compose.material.icons.outlined.CellTower
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MedicalServices
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.ui.graphics.vector.ImageVector

/** Echelon amplifier drawn above a unit frame (MIL-STD-2525 style marks). */
object Echelons {
    val all = listOf(
        "" to "None",
        "tm" to "Team",
        "sqd" to "Squad",
        "sec" to "Section",
        "plt" to "Platoon",
        "co" to "Company",
        "bn" to "Battalion",
        "rgt" to "Regiment",
        "bde" to "Brigade",
    )

    fun label(key: String): String = all.firstOrNull { it.first == key }?.second ?: key
}

/** Symbols a waypoint can carry. Keys are stored with the waypoint; icons resolve here. */
object WaypointSymbols {

    /** Plain drawn shapes (rendered by WaypointMarker, not material icons). */
    val shapes = listOf("dot", "triangle", "square", "diamond", "cross")

    val all = listOf(
        "dot", "triangle", "square", "diamond", "cross",
        "flag", "target", "star", "home", "danger",
        "water", "camp", "vehicle", "antenna", "medic",
    )

    fun isShape(key: String): Boolean = key in shapes

    /** Short display label for a basic symbol or shape (pickers). */
    fun label(key: String): String = when (key) {
        "dot" -> "Dot"
        "triangle" -> "Triangle"
        "square" -> "Square"
        "diamond" -> "Diamond"
        "cross" -> "Cross"
        "flag" -> "Flag"
        "target" -> "Target"
        "star" -> "Star"
        "home" -> "Home"
        "danger" -> "Danger"
        "water" -> "Water"
        "camp" -> "Camp"
        "vehicle" -> "Vehicle"
        "antenna" -> "Antenna"
        "medic" -> "Medic"
        else -> key
    }

    /** Tactical mission task symbols (drawn by WaypointMarker). */
    val tasks = listOf(
        "task_block", "task_ambush", "task_sbf", "task_fix", "task_secure",
        "task_occupy", "task_retain", "task_screen", "task_guard", "task_cover",
    )

    fun isTask(key: String): Boolean = key.startsWith("task_")

    fun taskLabel(key: String): String = when (key) {
        "task_block" -> "Block"
        "task_ambush" -> "Ambush"
        "task_sbf" -> "Support by fire"
        "task_fix" -> "Fix"
        "task_secure" -> "Secure"
        "task_occupy" -> "Occupy"
        "task_retain" -> "Retain"
        "task_screen" -> "Screen"
        "task_guard" -> "Guard"
        "task_cover" -> "Cover"
        else -> key
    }

    /** Letter-badge tasks render as a letter instead of a glyph. */
    fun taskLetter(key: String): String? = when (key) {
        "task_screen" -> "S"
        "task_guard" -> "G"
        "task_cover" -> "C"
        else -> null
    }

    fun icon(key: String): ImageVector = when (key) {
        "target" -> Icons.Outlined.Adjust
        "star" -> Icons.Outlined.Star
        "home" -> Icons.Outlined.Home
        "danger" -> Icons.Outlined.Warning
        "water" -> Icons.Outlined.WaterDrop
        "camp" -> Icons.Outlined.Cabin
        "vehicle" -> Icons.Outlined.DirectionsCar
        "antenna" -> Icons.Outlined.CellTower
        "medic" -> Icons.Outlined.MedicalServices
        else -> Icons.Outlined.Flag
    }
}
