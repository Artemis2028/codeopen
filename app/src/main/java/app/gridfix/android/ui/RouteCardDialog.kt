package app.gridfix.android.ui

import android.content.Intent
import android.hardware.GeomagneticField
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.gridfix.android.coords.Coordinates
import app.gridfix.android.data.AppSettings
import app.gridfix.android.data.TacGraphic
import java.util.Locale
import kotlin.math.roundToInt

private data class Leg(
    val index: Int,
    val azimuth: String,
    val backAzimuth: String,
    val distance: String,
    val paces: Int,
    val toGrid: String,
    val distanceMeters: Float,
)

/**
 * Route card for a route graphic: per-leg azimuth (in the chosen north
 * reference and angle unit), back-azimuth, distance, and pace count, with a
 * shareable plain-text version. The classic land-nav route card, computed
 * instead of penciled.
 */
@Composable
fun RouteCardDialog(
    route: TacGraphic,
    settings: AppSettings,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current

    val refLetter = when (settings.northRef) {
        1 -> "M"
        2 -> "G"
        else -> "T"
    }

    val legs = remember(route.id, settings.northRef, settings.angleUnit, settings.units, settings.pacePer100m) {
        buildList {
            val pts = route.points
            for (i in 0 until pts.size - 1) {
                val a = pts[i]
                val b = pts[i + 1]
                val nav = Coordinates.navInfo(a.lat, a.lon, b.lat, b.lon)
                val midLat = (a.lat + b.lat) / 2.0
                val midLon = (a.lon + b.lon) / 2.0
                val declination = GeomagneticField(
                    midLat.toFloat(), midLon.toFloat(), 0f, System.currentTimeMillis()
                ).declination
                fun toRef(angleTrue: Float): Float = when (settings.northRef) {
                    1 -> (angleTrue - declination + 360f) % 360f
                    2 -> (angleTrue - Coordinates.gridConvergence(midLat, midLon).toFloat() + 360f) % 360f
                    else -> angleTrue
                }
                add(
                    Leg(
                        index = i + 1,
                        azimuth = Coordinates.formatAngle(toRef(nav.bearingTrue), settings.angleUnit),
                        backAzimuth = Coordinates.formatAngle(
                            toRef((nav.bearingTrue + 180f) % 360f), settings.angleUnit
                        ),
                        distance = Coordinates.formatDistance(nav.distanceMeters, settings.units),
                        paces = (nav.distanceMeters / 100f * settings.pacePer100m).roundToInt(),
                        toGrid = Coordinates.mgrs(b.lat, b.lon, 8)?.full ?: "—",
                        distanceMeters = nav.distanceMeters,
                    )
                )
            }
        }
    }
    val totalMeters = legs.sumOf { it.distanceMeters.toDouble() }.toFloat()
    val totalPaces = legs.sumOf { it.paces }
    val startGrid = route.points.firstOrNull()?.let {
        Coordinates.mgrs(it.lat, it.lon, 8)?.full
    } ?: "—"

    fun shareText(): String {
        val sb = StringBuilder()
        sb.append("GRIDFIX ROUTE CARD — ").append(route.name.uppercase(Locale.US)).append('\n')
        sb.append("START ").append(startGrid).append('\n')
        for (l in legs) {
            sb.append(
                String.format(
                    Locale.US,
                    "LEG %d: %s %s / back %s — %s — %d paces → %s%n",
                    l.index, l.azimuth, refLetter, l.backAzimuth, l.distance, l.paces, l.toGrid,
                )
            )
        }
        sb.append(
            String.format(
                Locale.US,
                "TOTAL %s — %d paces%n",
                Coordinates.formatDistance(totalMeters, settings.units), totalPaces,
            )
        )
        sb.append("(north ").append(refLetter)
            .append(" · pace ").append(settings.pacePer100m).append("/100m · ")
            .append(Coordinates.dtg(System.currentTimeMillis())).append(")")
        return sb.toString()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Route card — ${route.name}") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    "START  $startGrid",
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                legs.forEach { l ->
                    Column {
                        Row(Modifier.fillMaxWidth()) {
                            Text(
                                "${l.index}",
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "${l.azimuth} $refLetter   back ${l.backAzimuth}",
                                    fontFamily = FontFamily.Monospace,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Text(
                                    "${l.distance}   ${l.paces} paces   → ${l.toGrid}",
                                    fontFamily = FontFamily.Monospace,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Spacer(Modifier.height(2.dp))
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                Text(
                    "TOTAL  " + Coordinates.formatDistance(totalMeters, settings.units) +
                        "   $totalPaces paces",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "North: $refLetter · pace ${settings.pacePer100m}/100 m",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, shareText())
                }
                runCatching {
                    context.startActivity(Intent.createChooser(send, "Share route card"))
                }
            }) { Text("Share") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = {
                    // Printable strip map: overview sketch + full leg table
                    val pdf = StripMapPdf.build(context, route, settings)
                    if (pdf != null) {
                        runCatching {
                            val uri = androidx.core.content.FileProvider.getUriForFile(
                                context,
                                "app.gridfix.android.fileprovider",
                                pdf,
                            )
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "application/pdf"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(send, "Share strip map PDF"))
                        }
                    }
                }) { Text("PDF") }
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        },
    )
}
