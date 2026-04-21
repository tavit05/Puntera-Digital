package com.punteradigital.inventory.presentation.scanner

import android.Manifest
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.punteradigital.inventory.domain.model.BajaReason
import com.punteradigital.inventory.domain.model.Origin
import com.punteradigital.inventory.presentation.viewmodel.InventoryViewModel
import com.punteradigital.inventory.presentation.viewmodel.InventoryUiState
import com.punteradigital.inventory.presentation.viewmodel.ScannedInfo
import com.punteradigital.inventory.presentation.components.*
import com.punteradigital.inventory.ui.theme.*
import kotlinx.coroutines.launch

/**
 * Unified scanner screen supporting Manual and Rapid (Burst) scan modes
 * for Stand-By, Quality, and Verification modules.
 * Now includes mandatory Cliente/Observaciones modal for Stand-By.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnifiedScannerScreen(
    viewModel: InventoryViewModel,
    moduleName: String, // STANDBY, QUALITY, VERIFY
    scanType: String,   // MANUAL, RAPID
    onBack: () -> Unit
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val uiState by viewModel.uiState.collectAsState()
    val user by viewModel.currentUser.collectAsState()
    val origin by viewModel.currentOrigin.collectAsState()
    val dispatchBatch by viewModel.dispatchBatch.collectAsState()

    var hasCameraPermission by remember { mutableStateOf(false) }
    var isPaused by remember { mutableStateOf(false) }
    var cameraControl by remember { mutableStateOf<CameraControl?>(null) }
    var isFlashOn by remember { mutableStateOf(false) }

    // Manual mode state
    var scannedResult by remember { mutableStateOf<ScannedInfo?>(null) }
    var showDetailSheet by remember { mutableStateOf(false) }
    var currentQrCode by remember { mutableStateOf("") }
    var showManualInput by remember { mutableStateOf(false) }
    var manualUuid by remember { mutableStateOf("") }

    // Rapid mode state
    val scannedUuids = remember { mutableStateListOf<String>() }
    var scanCount by remember { mutableIntStateOf(0) }

    // Quality mode state
    var selectedBajaReason by remember { mutableStateOf<BajaReason?>(null) }

    // Stand-By Cliente/Observaciones modal
    var showClienteModal by remember { mutableStateOf(false) }
    var pendingStandByUuid by remember { mutableStateOf("") }
    var clienteInput by remember { mutableStateOf("") }
    var observacionesInput by remember { mutableStateOf("") }

    // Verify mode state
    var verifyResult by remember { mutableStateOf<String?>(null) }

    // UUID-not-found feedback
    var notFoundUuid by remember { mutableStateOf<String?>(null) }

    val sheetState = rememberModalBottomSheetState()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { hasCameraPermission = it }

    LaunchedEffect(Unit) {
        permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    val moduleTitle = when (moduleName) {
        "STANDBY" -> "Stand-By"
        "QUALITY" -> "Calidad / Bajas"
        "VERIFY" -> "Verificar UUID"
        else -> moduleName
    }
    val moduleColor = when (moduleName) {
        "STANDBY" -> StandByAmber
        "QUALITY" -> QualityPurple
        "VERIFY" -> RefillBlue
        else -> MaterialTheme.colorScheme.primary
    }

    val isRapid = scanType == "RAPID"

    fun processScannedUuid(uuid: String) {
        Log.d("Scanner", "processScannedUuid called with: $uuid, module=$moduleName, isRapid=$isRapid")

        if (moduleName == "VERIFY") {
            // Verification mode: just show info about the product
            isPaused = true
            currentQrCode = uuid
            scope.launch {
                scannedResult = viewModel.getScannedInfo(uuid)
                if (scannedResult == null) {
                    Log.w("Scanner", "VERIFY: UUID not found in DB: $uuid")
                    notFoundUuid = uuid
                    isPaused = false
                } else {
                    showDetailSheet = true
                }
            }
            return
        }

        if (isRapid) {
            if (uuid in scannedUuids) return

            when (moduleName) {
                "STANDBY" -> {
                    viewModel.processStandBy(uuid, user?.id ?: "UNKNOWN")
                    scannedUuids.add(uuid)
                    scanCount++
                }
                "QUALITY" -> {
                    if (selectedBajaReason != null) {
                        viewModel.processQualityBaja(uuid, selectedBajaReason!!, user?.id ?: "UNKNOWN")
                        scannedUuids.add(uuid)
                        scanCount++
                    }
                }
            }
            isPaused = false
        } else {
            // Manual mode: pause and show details
            isPaused = true
            currentQrCode = uuid
            scope.launch {
                scannedResult = viewModel.getScannedInfo(uuid)
                if (scannedResult == null) {
                    Log.w("Scanner", "MANUAL: UUID not found in DB: $uuid")
                    notFoundUuid = uuid
                    isPaused = false  // Resume scanning so user can try again
                } else {
                    Log.d("Scanner", "UUID found: $uuid -> ${scannedResult!!::class.simpleName}")
                    showDetailSheet = true
                }
            }
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("$moduleTitle · ${if (isRapid) "Ráfaga" else "Manual"}", fontWeight = FontWeight.Bold)
                        Text("Modo: ${origin.displayName}", style = MaterialTheme.typography.bodySmall, color = moduleColor)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Camera Preview
            if (hasCameraPermission) {
                AndroidView(
                    factory = { ctx ->
                        val previewView = PreviewView(ctx)
                        val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

                        cameraProviderFuture.addListener({
                            val cameraProvider = cameraProviderFuture.get()
                            val preview = Preview.Builder().build().also {
                                it.surfaceProvider = previewView.surfaceProvider
                            }

                            val imageAnalysis = ImageAnalysis.Builder()
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .build()
                                .also { analysis ->
                                    // Create scanner ONCE — reused for all frames.
                                    // Previously created per-frame, leaking ML Kit instances.
                                    val barcodeScanner = BarcodeScanning.getClient()

                                    analysis.setAnalyzer(ContextCompat.getMainExecutor(ctx)) { imageProxy ->
                                        if (isPaused) {
                                            imageProxy.close()
                                            return@setAnalyzer
                                        }

                                        @androidx.annotation.OptIn(ExperimentalGetImage::class)
                                        val mediaImage = imageProxy.image
                                        if (mediaImage != null) {
                                            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

                                            barcodeScanner.process(image)
                                                .addOnSuccessListener { barcodes ->
                                                    for (barcode in barcodes) {
                                                        barcode.rawValue?.let { qrValue ->
                                                            if (qrValue.startsWith("FS-") || qrValue.startsWith("SF-")) {
                                                                processScannedUuid(qrValue)
                                                            }
                                                        }
                                                    }
                                                }
                                                .addOnCompleteListener { imageProxy.close() }
                                        } else {
                                            // Must close imageProxy even when mediaImage is null,
                                            // otherwise the camera pipeline stalls (frame starvation).
                                            imageProxy.close()
                                        }
                                    }
                                }

                            try {
                                cameraProvider.unbindAll()
                                val camera = cameraProvider.bindToLifecycle(
                                    lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA,
                                    preview, imageAnalysis
                                )
                                cameraControl = camera.cameraControl
                            } catch (exc: Exception) {
                                Log.e("Scanner", "Camera error", exc)
                            }
                        }, ContextCompat.getMainExecutor(ctx))
                        previewView
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            // ═══ RAPID MODE OVERLAY ═══
            if (isRapid) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 16.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = moduleColor,
                        shadowElevation = 8.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.QrCodeScanner, null, tint = Color.White, modifier = Modifier.size(24.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("$scanCount", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Color.White)
                            Spacer(Modifier.width(4.dp))
                            Text("escaneados", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.8f))
                        }
                    }
                }
            }

            // Quality reason selector (for rapid quality mode)
            if (moduleName == "QUALITY" && isRapid) {
                KineticCard(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 24.dp)
                        .fillMaxWidth(0.9f),
                    padding = 16.dp
                ) {
                    Column {
                        Text("Motivo de Baja", style = MaterialTheme.typography.titleSmall, color = QualityPurple)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            BajaReason.entries.forEach { reason ->
                                FilterChip(
                                    selected = selectedBajaReason == reason,
                                    onClick = { selectedBajaReason = reason },
                                    label = { Text(reason.displayName, style = MaterialTheme.typography.labelSmall) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = QualityPurple,
                                        selectedLabelColor = Color.White
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // Floating controls
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 24.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                FloatingActionButton(
                    onClick = { isFlashOn = !isFlashOn; cameraControl?.enableTorch(isFlashOn) },
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                    contentColor = if (isFlashOn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    shape = CircleShape,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(if (isFlashOn) Icons.Default.FlashOn else Icons.Default.FlashOff, "Flash")
                }

                if (!isRapid) {
                    FloatingActionButton(
                        onClick = { showManualInput = true },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        shape = CircleShape,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.Default.Keyboard, "Entrada manual")
                    }
                }
            }

            // ═══ POKA-YOKE ALERT ═══
            if (uiState is InventoryUiState.PokayokeAlert) {
                val alert = uiState as InventoryUiState.PokayokeAlert
                AlertDialog(
                    onDismissRequest = { viewModel.resetUiState() },
                    icon = { Icon(Icons.Default.Warning, null, tint = CriticalRed, modifier = Modifier.size(48.dp)) },
                    title = { Text("⚠ ALERTA POKA-YOKE", color = CriticalRed, fontWeight = FontWeight.Bold) },
                    text = { Text(alert.message, textAlign = TextAlign.Center) },
                    confirmButton = {
                        KineticButton(
                            text = "ENTENDIDO",
                            onClick = { viewModel.resetUiState() },
                            type = ButtonType.DANGER
                        )
                    }
                )
            }

            // Manual input dialog
            if (showManualInput) {
                AlertDialog(
                    onDismissRequest = { showManualInput = false },
                    title = { Text("Entrada Manual") },
                    text = {
                        Column {
                            Text("Ingrese el UUID del producto:")
                            Spacer(Modifier.height(8.dp))
                            KineticTextField(
                                value = manualUuid,
                                onValueChange = { manualUuid = it.uppercase() },
                                label = "UUID (FS-xxxx / SF-xxxx)",
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    },
                    confirmButton = {
                        KineticButton(
                            text = "BUSCAR",
                            onClick = {
                                if (manualUuid.isNotEmpty()) {
                                    processScannedUuid(manualUuid)
                                    showManualInput = false
                                    manualUuid = ""
                                }
                            }
                        )
                    },
                    dismissButton = {
                        TextButton(onClick = { showManualInput = false }) { Text("CANCELAR") }
                    }
                )
            }

            // Manual mode bottom sheet
            if (showDetailSheet && !isRapid) {
                ModalBottomSheet(
                    onDismissRequest = { showDetailSheet = false; isPaused = false },
                    sheetState = sheetState
                ) {
                    ManualScanDetailSheet(
                        scannedInfo = scannedResult,
                        qrCode = currentQrCode,
                        moduleName = moduleName,
                        onConfirm = { reason ->
                            when (moduleName) {
                                "STANDBY" -> {
                                    // Show Cliente/Observaciones modal before confirming
                                    pendingStandByUuid = currentQrCode
                                    showClienteModal = true
                                }
                                "QUALITY" -> {
                                    val bajaReason = BajaReason.entries.find { it.displayName == reason }
                                    if (bajaReason != null) {
                                        viewModel.processQualityBaja(currentQrCode, bajaReason, user?.id ?: "UNKNOWN")
                                    }
                                }
                                "VERIFY" -> {
                                    // Just close — verification is read-only
                                }
                            }
                            showDetailSheet = false
                            if (moduleName != "STANDBY") isPaused = false
                        },
                        onCancel = { showDetailSheet = false; isPaused = false }
                    )
                }
            }

            // ═══ CLIENTE/OBSERVACIONES MODAL (for Stand-By) ═══
            if (showClienteModal) {
                AlertDialog(
                    onDismissRequest = {
                        showClienteModal = false
                        isPaused = false
                    },
                    icon = { Icon(Icons.Default.Person, null, tint = StandByAmber, modifier = Modifier.size(40.dp)) },
                    title = { Text("Datos de Stand-By", fontWeight = FontWeight.Bold) },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                "UUID: $pendingStandByUuid",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            KineticTextField(
                                value = clienteInput,
                                onValueChange = { clienteInput = it },
                                label = "Cliente *",
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            KineticTextField(
                                value = observacionesInput,
                                onValueChange = { observacionesInput = it },
                                label = "Observaciones",
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    },
                    confirmButton = {
                        KineticButton(
                            text = "CONFIRMAR STAND-BY",
                            onClick = {
                                viewModel.processStandBy(
                                    uuid = pendingStandByUuid,
                                    userId = user?.id ?: "UNKNOWN",
                                    cliente = clienteInput,
                                    observaciones = observacionesInput
                                )
                                showClienteModal = false
                                isPaused = false
                                clienteInput = ""
                                observacionesInput = ""
                            },
                            enabled = clienteInput.isNotBlank(),
                            type = ButtonType.PRIMARY
                        )
                    },
                    dismissButton = {
                        TextButton(onClick = {
                            showClienteModal = false
                            isPaused = false
                        }) { Text("CANCELAR") }
                    }
                )
            }

            // ═══ UUID NOT FOUND FEEDBACK ═══
            if (notFoundUuid != null) {
                AlertDialog(
                    onDismissRequest = { notFoundUuid = null },
                    icon = {
                        Icon(
                            Icons.Default.SearchOff,
                            contentDescription = null,
                            tint = CriticalRed,
                            modifier = Modifier.size(48.dp)
                        )
                    },
                    title = {
                        Text(
                            "UUID No Registrado",
                            fontWeight = FontWeight.Bold,
                            color = CriticalRed
                        )
                    },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                "El código escaneado no fue encontrado en la base de datos del sistema.",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh
                            ) {
                                Text(
                                    notFoundUuid ?: "",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = SpaceGrotesk,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                            Text(
                                "Posibles causas:\n• El QR pertenece a otro sistema\n• El producto fue eliminado\n• Error de lectura del código",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    confirmButton = {
                        KineticButton(
                            text = "REINTENTAR",
                            onClick = { notFoundUuid = null },
                            type = ButtonType.PRIMARY
                        )
                    },
                    dismissButton = {
                        TextButton(onClick = { notFoundUuid = null }) {
                            Text("CERRAR")
                        }
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualScanDetailSheet(
    scannedInfo: ScannedInfo?,
    qrCode: String,
    moduleName: String,
    onConfirm: (String) -> Unit,
    onCancel: () -> Unit
) {
    var selectedReason by remember { mutableStateOf("") }
    var reasonExpanded by remember { mutableStateOf(false) }

    val title = when (moduleName) {
        "STANDBY" -> "Confirmar Stand-By"
        "QUALITY" -> "Registrar Baja"
        "VERIFY" -> "Información del UUID"
        else -> "Confirmar Acción"
    }

    Column(modifier = Modifier.padding(24.dp).fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))

        // Product detail card
        KineticCard(
            padding = 16.dp
        ) {
            when (scannedInfo) {
                is ScannedInfo.Master -> {
                    Column {
                        Text("📦 Caja Master", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Text("UUID: $qrCode", style = MaterialTheme.typography.bodyMedium)
                        Text("Modelo: ${scannedInfo.entity.model}", style = MaterialTheme.typography.bodyMedium)
                        Text("Talla: ${scannedInfo.entity.size}", style = MaterialTheme.typography.bodyMedium)
                        Text("Unidades: ${scannedInfo.entity.activeChildCount}/${scannedInfo.entity.childCount}",
                            style = MaterialTheme.typography.bodyMedium)
                        if (scannedInfo.entity.status == "PENDIENTE_POR_RELLENAR") {
                            Spacer(Modifier.height(4.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = WarningOrange.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    "⚠ PENDIENTE POR RELLENAR",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = WarningOrange
                                )
                            }
                        }
                        Text("Origen: ${scannedInfo.entity.origin}", style = MaterialTheme.typography.bodyMedium,
                            color = if (scannedInfo.entity.origin == "FOOT_SAFE") FootSafeYellow else SafetyCobalt)
                    }
                }
                is ScannedInfo.UnitInfo -> {
                    Column {
                        Text("👟 Unidad Individual", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Text("UUID: $qrCode", style = MaterialTheme.typography.bodyMedium)
                        Text("Modelo: ${scannedInfo.entity.model}", style = MaterialTheme.typography.bodyMedium)
                        Text("Talla: ${scannedInfo.entity.size}", style = MaterialTheme.typography.bodyMedium)
                        Text("Lote: ${scannedInfo.entity.lot}", style = MaterialTheme.typography.bodyMedium)
                        Text("Estado: ${scannedInfo.entity.status}", style = MaterialTheme.typography.bodyMedium)
                        Text("Origen: ${scannedInfo.entity.origin}", style = MaterialTheme.typography.bodyMedium,
                            color = if (scannedInfo.entity.origin == "FOOT_SAFE") FootSafeYellow else SafetyCobalt)
                        if (scannedInfo.entity.parentUuid != null) {
                            Text("Caja Master: ${scannedInfo.entity.parentUuid}", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                null -> {
                    Column {
                        Text("❌ No encontrado", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                        Text("UUID: $qrCode", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }

        // Quality reason selector
        if (moduleName == "QUALITY") {
            Spacer(Modifier.height(16.dp))
            ExposedDropdownMenuBox(
                expanded = reasonExpanded,
                onExpandedChange = { reasonExpanded = it }
            ) {
                KineticTextField(
                    value = selectedReason,
                    onValueChange = {},
                    readOnly = true,
                    label = "Motivo de Baja *",
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = reasonExpanded) },
                    modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, true).fillMaxWidth()
                )
                ExposedDropdownMenu(expanded = reasonExpanded, onDismissRequest = { reasonExpanded = false }) {
                    BajaReason.entries.forEach { reason ->
                        DropdownMenuItem(
                            text = { Text(reason.displayName) },
                            onClick = { selectedReason = reason.displayName; reasonExpanded = false }
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        KineticButton(
            text = when (moduleName) {
                "VERIFY" -> "CERRAR"
                else -> "CONFIRMAR"
            },
            onClick = {
                if (moduleName == "QUALITY") onConfirm(selectedReason)
                else onConfirm("")
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = scannedInfo != null && (moduleName != "QUALITY" || selectedReason.isNotEmpty()),
            type = if (moduleName == "VERIFY") ButtonType.SECONDARY else ButtonType.PRIMARY
        )

        TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
            Text("CANCELAR", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
