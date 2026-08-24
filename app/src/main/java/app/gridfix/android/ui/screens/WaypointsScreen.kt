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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.gridfix.android.coords.Coordinates
import app.gridfix.android.data.AppSettings
import app.gridfix.android.data.DEFAULT_FOLDER
import app.gridfix.android.data.FolderInfo
import app.gridfix.android.data.Waypoint
import app.gridfix.android.data.WaypointDraft
import app.gridfix.android.location.FixData
import app.gridfix.android.ui.Affiliations
import app.gridfix.android.ui.NatoSymbols
import app.gridfix.android.ui.WaypointMarker
import app.gridfix.android.ui.WaypointSymbols
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
)
{
    var dialogOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Waypoint?>(null) }
    var deleteCandidate by remember { mutableStateOf<Waypoint?>(null) }
    var newFolderOpen by remember { mutableStateOf(false) }
    val collapsed = remember { mutableStateMapOf<String, Boolean>() }
    val loc = fix.location
    val byFolder = waypoints.groupBy { it.folder }

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
                            "${list.size}",
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
            fix = fix,
            folderNames = folders.map { it.name },
            defaultName = "WP " + (waypoints.size + 1),
            onConfirm = { draft ->
                val target = editing
                if (target == null) onAdd(draft) else onUpdate(target.id, draft)
                dialogOpen = false
            },
            onDismiss = { dialogOpen = false },
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
            WaypointMarker(symbol = w.symbol, affiliation = w.affiliation, size = 34.dp)
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
private fun SymbolRow(
    keys: List<String>,
    selected: String,
    affiliation: String,
    onSelect: (String) -> Unit,
) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        keys.forEach { key ->
            val isSelected = key == selected
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .border(
                        width = if (isSelected) 2.dp else 1.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outline,
                        shape = CircleShape,
                    )
                    .clickable { onSelect(key) },
                contentAlignment = Alignment.Center,
            ) {
                WaypointMarker(symbol = key, affiliation = affiliation, size = 32.dp)
            }
        }
    }
}

@Composable
private fun WaypointDialog(
    initial: Waypoint?,
    fix: FixData,
    folderNames: List<String>,
    defaultName: String,
    onConfirm: (WaypointDraft) -> Unit,
    onDismiss: () -> Unit,
) {
    val loc = fix.location
    val baseParts = remember(initial) {
        when {
            initial != null -> Coordinates.mgrs(initial.lat, initial.lon, 10)
            loc != null -> Coordinates.mgrs(loc.latitude, loc.longitude, 10)
            else -> null
        }
    }

    var name by remember(initial) { mutableStateOf(initial?.name ?: "") }
    var folder by remember(initial) { mutableStateOf(initial?.folder ?: DEFAULT_FOLDER) }
    var symbol by remember(initial) { mutableStateOf(initial?.symbol ?: "flag") }
    var affiliation by remember(initial) { mutableStateOf(initial?.affiliation ?: "none") }
    var useCurrent by remember(initial) { mutableStateOf(initial == null && loc != null) }
    var gzdSquare by remember(initial) {
        mutableStateOf(baseParts?.let { "${it.gzd} ${it.square}" } ?: "")
    }
    var easting by remember(initial) { mutableStateOf(baseParts?.easting ?: "") }
    var northing by remember(initial) { mutableStateOf(baseParts?.northing ?: "") }
    var error by remember(initial) { mutableStateOf<String?>(null) }

    val bigDigits = LocalTextStyle.current.copy(
        fontFamily = FontFamily.Monospace,
        fontSize = 22.sp,
        textAlign = TextAlign.Center,
    )

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

                Text("Affiliation", style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Affiliations.all.forEach { key ->
                        FilterChip(
                            selected = key == affiliation,
                            onClick = { affiliation = key },
                            label = { Text(Affiliations.label(key)) },
                        )
                    }
                }

                Text("Symbol", style = MaterialTheme.typography.labelLarge)
                SymbolRow(
                    keys = WaypointSymbols.all,
                    selected = symbol,
                    affiliation = affiliation,
                ) { symbol = it }

                Text("Tactical task", style = MaterialTheme.typography.labelLarge)
                SymbolRow(
                    keys = WaypointSymbols.tasks,
                    selected = symbol,
                    affiliation = affiliation,
                ) { symbol = it }

                Text("NATO units", style = MaterialTheme.typography.labelLarge)
                NatoSymbols.affiliations.forEach { (aff, affLabel) ->
                    Text(
                        affLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    SymbolRow(
                        keys = NatoSymbols.keysFor(aff),
                        selected = symbol,
                        affiliation = affiliation,
                    ) { symbol = it }
                }

                Text("Folder", style = MaterialTheme.typography.labelLarge)
                if (folderNames.isNotEmpty()) {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        folderNames.forEach { f ->
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
                    label = { Text("Folder (type to create new)") },
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
                        value = gzdSquare,
                        onValueChange = { v ->
                            gzdSquare = v.uppercase(Locale.US)
                                .filter { it.isLetterOrDigit() || it == ' ' }
                                .take(7)
                            error = null
                        },
                        label = { Text("Grid zone") },
                        placeholder = { Text("39R TM") },
                        singleLine = true,
                        textStyle = bigDigits,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = easting,
                            onValueChange = { v ->
                                easting = v.filter { it.isDigit() }.take(5)
                                error = null
                            },
                            label = { Text("Easting") },
                            placeholder = { Text("23559") },
                            singleLine = true,
                            textStyle = bigDigits,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = northing,
                            onValueChange = { v ->
                                northing = v.filter { it.isDigit() }.take(5)
                                error = null
                            },
                            label = { Text("Northing") },
                            placeholder = { Text("96991") },
                            singleLine = true,
                            textStyle = bigDigits,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                        )
                    }
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
                    onConfirm(
                        WaypointDraft(finalName, loc.latitude, loc.longitude, finalFolder, symbol, affiliation)
                    )
                } else if (easting.isEmpty() || easting.length != northing.length) {
                    error = "Easting and northing need the same number of digits."
                } else {
                    val parsed = Coordinates.parseMgrs(gzdSquare + easting + northing)
                    if (parsed == null) {
                        error = "Couldn't read that grid — check the zone letters and digits."
                    } else {
                        onConfirm(
                            WaypointDraft(finalName, parsed.first, parsed.second, finalFolder, symbol, affiliation)
                        )
                    }
                }
            }) { Text(if (initial == null) "Add" else "Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
