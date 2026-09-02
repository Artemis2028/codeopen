package app.gridfix.android.ui.screens

import android.content.Context
import android.hardware.GeomagneticField
import android.hardware.SensorManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.gridfix.android.coords.Coordinates
import app.gridfix.android.data.AppSettings
import app.gridfix.android.data.Waypoint
import app.gridfix.android.location.CompassData
import app.gridfix.android.location.CompassTracker
import app.gridfix.android.location.FixData
import app.gridfix.android.ui.WaypointMarker
import app.gridfix.android.ui.faces.DialNavigateFace
import app.gridfix.android.ui.faces.DialNavigateInstrument
import app.gridfix.android.ui.faces.DistanceHero
import app.gridfix.android.ui.faces.Face
import app.gridfix.android.ui.faces.FaceCells
import app.gridfix.android.ui.faces.GlanceArrow
import app.gridfix.android.ui.faces.GlanceNavigateFace
import app.gridfix.android.ui.faces.dialNavigateHint
import app.gridfix.android.ui.faces.facePalette
import app.gridfix.android.ui.faces.northRefLetter
import app.gridfix.android.ui.faces.steerText
import app.gridfix.android.ui.faces.toNorthRef
import app.gridfix.android.ui.isLandscape
import app.gridfix.android.ui.theme.LabelFamily
import app.gridfix.android.ui.theme.MonoFamily
import kotlin.math.abs

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
    LifecycleStartEffect(Unit) {
        compass.start()
        onStopOrDispose { compass.stop() }
    }
    val compassData by compass.data.collectAsStateWithLifecycle()
    val landscape = isLandscape()

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

    fun toRef(angleTrue: Float): Float = toNorthRef(angleTrue, settings.northRef, declination, convergence)
    val refLetter = northRefLetter(settings.northRef)

    val nav = if (loc != null && target != null) {
        Coordinates.navInfo(loc.latitude, loc.longitude, target.lat, target.lon)
    } else null

    // Arrival alert: one buzz + tone when closing inside 50 m of the target;
    // re-arms after moving back out past 150 m or switching targets.
    var alertedFor by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(nav?.distanceMeters?.toInt(), target?.id) {
        val t = target ?: return@LaunchedEffect
        val dist = nav?.distanceMeters ?: return@LaunchedEffect
        if (dist < 50f && alertedFor != t.id) {
            alertedFor = t.id
            runCatching {
                deviceVibrator(context)
                    ?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 300, 150, 300), -1))
            }
            runCatching {
                val tg = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 85)
                tg.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 500)
                kotlinx.coroutines.delay(700)
                tg.release()
            }
        } else if (dist > 150f && alertedFor == t.id) {
            alertedFor = null
        }
    }

    // Off-azimuth haptic guide: silence means on line; two short taps = target
    // is to your RIGHT, one long buzz = to your LEFT. Cadence quickens the
    // further off you drift, so it works with the phone in a pocket.
    var hapticGuide by remember { mutableStateOf(false) }
    val deviation: Float? = if (nav != null && headingTrue != null) {
        ((nav.bearingTrue - headingTrue + 540f) % 360f) - 180f
    } else null
    val devState = rememberUpdatedState(deviation)
    LaunchedEffect(hapticGuide) {
        if (!hapticGuide) return@LaunchedEffect
        val vib = deviceVibrator(context) ?: return@LaunchedEffect
        while (true) {
            val d = devState.value
            when {
                d == null -> kotlinx.coroutines.delay(1500)
                abs(d) <= 8f -> kotlinx.coroutines.delay(1200)
                else -> {
                    runCatching {
                        if (d > 0f) {
                            vib.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 70, 90, 70), -1))
                        } else {
                            vib.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 300), -1))
                        }
                    }
                    kotlinx.coroutines.delay(
                        when {
                            abs(d) > 60f -> 800L
                            abs(d) > 25f -> 1300L
                            else -> 2000L
                        }
                    )
                }
            }
        }
    }

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

    val subtle = MaterialTheme.colorScheme.onSurfaceVariant
    val palette = facePalette(settings.nightMode)

    // Everything the faces show, in the user's north reference and units. The
    // reference letter rides in the cell labels so a mils value stays one number.
    val headingRef = headingTrue?.let { toRef(it) }
    val targetRef = nav?.let { toRef(it.bearingTrue) }
    val headingText = headingRef?.let { Coordinates.formatAngle(it, settings.angleUnit) + " " + refLetter } ?: "—"
    val distanceText = if (nav != null) Coordinates.formatDistance(nav.distanceMeters, settings.units) else "—"
    val speed = loc?.takeIf { it.hasSpeed() && it.speed > 0.4f }?.speed
    val etaText = when {
        nav != null && nav.distanceMeters < 50f -> "HERE"
        nav != null && speed != null -> {
            val secs = (nav.distanceMeters / speed).toInt()
            if (secs >= 3600) {
                String.format(java.util.Locale.US, "%d:%02d h", secs / 3600, (secs % 3600) / 60)
            } else {
                String.format(java.util.Locale.US, "%d:%02d", secs / 60, secs % 60)
            }
        }
        else -> "—"
    }
    val cells = listOf(
        "Azimuth · $refLetter" to (targetRef?.let { Coordinates.formatAngle(it, settings.angleUnit) } ?: "—"),
        "Back az · $refLetter" to (targetRef?.let { Coordinates.formatAngle((it + 180f) % 360f, settings.angleUnit) } ?: "—"),
        "Time to go" to etaText,
    )
    val relBearing = if (nav != null && headingTrue != null) (nav.bearingTrue - headingTrue + 360f) % 360f else null
    val steerLine = "HDG $headingText · ${steerText(deviation, settings.angleUnit)}"

    BoxWithConstraints(Modifier.fillMaxSize()) {
        // 16 dp of padding each side; the dial must fit inside it
        val dialSize = if (landscape) {
            (maxHeight - 40.dp).coerceIn(160.dp, 300.dp)
        } else {
            (maxWidth - 62.dp).coerceAtMost(if (settings.face == Face.LENSATIC) 360.dp else 330.dp)
        }
        val density = LocalDensity.current
        val faceDensity = Density(density.density, density.fontScale.coerceAtMost(1.15f))
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (landscape) {
                // Instrument on the left, target / distance / cells on the right
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = maxHeight - 32.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CompositionLocalProvider(LocalDensity provides faceDensity) {
                        Box(Modifier.size(dialSize), contentAlignment = Alignment.Center) {
                            when (settings.face) {
                                Face.GLANCE -> GlanceArrow(relBearing, palette, dialSize)
                                else -> DialNavigateInstrument(
                                    p = palette,
                                    style = settings.face,
                                    dialSize = dialSize,
                                    headingRef = headingRef,
                                    targetRef = targetRef,
                                    headingText = headingText,
                                    deviation = deviation,
                                    angleUnit = settings.angleUnit,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.width(20.dp))
                    Column(Modifier.weight(1f)) {
                        TargetSelector(target, waypoints, settings, subtle, onSelect)
                        Spacer(Modifier.height(6.dp))
                        CompositionLocalProvider(LocalDensity provides faceDensity) {
                            DistanceHero(distanceText, palette, numeralSize = 56.sp, numeralLine = 60.sp, unitSize = 16.sp, display = settings.face == Face.GLANCE)
                            Spacer(Modifier.height(8.dp))
                            FaceCells(cells, palette)
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            if (settings.face == Face.LENSATIC) dialNavigateHint(settings.face) else steerLine,
                            fontFamily = if (settings.face == Face.LENSATIC) LabelFamily else MonoFamily,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = 1.sp,
                            color = if (settings.face != Face.LENSATIC && deviation != null && abs(deviation) <= 3f) palette.lume else palette.muted,
                            maxLines = 2,
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            FilterChip(
                                selected = hapticGuide,
                                onClick = { hapticGuide = !hapticGuide },
                                label = { Text(if (hapticGuide) "HAPTIC GUIDE ON" else "HAPTIC GUIDE") },
                            )
                        }
                    }
                }
                NavigateHints(loc, compassData, hapticGuide, subtle)
            } else {
                TargetSelector(target, waypoints, settings, subtle, onSelect, centered = true)

                Spacer(Modifier.height(12.dp))

                // ---- The face: arrow, lensatic dial, or clean card ----
                CompositionLocalProvider(LocalDensity provides faceDensity) {
                    when (settings.face) {
                        Face.GLANCE -> GlanceNavigateFace(
                            p = palette,
                            relBearing = relBearing,
                            distance = distanceText,
                            cells = cells,
                            headingLine = steerLine,
                        )
                        else -> DialNavigateFace(
                            p = palette,
                            style = settings.face,
                            dialSize = dialSize,
                            headingRef = headingRef,
                            targetRef = targetRef,
                            headingText = headingText,
                            deviation = deviation,
                            angleUnit = settings.angleUnit,
                            distance = distanceText,
                            cells = cells,
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                // Eyes-free aid: haptic azimuth guide
                FilterChip(
                    selected = hapticGuide,
                    onClick = { hapticGuide = !hapticGuide },
                    label = { Text(if (hapticGuide) "HAPTIC GUIDE ON" else "HAPTIC GUIDE") },
                )
                NavigateHints(loc, compassData, hapticGuide, subtle)
            }
        }
    }
}

/** The target picker: marker, name, drop-down of every navigable waypoint, and its grid underneath. */
@Composable
private fun TargetSelector(
    target: Waypoint?,
    waypoints: List<Waypoint>,
    settings: AppSettings,
    subtle: androidx.compose.ui.graphics.Color,
    onSelect: (String) -> Unit,
    centered: Boolean = false,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Column(horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start) {
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
                Text(target?.name ?: "Select target", style = MaterialTheme.typography.titleLarge, maxLines = 1)
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
                fontFamily = MonoFamily,
                color = subtle,
            )
        }
    }
}

/** Below the face: what the haptic guide means, and why a heading or fix may be missing. */
@Composable
private fun NavigateHints(
    loc: android.location.Location?,
    compassData: CompassData,
    hapticGuide: Boolean,
    subtle: androidx.compose.ui.graphics.Color,
) {
    if (hapticGuide) {
        Spacer(Modifier.height(6.dp))
        Text(
            "Pocket the phone: silence = on azimuth · two taps = turn RIGHT · long buzz = turn LEFT",
            style = MaterialTheme.typography.bodySmall,
            color = subtle,
            textAlign = TextAlign.Center,
        )
    }
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
    } else if (!compassData.hasReading) {
        Spacer(Modifier.height(12.dp))
        Text(
            "Waiting for the compass — the instrument is dimmed until it has a heading.",
            style = MaterialTheme.typography.bodySmall,
            color = subtle,
            textAlign = TextAlign.Center,
        )
    }
}

private fun deviceVibrator(context: Context): Vibrator? = runCatching {
    if (Build.VERSION.SDK_INT >= 31) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager)
            .defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }
}.getOrNull()
