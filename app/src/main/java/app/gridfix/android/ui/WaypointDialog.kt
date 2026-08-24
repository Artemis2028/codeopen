package app.gridfix.android.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LocalTextStyle
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.gridfix.android.coords.Coordinates
import app.gridfix.android.data.DEFAULT_FOLDER
import app.gridfix.android.data.Waypoint
import app.gridfix.android.data.WaypointDraft
import java.util.Locale

@Composable
fun SymbolRow(
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

/**
 * Create/edit dialog for a waypoint, shared by the Waypoints screen (preset =
 * current GPS position) and the Map screen (preset = crosshair position).
 */
@Composable
fun WaypointDialog(
    initial: Waypoint?,
    presetLat: Double?,
    presetLon: Double?,
    presetLabel: String,
    folderNames: List<String>,
    defaultName: String,
    onConfirm: (WaypointDraft) -> Unit,
    onDismiss: () -> Unit,
) {
    val baseParts = remember(initial) {
        when {
            initial != null -> Coordinates.mgrs(initial.lat, initial.lon, 10)
            presetLat != null && presetLon != null -> Coordinates.mgrs(presetLat, presetLon, 10)
            else -> null
        }
    }

    var name by remember(initial) { mutableStateOf(initial?.name ?: "") }
    var folder by remember(initial) { mutableStateOf(initial?.folder ?: DEFAULT_FOLDER) }
    var symbol by remember(initial) { mutableStateOf(initial?.symbol ?: "flag") }
    var affiliation by remember(initial) { mutableStateOf(initial?.affiliation ?: "none") }
    var usePreset by remember(initial) { mutableStateOf(initial == null && presetLat != null) }
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
                        selected = usePreset,
                        onClick = { usePreset = true },
                        label = { Text(presetLabel) },
                        enabled = presetLat != null,
                    )
                    FilterChip(
                        selected = !usePreset,
                        onClick = { usePreset = false },
                        label = { Text("MGRS grid") },
                    )
                }
                if (!usePreset) {
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
                } else if (presetLat != null && presetLon != null) {
                    Text(
                        "$presetLabel: " + (Coordinates.mgrs(presetLat, presetLon, 10)?.full ?: "—"),
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
                if (usePreset && presetLat != null && presetLon != null) {
                    onConfirm(
                        WaypointDraft(finalName, presetLat, presetLon, finalFolder, symbol, affiliation)
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
