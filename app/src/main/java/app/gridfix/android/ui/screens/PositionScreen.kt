package app.gridfix.android.ui.screens

import android.hardware.GeomagneticField
import android.hardware.SensorManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import app.gridfix.android.coords.Phonetic
import app.gridfix.android.coords.SunMoon
import app.gridfix.android.data.AppSettings
import app.gridfix.android.data.SettingsRepository
import app.gridfix.android.location.CompassTracker
import app.gridfix.android.location.FixData
import app.gridfix.android.ui.QrDialog
import app.gridfix.android.ui.faces.DialPositionFace
import app.gridfix.android.ui.faces.DialPositionLandscape
import app.gridfix.android.ui.faces.Face
import app.gridfix.android.ui.faces.GlancePositionFace
import app.gridfix.android.ui.faces.GlancePositionLandscape
import app.gridfix.android.ui.faces.FixBars
import app.gridfix.android.ui.faces.facePalette
import app.gridfix.android.ui.faces.fixAgeSeconds
import app.gridfix.android.ui.faces.fixAgeText
import app.gridfix.android.ui.faces.fixColor
import app.gridfix.android.ui.faces.fixQuality
import app.gridfix.android.ui.faces.fixSummary
import app.gridfix.android.ui.faces.northRefLetter
import app.gridfix.android.ui.faces.overPrecise
import app.gridfix.android.ui.faces.toNorthRef
import app.gridfix.android.ui.geoUri
import app.gridfix.android.ui.isLandscape
import app.gridfix.android.ui.theme.MonoFamily
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun PositionScreen(
    fix: FixData,
    settings: AppSettings,
    repo: SettingsRepository,
    onMark: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var markedAt by remember { mutableStateOf(0L) }
    var qrOpen by remember { mutableStateOf(false) }

    // 1 Hz ticker for the clock and fix age
    val now by produceState(initialValue = System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            delay(1000)
        }
    }

    // The dial faces are live compasses; the Glance face does not need the sensor.
    // Started/stopped with the lifecycle, so Home or screen-off releases it.
    val compass = remember { CompassTracker(context.applicationContext) }
    LifecycleStartEffect(settings.face) {
        if (settings.face != Face.GLANCE) compass.start()
        onStopOrDispose { compass.stop() }
    }
    val compassData by compass.data.collectAsStateWithLifecycle()
    val landscape = isLandscape()

    val loc = fix.location
    val parts = loc?.let { Coordinates.mgrs(it.latitude, it.longitude, settings.mgrsDigits) }
    val palette = facePalette(settings.nightMode)

    // Declination and grid convergence, refreshed when we move ~10 km
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
    val convergence = if (loc == null) 0f else Coordinates.gridConvergence(loc.latitude, loc.longitude).toFloat()

    // Heading: compass sensor when a dial face is up, GPS course as the fallback while moving
    val compassHeading = settings.face != Face.GLANCE && compassData.hasSensor && compassData.hasReading
    val headingTrue: Float? = when {
        compassHeading -> (compassData.azimuthMagnetic + declination + 360f) % 360f
        loc != null && loc.hasBearing() && loc.hasSpeed() && loc.speed > 0.5f -> (loc.bearing + 360f) % 360f
        else -> null
    }
    val headingRef = headingTrue?.let { toNorthRef(it, settings.northRef, declination, convergence) }
    val refLetter = northRefLetter(settings.northRef)

    val elevText = if (loc != null && loc.hasAltitude()) Coordinates.formatAltitude(loc.altitude, settings.units) else "—"
    val speedText = if (loc != null && loc.hasSpeed()) Coordinates.formatSpeed(loc.speed, settings.units) else "—"
    // The reference letter lives in the label so "1234 mils" stays one number in its cell;
    // a heading taken from the direction of travel says so.
    val headingText = headingRef?.let { Coordinates.formatAngle(it, settings.angleUnit) } ?: "—"
    val headingLabel = (if (compassHeading || headingRef == null) "Heading" else "Course") + " · " + refLetter
    val cells = listOf("Elev" to elevText, headingLabel to headingText, "Speed" to speedText)
    val utmText = loc?.let { Coordinates.formatUtm(Coordinates.utm(it.latitude, it.longitude)) } ?: "—"
    val dtgText = Coordinates.dtg(now)
    val accuracyText = if (loc != null && loc.hasAccuracy()) Coordinates.formatAccuracy(loc.accuracy, settings.units) else null
    // Re-graded every second (this scope recomposes on the `now` tick), so a fix ages into STALE
    val quality = fixQuality(loc, fix.satellitesUsed)
    val gpsOff = loc == null && !fix.gpsEnabled
    val precisionWarn = overPrecise(quality, settings.mgrsDigits)
    val statusLine = when {
        gpsOff -> "GPS OFF · enable Location"
        else -> fixSummary(quality, fix.satellitesUsed, accuracyText, showTrust = precisionWarn)
    }
    val precisionLabel = "${settings.mgrsDigits}-DIGIT"
    val trustLine = when {
        loc == null -> "NO FIX"
        else -> "TRUST ${quality.trustDigits}-DIGIT"
    }
    val dialFixLine = when {
        gpsOff -> "GPS OFF"
        loc == null -> "ACQUIRING · ${fix.satellitesUsed} SATS"
        else -> listOfNotNull(quality.word, if (quality.stale) "${quality.ageSeconds} s" else accuracyText).joinToString(" ")
    }
    val dialPrecision = if (precisionWarn) "TRUST ${quality.trustDigits}-DIGIT" else precisionLabel
    val cyclePrecision: () -> Unit = {
        val next = when (settings.mgrsDigits) {
            4 -> 6
            6 -> 8
            8 -> 10
            else -> 4
        }
        scope.launch { repo.setMgrsDigits(next) }
    }

    // Light data for the dial faces: local day at the fix, device time zone
    val sunRow = remember(loc?.latitude?.let { (it * 100).toInt() }, loc?.longitude?.let { (it * 100).toInt() }, now / 600_000) {
        if (loc == null) "—" to "—" else {
            val cal = Calendar.getInstance()
            val y = cal.get(Calendar.YEAR)
            val m = cal.get(Calendar.MONTH) + 1
            val d = cal.get(Calendar.DAY_OF_MONTH)
            val t = SunMoon.sunTimes(y, m, d, loc.latitude, loc.longitude)
            ("BMNT ${SunMoon.formatLocal(t.bmnt, y, m, d)} · SR ${SunMoon.formatLocal(t.sunrise, y, m, d)}") to
                ("SS ${SunMoon.formatLocal(t.sunset, y, m, d)} · EENT ${SunMoon.formatLocal(t.eent, y, m, d)}")
        }
    }
    val declText = if (loc == null) "DECL —" else {
        val d = declination.roundToInt()
        "DECL ${abs(d)}° ${if (declination >= 0f) "E" else "W"}"
    }
    val latLonShort = loc?.let { Coordinates.formatLatLon(it.latitude, it.longitude, 0).replace("   ", " ") } ?: "—"
    val rows = listOf(
        Triple(utmText, dtgText, false),
        Triple(latLonShort, declText, false),
        Triple(sunRow.first, sunRow.second, true),
    )

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val viewport = maxHeight
        val dialSize = if (landscape) {
            (maxHeight - 24.dp).coerceIn(160.dp, 300.dp)
        } else {
            (maxWidth - 30.dp).coerceAtMost(if (settings.face == Face.LENSATIC) 360.dp else 330.dp)
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            // ---- The face ---- (instrument type is clamped at 1.15x so the numerals stay on the glass)
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, density.fontScale.coerceAtMost(1.15f)),
            ) {
                when {
                    settings.face == Face.GLANCE && landscape -> GlancePositionLandscape(
                        p = palette,
                        parts = parts,
                        acquiring = loc == null,
                        quality = quality,
                        statusLine = statusLine,
                        precisionLabel = precisionLabel,
                        precisionWarn = precisionWarn,
                        trustLine = trustLine,
                        onCyclePrecision = cyclePrecision,
                        cells = cells,
                        utm = utmText,
                        dtg = dtgText,
                        minHeight = viewport,
                    )
                    settings.face == Face.GLANCE -> GlancePositionFace(
                        p = palette,
                        parts = parts,
                        acquiring = loc == null,
                        quality = quality,
                        statusLine = statusLine,
                        precisionLabel = precisionLabel,
                        precisionWarn = precisionWarn,
                        onCyclePrecision = cyclePrecision,
                        cells = cells,
                        utm = utmText,
                        dtg = dtgText,
                        minHeight = viewport,
                    )
                    landscape -> DialPositionLandscape(
                        p = palette,
                        style = settings.face,
                        dialSize = dialSize,
                        parts = parts,
                        acquiring = loc == null,
                        quality = quality,
                        fixLine = dialFixLine,
                        precisionLabel = dialPrecision,
                        onCyclePrecision = cyclePrecision,
                        headingRef = headingRef,
                        cells = cells,
                        rows = rows,
                        minHeight = viewport,
                    )
                    else -> DialPositionFace(
                        p = palette,
                        style = settings.face,
                        dialSize = dialSize,
                        parts = parts,
                        acquiring = loc == null,
                        quality = quality,
                        fixLine = dialFixLine,
                        precisionLabel = dialPrecision,
                        onCyclePrecision = cyclePrecision,
                        headingRef = headingRef,
                        cells = cells,
                        rows = rows,
                    )
                }
            }

            // ---- Below the fold: phonetic, MARK, full readouts ----
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (gpsOff) {
                    Text(
                        "GPS is off — enable Location in your phone's settings.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                // A dial face without a heading is dimmed; say why
                if (settings.face != Face.GLANCE) {
                    val subtle = MaterialTheme.colorScheme.onSurfaceVariant
                    when {
                        !compassData.hasSensor -> Text(
                            "No compass sensor on this device — the dial turns with your direction of travel while moving.",
                            style = MaterialTheme.typography.bodySmall,
                            color = subtle,
                        )
                        compassData.hasReading && compassData.accuracy <= SensorManager.SENSOR_STATUS_ACCURACY_LOW -> Text(
                            "Compass needs calibration — move your phone in a figure-8 a few times.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        headingRef == null -> Text(
                            "Waiting for the compass — the dial is dimmed until it has a heading.",
                            style = MaterialTheme.typography.bodySmall,
                            color = subtle,
                        )
                    }
                }

                if (loc != null && parts != null && parts.square.isNotEmpty()) {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(start = 14.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "PHONETIC",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    letterSpacing = 1.2.sp,
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    Phonetic.mgrs(parts.full),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = MonoFamily,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                            IconButton(onClick = { qrOpen = true }) {
                                Icon(
                                    Icons.Outlined.QrCode2,
                                    contentDescription = "Share as QR code",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                // One-tap MARK: timestamped waypoint at the current fix
                if (onMark != null) {
                    Button(
                        onClick = {
                            onMark()
                            markedAt = now
                        },
                        enabled = loc != null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary,
                            contentColor = MaterialTheme.colorScheme.onSecondary,
                        ),
                    ) {
                        Text(
                            if (now - markedAt < 3000) "MARKED — set as Navigate target" else "MARK",
                            fontFamily = MonoFamily,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp,
                        )
                    }
                }

                InfoCard(
                    label = "LAT / LON",
                    value = loc?.let { Coordinates.formatLatLon(it.latitude, it.longitude, settings.latLonFormat) } ?: "—",
                    modifier = Modifier.fillMaxWidth(),
                )
                InfoCard(
                    label = "UTM",
                    value = utmText,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    InfoCard(
                        label = "ALTITUDE (GPS)",
                        value = elevText,
                        modifier = Modifier.weight(1f),
                    )
                    InfoCard(
                        label = "ACCURACY",
                        value = accuracyText ?: "—",
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    InfoCard(
                        label = "SPEED",
                        value = speedText,
                        modifier = Modifier.weight(1f),
                    )
                    InfoCard(
                        label = "COURSE (GPS)",
                        value = if (loc != null && loc.hasBearing() && loc.hasSpeed() && loc.speed > 0.5f) {
                            "${loc.bearing.roundToInt()}° T"
                        } else "—",
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    InfoCard(
                        label = "DTG ZULU",
                        value = dtgText,
                        modifier = Modifier.weight(1f),
                    )
                    InfoCard(
                        label = "FIX AGE",
                        value = loc?.let { fixAgeText(fixAgeSeconds(it)) } ?: "—",
                        modifier = Modifier.weight(1f),
                    )
                }
                // Fix quality the operator can act on: bars, a word, which MGRS
                // precision the error circle supports, and where the fix came from
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FixBars(quality, palette, height = 16.dp)
                        Spacer(Modifier.size(10.dp))
                        Text(
                            when {
                                gpsOff -> "GPS OFF"
                                loc == null -> "ACQUIRING"
                                else -> "${quality.word} FIX · TRUST ${quality.trustDigits}-DIGIT"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (loc == null) MaterialTheme.colorScheme.onSurfaceVariant else fixColor(quality, palette),
                            letterSpacing = 1.2.sp,
                            maxLines = 1,
                        )
                    }
                    Text(
                        when {
                            gpsOff -> "Location is off in the phone settings"
                            quality.stale -> "Last fix ${fixAgeText(quality.ageSeconds)} ago · ${fix.satellitesUsed} of ${fix.satellitesVisible} satellites"
                            quality.network -> "Network position (Wi-Fi / cell), not satellites"
                            else -> "${fix.satellitesUsed} of ${fix.satellitesVisible} satellites used"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = MonoFamily,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
                Text(
                    when {
                        gpsOff -> "The receiver is idle until Location is switched on. Pace count and map until then."
                        loc == null -> "Waiting for a position. Clear sky helps; the count is satellites the receiver is using of those it can hear."
                        quality.stale -> "This position is old — nothing new from the receiver for ${fixAgeText(quality.ageSeconds)}. Get open sky and wait for a fresh fix before trusting a grid."
                        quality.network -> "This position came from Wi-Fi or cell towers, not satellites. Read no finer than ${quality.trustDigits} digits until the GPS reports."
                        quality.bars >= 4 -> "Accuracy is the error circle around the fix. This one supports ${quality.trustDigits}-digit grids; finer digits are noise."
                        quality.bars >= 2 -> "Usable, but read no finer than ${quality.trustDigits} digits and confirm with terrain association."
                        else -> "Do not trust this fix for a grid — move to open sky, wait, and rely on pace count and map."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    if (qrOpen && loc != null) {
        QrDialog(
            title = "My position",
            payload = geoUri(loc.latitude, loc.longitude, "Position " + Coordinates.dtg(now).take(7)),
            caption = parts?.full ?: "",
            onDismiss = { qrOpen = false },
        )
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
                fontFamily = MonoFamily,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
