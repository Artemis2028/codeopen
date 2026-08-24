package app.gridfix.android.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.gridfix.android.data.AppSettings
import app.gridfix.android.data.SettingsRepository
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(repo: SettingsRepository, settings: AppSettings) {
    val scope = rememberCoroutineScope()

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
            subtitle = "Prevent the display from sleeping while GridFix is open",
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

        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        Text(
            "GridFix 0.2.1 · Milestone 2\n" +
                "Working title — the public name is decided before launch.\n" +
                "MGRS conversion by the NGA MGRS library (MIT license).\n\n" +
                "GridFix is a training and recreation aid, not a primary means of navigation.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
