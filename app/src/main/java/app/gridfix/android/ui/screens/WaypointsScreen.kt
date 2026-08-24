package app.gridfix.android.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.gridfix.android.coords.Coordinates
import app.gridfix.android.data.AppSettings
import app.gridfix.android.data.Waypoint
import app.gridfix.android.location.FixData
import java.util.Locale

@Composable
fun WaypointsScreen(
    fix: FixData,
    settings: AppSettings,
    waypoints: List<Waypoint>,
    onAdd: (name: String, lat: Double, lon: Double) -> Unit,
    onDelete: (String) -> Unit,
    onNavigateTo: (String) -> Unit,
) {
    var showAdd by remember { mutableStateOf(false) }
    var deleteCandidate by remember { mutableStateOf<Waypoint?>(null) }
    val loc = fix.location

    Box(Modifier.fillMaxSize()) {
        if (waypoints.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    Icons.Outlined.Flag,
                    contentDescription = null,
                    modifier = Modifier.size(56.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                Text("No waypoints yet", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Tap + to mark your current position or enter an MGRS grid.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(waypoints, key = { it.id }) { w ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onNavigateTo(w.id) },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    ) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(w.name, style = MaterialTheme.typography.titleMedium)
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    Coordinates.mgrs(w.lat, w.lon, 8)?.full ?: String.format(
                                        Locale.US, "%.5f, %.5f", w.lat, w.lon,
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (loc != null) {
                                val nav = Coordinates.navInfo(loc.latitude, loc.longitude, w.lat, w.lon)
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        Coordinates.formatDistance(nav.distanceMeters, settings.units),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontFamily = FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                    Text(
                                        Coordinates.formatAngle(nav.bearingTrue, settings.angleUnit) + " T",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontFamily = FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            IconButton(onClick = { deleteCandidate = w }) {
                                Icon(
                                    Icons.Outlined.Delete,
                                    contentDescription = "Delete ${w.name}",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { showAdd = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ) {
            Icon(Icons.Filled.Add, contentDescription = "Add waypoint")
        }
    }

    if (showAdd) {
        var name by remember { mutableStateOf("") }
        var coord by remember { mutableStateOf("") }
        var useCurrent by remember { mutableStateOf(loc != null) }
        var error by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text("New waypoint") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Name") },
                        singleLine = true,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = useCurrent,
                            onClick = { useCurrent = true },
                            label = { Text("Current position") },
                            enabled = loc != null,
                        )
                        FilterChip(
                            selected = !useCurrent,
                            onClick = { useCurrent = false },
                            label = { Text("Enter MGRS") },
                        )
                    }
                    if (!useCurrent) {
                        OutlinedTextField(
                            value = coord,
                            onValueChange = { coord = it.uppercase(Locale.US); error = null },
                            label = { Text("MGRS grid") },
                            placeholder = { Text("40R CN 12345 67890") },
                            singleLine = true,
                        )
                    } else if (loc != null) {
                        Text(
                            "Position: " + (Coordinates.mgrs(loc.latitude, loc.longitude, 8)?.full ?: "—"),
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    error?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val fallbackName = "WP " + (waypoints.size + 1)
                    if (useCurrent && loc != null) {
                        onAdd(name.ifBlank { fallbackName }, loc.latitude, loc.longitude)
                        showAdd = false
                    } else {
                        val parsed = Coordinates.parseMgrs(coord)
                        if (parsed == null) {
                            error = "Couldn't read that grid — check it and try again."
                        } else {
                            onAdd(name.ifBlank { fallbackName }, parsed.first, parsed.second)
                            showAdd = false
                        }
                    }
                }) { Text("Add") }
            },
            dismissButton = {
                TextButton(onClick = { showAdd = false }) { Text("Cancel") }
            },
        )
    }

    deleteCandidate?.let { w ->
        AlertDialog(
            onDismissRequest = { deleteCandidate = null },
            title = { Text("Delete ${w.name}?") },
            text = { Text("This waypoint will be removed permanently.") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(w.id)
                    deleteCandidate = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deleteCandidate = null }) { Text("Cancel") }
            },
        )
    }
}
