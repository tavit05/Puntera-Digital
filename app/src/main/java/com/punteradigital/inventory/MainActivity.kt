package com.punteradigital.inventory

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.outlined.Assignment
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.punteradigital.inventory.domain.model.Origin
import com.punteradigital.inventory.presentation.auth.LoginScreen
import com.punteradigital.inventory.presentation.dashboard.DashboardScreen
import com.punteradigital.inventory.presentation.dispatch.DispatchListScreen
import com.punteradigital.inventory.presentation.inbound.EntryScreen
import com.punteradigital.inventory.presentation.muestras.MuestrasScreen
import com.punteradigital.inventory.presentation.refill.RefillMasterBoxScreen
import com.punteradigital.inventory.presentation.scanner.UnifiedScannerScreen
import com.punteradigital.inventory.presentation.home.HomeScreen
import com.punteradigital.inventory.presentation.settings.PrinterConfigScreen
import com.punteradigital.inventory.presentation.settings.SettingsScreen
import com.punteradigital.inventory.presentation.settings.CatalogScreen
import com.punteradigital.inventory.presentation.settings.CatalogEditScreen
import com.punteradigital.inventory.presentation.traceability.TraceabilityScreen
import com.punteradigital.inventory.presentation.viewmodel.InventoryViewModel
import com.punteradigital.inventory.presentation.components.*
import com.punteradigital.inventory.ui.theme.*
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var printerPreferences: com.punteradigital.inventory.data.local.PrinterPreferences

    @Inject
    lateinit var printRepository: com.punteradigital.inventory.data.repository.PrintRepository

    @Inject
    lateinit var themePreferences: com.punteradigital.inventory.data.local.ThemePreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: InventoryViewModel = hiltViewModel()
            val origin by viewModel.currentOrigin.collectAsState()
            val isDarkMode by themePreferences.isDarkMode.collectAsState()

            // Dynamic theme based on origin and dark/light
            PunteraDigitalTheme(origin = origin, isDarkMode = isDarkMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    PunteraApp(viewModel, printerPreferences, printRepository, themePreferences)
                }
            }
        }
    }
}

// Routes
@Serializable object Login
@Serializable object MainHub
@Serializable data class ScannerRoute(val mode: String, val scanType: String)
@Serializable object DispatchListRoute
@Serializable object MuestrasRoute
@Serializable object RefillMasterBoxRoute
@Serializable object SettingsRoute
@Serializable object CatalogRoute
@Serializable data class CatalogEditRoute(val itemId: String?)
@Serializable object PrinterConfigRoute

@Composable
fun PunteraApp(
    viewModel: InventoryViewModel,
    printerPreferences: com.punteradigital.inventory.data.local.PrinterPreferences,
    printRepository: com.punteradigital.inventory.data.repository.PrintRepository,
    themePreferences: com.punteradigital.inventory.data.local.ThemePreferences
) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Login,
        enterTransition = { fadeIn(animationSpec = tween(300)) },
        exitTransition = { fadeOut(animationSpec = tween(300)) }
    ) {
        composable<Login> {
            LoginScreen(onLoginSuccess = { user ->
                viewModel.setCurrentUser(user)
                navController.navigate(MainHub) {
                    popUpTo(Login) { inclusive = true }
                }
            })
        }
        composable<MainHub> {
            MainDashboard(
                viewModel = viewModel,
                themePreferences = themePreferences,
                onNavigateToScanner = { mode, scanType ->
                    navController.navigate(ScannerRoute(mode, scanType))
                },
                onNavigateToDispatchList = {
                    navController.navigate(DispatchListRoute)
                },
                onNavigateToMuestras = {
                    navController.navigate(MuestrasRoute)
                },
                onNavigateToRefill = {
                    navController.navigate(RefillMasterBoxRoute)
                },
                onNavigateToCatalog = {
                    navController.navigate(CatalogRoute)
                },
                onNavigateToPrinter = {
                    navController.navigate(PrinterConfigRoute)
                }
            )
        }
        composable<ScannerRoute> { backStackEntry ->
            val route = backStackEntry.arguments
            val mode = route?.getString("mode") ?: "STANDBY"
            val scanType = route?.getString("scanType") ?: "MANUAL"
            UnifiedScannerScreen(
                viewModel = viewModel,
                moduleName = mode,
                scanType = scanType,
                onBack = { navController.popBackStack() }
            )
        }
        composable<DispatchListRoute> {
            DispatchListScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onVerifyScan = {
                    navController.navigate(ScannerRoute("VERIFY", "MANUAL"))
                }
            )
        }
        composable<MuestrasRoute> {
            MuestrasScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }
        composable<RefillMasterBoxRoute> {
            RefillMasterBoxScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }
        composable<SettingsRoute> {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onNavigateToCatalog = { navController.navigate(CatalogRoute) },
                onNavigateToPrinter = { navController.navigate(PrinterConfigRoute) },
                themePreferences = themePreferences
            )
        }
        composable<CatalogRoute> {
            CatalogScreen(
                onBack = { navController.popBackStack() },
                onNavigateToEdit = { id -> 
                    navController.navigate(CatalogEditRoute(id)) 
                }
            )
        }
        composable<CatalogEditRoute> { backStackEntry ->
            val route = backStackEntry.arguments
            val itemId = route?.getString("itemId")
            CatalogEditScreen(
                itemId = itemId,
                onBack = { navController.popBackStack() }
            )
        }
        composable<PrinterConfigRoute> {
            val context = androidx.compose.ui.platform.LocalContext.current
            PrinterConfigScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainDashboard(
    viewModel: InventoryViewModel,
    themePreferences: com.punteradigital.inventory.data.local.ThemePreferences,
    onNavigateToScanner: (String, String) -> Unit,
    onNavigateToDispatchList: () -> Unit,
    onNavigateToMuestras: () -> Unit,
    onNavigateToRefill: () -> Unit,
    onNavigateToCatalog: () -> Unit,
    onNavigateToPrinter: () -> Unit
) {
    val origin by viewModel.currentOrigin.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }
    var showEditSheet by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    val navItems = listOf(
        KineticNavItem("Inicio", Icons.Outlined.Home, Icons.Filled.Home, "🏠"),
        KineticNavItem("Entrada", Icons.AutoMirrored.Outlined.Assignment, Icons.AutoMirrored.Filled.Assignment, "📋"),
        KineticNavItem("Movim.", Icons.Outlined.SwapHoriz, Icons.Filled.SwapHoriz, "🔄"),
        KineticNavItem("Traza", Icons.Outlined.History, Icons.Filled.History, "📜"),
        KineticNavItem("Config", Icons.Outlined.Settings, Icons.Filled.Settings, "⚙")
    )

    // HorizontalPager state
    val pagerState = rememberPagerState(pageCount = { navItems.size })

    // Sync: pager swipe → bottom nav (ONE direction only, using settledPage to avoid flicker)
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            selectedTab = page
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            KineticBottomNavBar(
                items = navItems,
                selectedIndex = selectedTab,
                onItemSelected = { index ->
                    selectedTab = index
                    coroutineScope.launch {
                        pagerState.animateScrollToPage(index)
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                beyondViewportPageCount = 1
            ) { page ->
                when (page) {
                    0 -> HomeScreen(
                        viewModel = viewModel,
                        onNavigateToEntry = { selectedTab = 1 },
                        onNavigateToMovements = { selectedTab = 2 },
                        onNavigateToRefill = onNavigateToRefill
                    )
                    1 -> EntryScreen(viewModel = viewModel)
                    2 -> MovementHubScreen(
                        viewModel = viewModel,
                        onNavigateToScanner = onNavigateToScanner,
                        onNavigateToDispatchList = onNavigateToDispatchList,
                        onNavigateToMuestras = onNavigateToMuestras
                    )
                    3 -> TraceabilityScreen(viewModel = viewModel)
                    4 -> SettingsScreen(
                        onNavigateToCatalog = onNavigateToCatalog,
                        onNavigateToPrinter = onNavigateToPrinter,
                        themePreferences = themePreferences
                    )
                }
            }
        }
    }

    if (showEditSheet) {
        com.punteradigital.inventory.presentation.components.GlobalEditPanelBottomSheet(
            onDismissRequest = { showEditSheet = false }
        )
    }
}

/**
 * Movement hub with Stand-By, Dispatch, Quality, and Muestras Retornables.
 * Dispatch now navigates to a list-based screen instead of scanner.
 */
@Composable
fun MovementHubScreen(
    viewModel: InventoryViewModel,
    onNavigateToScanner: (String, String) -> Unit,
    onNavigateToDispatchList: () -> Unit,
    onNavigateToMuestras: () -> Unit
) {
    val origin by viewModel.currentOrigin.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            "Módulos de Movimiento",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground
        )

        // Stand-By — still uses scanner
        MovementCard(
            title = "Stand-By",
            subtitle = "Pre-despacho → ZONA_PREDESPACHO",
            emoji = "⏸",
            color = StandByAmber,
            onManual = { onNavigateToScanner("STANDBY", "MANUAL") },
            onRapid = { onNavigateToScanner("STANDBY", "RAPID") }
        )

        // Dispatch — now goes to checklist screen
        KineticCard(
            modifier = Modifier.fillMaxWidth(),
            onClick = onNavigateToDispatchList,
            padding = 20.dp
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = DispatchGreen.copy(alpha = 0.15f),
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("🚚", fontSize = 28.sp)
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("Despacho", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    Text("Seleccionar items en Stand-By para salida", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // Quality — still uses scanner
        MovementCard(
            title = "Calidad / Bajas",
            subtitle = "Hidrolizado · Segunda · Daño Físico",
            emoji = "⊖",
            color = QualityPurple,
            onManual = { onNavigateToScanner("QUALITY", "MANUAL") },
            onRapid = { onNavigateToScanner("QUALITY", "RAPID") }
        )

        // Muestras Retornables — NEW
        KineticCard(
            modifier = Modifier.fillMaxWidth(),
            onClick = onNavigateToMuestras,
            padding = 20.dp
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MuestraTeal.copy(alpha = 0.15f),
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("🏪", fontSize = 28.sp)
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("Muestras Retornables", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    Text("Uso comercial, retornable al stock", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun MovementCard(
    title: String,
    subtitle: String,
    emoji: String,
    color: Color,
    onManual: () -> Unit,
    onRapid: () -> Unit
) {
    KineticCard(
        modifier = Modifier.fillMaxWidth(),
        padding = 20.dp
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = color.copy(alpha = 0.15f),
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(emoji, fontSize = 28.sp)
                }
            }
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            KineticButton(
                text = "👆 Manual",
                onClick = onManual,
                modifier = Modifier.weight(1f),
                type = ButtonType.SECONDARY
            )

            KineticButton(
                text = "⚡ Ráfaga",
                onClick = onRapid,
                modifier = Modifier.weight(1f),
                type = ButtonType.PRIMARY
            )
        }
    }
}

data class NavigationItem(
    val label: String,
    val unselectedIcon: ImageVector,
    val selectedIcon: ImageVector
)
