package app.gridfix.android.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.gridfix.android.coords.Coordinates
import app.gridfix.android.data.AppSettings
import app.gridfix.android.data.SettingsRepository
import app.gridfix.android.location.LocationTracker
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun PositionScreen(repo: SettingsRepository, settings: AppSettings) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        hasPermission = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
    }

    val tracker = remember { LocationTracker(context.applicationContext) }
    DisposableEffect(hasPermission) {
        if (hasPermission) tracker.start()
        onDispose { tracker.stop() }
    }
    val fix by tracker.fix.collectAsStateWithLifecycle()

    // 1 Hz ticker for the clock and fix age
    val now by produceState(initialValue = System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            delay(1000)
        }
    }

    if (!hasPermission) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                Icons.Outlined.MyLocation,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(16.dp))
            Text("Location access needed", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Text(
                "GridFix reads your position straight from the GPS chip. Everything stays on your phone — no account, no tracking, no internet needed.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            Button(onClick = {
                permissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                    )
                )
            }) {
                Text("Grant location access")
            }
        }
        return
    }

    val loc = fix.location

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // ---- Main MGRS card ----
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "MGRS · WGS84",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 1.5.sp,
                    )
                    AssistChip(
                        onClick = {
                            val next = when (settings.mgrsDigits) {
                                4 -> 6
                                6 -> 8
                                8 -> 10
                                else -> 4
                            }
                            scope.launch { repo.setMgrsDigits(next) }
                        },
                        label = { Text("${settings.mgrsDigits}-DIGIT") },
                    )
                }
                Spacer(Modifier.height(10.dp))

                if (loc == null) {
                    Text(
                        "ACQUIRING SIGNAL",
                        style = MaterialTheme.typography.headlineSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "${fix.satellitesUsed}/${fix.satellitesVisible} satellites",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (!fix.gpsEnabled) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "GPS is off — enable Location in your phone's settings.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                } else {
                    val parts = Coordinates.mgrs(loc.latitude, loc.longitude, settings.mgrsDigits)
                    when {
                        parts == null -> Text(
                            "—",
                            style = MaterialTheme.typography.headlineLarge,
                            fontFamily = FontFamily.Monospace,
                        )
                        parts.square.isEmpty() -> Text(
                            parts.full,
                            style = MaterialTheme.typography.headlineMedium,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        else -> {
                            Text(
                                "${parts.gzd} ${parts.square}",
                                style = MaterialTheme.typography.headlineSmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                "${parts.easting} ${parts.northing}",
                                fontSize = 38.sp,
                                lineHeight = 44.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
        }

        // ---- Secondary readouts ----
        InfoCard(
            label = "LAT / LON",
            value = loc?.let { Coordinates.formatLatLon(it.latitude, it.longitude, settings.latLonFormat) } ?: "—",
            modifier = Modifier.fillMaxWidth(),
        )
        InfoCard(
            label = "UTM",
            value = loc?.let { Coordinates.formatUtm(Coordinates.utm(it.latitude, it.longitude)) } ?: "—",
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            InfoCard(
                label = "ALTITUDE MSL",
                value = if (loc != null && loc.hasAltitude()) {
                    Coordinates.formatAltitude(loc.altitude, settings.units)
                } else "—",
                modifier = Modifier.weight(1f),
            )
            InfoCard(
                label = "ACCURACY",
                value = if (loc != null && loc.hasAccuracy()) {
                    Coordinates.formatAccuracy(loc.accuracy, settings.units)
                } else "—",
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            InfoCard(
                label = "SPEED",
                value = if (loc != null && loc.hasSpeed()) {
                    Coordinates.formatSpeed(loc.speed, settings.units)
                } else "—",
                modifier = Modifier.weight(1f),
            )
            InfoCard(
                label = "HEADING",
                value = if (loc != null && loc.hasBearing() && loc.hasSpeed() && loc.speed > 0.5f) {
                    "${loc.bearing.roundToInt()}° T"
                } else "—",
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            InfoCard(
                label = "DTG ZULU",
                value = Coordinates.dtg(now),
                modifier = Modifier.weight(1f),
            )
            InfoCard(
                label = "FIX AGE",
                value = loc?.let { fixAge(it, now) } ?: "—",
                modifier = Modifier.weight(1f),
            )
        }
        Text(
            "GNSS ${fix.satellitesUsed}/${fix.satellitesVisible} SATELLITES IN FIX",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 1.2.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private fun fixAge(loc: Location, now: Long): String {
    val s = ((now - loc.time) / 1000).coerceAtLeast(0)
    return when {
        s < 60 -> "${s}s"
        s < 3600 -> "${s / 60}m ${s % 60}s"
        else -> ">1h"
    }
}

@Composable
private fun InfoCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 1.2.sp,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                value,
                style = MaterialTheme.typography.titleMedium,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
