package app.gridfix.android.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.gridfix.android.data.AppSettings
import app.gridfix.android.data.GraphicsRepository
import app.gridfix.android.data.KIND_UNIT
import app.gridfix.android.data.SettingsRepository
import app.gridfix.android.data.TrackRepository
import app.gridfix.android.data.WaypointRepository
import app.gridfix.android.location.TrackRecorderService
import app.gridfix.android.location.LocationTracker
import app.gridfix.android.ui.screens.MapScreen
import app.gridfix.android.ui.screens.NavigateScreen
import app.gridfix.android.ui.screens.PositionScreen
import app.gridfix.android.ui.screens.SettingsScreen
import app.gridfix.android.ui.screens.WaypointsScreen
import app.gridfix.android.ui.theme.GridFixTheme
import kotlinx.coroutines.launch

private data class NavItem(val route: String, val label: String, val icon: ImageVector)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GridFixApp() {
    val context = LocalContext.current
    val repo = remember { SettingsRepository(context.applicationContext) }
    val settings by repo.settings.collectAsStateWithLifecycle(initialValue = AppSettings())
    val waypointRepo = remember { WaypointRepository(context.applicationContext) }
    val waypoints by waypointRepo.waypoints.collectAsStateWithLifecycle(initialValue = emptyList())
    val selectedId by waypointRepo.selectedId.collectAsStateWithLifecycle(initialValue = null)
    val folders by waypointRepo.folders.collectAsStateWithLifecycle(initialValue = emptyList())
    val graphicsRepo = remember { GraphicsRepository(context.applicationContext) }
    val graphics by graphicsRepo.graphics.collectAsStateWithLifecycle(initialValue = emptyList())
    val trackRepo = remember { TrackRepository(context.applicationContext) }
    val tracks by trackRepo.tracks.collectAsStateWithLifecycle(initialValue = emptyList())
    var viewedTrackId by remember { mutableStateOf<String?>(null) }
    var mapFocus by remember { mutableStateOf<Pair<Double, Double>?>(null) }

    // "Armor Brigade 1"-style automatic unit names from symbol + echelon
    val unitNameFor: (String, String) -> String = { symbol, echelon ->
        val func = NatoSymbols.functionLabel(symbol)
        val ech = if (echelon.isEmpty()) "" else " " + Echelons.label(echelon)
        val base = (func + ech).trim()
        val n = waypoints.count { it.kind == KIND_UNIT && it.name.startsWith(base) } + 1
        "$base $n"
    }

    // Waypoints in visible ("active") overlays are the ones offered for navigation
    val navigableWaypoints = if (folders.isEmpty()) waypoints else {
        val visibleNames = folders.filter { it.visible }.map { it.name }.toSet()
        waypoints.filter { it.folder in visibleNames }
    }
    val scope = rememberCoroutineScope()

    // Location permission + shared tracker, hoisted so every screen can use the fix
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
    val requestPermission = {
        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            )
        )
    }
    val tracker = remember { LocationTracker(context.applicationContext) }
    DisposableEffect(hasPermission) {
        if (hasPermission) tracker.start()
        onDispose { tracker.stop() }
    }
    val fix by tracker.fix.collectAsStateWithLifecycle()

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val view = LocalView.current
    DisposableEffect(settings.keepScreenOn) {
        view.keepScreenOn = settings.keepScreenOn
        onDispose { view.keepScreenOn = false }
    }

    val items = listOf(
        NavItem("position", "Position", Icons.Outlined.MyLocation),
        NavItem("navigate", "Navigate", Icons.Outlined.Explore),
        NavItem("map", "Map", Icons.Outlined.Map),
        NavItem("waypoints", "Waypoints", Icons.Outlined.Flag),
    )

    fun goTo(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.startDestinationId) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    GridFixTheme(nightMode = settings.nightMode) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            "GRIDFIX",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 4.sp,
                        )
                    },
                    navigationIcon = {
                        if (currentRoute == "settings") {
                            IconButton(onClick = { navController.popBackStack() }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = { scope.launch { repo.setNightMode(!settings.nightMode) } }) {
                            Icon(
                                if (settings.nightMode) Icons.Outlined.LightMode else Icons.Outlined.DarkMode,
                                contentDescription = "Toggle night mode",
                            )
                        }
                        if (currentRoute != "settings") {
                            IconButton(onClick = {
                                navController.navigate("settings") { launchSingleTop = true }
                            }) {
                                Icon(Icons.Outlined.Settings, contentDescription = "Settings")
                            }
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.primary,
                        actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        navigationIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                )
            },
            bottomBar = {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    items.forEach { item ->
                        NavigationBarItem(
                            selected = currentRoute == item.route,
                            onClick = { goTo(item.route) },
                            icon = { Icon(item.icon, contentDescription = item.label) },
                            label = { Text(item.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                indicatorColor = MaterialTheme.colorScheme.surfaceVariant,
                            ),
                        )
                    }
                }
            },
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = "position",
                modifier = Modifier.padding(innerPadding),
            ) {
                composable("position") {
                    if (!hasPermission) PermissionGate(requestPermission)
                    else PositionScreen(fix = fix, settings = settings, repo = repo)
                }
                composable("navigate") {
                    if (!hasPermission) PermissionGate(requestPermission)
                    else NavigateScreen(
                        fix = fix,
                        settings = settings,
                        waypoints = navigableWaypoints,
                        selectedId = selectedId,
                        onSelect = { id -> scope.launch { waypointRepo.select(id) } },
                    )
                }
                composable("map") {
                    MapScreen(
                        fix = fix,
                        settings = settings,
                        waypoints = waypoints,
                        folders = folders,
                        hasPermission = hasPermission,
                        onRequestPermission = requestPermission,
                        onAdd = { draft ->
                            scope.launch { waypointRepo.add(draft, System.currentTimeMillis()) }
                        },
                        onUpdate = { id, draft ->
                            scope.launch { waypointRepo.update(id, draft) }
                        },
                        onNavigateTo = { id ->
                            scope.launch { waypointRepo.select(id) }
                            goTo("navigate")
                        },
                        graphics = graphics,
                        onAddGraphic = { name, type, points, folder, affiliation ->
                            scope.launch {
                                waypointRepo.addFolder(folder)   // so the overlay eye toggle exists
                                graphicsRepo.add(name, type, points, folder, affiliation, System.currentTimeMillis())
                            }
                        },
                        onUpdateGraphic = { id, name, folder, affiliation ->
                            scope.launch {
                                waypointRepo.addFolder(folder)
                                graphicsRepo.rename(id, name, folder, affiliation)
                            }
                        },
                        onDeleteGraphic = { id -> scope.launch { graphicsRepo.delete(id) } },
                        viewedTrackId = viewedTrackId,
                        onRecordStart = {
                            scope.launch {
                                val id = trackRepo.startTrack(System.currentTimeMillis())
                                TrackRecorderService.start(context.applicationContext, id)
                            }
                        },
                        onRecordStop = { name, discard ->
                            val id = TrackRecorderService.active.value?.trackId
                            TrackRecorderService.stop(context.applicationContext)
                            if (id != null) {
                                scope.launch {
                                    kotlinx.coroutines.delay(400)
                                    if (discard) {
                                        trackRepo.discard(id)
                                    } else {
                                        trackRepo.finishTrack(id, name, System.currentTimeMillis())
                                    }
                                }
                            }
                        },
                        focusAt = mapFocus,
                        onFocusConsumed = { mapFocus = null },
                        unitNameFor = unitNameFor,
                    )
                }
                composable("waypoints") {
                    WaypointsScreen(
                        fix = fix,
                        settings = settings,
                        waypoints = waypoints,
                        folders = folders,
                        onAdd = { draft ->
                            scope.launch { waypointRepo.add(draft, System.currentTimeMillis()) }
                        },
                        onUpdate = { id, draft ->
                            scope.launch { waypointRepo.update(id, draft) }
                        },
                        onDelete = { id -> scope.launch { waypointRepo.delete(id) } },
                        onAddFolder = { name -> scope.launch { waypointRepo.addFolder(name) } },
                        onSetFolderVisible = { name, visible ->
                            scope.launch { waypointRepo.setFolderVisible(name, visible) }
                        },
                        onNavigateTo = { id ->
                            scope.launch { waypointRepo.select(id) }
                            goTo("navigate")
                        },
                        graphics = graphics,
                        onDeleteGraphic = { id -> scope.launch { graphicsRepo.delete(id) } },
                        onClearGraphics = { folder -> scope.launch { graphicsRepo.deleteFolder(folder) } },
                        tracks = tracks,
                        viewedTrackId = viewedTrackId,
                        onViewTrack = { id ->
                            viewedTrackId = id
                            if (id != null) goTo("map")
                        },
                        onDeleteTrack = { id ->
                            if (viewedTrackId == id) viewedTrackId = null
                            scope.launch { trackRepo.delete(id) }
                        },
                        onShowOnMap = { w ->
                            mapFocus = w.lat to w.lon
                            goTo("map")
                        },
                        unitNameFor = unitNameFor,
                    )
                }
                composable("settings") { SettingsScreen(repo, settings) }
            }
        }
    }
}

@Composable
private fun PermissionGate(onRequest: () -> Unit) {
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
        Button(onClick = onRequest) {
            Text("Grant location access")
        }
    }
}
