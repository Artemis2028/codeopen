package app.gridfix.android.ui.screens

import android.hardware.GeomagneticField
import android.hardware.SensorManager
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.gridfix.android.coords.Coordinates
import app.gridfix.android.data.AppSettings
import app.gridfix.android.data.Waypoint
import app.gridfix.android.location.CompassTracker
import app.gridfix.android.location.FixData
import app.gridfix.android.ui.WaypointMarker

@Composable
fun NavigateScreen(
    fix: FixData,
    settings: AppSettings,
    waypoints: List<Waypoint>,
    selectedId: String?,
    onSelect: (String) -> Unit,
) {
    val context = LocalContext.current
    val compass = remember { CompassTracker(context.applicationContext) }
    DisposableEffect(Unit) {
        compass.start()
        onDispose { compass.stop() }
    }
    val compassData by compass.data.collectAsStateWithLifecycle()

    val loc = fix.location
    val target = waypoints.firstOrNull { it.id == selectedId } ?: waypoints.firstOrNull()

    // Magnetic declination from the World Magnetic Model, refreshed when we move ~10 km
    val declination = remember(
        loc?.latitude?.let { (it * 10).toInt() },
        loc?.longitude?.let { (it * 10).toInt() },
    ) {
        if (loc == null) 0f else GeomagneticField(
            loc.latitude.toFloat(),
            loc.longitude.toFloat(),
            if (loc.hasAltitude()) loc.altitude.toFloat() else 0f,
            loc.time,
        ).declination
    }
    val convergence = if (loc == null) 0f else {
        Coordinates.gridConvergence(loc.latitude, loc.longitude).toFloat()
    }

    // Heading: compass sensor preferred, GPS course as fallback while moving
    val headingTrue: Float? = when {
        compassData.hasSensor && compassData.hasReading ->
            (compassData.azimuthMagnetic + declination + 360f) % 360f
        loc != null && loc.hasBearing() && loc.hasSpeed() && loc.speed > 0.5f ->
            (loc.bearing + 360f) % 360f
        else -> null
    }

    fun toRef(angleTrue: Float): Float = when (settings.northRef) {
        1 -> (angleTrue - declination + 360f) % 360f
        2 -> (angleTrue - convergence + 360f) % 360f
        else -> angleTrue
    }
    val refLetter = when (settings.northRef) {
        1 -> "M"
        2 -> "G"
        else -> "T"
    }

    val nav = if (loc != null && target != null) {
        Coordinates.navInfo(loc.latitude, loc.longitude, target.lat, target.lon)
    } else null

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
                "Create your first waypoint in the Waypoints tab, then come back here to navigate to it.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        return
    }

    // Colors captured outside the Canvas draw scope
    val ringColor = MaterialTheme.colorScheme.outline
    val onSurface = MaterialTheme.colorScheme.onSurface
    val subtle = MaterialTheme.colorScheme.onSurfaceVariant
    val primary = MaterialTheme.colorScheme.primary
    val textMeasurer = rememberTextMeasurer()
    val cardinalStyle = TextStyle(
        fontSize = 15.sp,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Target selector
        var menuOpen by remember { mutableStateOf(false) }
        Box {
            Row(
                modifier = Modifier.clickable { menuOpen = true },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                WaypointMarker(
                    symbol = target?.symbol ?: "flag",
                    affiliation = target?.affiliation ?: "none",
                    size = 32.dp,
                    echelon = target?.echelon ?: "",
                    night = settings.nightMode,
                )
                Spacer(Modifier.width(8.dp))
                Text(target?.name ?: "Select target", style = MaterialTheme.typography.titleLarge)
                Icon(Icons.Outlined.ArrowDropDown, contentDescription = "Change target", tint = subtle)
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                waypoints.forEach { w ->
                    DropdownMenuItem(
                        text = { Text(w.name) },
                        leadingIcon = {
                            WaypointMarker(symbol = w.symbol, affiliation = w.affiliation, size = 26.dp, echelon = w.echelon, night = settings.nightMode)
                        },
                        onClick = {
                            onSelect(w.id)
                            menuOpen = false
                        },
                    )
                }
            }
        }
        target?.let { t ->
            val parts = Coordinates.mgrs(t.lat, t.lon, 8)
            Text(
                parts?.full ?: "",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = subtle,
            )
        }

        Spacer(Modifier.height(12.dp))

        // Compass rose with target arrow
        Canvas(Modifier.size(280.dp)) {
            val c = center
            val r = size.minDimension / 2f - 8.dp.toPx()
            drawCircle(ringColor, radius = r, center = c, style = Stroke(width = 2.dp.toPx()))

            val ringHeading = headingTrue?.let { toRef(it) } ?: 0f
            rotate(-ringHeading, pivot = c) {
                for (i in 0 until 72) {
                    val major = i % 18 == 0
                    val medium = i % 6 == 0
                    rotate(i * 5f, pivot = c) {
                        drawLine(
                            color = if (major) onSurface else subtle,
                            start = Offset(c.x, c.y - r),
                            end = Offset(
                                c.x,
                                c.y - r + (if (major) 14.dp else if (medium) 10.dp else 5.dp).toPx(),
                            ),
                            strokeWidth = if (major) 3f else 1.5f,
                        )
                    }
                }
                listOf("N" to 0f, "E" to 90f, "S" to 180f, "W" to 270f).forEach { (label, ang) ->
                    rotate(ang, pivot = c) {
                        val layout = textMeasurer.measure(AnnotatedString(label), style = cardinalStyle)
                        drawText(
                            layout,
                            color = if (label == "N") primary else subtle,
                            topLeft = Offset(
                                c.x - layout.size.width / 2f,
                                c.y - r + 18.dp.toPx(),
                            ),
                        )
                    }
                }
            }

            if (nav != null && headingTrue != null) {
                rotate((nav.bearingTrue - headingTrue + 360f) % 360f, pivot = c) {
                    val path = Path().apply {
                        moveTo(c.x, c.y - r + 34.dp.toPx())
                        lineTo(c.x - 16.dp.toPx(), c.y + 12.dp.toPx())
                        lineTo(c.x, c.y - 4.dp.toPx())
                        lineTo(c.x + 16.dp.toPx(), c.y + 12.dp.toPx())
                        close()
                    }
                    drawPath(path, primary)
                }
            }
            drawCircle(onSurface, radius = 3.dp.toPx(), center = c)
        }

        Spacer(Modifier.height(8.dp))

        // Distance
        Text(
            if (nav != null) Coordinates.formatDistance(nav.distanceMeters, settings.units) else "—",
            fontSize = 44.sp,
            lineHeight = 50.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            color = primary,
        )
        Text(
            "DISTANCE",
            style = MaterialTheme.typography.labelSmall,
            color = subtle,
            letterSpacing = 1.5.sp,
        )

        Spacer(Modifier.height(14.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MiniStat(
                label = "BEARING",
                value = if (nav != null) {
                    Coordinates.formatAngle(toRef(nav.bearingTrue), settings.angleUnit) + " " + refLetter
                } else "—",
                modifier = Modifier.weight(1f),
            )
            MiniStat(
                label = "BACK-AZ",
                value = if (nav != null) {
                    Coordinates.formatAngle(toRef((nav.bearingTrue + 180f) % 360f), settings.angleUnit) + " " + refLetter
                } else "—",
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(12.dp))
        MiniStat(
            label = "YOUR HEADING",
            value = if (headingTrue != null) {
                Coordinates.formatAngle(toRef(headingTrue), settings.angleUnit) + " " + refLetter
            } else "—",
            modifier = Modifier.fillMaxWidth(),
        )

        // Status hints
        if (loc == null) {
            Spacer(Modifier.height(12.dp))
            Text(
                "Acquiring GPS signal…",
                style = MaterialTheme.typography.bodyMedium,
                color = subtle,
            )
        }
        if (!compassData.hasSensor) {
            Spacer(Modifier.height(12.dp))
            Text(
                "No compass sensor on this device — heading uses your direction of travel while moving.",
                style = MaterialTheme.typography.bodySmall,
                color = subtle,
                textAlign = TextAlign.Center,
            )
        } else if (compassData.accuracy <= SensorManager.SENSOR_STATUS_ACCURACY_LOW) {
            Spacer(Modifier.height(12.dp))
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Text(
                    "Compass needs calibration — move your phone in a figure-8 a few times.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(12.dp),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun MiniStat(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                value,
                style = MaterialTheme.typography.titleLarge,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 1.2.sp,
            )
        }
    }
}
