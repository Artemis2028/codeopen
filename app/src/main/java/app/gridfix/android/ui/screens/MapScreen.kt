package app.gridfix.android.ui.screens

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.hardware.GeomagneticField
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.outlined.AddLocationAlt
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.GridOn
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.gridfix.android.coords.Coordinates
import app.gridfix.android.data.AppSettings
import app.gridfix.android.data.FolderInfo
import app.gridfix.android.data.GeoVertex
import app.gridfix.android.data.GraphicTypes
import app.gridfix.android.data.MapPrefs
import app.gridfix.android.data.MapPrefsData
import app.gridfix.android.data.TacGraphic
import app.gridfix.android.data.Waypoint
import app.gridfix.android.data.WaypointDraft
import app.gridfix.android.location.FixData
import app.gridfix.android.map.ControlMeasuresOverlay
import app.gridfix.android.map.MapSetup
import app.gridfix.android.map.MgrsGridOverlay
import app.gridfix.android.ui.Affiliations
import app.gridfix.android.ui.WaypointDialog
import app.gridfix.android.ui.WaypointMarker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.cachemanager.CacheManager
import org.osmdroid.tileprovider.modules.OfflineTileProvider
import org.osmdroid.tileprovider.tilesource.FileBasedTileSource
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.tileprovider.util.SimpleRegisterReceiver
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import java.io.File
import kotlin.math.hypot

/** Mutable references shared between the AndroidView factory callbacks and Compose. */
private class MapHolder {
    var map: MapView? = null
    var grid: MgrsGridOverlay? = null
    var cm: ControlMeasuresOverlay? = null
    var appliedLayer = ""
    var appliedNight: Boolean? = null
    var appliedGrid: Boolean? = null
}

@Composable
fun MapScreen(
    fix: FixData,
    settings: AppSettings,
    waypoints: List<Waypoint>,
    folders: List<FolderInfo>,
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
    onAdd: (WaypointDraft) -> Unit,
    onUpdate: (id: String, draft: WaypointDraft) -> Unit,
    onNavigateTo: (String) -> Unit,
    graphics: List<TacGraphic>,
    onAddGraphic: (name: String, type: String, points: List<GeoVertex>, folder: String, affiliation: String) -> Unit,
    onUpdateGraphic: (id: String, name: String, folder: String, affiliation: String) -> Unit,
    onDeleteGraphic: (String) -> Unit,
) {
    val context = LocalContext.current
    remember { MapSetup.init(context.applicationContext); true }
    val mapPrefs = remember { MapPrefs(context.applicationContext) }
    val prefsOrNull by mapPrefs.prefs.collectAsStateWithLifecycle(initialValue = null as MapPrefsData?)
    val p = prefsOrNull ?: return

    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val holder = remember { MapHolder() }

    // Waypoints and graphics in visible folders are drawn on the map
    val visibleWaypoints = remember(waypoints, folders) {
        if (folders.isEmpty()) waypoints else {
            val visible = folders.filter { it.visible }.map { it.name }.toSet()
            waypoints.filter { it.folder in visible }
        }
    }
    val visibleGraphics = remember(graphics, folders) {
        if (folders.isEmpty()) graphics else {
            val visible = folders.filter { it.visible }.map { it.name }.toSet()
            val known = folders.map { it.name }.toSet()
            graphics.filter { it.folder in visible || it.folder !in known }
        }
    }

    var cameraTick by remember { mutableIntStateOf(0) }
    var following by remember { mutableStateOf(false) }
    var rulerAnchor by remember { mutableStateOf<GeoPoint?>(null) }
    var drawType by remember { mutableStateOf<String?>(null) }
    var drawAffiliation by remember { mutableStateOf("none") }
    var drawPoints by remember { mutableStateOf<List<GeoVertex>>(emptyList()) }
    var drawPickerOpen by remember { mutableStateOf(false) }
    var drawNameOpen by remember { mutableStateOf(false) }
    var editingGraphic by remember { mutableStateOf<TacGraphic?>(null) }
    var newWpAt by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var editingWp by remember { mutableStateOf<Waypoint?>(null) }
    var infoWp by remember { mutableStateOf<Waypoint?>(null) }
    var layersOpen by remember { mutableStateOf(false) }
    var downloadOpen by remember { mutableStateOf(false) }
    var gridInterval by remember { mutableStateOf("") }
    var downloadStatus by remember { mutableStateOf<String?>(null) }
    var importMessage by remember { mutableStateOf<String?>(null) }
    var readoutHeightPx by remember { mutableIntStateOf(0) }
    var mbtilesFiles by remember { mutableStateOf(listMbtiles(context)) }

    val offlineName = if (p.baseLayer.startsWith("mbtiles:")) p.baseLayer.removePrefix("mbtiles:") else null
    val offlineFile = offlineName?.let { File(MapSetup.mbtilesDir(context), it) }?.takeIf { it.exists() }
    val layer = MapSetup.layerFor(p.baseLayer)

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val result = importMbtiles(context, uri)
                mbtilesFiles = listMbtiles(context)
                importMessage = result.second
                if (result.first != null) {
                    mapPrefs.setBaseLayer("mbtiles:${result.first}")
                }
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        key(offlineFile?.absolutePath ?: "online") {
            var mapView by remember { mutableStateOf<MapView?>(null) }

            androidx.compose.ui.viewinterop.AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    val map = MapView(ctx)
                    map.setMultiTouchControls(true)
                    map.isTilesScaledToDpi = true
                    map.zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
                    map.setMinZoomLevel(3.0)
                    map.setMaxZoomLevel(21.5)
                    if (offlineFile != null) {
                        try {
                            val provider = OfflineTileProvider(SimpleRegisterReceiver(ctx), arrayOf(offlineFile))
                            map.tileProvider = provider
                            val sourceName = provider.archives.firstOrNull()?.tileSources?.firstOrNull()
                            if (sourceName != null) {
                                map.setTileSource(FileBasedTileSource.getSource(sourceName))
                            } else {
                                map.setTileSource(TileSourceFactory.DEFAULT_TILE_SOURCE)
                            }
                            map.setUseDataConnection(false)
                        } catch (e: Exception) {
                            map.setTileSource(MapSetup.layerFor("topo").source)
                        }
                    } else {
                        map.setTileSource(MapSetup.layerFor(p.baseLayer).source)
                        holder.appliedLayer = p.baseLayer
                    }
                    val grid = MgrsGridOverlay(ctx.resources.displayMetrics.density)
                    grid.onIntervalLabel = { label -> gridInterval = label }
                    holder.grid = grid
                    map.overlays.add(
                        MapEventsOverlay(object : MapEventsReceiver {
                            override fun singleTapConfirmedHelper(gp: GeoPoint?): Boolean {
                                if (gp == null) return false
                                if (drawType != null) {
                                    drawPoints = drawPoints + GeoVertex(gp.latitude, gp.longitude)
                                    holder.map?.invalidate()
                                    return true
                                }
                                if (rulerAnchor != null) {
                                    rulerAnchor = gp
                                    return true
                                }
                                // Tap near a control-measure graphic opens its editor
                                val m = holder.map
                                val cmo = holder.cm
                                if (m != null && cmo != null) {
                                    val px = android.graphics.Point()
                                    m.projection.toPixels(gp, px)
                                    val thresh = 26f * m.context.resources.displayMetrics.density
                                    var best: TacGraphic? = null
                                    var bestD = thresh
                                    for (g in cmo.graphics) {
                                        val d = cmo.distanceToGraphic(
                                            m.projection, g, px.x.toFloat(), px.y.toFloat()
                                        )
                                        if (d < bestD) {
                                            bestD = d
                                            best = g
                                        }
                                    }
                                    if (best != null) {
                                        editingGraphic = best
                                        return true
                                    }
                                }
                                return false
                            }

                            override fun longPressHelper(gp: GeoPoint?): Boolean {
                                if (gp != null) {
                                    if (drawType != null) {
                                        drawPoints = drawPoints + GeoVertex(gp.latitude, gp.longitude)
                                        holder.map?.invalidate()
                                    } else {
                                        newWpAt = gp.latitude to gp.longitude
                                    }
                                    return true
                                }
                                return false
                            }
                        })
                    )
                    map.overlays.add(grid)
                    val cm = ControlMeasuresOverlay(ctx.resources.displayMetrics.density)
                    holder.cm = cm
                    map.overlays.add(cm)
                    map.addMapListener(object : MapListener {
                        override fun onScroll(event: ScrollEvent?): Boolean {
                            cameraTick++
                            return false
                        }

                        override fun onZoom(event: ZoomEvent?): Boolean {
                            cameraTick++
                            return false
                        }
                    })
                    map.setOnTouchListener { v, ev ->
                        if (ev.actionMasked == android.view.MotionEvent.ACTION_DOWN) {
                            following = false
                            v.performClick()
                        }
                        false
                    }
                    map.controller.setZoom(p.lastZoom)
                    map.controller.setCenter(GeoPoint(p.lastLat, p.lastLon))
                    holder.map = map
                    mapView = map
                    cameraTick++   // wake up projection-dependent composables
                    map
                },
                update = { map ->
                    if (offlineFile == null && holder.appliedLayer != p.baseLayer) {
                        map.setTileSource(MapSetup.layerFor(p.baseLayer).source)
                        holder.appliedLayer = p.baseLayer
                    }
                    if (holder.appliedNight != settings.nightMode) {
                        holder.appliedNight = settings.nightMode
                        map.overlayManager.tilesOverlay.setColorFilter(
                            if (settings.nightMode) MapSetup.nightTileFilter else null
                        )
                        map.invalidate()
                    }
                    holder.grid?.let { g ->
                        g.nightMode = settings.nightMode
                        g.lightLines = !settings.nightMode && p.baseLayer == "sat" && offlineFile == null
                        g.attribution = if (offlineFile != null) "MBTiles: ${offlineFile.name}" else layer.attribution
                        g.bottomInsetPx = readoutHeightPx.toFloat()
                        if (holder.appliedGrid != p.gridEnabled) {
                            holder.appliedGrid = p.gridEnabled
                            g.gridEnabled = p.gridEnabled
                            map.invalidate()
                        }
                    }
                    holder.cm?.let { c ->
                        c.graphics = visibleGraphics
                        c.selectedId = editingGraphic?.id
                        c.nightMode = settings.nightMode
                        c.lightLines = !settings.nightMode && p.baseLayer == "sat" && offlineFile == null
                        c.draftActive = drawType != null
                        c.draftType = drawType ?: "phase_line"
                        c.draftAffiliation = drawAffiliation
                        c.draftPoints = drawPoints
                        map.invalidate()
                    }
                },
            )

            // Map lifecycle: resume/pause with the app, detach when leaving
            val lifecycleOwner = LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    when (event) {
                        Lifecycle.Event.ON_RESUME -> holder.map?.onResume()
                        Lifecycle.Event.ON_PAUSE -> {
                            holder.map?.let { m ->
                                scope.launch {
                                    mapPrefs.setCamera(
                                        m.mapCenter.latitude, m.mapCenter.longitude, m.zoomLevelDouble
                                    )
                                }
                            }
                            holder.map?.onPause()
                        }
                        else -> {}
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                    holder.map?.onDetach()
                    holder.map = null
                    holder.grid = null
                    holder.cm = null
                    holder.appliedLayer = ""
                    holder.appliedNight = null
                    holder.appliedGrid = null
                }
            }

            // Persist the camera shortly after movement stops
            LaunchedEffect(cameraTick) {
                delay(1500)
                mapView?.let { m ->
                    mapPrefs.setCamera(m.mapCenter.latitude, m.mapCenter.longitude, m.zoomLevelDouble)
                }
            }

            // Follow-me: recenter on every fix while enabled (touch cancels)
            LaunchedEffect(following, fix.location?.latitude, fix.location?.longitude) {
                val loc = fix.location
                if (following && loc != null) {
                    mapView?.controller?.animateTo(GeoPoint(loc.latitude, loc.longitude))
                }
            }

            // ---- Compose overlays positioned via the map projection ----
            @Suppress("UNUSED_EXPRESSION")
            cameraTick
            val map = mapView
            if (map != null) {
                val proj = map.projection
                val pxPoint = android.graphics.Point()
                val markerDp = 32.dp
                val markerPx = with(density) { markerDp.roundToPx() }
                val showNames = map.zoomLevelDouble >= 12.0

                // Own position + accuracy
                fix.location?.let { loc ->
                    val own = GeoPoint(loc.latitude, loc.longitude)
                    proj.toPixels(own, pxPoint)
                    val ox = pxPoint.x
                    val oy = pxPoint.y
                    if (ox in -400..(map.width + 400) && oy in -400..(map.height + 400)) {
                        val east = own.destinationPoint(1000.0, 90.0)
                        proj.toPixels(east, pxPoint)
                        val pxPerMeter = hypot(
                            (pxPoint.x - ox).toDouble(), (pxPoint.y - oy).toDouble()
                        ).toFloat() / 1000f
                        val accPx = (loc.accuracy * pxPerMeter).coerceAtMost(600f)
                        val primary = MaterialTheme.colorScheme.primary
                        val halo = if (settings.nightMode) androidx.compose.ui.graphics.Color.Black
                        else androidx.compose.ui.graphics.Color.White
                        Canvas(Modifier.fillMaxSize()) {
                            if (accPx > 8f) {
                                drawCircle(primary.copy(alpha = 0.10f), radius = accPx, center = Offset(ox.toFloat(), oy.toFloat()))
                                drawCircle(primary.copy(alpha = 0.35f), radius = accPx, center = Offset(ox.toFloat(), oy.toFloat()), style = Stroke(1.5.dp.toPx()))
                            }
                            drawCircle(halo, radius = 8.5.dp.toPx(), center = Offset(ox.toFloat(), oy.toFloat()))
                            drawCircle(primary, radius = 6.dp.toPx(), center = Offset(ox.toFloat(), oy.toFloat()))
                        }
                    }
                }

                // Ruler line: anchor -> crosshair
                rulerAnchor?.let { anchor ->
                    proj.toPixels(anchor, pxPoint)
                    val ax = pxPoint.x.toFloat()
                    val ay = pxPoint.y.toFloat()
                    val secondary = MaterialTheme.colorScheme.secondary
                    Canvas(Modifier.fillMaxSize()) {
                        val cx = size.width / 2f
                        val cy = size.height / 2f
                        drawLine(secondary, Offset(ax, ay), Offset(cx, cy), strokeWidth = 2.5.dp.toPx())
                        drawCircle(secondary, radius = 5.dp.toPx(), center = Offset(ax, ay))
                        drawCircle(secondary, radius = 3.dp.toPx(), center = Offset(ax, ay), style = Stroke(1.5.dp.toPx()))
                    }
                }

                // Waypoints
                visibleWaypoints.forEach { w ->
                    proj.toPixels(GeoPoint(w.lat, w.lon), pxPoint)
                    val wx = pxPoint.x
                    val wy = pxPoint.y
                    if (wx in -markerPx..(map.width + markerPx) && wy in -markerPx..(map.height + markerPx)) {
                        Column(
                            modifier = Modifier
                                .offset { IntOffset(wx - markerPx / 2, wy - markerPx / 2) }
                                .wrapContentSize(unbounded = true),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(
                                Modifier
                                    .size(markerDp)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                    ) { infoWp = w }
                            ) {
                                WaypointMarker(
                                    symbol = w.symbol,
                                    affiliation = w.affiliation,
                                    size = markerDp,
                                    echelon = w.echelon,
                                    night = settings.nightMode,
                                )
                            }
                            if (showNames) {
                                Surface(
                                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f),
                                    shape = MaterialTheme.shapes.extraSmall,
                                ) {
                                    Text(
                                        if (w.designation.isEmpty()) w.name else "${w.name} · ${w.designation}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = FontFamily.Monospace,
                                        modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Crosshair
        val crossColor = MaterialTheme.colorScheme.primary
        Canvas(Modifier.align(Alignment.Center).size(44.dp)) {
            val c = size.width / 2f
            val gap = 6.dp.toPx()
            val arm = 16.dp.toPx()
            val stroke = 2.dp.toPx()
            listOf(
                Offset(c - gap - arm, c) to Offset(c - gap, c),
                Offset(c + gap, c) to Offset(c + gap + arm, c),
                Offset(c, c - gap - arm) to Offset(c, c - gap),
                Offset(c, c + gap) to Offset(c, c + gap + arm),
            ).forEach { (a, b) ->
                drawLine(crossColor, a, b, strokeWidth = stroke)
            }
            drawCircle(crossColor, radius = 2.dp.toPx(), center = Offset(c, c))
        }

        // Right-side buttons
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            MapButton(Icons.Outlined.Layers, "Layers", false) { layersOpen = true }
            MapButton(Icons.Outlined.MyLocation, "My location", following) {
                if (!hasPermission) {
                    onRequestPermission()
                } else {
                    following = true
                    fix.location?.let { loc ->
                        holder.map?.controller?.animateTo(GeoPoint(loc.latitude, loc.longitude))
                    }
                }
            }
            MapButton(Icons.Outlined.Straighten, "Ruler", rulerAnchor != null) {
                rulerAnchor = if (rulerAnchor == null) {
                    holder.map?.mapCenter?.let { GeoPoint(it.latitude, it.longitude) }
                } else null
            }
            MapButton(Icons.Outlined.Timeline, "Draw graphic", drawType != null) {
                if (drawType == null) drawPickerOpen = true
            }
            MapButton(Icons.Outlined.AddLocationAlt, "Waypoint at crosshair", false) {
                holder.map?.mapCenter?.let { c -> newWpAt = c.latitude to c.longitude }
            }
        }

        // Status chips (download progress / import result / no-permission hint)
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            downloadStatus?.let { status ->
                StatusChip(status) { downloadStatus = null }
            }
            importMessage?.let { msg ->
                StatusChip(msg) { importMessage = null }
            }
            if (!hasPermission) {
                StatusChip("Location off — tap to enable") { onRequestPermission() }
            }
        }

        // Bottom stack: draw-mode action bar (when drawing) above the crosshair readout
        @Suppress("UNUSED_EXPRESSION")
        cameraTick
        val center = holder.map?.mapCenter
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .onSizeChanged { readoutHeightPx = it.height },
        ) {
        drawType?.let { dt ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f),
            ) {
                Row(
                    Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "${GraphicTypes.label(dt)} · ${drawPoints.size} pts",
                        style = MaterialTheme.typography.labelMedium,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                    )
                    TextButton(onClick = {
                        holder.map?.mapCenter?.let { c ->
                            drawPoints = drawPoints + GeoVertex(c.latitude, c.longitude)
                            holder.map?.invalidate()
                        }
                    }) { Text("+Point") }
                    TextButton(
                        enabled = drawPoints.isNotEmpty(),
                        onClick = {
                            drawPoints = drawPoints.dropLast(1)
                            holder.map?.invalidate()
                        },
                    ) { Text("Undo") }
                    TextButton(
                        enabled = drawPoints.size >= GraphicTypes.minPoints(dt),
                        onClick = { drawNameOpen = true },
                    ) { Text("Done") }
                    TextButton(onClick = {
                        drawType = null
                        drawPoints = emptyList()
                        holder.map?.invalidate()
                    }) { Text("✕") }
                }
            }
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.93f),
        ) {
            Column(
                Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val parts = center?.let { Coordinates.mgrs(it.latitude, it.longitude, settings.mgrsDigits) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (parts == null) "—"
                        else if (parts.easting.isEmpty()) parts.full
                        else "${parts.gzd} ${parts.square} ${parts.easting} ${parts.northing}",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                    )
                    if (gridInterval.isNotEmpty()) {
                        Spacer(Modifier.width(10.dp))
                        Text(
                            gridInterval,
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(2.dp))
                val loc = fix.location
                val declination = remember(
                    loc?.latitude?.let { (it * 10).toInt() },
                    loc?.longitude?.let { (it * 10).toInt() },
                ) {
                    if (loc == null) 0f else GeomagneticField(
                        loc.latitude.toFloat(), loc.longitude.toFloat(),
                        if (loc.hasAltitude()) loc.altitude.toFloat() else 0f, loc.time,
                    ).declination
                }
                fun toRef(angleTrue: Float): Float = when (settings.northRef) {
                    1 -> (angleTrue - declination + 360f) % 360f
                    2 -> if (center == null) angleTrue else
                        (angleTrue - Coordinates.gridConvergence(center.latitude, center.longitude).toFloat() + 360f) % 360f
                    else -> angleTrue
                }
                val refLetter = when (settings.northRef) {
                    1 -> "M"
                    2 -> "G"
                    else -> "T"
                }
                val anchor = rulerAnchor
                val line2 = when {
                    anchor != null && center != null -> {
                        val nav = Coordinates.navInfo(anchor.latitude, anchor.longitude, center.latitude, center.longitude)
                        "RULER  " + Coordinates.formatDistance(nav.distanceMeters, settings.units) +
                            "  " + Coordinates.formatAngle(toRef(nav.bearingTrue), settings.angleUnit) + " " + refLetter +
                            "  back " + Coordinates.formatAngle(toRef((nav.bearingTrue + 180f) % 360f), settings.angleUnit)
                    }
                    loc != null && center != null -> {
                        val nav = Coordinates.navInfo(loc.latitude, loc.longitude, center.latitude, center.longitude)
                        "ME → CROSSHAIR  " + Coordinates.formatDistance(nav.distanceMeters, settings.units) +
                            "  " + Coordinates.formatAngle(toRef(nav.bearingTrue), settings.angleUnit) + " " + refLetter
                    }
                    else -> "long-press the map to drop a waypoint"
                }
                Text(
                    line2,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = if (anchor != null) MaterialTheme.colorScheme.secondary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
        }
    }

    // ---- Dialogs ----

    if (layersOpen) {
        AlertDialog(
            onDismissRequest = { layersOpen = false },
            title = { Text("Map layers") },
            text = {
                Column(
                    Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    MapSetup.baseLayers.forEach { bl ->
                        LayerRow(
                            label = bl.label,
                            selected = p.baseLayer == bl.key && offlineFile == null,
                        ) {
                            scope.launch { mapPrefs.setBaseLayer(bl.key) }
                            layersOpen = false
                        }
                    }
                    mbtilesFiles.forEach { name ->
                        LayerRow(
                            label = name,
                            selected = offlineName == name,
                        ) {
                            scope.launch { mapPrefs.setBaseLayer("mbtiles:$name") }
                            layersOpen = false
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Outlined.GridOn,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text("MGRS grid", Modifier.weight(1f))
                        Switch(
                            checked = p.gridEnabled,
                            onCheckedChange = { v -> scope.launch { mapPrefs.setGridEnabled(v) } },
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    TextButton(onClick = { importLauncher.launch(arrayOf("*/*")) }) {
                        Text("Import MBTiles file…")
                    }
                    if (offlineFile == null) {
                        TextButton(onClick = { layersOpen = false; downloadOpen = true }) {
                            Icon(
                                Icons.Outlined.Download,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("Download visible area for offline")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { layersOpen = false }) { Text("Close") }
            },
        )
    }

    if (downloadOpen) {
        val map = holder.map
        if (map == null) {
            downloadOpen = false
        } else {
            val zMin = map.zoomLevelDouble.toInt().coerceAtLeast(3)
            val zMax = (zMin + 4).coerceAtMost(layer.maxDownloadZoom).coerceAtLeast(zMin)
            val bbox = map.boundingBox
            val tiles = remember(zMin, zMax) {
                runCatching { CacheManager(map).possibleTilesInArea(bbox, zMin, zMax) }.getOrDefault(0)
            }
            val tooBig = tiles > 12000
            AlertDialog(
                onDismissRequest = { downloadOpen = false },
                title = { Text("Download this area") },
                text = {
                    Text(
                        if (tooBig) {
                            "The current view needs about $tiles tiles at zoom $zMin–$zMax, which is too much for one download. Zoom in and try again."
                        } else {
                            "Save the visible area for offline use at zoom $zMin–$zMax — about $tiles tiles (≈${(tiles * 20) / 1024} MB) from the ${layer.label} basemap. Cached tiles are also kept automatically as you browse."
                        }
                    )
                },
                confirmButton = {
                    TextButton(
                        enabled = !tooBig && tiles > 0,
                        onClick = {
                            downloadOpen = false
                            downloadStatus = "Starting download…"
                            try {
                                CacheManager(map).downloadAreaAsyncNoUI(
                                    context, bbox, zMin, zMax,
                                    object : CacheManager.CacheManagerCallback {
                                        override fun onTaskComplete() {
                                            downloadStatus = "Offline area saved"
                                        }

                                        override fun onTaskFailed(errors: Int) {
                                            downloadStatus = "Download done, $errors tiles failed"
                                        }

                                        override fun updateProgress(
                                            progress: Int,
                                            currentZoomLevel: Int,
                                            zoomMin: Int,
                                            zoomMax: Int,
                                        ) {
                                            downloadStatus = "Downloading… $progress / $tiles tiles"
                                        }

                                        override fun downloadStarted() {
                                            downloadStatus = "Downloading…"
                                        }

                                        override fun setPossibleTilesInArea(total: Int) {}
                                    },
                                )
                            } catch (e: Exception) {
                                downloadStatus = "Download failed to start"
                            }
                        },
                    ) { Text("Download") }
                },
                dismissButton = {
                    TextButton(onClick = { downloadOpen = false }) { Text("Cancel") }
                },
            )
        }
    }

    if (drawPickerOpen) {
        AlertDialog(
            onDismissRequest = { drawPickerOpen = false },
            title = { Text("Draw graphic") },
            text = {
                Column(
                    Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    GraphicTypes.all.forEach { (key, label, _) ->
                        Text(
                            label,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    drawType = key
                                    drawPoints = emptyList()
                                    drawPickerOpen = false
                                }
                                .padding(vertical = 8.dp),
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text("Color", style = MaterialTheme.typography.labelLarge)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Affiliations.all.forEach { key ->
                            FilterChip(
                                selected = key == drawAffiliation,
                                onClick = { drawAffiliation = key },
                                label = { Text(Affiliations.label(key)) },
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Then tap the map (or +Point at the crosshair) to place vertices, and Done to save.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { drawPickerOpen = false }) { Text("Cancel") }
            },
        )
    }

    if (drawNameOpen) {
        val dt = drawType
        if (dt == null) {
            drawNameOpen = false
        } else {
            var gName by remember(dt) { mutableStateOf("") }
            var gFolder by remember(dt) { mutableStateOf("Graphics") }
            var gAff by remember(dt) { mutableStateOf(drawAffiliation) }
            AlertDialog(
                onDismissRequest = { drawNameOpen = false },
                title = { Text("Save ${GraphicTypes.label(dt).lowercase()}") },
                text = {
                    Column(
                        Modifier.verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        OutlinedTextField(
                            value = gName,
                            onValueChange = { gName = it.take(20) },
                            label = { Text("Name / designation") },
                            placeholder = { Text(if (dt == "phase_line") "e.g. BLUE" else "e.g. BRAVO") },
                            singleLine = true,
                        )
                        Text("Color", style = MaterialTheme.typography.labelLarge)
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Affiliations.all.forEach { key ->
                                FilterChip(
                                    selected = key == gAff,
                                    onClick = { gAff = key },
                                    label = { Text(Affiliations.label(key)) },
                                )
                            }
                        }
                        Text("Folder", style = MaterialTheme.typography.labelLarge)
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            (folders.map { it.name } + "Graphics").distinct().forEach { f ->
                                FilterChip(
                                    selected = f == gFolder,
                                    onClick = { gFolder = f },
                                    label = { Text(f) },
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        val finalName = gName.trim().ifBlank { "${graphics.size + 1}" }
                        onAddGraphic(finalName, dt, drawPoints, gFolder, gAff)
                        drawNameOpen = false
                        drawType = null
                        drawPoints = emptyList()
                        holder.map?.invalidate()
                    }) { Text("Save") }
                },
                dismissButton = {
                    TextButton(onClick = { drawNameOpen = false }) { Text("Keep drawing") }
                },
            )
        }
    }

    editingGraphic?.let { g ->
        var gName by remember(g.id) { mutableStateOf(g.name) }
        var gFolder by remember(g.id) { mutableStateOf(g.folder) }
        var gAff by remember(g.id) { mutableStateOf(g.affiliation) }
        AlertDialog(
            onDismissRequest = { editingGraphic = null },
            title = { Text(GraphicTypes.label(g.type)) },
            text = {
                Column(
                    Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedTextField(
                        value = gName,
                        onValueChange = { gName = it.take(20) },
                        label = { Text("Name / designation") },
                        singleLine = true,
                    )
                    Text("Color", style = MaterialTheme.typography.labelLarge)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Affiliations.all.forEach { key ->
                            FilterChip(
                                selected = key == gAff,
                                onClick = { gAff = key },
                                label = { Text(Affiliations.label(key)) },
                            )
                        }
                    }
                    Text("Folder", style = MaterialTheme.typography.labelLarge)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        (folders.map { it.name } + g.folder).distinct().forEach { f ->
                            FilterChip(
                                selected = f == gFolder,
                                onClick = { gFolder = f },
                                label = { Text(f) },
                            )
                        }
                    }
                    Text(
                        "${g.points.size} vertices",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onUpdateGraphic(g.id, gName.trim().ifBlank { g.name }, gFolder, gAff)
                    editingGraphic = null
                }) { Text("Save") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        onDeleteGraphic(g.id)
                        editingGraphic = null
                    }) { Text("Delete") }
                    TextButton(onClick = { editingGraphic = null }) { Text("Close") }
                }
            },
        )
    }

    infoWp?.let { w ->
        val loc = fix.location
        AlertDialog(
            onDismissRequest = { infoWp = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    WaypointMarker(symbol = w.symbol, affiliation = w.affiliation, size = 30.dp, echelon = w.echelon, night = settings.nightMode)
                    Spacer(Modifier.width(10.dp))
                    Text(w.name)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        Coordinates.mgrs(w.lat, w.lon, 10)?.full ?: "",
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (w.designation.isNotEmpty()) {
                        Text(
                            w.designation,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        "Folder: ${w.folder}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (loc != null) {
                        val nav = Coordinates.navInfo(loc.latitude, loc.longitude, w.lat, w.lon)
                        Text(
                            Coordinates.formatDistance(nav.distanceMeters, settings.units) +
                                "  " + Coordinates.formatAngle(nav.bearingTrue, settings.angleUnit) + " T from you",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    infoWp = null
                    onNavigateTo(w.id)
                }) { Text("Navigate") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { editingWp = w; infoWp = null }) { Text("Edit") }
                    TextButton(onClick = { infoWp = null }) { Text("Close") }
                }
            },
        )
    }

    newWpAt?.let { (lat, lon) ->
        WaypointDialog(
            initial = null,
            presetLat = lat,
            presetLon = lon,
            presetLabel = "Map position",
            folderNames = folders.map { it.name },
            defaultName = "WP " + (waypoints.size + 1),
            onConfirm = { draft ->
                onAdd(draft)
                newWpAt = null
            },
            onDismiss = { newWpAt = null },
            night = settings.nightMode,
        )
    }

    editingWp?.let { w ->
        WaypointDialog(
            initial = w,
            presetLat = null,
            presetLon = null,
            presetLabel = "Map position",
            folderNames = folders.map { it.name },
            defaultName = w.name,
            onConfirm = { draft ->
                onUpdate(w.id, draft)
                editingWp = null
            },
            onDismiss = { editingWp = null },
            night = settings.nightMode,
        )
    }

    // Auto-clear finished download status
    LaunchedEffect(downloadStatus) {
        val s = downloadStatus
        if (s != null && (s.startsWith("Offline area") || s.startsWith("Download done") || s.startsWith("Download failed"))) {
            delay(5000)
            if (downloadStatus == s) downloadStatus = null
        }
    }
}

@Composable
private fun MapButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        shadowElevation = 3.dp,
    ) {
        IconButton(onClick = onClick) {
            Icon(
                icon,
                contentDescription = description,
                tint = if (active) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StatusChip(text: String, onClick: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f),
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun LayerRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun listMbtiles(context: Context): List<String> =
    MapSetup.mbtilesDir(context).listFiles()
        ?.filter { it.isFile && it.name.endsWith(".mbtiles", ignoreCase = true) }
        ?.map { it.name }
        ?.sorted()
        ?: emptyList()

/**
 * Copy the picked document into the app's MBTiles folder and sanity-check it.
 * Returns (importedFileName or null, user message).
 */
private suspend fun importMbtiles(context: Context, uri: Uri): Pair<String?, String> =
    withContext(Dispatchers.IO) {
        try {
            var display = "imported.mbtiles"
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && cursor.moveToFirst()) {
                    cursor.getString(idx)?.let { display = it }
                }
            }
            var name = display.replace(Regex("[^A-Za-z0-9 ._-]"), "_").trim()
            if (!name.endsWith(".mbtiles", ignoreCase = true)) name += ".mbtiles"
            val dir = MapSetup.mbtilesDir(context)
            var target = File(dir, name)
            var counter = 2
            while (target.exists()) {
                target = File(dir, name.removeSuffix(".mbtiles") + "-$counter.mbtiles")
                counter++
            }
            val input = context.contentResolver.openInputStream(uri)
                ?: return@withContext null to "Couldn't open that file"
            input.use { stream ->
                target.outputStream().use { output -> stream.copyTo(output) }
            }
            // Sanity check: must be SQLite with tiles, and raster (not vector) format
            val db = SQLiteDatabase.openDatabase(target.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
            var format = ""
            var hasTiles = false
            db.use { d ->
                d.rawQuery(
                    "SELECT name FROM sqlite_master WHERE type IN ('table','view') AND name='tiles'",
                    null,
                ).use { c -> hasTiles = c.moveToFirst() }
                runCatching {
                    d.rawQuery("SELECT value FROM metadata WHERE name='format'", null).use { c ->
                        if (c.moveToFirst()) format = c.getString(0) ?: ""
                    }
                }
            }
            if (!hasTiles) {
                target.delete()
                return@withContext null to "Not a usable MBTiles file (no tiles table)"
            }
            if (format.equals("pbf", ignoreCase = true)) {
                target.delete()
                return@withContext null to "Vector MBTiles aren't supported — use raster (png/jpg) tiles"
            }
            target.name to "Imported ${target.name}"
        } catch (e: Exception) {
            null to "Import failed: ${e.message ?: "unknown error"}"
        }
    }
