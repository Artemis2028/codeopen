package app.gridfix.android.ui.screens

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.gridfix.android.coords.Coordinates
import app.gridfix.android.data.AppSettings
import app.gridfix.android.data.FolderInfo
import app.gridfix.android.data.GraphicTypes
import app.gridfix.android.data.TacGraphic
import app.gridfix.android.data.Waypoint
import app.gridfix.android.data.WaypointDraft
import app.gridfix.android.location.FixData
import app.gridfix.android.ui.Affiliations
import app.gridfix.android.ui.WaypointDialog
import app.gridfix.android.ui.WaypointMarker
import java.util.Locale

@Composable
fun WaypointsScreen(
    fix: FixData,
    settings: AppSettings,
    waypoints: List<Waypoint>,
    folders: List<FolderInfo>,
    onAdd: (WaypointDraft) -> Unit,
    onUpdate: (id: String, draft: WaypointDraft) -> Unit,
    onDelete: (String) -> Unit,
    onNavigateTo: (String) -> Unit,
    onAddFolder: (String) -> Unit,
    onSetFolderVisible: (name: String, visible: Boolean) -> Unit,
    graphics: List<TacGraphic>,
    onDeleteGraphic: (String) -> Unit,
    onClearGraphics: (folder: String) -> Unit,
)
{
    var dialogOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Waypoint?>(null) }
    var deleteCandidate by remember { mutableStateOf<Waypoint?>(null) }
    var deleteGraphicCandidate by remember { mutableStateOf<TacGraphic?>(null) }
    var clearFolderCandidate by remember { mutableStateOf<String?>(null) }
    var newFolderOpen by remember { mutableStateOf(false) }
    val collapsed = remember { mutableStateMapOf<String, Boolean>() }
    val loc = fix.location
    val byFolder = waypoints.groupBy { it.folder }
    val graphicsByFolder = graphics.groupBy { it.folder }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "overlays-header") {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "OVERLAYS",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 2.sp,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { newFolderOpen = true }) {
                        Icon(
                            Icons.Outlined.CreateNewFolder,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.size(6.dp))
                        Text("New folder")
                    }
                }
            }

            folders.forEach { folder ->
                val list = byFolder[folder.name].orEmpty()
                val glist = graphicsByFolder[folder.name].orEmpty()
                val isCollapsed = collapsed[folder.name] == true
                item(key = "folder-${folder.name}") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { collapsed[folder.name] = !isCollapsed },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            if (isCollapsed || !folder.visible) Icons.Outlined.ExpandMore
                            else Icons.Outlined.ExpandLess,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.size(8.dp))
                        Text(
                            folder.name.uppercase(Locale.US),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (folder.visible) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            letterSpacing = 2.sp,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            if (glist.isEmpty()) "${list.size}" else "${list.size} + ${glist.size}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        IconButton(onClick = { onSetFolderVisible(folder.name, !folder.visible) }) {
                            Icon(
                                if (folder.visible) Icons.Outlined.Visibility
                                else Icons.Outlined.VisibilityOff,
                                contentDescription = if (folder.visible) "Hide overlay" else "Show overlay",
                                tint = if (folder.visible) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                if (folder.visible && !isCollapsed) {
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
                    items(glist, key = { "g-" + it.id }) { g ->
                        GraphicRow(
                            g = g,
                            night = settings.nightMode,
                            onDelete = { deleteGraphicCandidate = g },
                        )
                    }
                    if (glist.size > 1) {
                        item(key = "clear-${folder.name}") {
                            TextButton(onClick = { clearFolderCandidate = folder.name }) {
                                Text("Clear ${glist.size} graphics in ${folder.name}")
                            }
                        }
                    }
                }
            }

            if (waypoints.isEmpty()) {
                item(key = "empty-hint") {
                    Text(
                        "No waypoints yet — tap + to mark your current position or enter an MGRS grid.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 24.dp),
                    )
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
            presetLat = loc?.latitude,
            presetLon = loc?.longitude,
            presetLabel = "Current position",
            folderNames = folders.map { it.name },
            defaultName = "WP " + (waypoints.size + 1),
            onConfirm = { draft ->
                val target = editing
                if (target == null) onAdd(draft) else onUpdate(target.id, draft)
                dialogOpen = false
            },
            onDismiss = { dialogOpen = false },
            night = settings.nightMode,
        )
    }

    if (newFolderOpen) {
        var folderName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { newFolderOpen = false },
            title = { Text("New folder") },
            text = {
                OutlinedTextField(
                    value = folderName,
                    onValueChange = { folderName = it },
                    label = { Text("Folder name") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (folderName.isNotBlank()) onAddFolder(folderName.trim())
                    newFolderOpen = false
                }) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { newFolderOpen = false }) { Text("Cancel") }
            },
        )
    }

    deleteGraphicCandidate?.let { g ->
        AlertDialog(
            onDismissRequest = { deleteGraphicCandidate = null },
            title = { Text("Delete ${g.name.ifBlank { GraphicTypes.label(g.type) }}?") },
            text = { Text("This drawn graphic will be removed permanently.") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteGraphic(g.id)
                    deleteGraphicCandidate = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deleteGraphicCandidate = null }) { Text("Cancel") }
            },
        )
    }

    clearFolderCandidate?.let { fname ->
        val count = graphicsByFolder[fname].orEmpty().size
        AlertDialog(
            onDismissRequest = { clearFolderCandidate = null },
            title = { Text("Clear $fname?") },
            text = { Text("All $count drawn graphics in this folder will be removed permanently. Waypoints are not affected.") },
            confirmButton = {
                TextButton(onClick = {
                    onClearGraphics(fname)
                    clearFolderCandidate = null
                }) { Text("Clear") }
            },
            dismissButton = {
                TextButton(onClick = { clearFolderCandidate = null }) { Text("Cancel") }
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

@Composable
private fun GraphicRow(
    g: TacGraphic,
    night: Boolean,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Outlined.Timeline,
                contentDescription = null,
                tint = if (night) Color(0xFFFF3B30)
                else Affiliations.color(g.affiliation, MaterialTheme.colorScheme.primary),
                modifier = Modifier.size(26.dp),
            )
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    g.name.ifBlank { GraphicTypes.label(g.type) },
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    "${GraphicTypes.label(g.type)} · ${g.points.size} points",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = "Delete graphic",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
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
            WaypointMarker(symbol = w.symbol, affiliation = w.affiliation, size = 34.dp, echelon = w.echelon, night = settings.nightMode)
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

