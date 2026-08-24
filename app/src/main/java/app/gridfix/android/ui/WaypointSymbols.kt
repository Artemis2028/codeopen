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

/** Symbols a waypoint can carry. Keys are stored with the waypoint; icons resolve here. */
object WaypointSymbols {

    val all = listOf(
        "flag", "target", "star", "home", "danger",
        "water", "camp", "vehicle", "antenna", "medic",
    )

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
