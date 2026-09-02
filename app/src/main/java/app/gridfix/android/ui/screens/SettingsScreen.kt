package app.gridfix.android.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.gridfix.android.data.AppSettings
import app.gridfix.android.data.SettingsRepository
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    repo: SettingsRepository,
    settings: AppSettings,
    entitled: Boolean = false,
    onPreviewPaywall: () -> Unit = {},
    onOpenReference: () -> Unit = {},
    onBackup: (android.net.Uri, (String) -> Unit) -> Unit = { _, _ -> },
    onRestore: (android.net.Uri, (String) -> Unit) -> Unit = { _, _ -> },
) {
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    var backupStatus by remember { mutableStateOf<String?>(null) }
    val backupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri -> if (uri != null) onBackup(uri) { backupStatus = it } }
    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) onRestore(uri) { backupStatus = it } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(
            "SETTINGS",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 2.sp,
        )

        SettingSwitch(
            title = "Night mode",
            subtitle = "Red-on-black display to preserve night vision",
            checked = settings.nightMode,
        ) { scope.launch { repo.setNightMode(it) } }

        SettingSwitch(
            title = "Keep screen on",
            subtitle = "Prevent the display from sleeping while MGRS GPS is open",
            checked = settings.keepScreenOn,
        ) { scope.launch { repo.setKeepScreenOn(it) } }

        ChipGroup(
            title = "MGRS precision",
            options = listOf("4-digit", "6-digit", "8-digit", "10-digit"),
            selected = when (settings.mgrsDigits) {
                4 -> 0
                6 -> 1
                8 -> 2
                else -> 3
            },
        ) { index -> scope.launch { repo.setMgrsDigits(listOf(4, 6, 8, 10)[index]) } }

        ChipGroup(
            title = "Lat/Lon format",
            options = listOf("DD", "DDM", "DMS"),
            selected = settings.latLonFormat,
        ) { index -> scope.launch { repo.setLatLonFormat(index) } }

        ChipGroup(
            title = "Units",
            options = listOf("Metric", "Imperial", "Nautical"),
            selected = settings.units,
        ) { index -> scope.launch { repo.setUnits(index) } }

        ChipGroup(
            title = "Angle unit",
            options = listOf("Degrees", "Mils"),
            selected = settings.angleUnit,
        ) { index -> scope.launch { repo.setAngleUnit(index) } }

        ChipGroup(
            title = "North reference",
            options = listOf("True", "Magnetic", "Grid"),
            selected = settings.northRef,
        ) { index -> scope.launch { repo.setNorthRef(index) } }

        PaceSetting(
            current = settings.pacePer100m,
            onChange = { v -> scope.launch { repo.setPacePer100m(v) } },
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        Column(
            Modifier
                .fillMaxWidth()
                .clickable { onOpenReference() }
        ) {
            Text("Field reference", style = MaterialTheme.typography.bodyLarge)
            Text(
                "Phonetic alphabet · grid reading · pace counts · contours · symbols",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        Column {
            Text("Backup & restore", style = MaterialTheme.typography.bodyLarge)
            Text(
                "Everything — waypoints, units, graphics, tracks, settings, practice log — " +
                    "in one file you keep. Restoring never duplicates what's already here.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row {
                TextButton(onClick = {
                    val stamp = java.text.SimpleDateFormat("yyyyMMdd-HHmm", java.util.Locale.US)
                        .format(java.util.Date())
                    backupLauncher.launch("gridfix-backup-$stamp.zip")
                }) { Text("Back up now") }
                TextButton(onClick = {
                    restoreLauncher.launch(arrayOf("application/zip", "application/octet-stream"))
                }) { Text("Restore…") }
            }
            backupStatus?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        Column {
            Text("MGRS GPS Pro", style = MaterialTheme.typography.bodyLarge)
            Text(
                when {
                    entitled -> "Subscription active."
                    app.gridfix.android.BuildConfig.DEBUG ->
                        "No subscription on this device — debug builds run unlocked."
                    else -> "No active subscription on this device."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row {
                TextButton(onClick = {
                    uriHandler.openUri(app.gridfix.android.billing.BillingManager.MANAGE_URL)
                }) { Text("Manage subscription") }
                if (app.gridfix.android.BuildConfig.DEBUG) {
                    TextButton(onClick = onPreviewPaywall) { Text("Preview paywall") }
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        Text(
            "MGRS GPS " + app.gridfix.android.BuildConfig.VERSION_NAME + "\n" +
                "MGRS conversion by the NGA MGRS library (MIT license).\n" +
                "Elevation data: Terrarium tiles via AWS Open Data (Mapzen) — " +
                "SRTM, USGS 3DEP/NED, GMTED2010, ETOPO1.\n\n" +
                "MGRS GPS is a training and recreation aid, not a primary means of navigation.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PaceSetting(current: Int, onChange: (Int) -> Unit) {
    var text by remember(current) { mutableStateOf(current.toString()) }
    Column {
        Text("Pace count per 100 m", style = MaterialTheme.typography.titleMedium)
        Text(
            "Your paces for 100 m on flat ground — used on route cards. Walk a known 100 m to find it.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = text,
            onValueChange = { v ->
                text = v.filter { it.isDigit() }.take(3)
                text.toIntOrNull()?.let { n -> if (n in 30..200) onChange(n) }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            suffix = { Text("paces") },
        )
    }
}

@Composable
private fun SettingSwitch(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun ChipGroup(
    title: String,
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    Column {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEachIndexed { index, label ->
                FilterChip(
                    selected = index == selected,
                    onClick = { onSelect(index) },
                    label = { Text(label) },
                )
            }
        }
    }
}
