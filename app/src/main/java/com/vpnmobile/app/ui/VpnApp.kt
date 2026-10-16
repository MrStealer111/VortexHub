package com.vpnmobile.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.vpnmobile.app.R
import com.vpnmobile.app.data.TunnelProfile
import com.vpnmobile.app.ui.screens.HomeScreen
import com.vpnmobile.app.ui.screens.ImportScreen
import com.vpnmobile.app.ui.screens.PersonScreen
import com.vpnmobile.app.ui.screens.PremiumScreen
import com.vpnmobile.app.ui.screens.QrScanScreen
import com.vpnmobile.app.ui.screens.ServersScreen
import com.vpnmobile.app.ui.screens.SettingsScreen
import com.vpnmobile.app.ui.screens.SplashScreen
import com.vpnmobile.app.ui.screens.SplitTunnelScreen
import com.vpnmobile.app.ui.screens.WarpGenerateScreen

object Routes {
    const val SPLASH = "splash"
    const val HOME = "home"
    const val SERVERS = "servers"
    const val IMPORT = "import"
    const val SETTINGS = "settings"
    const val SETTINGS_SCREEN = "settings_screen"
    const val SPLIT_TUNNEL = "split_tunnel"
    const val WARP_GEN = "warp_gen"
    const val QR_SCAN = "qr_scan"
    const val PREMIUM = "premium"
}

@Composable
fun VpnApp(
    viewModel: MainViewModel,
    needsPermission: Boolean,
    onConnect: (TunnelProfile) -> Unit,
    onRequestVpnPermission: () -> Unit,
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val snackbarHostState = remember { SnackbarHostState() }
    var showSplash by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }
    LaunchedEffect(Unit) {
        viewModel.needPremium.collect { navController.navigate(Routes.PREMIUM) }
    }

    if (showSplash) {
        SplashScreen(onSplashComplete = { showSplash = false })
    } else {
        Scaffold(
            containerColor = Color(0xFF070E1F),
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                if (currentRoute == Routes.HOME || currentRoute == Routes.SERVERS || currentRoute == Routes.SETTINGS) {
                    PillBottomBar(currentRoute = currentRoute, onNavigate = navController::navigateTo)
                }
            },
        ) { padding ->
            NavHost(
                navController = navController,
                startDestination = Routes.HOME,
                modifier = Modifier.padding(padding),
            ) {
                composable(
                Routes.HOME,
                enterTransition = { fadeIn(tween(260)) + slideInHorizontally(tween(260)) { it / 5 } },
                exitTransition = { fadeOut(tween(260)) + slideOutHorizontally(tween(260)) { -it / 5 } },
            ) {
                    HomeScreen(
                        viewModel = viewModel,
                        needsPermission = needsPermission,
                        onConnect = onConnect,
                        onRequestVpnPermission = onRequestVpnPermission,
                        onOpenServers = { navController.navigateTo(Routes.SERVERS) },
                    )
                }
                composable(
                Routes.SERVERS,
                enterTransition = { fadeIn(tween(260)) + slideInHorizontally(tween(260)) { it / 5 } },
                exitTransition = { fadeOut(tween(260)) + slideOutHorizontally(tween(260)) { -it / 5 } },
            ) {
                    ServersScreen(
                        viewModel = viewModel,
                        onConnect = onConnect,
                        onImport = { navController.navigateTo(Routes.IMPORT) },
                        onWarpGenerate = { navController.navigate(Routes.WARP_GEN) },
                        onQrScan = { navController.navigate(Routes.QR_SCAN) },
                    )
                }
                composable(
                Routes.IMPORT,
                enterTransition = { fadeIn(tween(260)) + slideInHorizontally(tween(260)) { it / 5 } },
                exitTransition = { fadeOut(tween(260)) + slideOutHorizontally(tween(260)) { -it / 5 } },
            ) {
                    ImportScreen(
                        viewModel = viewModel,
                        onBack = { navController.popBackStack() },
                        onWarpGenerate = { navController.navigate(Routes.WARP_GEN) },
                    )
                }
                composable(
                Routes.SETTINGS,
                enterTransition = { fadeIn(tween(260)) + slideInHorizontally(tween(260)) { it / 5 } },
                exitTransition = { fadeOut(tween(260)) + slideOutHorizontally(tween(260)) { -it / 5 } },
            ) {
                    PersonScreen(
                        viewModel = viewModel,
                        onPremium = { navController.navigate(Routes.PREMIUM) },
                        onSettings = { navController.navigate(Routes.SETTINGS_SCREEN) }
                    )
                }
                composable(
                Routes.SETTINGS_SCREEN,
                enterTransition = { fadeIn(tween(260)) + slideInHorizontally(tween(260)) { it / 5 } },
                exitTransition = { fadeOut(tween(260)) + slideOutHorizontally(tween(260)) { -it / 5 } },
            ) {
                    SettingsScreen(
                        viewModel = viewModel,
                        onBack = { navController.popBackStack() }
                    )
                }
                composable(
                Routes.SPLIT_TUNNEL,
                enterTransition = { fadeIn(tween(260)) + slideInHorizontally(tween(260)) { it / 5 } },
                exitTransition = { fadeOut(tween(260)) + slideOutHorizontally(tween(260)) { -it / 5 } },
            ) {
                    SplitTunnelScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
                }
                composable(
                Routes.WARP_GEN,
                enterTransition = { fadeIn(tween(260)) + slideInHorizontally(tween(260)) { it / 5 } },
                exitTransition = { fadeOut(tween(260)) + slideOutHorizontally(tween(260)) { -it / 5 } },
            ) {
                    WarpGenerateScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
                }
                composable(
                Routes.QR_SCAN,
                enterTransition = { fadeIn(tween(260)) + slideInHorizontally(tween(260)) { it / 5 } },
                exitTransition = { fadeOut(tween(260)) + slideOutHorizontally(tween(260)) { -it / 5 } },
            ) {
                    QrScanScreen(viewModel = viewModel, onBack = { navController.popBackStack() }, onScanned = { navController.popBackStack() })
                }
                composable(
                Routes.PREMIUM,
                enterTransition = { fadeIn(tween(260)) + slideInHorizontally(tween(260)) { it / 5 } },
                exitTransition = { fadeOut(tween(260)) + slideOutHorizontally(tween(260)) { -it / 5 } },
            ) {
                    PremiumScreen(onBack = { navController.popBackStack() })
                }
            }
        }
    }
}

private fun NavHostController.navigateTo(route: String) {
    navigate(route) {
        popUpTo(Routes.HOME) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
private fun PillBottomBar(currentRoute: String?, onNavigate: (String) -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth().background(Color(0xFF0A0A0A)).padding(horizontal = 20.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier.clip(RoundedCornerShape(50)).background(Color(0xFF1E1E1E)).padding(horizontal = 6.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            PillNavItem(icon = Icons.Filled.Home, label = "Home", selected = currentRoute == Routes.HOME, onClick = { onNavigate(Routes.HOME) })
            PillNavItem(icon = Icons.Filled.Language, label = "Configs", selected = currentRoute == Routes.SERVERS, onClick = { onNavigate(Routes.SERVERS) }, showArrow = true)
            PillNavItem(icon = Icons.Filled.Cloud, label = "Person", selected = currentRoute == Routes.SETTINGS, onClick = { onNavigate(Routes.SETTINGS) }, showArrow = true)
        }
    }
}

@Composable
private fun PillNavItem(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit, showArrow: Boolean = false) {
    val scale by animateFloatAsState(if (selected) 1f else 0.92f, spring(dampingRatio = 0.6f, stiffness = 400f), label = "pillScale")
    val bg = if (selected) Brush.linearGradient(listOf(Color(0xFFFF8C00), Color(0xFFFF6B00))) else Brush.linearGradient(listOf(Color.Transparent, Color.Transparent))
    val contentColor = if (selected) Color.White else Color(0xFF8EA0C2)
    Box(
        modifier = Modifier.clip(RoundedCornerShape(50)).background(bg).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick).padding(horizontal = if (selected) 18.dp else 14.dp, vertical = 10.dp).scale(scale),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, null, Modifier.size(18.dp), contentColor)
            if (selected) {
                Text(label, color = contentColor, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 0.3.sp)
                if (showArrow) Text("→", color = contentColor, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }
    }
}
