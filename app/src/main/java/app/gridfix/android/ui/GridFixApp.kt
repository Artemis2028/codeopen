package app.gridfix.android.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Settings
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.gridfix.android.data.AppSettings
import app.gridfix.android.data.SettingsRepository
import app.gridfix.android.ui.screens.PlaceholderScreen
import app.gridfix.android.ui.screens.PositionScreen
import app.gridfix.android.ui.screens.SettingsScreen
import app.gridfix.android.ui.theme.GridFixTheme
import kotlinx.coroutines.launch

private data class NavItem(val route: String, val label: String, val icon: ImageVector)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GridFixApp() {
    val context = LocalContext.current
    val repo = remember { SettingsRepository(context.applicationContext) }
    val settings by repo.settings.collectAsStateWithLifecycle(initialValue = AppSettings())
    val scope = rememberCoroutineScope()

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
                            onClick = {
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.startDestinationId) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
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
                composable("position") { PositionScreen(repo, settings) }
                composable("navigate") {
                    PlaceholderScreen(
                        icon = Icons.Outlined.Explore,
                        title = "Navigate",
                        message = "Compass navigation to your waypoints — bearing arrow, distance, and back-azimuth — arrives in Milestone 2.",
                    )
                }
                composable("map") {
                    PlaceholderScreen(
                        icon = Icons.Outlined.Map,
                        title = "Map",
                        message = "Map view with a scrolling MGRS grid overlay and offline maps arrives in Milestone 3.",
                    )
                }
                composable("waypoints") {
                    PlaceholderScreen(
                        icon = Icons.Outlined.Flag,
                        title = "Waypoints",
                        message = "Waypoint creation, groups, and GPX import/export arrive in Milestone 2.",
                    )
                }
                composable("settings") { SettingsScreen(repo, settings) }
            }
        }
    }
}
