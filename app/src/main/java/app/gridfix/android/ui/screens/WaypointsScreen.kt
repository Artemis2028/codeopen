package app.gridfix.android.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.gridfix.android.coords.Coordinates
import app.gridfix.android.data.AppSettings
import app.gridfix.android.data.DEFAULT_FOLDER
import app.gridfix.android.data.Waypoint
import app.gridfix.android.location.FixData
import app.gridfix.android.ui.WaypointSymbols
import java.util.Locale

@Composable
fun WaypointsScreen(
    fix: FixData,
    settings: AppSettings,
    waypoints: List<Waypoint>,
    onAdd: (name: String, lat: Double, lon: Double, folder: String, symbol: String) -> Unit,
    onUpdate: (id: String, name: String, lat: Double, lon: Double, folder: String, symbol: String) -> Unit,
    onDelete: (String) -> Unit,
    onNavigateTo: (String) -> Unit,
) {
    var dialogOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Waypoint?>(null) }
    var deleteCandidate by remember { mutableStateOf<Waypoint?>(null) }
    val collapsed = remember { mutableStateMapOf<String, Boolean>() }
    val loc = fix.location
    val existingFolders = remember(waypoints) {
        waypoints.map { it.folder }.distinct().sortedBy { it.lowercase(Locale.US) }
    }

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
            val grouped = waypoints.groupBy { it.folder }
                .entries.sortedBy { it.key.lowercase(Locale.US) }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                grouped.forEach { (folderName, list) ->
                    val isCollapsed = collapsed[folderName] == true
                    item(key = "folder-$folderName") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { collapsed[folderName] = !isCollapsed }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                if (isCollapsed) Icons.Outlined.ExpandMore else Icons.Outlined.ExpandLess,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.size(8.dp))
                            Text(
                                folderName.uppercase(Locale.US),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                letterSpacing = 2.sp,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                "${list.size}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (!isCollapsed) {
                        items(list, key = { it.id }) { w ->
                            WaypointRow(
                                w = w,
                                loc = loc,
                                settings = settings,
                                onClick = { onNavigateTo(w.id) },
                                onEdit = { editing = w; dialogOpen = true },
                                onDelete = { deleteCandidate = w },
                            )
                        }
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { editing = null; dialogOpen = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ) {
            Icon(Icons.Filled.Add, contentDescription = "Add waypoint")
        }
    }

    if (dialogOpen) {
        WaypointDialog(
            initial = editing,
            fix = fix,
            existingFolders = existingFolders,
            defaultName = "WP " + (waypoints.size + 1),
            onConfirm = { name, lat, lon, folder, symbol ->
                val target = editing
                if (target == null) onAdd(name, lat, lon, folder, symbol)
                else onUpdate(target.id, name, lat, lon, folder, symbol)
                dialogOpen = false
            },
            onDismiss = { dialogOpen = false },
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

@Composable
private fun WaypointRow(
    w: Waypoint,
    loc: android.location.Location?,
    settings: AppSettings,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, top = 12.dp, bottom = 12.dp, end = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                WaypointSymbols.icon(w.symbol),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(26.dp),
            )
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(w.name, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(2.dp))
                Text(
                    Coordinates.mgrs(w.lat, w.lon, 8)?.full
                        ?: String.format(Locale.US, "%.5f, %.5f", w.lat, w.lon),
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
            var menuOpen by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(
                        Icons.Outlined.MoreVert,
                        contentDescription = "Waypoint options",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Edit") },
                        leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                        onClick = { menuOpen = false; onEdit() },
                    )
                    DropdownMenuItem(
                        text = { Text("Delete") },
                        leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
                        onClick = { menuOpen = false; onDelete() },
                    )
                }
            }
        }
    }
}

@Composable
private fun WaypointDialog(
    initial: Waypoint?,
    fix: FixData,
    existingFolders: List<String>,
    defaultName: String,
    onConfirm: (name: String, lat: Double, lon: Double, folder: String, symbol: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val loc = fix.location
    var name by remember(initial) { mutableStateOf(initial?.name ?: "") }
    var folder by remember(initial) { mutableStateOf(initial?.folder ?: DEFAULT_FOLDER) }
    var symbol by remember(initial) { mutableStateOf(initial?.symbol ?: "flag") }
    var useCurrent by remember(initial) { mutableStateOf(initial == null && loc != null) }
    var coord by remember(initial) {
        mutableStateOf(
            when {
                initial != null -> Coordinates.mgrs(initial.lat, initial.lon, 10)?.full ?: ""
                loc != null -> Coordinates.mgrs(loc.latitude, loc.longitude, 10)?.full ?: ""
                else -> ""
            }
        )
    }
    var error by remember(initial) { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "New waypoint" else "Edit waypoint") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    placeholder = { Text(defaultName) },
                    singleLine = true,
                )

                Text("Symbol", style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    WaypointSymbols.all.forEach { key ->
                        val selected = key == symbol
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .border(
                                    width = if (selected) 2.dp else 1.dp,
                                    color = if (selected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outline,
                                    shape = CircleShape,
                                )
                                .clickable { symbol = key },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                WaypointSymbols.icon(key),
                                contentDescription = key,
                                tint = if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }
                }

                Text("Folder", style = MaterialTheme.typography.labelLarge)
                if (existingFolders.isNotEmpty()) {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        existingFolders.forEach { f ->
                            FilterChip(
                                selected = f == folder,
                                onClick = { folder = f },
                                label = { Text(f) },
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = folder,
                    onValueChange = { folder = it },
                    label = { Text("Folder name (type to create new)") },
                    singleLine = true,
                )

                Text("Position", style = MaterialTheme.typography.labelLarge)
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
                        label = { Text("MGRS grid") },
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
                        "Position: " + (Coordinates.mgrs(loc.latitude, loc.longitude, 10)?.full ?: "—"),
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
                val finalName = name.ifBlank { defaultName }
                val finalFolder = folder.ifBlank { DEFAULT_FOLDER }
                if (useCurrent && loc != null) {
                    onConfirm(finalName, loc.latitude, loc.longitude, finalFolder, symbol)
                } else {
                    val parsed = Coordinates.parseMgrs(coord)
                    if (parsed == null) {
                        error = "Couldn't read that grid — check it and try again."
                    } else {
                        onConfirm(finalName, parsed.first, parsed.second, finalFolder, symbol)
                    }
                }
            }) { Text(if (initial == null) "Add" else "Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
