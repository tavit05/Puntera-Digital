package com.punteradigital.inventory.presentation.inbound

import kotlinx.coroutines.delay

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.punteradigital.inventory.domain.model.Origin
import com.punteradigital.inventory.domain.rules.BusinessRules
import com.punteradigital.inventory.presentation.viewmodel.InventoryViewModel
import com.punteradigital.inventory.presentation.viewmodel.InventoryUiState
import com.punteradigital.inventory.presentation.viewmodel.RemainderMode
import com.punteradigital.inventory.presentation.components.*
import com.punteradigital.inventory.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryScreen(viewModel: InventoryViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val user by viewModel.currentUser.collectAsState()
    val origin by viewModel.currentOrigin.collectAsState()

    // Form state
    var selectedOrigin by remember { mutableStateOf(Origin.FOOT_SAFE) }
    var entryTypeExpanded by remember { mutableStateOf(false) }
    var selectedEntryType by remember { mutableStateOf("Producción") }
    val entryTypes = listOf("Producción", "Ajuste", "Traslado")

    var modelExpanded by remember { mutableStateOf(false) }
    var selectedModel by remember { mutableStateOf("") }
    val models = listOf("FS300CMFFPBL", "FS302CMN", "FS400BK", "FS500GY")

    var sizeExpanded by remember { mutableStateOf(false) }
    var selectedSize by remember { mutableStateOf("") }
    val sizes = (34..48).map { it.toString() }

    var lot by remember { mutableStateOf("") }
    var isMasterBox by remember { mutableStateOf(true) }
    var childCount by remember { mutableStateOf(BusinessRules.DEFAULT_MASTER_QTY.toString()) }
    var totalQuantity by remember { mutableStateOf("") }
    var remainderMode by remember { mutableStateOf(RemainderMode.LOOSE) }

    // Auto-boxing calculation
    val totalQty = totalQuantity.toIntOrNull() ?: 0
    val pairsPerBox = childCount.toIntOrNull() ?: BusinessRules.DEFAULT_MASTER_QTY
    val autoBox = if (isMasterBox && totalQty > 0 && pairsPerBox > 0)
        BusinessRules.calculateAutoBoxing(totalQty, pairsPerBox) else null

    // Update theme on origin change
    LaunchedEffect(selectedOrigin) {
        viewModel.setOrigin(selectedOrigin)
    }

    // Auto-reset form on success
    LaunchedEffect(uiState) {
        if (uiState is InventoryUiState.SuccessEntry) {
            delay(3000)
            viewModel.resetUiState()
            lot = ""
            totalQuantity = ""
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Registro de Nacimiento", fontWeight = FontWeight.Bold)
                    }
                },
                windowInsets = WindowInsets.statusBars,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .background(MaterialTheme.colorScheme.background)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // ═══ ORIGIN SELECTOR ═══
                KineticCard(
                    padding = 20.dp
                ) {
                    Column {
                        Text(
                            "Origen de Mercancía",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OriginChip(
                                label = "Foot Safe",
                                isSelected = selectedOrigin == Origin.FOOT_SAFE,
                                selectedColor = FootSafeYellow,
                                textColor = FootSafeBlack,
                                onClick = { selectedOrigin = Origin.FOOT_SAFE },
                                modifier = Modifier.weight(1f)
                            )
                            OriginChip(
                                label = "Safety",
                                isSelected = selectedOrigin == Origin.SAFETY,
                                selectedColor = SafetyCobalt,
                                textColor = SafetyWhite,
                                onClick = { selectedOrigin = Origin.SAFETY },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // ═══ DATA CAPTURE ═══
                KineticCard(
                    padding = 20.dp
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            "Datos del Producto",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )

                        // Entry Type
                        ExposedDropdownMenuBox(
                            expanded = entryTypeExpanded,
                            onExpandedChange = { entryTypeExpanded = it },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            KineticTextField(
                                value = selectedEntryType,
                                onValueChange = {},
                                readOnly = true,
                                label = "Tipo de Entrada",
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = entryTypeExpanded) },
                                modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, true).fillMaxWidth()
                            )
                            ExposedDropdownMenu(expanded = entryTypeExpanded, onDismissRequest = { entryTypeExpanded = false }) {
                                entryTypes.forEach { item ->
                                    DropdownMenuItem(text = { Text(item) }, onClick = { selectedEntryType = item; entryTypeExpanded = false })
                                }
                            }
                        }

                        // Model
                        ExposedDropdownMenuBox(
                            expanded = modelExpanded,
                            onExpandedChange = { modelExpanded = it },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            KineticTextField(
                                value = selectedModel,
                                onValueChange = {},
                                readOnly = true,
                                label = "Modelo",
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = modelExpanded) },
                                modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, true).fillMaxWidth()
                            )
                            ExposedDropdownMenu(expanded = modelExpanded, onDismissRequest = { modelExpanded = false }) {
                                models.forEach { item ->
                                    DropdownMenuItem(text = { Text(item) }, onClick = { selectedModel = item; modelExpanded = false })
                                }
                            }
                        }

                        // Size & Lot
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            ExposedDropdownMenuBox(
                                expanded = sizeExpanded,
                                onExpandedChange = { sizeExpanded = it },
                                modifier = Modifier.weight(1f)
                            ) {
                                KineticTextField(
                                    value = selectedSize,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = "Talla",
                                    modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, true).fillMaxWidth()
                                )
                                ExposedDropdownMenu(expanded = sizeExpanded, onDismissRequest = { sizeExpanded = false }) {
                                    sizes.forEach { item ->
                                        DropdownMenuItem(text = { Text(item) }, onClick = { selectedSize = item; sizeExpanded = false })
                                    }
                                }
                            }
                            KineticTextField(
                                value = lot,
                                onValueChange = { lot = it },
                                label = "Lote",
                                modifier = Modifier.weight(1f)
                            )
                        }

                        // ═══ TOTAL QUANTITY ═══
                        KineticTextField(
                            value = totalQuantity,
                            onValueChange = { totalQuantity = it },
                            label = "🔢 Cantidad Total a Ingresar *",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // ═══ MASTER BOX TOGGLE ═══
                KineticCard(
                    padding = 20.dp
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Caja Master", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                                Text(
                                    if (isMasterBox) {
                                        if (autoBox != null)
                                            "${autoBox.fullBoxes} caja${if (autoBox.fullBoxes != 1) "s" else ""} × $pairsPerBox pares"
                                        else "1 UUID Padre + $childCount Hijos"
                                    } else "Unidades individuales",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = isMasterBox,
                                onCheckedChange = { isMasterBox = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = MaterialTheme.colorScheme.primary,
                                    checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                                )
                            )
                        }

                        AnimatedVisibility(visible = isMasterBox) {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                KineticTextField(
                                    value = childCount,
                                    onValueChange = { childCount = it },
                                    label = "Pares por Caja",
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 12.dp)
                                )
                            }
                        }

                        // ═══ AUTO-BOXING PREVIEW (Poka-Yoke) ═══
                        if (isMasterBox && autoBox != null) {
                            KineticCard(
                                padding = 16.dp
                            ) {
                                Column {
                                    Text(
                                        "📦 Resumen de Ingreso",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        "Su ingreso resultará en:",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        "✅ ${autoBox.fullBoxes} Caja${if (autoBox.fullBoxes != 1) "s" else ""} Master completa${if (autoBox.fullBoxes != 1) "s" else ""} (${autoBox.pairsInFullBoxes} pares)",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = DispatchGreen
                                    )

                                    if (autoBox.hasRemainder) {
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            "+ ${autoBox.remainderPairs} par${if (autoBox.remainderPairs > 1) "es" else ""} adicional${if (autoBox.remainderPairs > 1) "es" else ""}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = WarningOrange
                                        )

                                        Spacer(Modifier.height(12.dp))
                                        Text(
                                            "¿Qué hacer con los pares sobrantes?",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(Modifier.height(8.dp))

                                        // Remainder mode radio buttons
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            FilterChip(
                                                selected = remainderMode == RemainderMode.LOOSE,
                                                onClick = { remainderMode = RemainderMode.LOOSE },
                                                label = { Text("Pares sueltos", style = MaterialTheme.typography.labelSmall) },
                                                leadingIcon = {
                                                    if (remainderMode == RemainderMode.LOOSE)
                                                        Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp))
                                                },
                                                modifier = Modifier.weight(1f),
                                                colors = FilterChipDefaults.filterChipColors(
                                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                                    selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary
                                                )
                                            )
                                            FilterChip(
                                                selected = remainderMode == RemainderMode.FILL_LATER,
                                                onClick = { remainderMode = RemainderMode.FILL_LATER },
                                                label = { Text("Caja incompleta", style = MaterialTheme.typography.labelSmall) },
                                                leadingIcon = {
                                                    if (remainderMode == RemainderMode.FILL_LATER)
                                                        Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp))
                                                },
                                                modifier = Modifier.weight(1f),
                                                colors = FilterChipDefaults.filterChipColors(
                                                    selectedContainerColor = RefillBlue,
                                                    selectedLabelColor = Color.White,
                                                    selectedLeadingIconColor = Color.White
                                                )
                                            )
                                        }

                                        // Description of selected mode
                                        Text(
                                            when (remainderMode) {
                                                RemainderMode.LOOSE -> "Se generarán etiquetas individuales sin caja padre."
                                                RemainderMode.FILL_LATER -> "Se creará una caja marcada como 'Pendiente por Rellenar' (${autoBox.remainderPairs}/$pairsPerBox)."
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(top = 4.dp)
                                        )
                                    }
                                }
                            }
                        }
                        
                        if (!isMasterBox && totalQty > 0) {
                            KineticCard(padding = 16.dp) {
                                Column {
                                    Text(
                                        "📦 Resumen de Ingreso",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        "Su ingreso resultará en:",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        "✅ $totalQty unidades individuales",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = DispatchGreen
                                    )
                                    Text(
                                        "Se generarán las etiquetas individuales, sin caja padre asignada.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(top = 8.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // ═══ ACTION BUTTON ═══
                val isButtonEnabled = selectedModel.isNotBlank() && selectedSize.isNotBlank() && lot.isNotBlank() && totalQty > 0 && uiState !is InventoryUiState.Loading
                
                KineticButton(
                    text = if (uiState is InventoryUiState.Loading) "PROCESANDO..." else "🖨 REGISTRAR E IMPRIMIR",
                    onClick = {
                        viewModel.evaluateSmartEntry(
                            origin = selectedOrigin,
                            model = selectedModel,
                            size = selectedSize,
                            lot = lot,
                            entryType = selectedEntryType,
                            totalQuantity = if (totalQty > 0) totalQty else pairsPerBox,
                            isMasterBox = isMasterBox,
                            childCount = pairsPerBox,
                            remainderMode = remainderMode,
                            userId = user?.id ?: "UNKNOWN"
                        )
                    },
                    modifier = Modifier.fillMaxWidth().height(64.dp),
                    enabled = isButtonEnabled,
                    isLoading = uiState is InventoryUiState.Loading,
                    isSuccess = uiState is InventoryUiState.SuccessEntry,
                    type = ButtonType.PRIMARY
                )

                Spacer(Modifier.height(80.dp)) // Bottom bar clearance
            }

            // ═══ SUCCESS OVERLAY ═══
            if (uiState is InventoryUiState.SuccessEntry) {
                val state = uiState as InventoryUiState.SuccessEntry
                EntrySuccessOverlay(
                    model = state.model,
                    uuids = state.uuids,
                    origin = state.origin,
                    message = state.message,
                    warning = state.warning,
                    onDismiss = { 
                        viewModel.resetUiState()
                        lot = ""
                        totalQuantity = ""
                    }
                )
            }

            // ═══ SMART ENTRY SUGGESTION OVERLAY ═══
            if (uiState is InventoryUiState.SmartEntrySuggestion) {
                val suggestion = uiState as InventoryUiState.SmartEntrySuggestion
                SmartSuggestionOverlay(
                    suggestion = suggestion,
                    onAddToBox = { boxUuid ->
                        viewModel.confirmAddToExistingBox(suggestion, boxUuid)
                    },
                    onLoose = {
                        viewModel.confirmLooseEntry(suggestion)
                    },
                    onNewBox = {
                        viewModel.confirmNewIncompleteBox(suggestion)
                    },
                    onDismiss = { viewModel.resetUiState() }
                )
            }

            // ═══ ERROR SNACKBAR ═══
            if (uiState is InventoryUiState.Error) {
                val state = uiState as InventoryUiState.Error
                Snackbar(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp),
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                    action = {
                        TextButton(onClick = { viewModel.resetUiState() }) {
                            Text("OK", color = MaterialTheme.colorScheme.onError)
                        }
                    }
                ) {
                    Text(state.message)
                }
            }
        }
    }
}

@Composable
fun OriginChip(
    label: String,
    isSelected: Boolean,
    selectedColor: Color,
    textColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.height(56.dp),
        shape = RoundedCornerShape(16.dp),
        color = if (isSelected) selectedColor else MaterialTheme.colorScheme.surface,
        onClick = onClick,
        tonalElevation = if (isSelected) 0.dp else 2.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                if (isSelected) {
                    Icon(
                        Icons.Default.CheckCircle, null,
                        tint = textColor,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSelected) textColor else MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
fun EntrySuccessOverlay(
    model: String,
    uuids: List<String>,
    origin: Origin,
    message: String,
    warning: String?,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f))
            .clickable { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .padding(24.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = if (warning == null) Icons.Default.CheckCircle else Icons.Default.Warning,
                    contentDescription = null,
                    tint = if (warning == null) DispatchGreen else StandByAmber,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    if (warning == null) "Entrada Registrada ✓" else "Registro con Aviso",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Origen: ${origin.displayName}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    "Modelo: $model · ${uuids.size} UUID(s) generados",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                if (message.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(12.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }

                if (warning != null) {
                    Spacer(Modifier.height(12.dp))
                    Surface(
                        color = StandByAmber.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = warning,
                            style = MaterialTheme.typography.bodySmall,
                            color = StandByAmber,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("CERRAR", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// SMART ENTRY SUGGESTION OVERLAY
// ═══════════════════════════════════════════════════════════════

@Composable
fun SmartSuggestionOverlay(
    suggestion: InventoryUiState.SmartEntrySuggestion,
    onAddToBox: (String) -> Unit,
    onLoose: () -> Unit,
    onNewBox: () -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f))
            .clickable { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .clickable(enabled = false) {}
                .padding(vertical = 24.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("⚡", fontSize = 28.sp)
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            "Entrada Inteligente",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            "${suggestion.requestedQuantity} par(es) · ${suggestion.model} · T.${suggestion.size}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))

                // Compatible boxes section
                if (suggestion.compatibleBoxes.isNotEmpty()) {
                    Text(
                        "📦 CAJAS COMPATIBLES ENCONTRADAS",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = DispatchGreen,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        "Se encontraron cajas incompletas del mismo modelo y talla",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))

                    suggestion.compatibleBoxes.forEach { box ->
                        val available = box.childCount - box.activeChildCount
                        val fillFraction = box.activeChildCount.toFloat() / box.childCount.toFloat()

                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            color = DispatchGreen.copy(alpha = 0.08f),
                            onClick = { onAddToBox(box.uuid) }
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            box.uuid,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = SpaceGrotesk,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            "Espacio disponible: $available par(es)",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = DispatchGreen,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = DispatchGreen.copy(alpha = 0.15f)
                                    ) {
                                        Text(
                                            "${box.activeChildCount}/${box.childCount}",
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = DispatchGreen,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                        )
                                    }
                                }

                                Spacer(Modifier.height(8.dp))

                                // Fill indicator
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .fillMaxWidth(fillFraction.coerceIn(0.02f, 1f))
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(DispatchGreen)
                                    )
                                }

                                // Smart message when qty > available
                                if (suggestion.requestedQuantity > available) {
                                    Spacer(Modifier.height(8.dp))
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = StandByAmber.copy(alpha = 0.1f)
                                    ) {
                                        Text(
                                            "⚡ Se agregarán $available a esta caja + ${suggestion.requestedQuantity - available} suelto(s)",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = StandByAmber,
                                            fontWeight = FontWeight.Medium,
                                            modifier = Modifier.padding(8.dp)
                                        )
                                    }
                                }

                                Spacer(Modifier.height(10.dp))
                                Button(
                                    onClick = { onAddToBox(box.uuid) },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = DispatchGreen)
                                ) {
                                    Text("AGREGAR A ESTA CAJA", fontWeight = FontWeight.Bold, color = Color.White)
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }

                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "— o también puedes —",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(Modifier.height(8.dp))
                } else {
                    // No compatible boxes
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("📭", fontSize = 24.sp)
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(
                                    "Sin cajas compatibles",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    "No hay cajas incompletas para ${suggestion.model} T.${suggestion.size}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }

                // Option: Loose
                OutlinedButton(
                    onClick = onLoose,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, RefillBlue.copy(alpha = 0.3f))
                ) {
                    Text("📋", fontSize = 16.sp)
                    Spacer(Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Crear par(es) suelto(s)",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            "Sin caja master — se pueden vincular después",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))

                // Option: New box
                OutlinedButton(
                    onClick = onNewBox,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, StandByAmber.copy(alpha = 0.3f))
                ) {
                    Text("📦", fontSize = 16.sp)
                    Spacer(Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Crear caja nueva incompleta",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            "${suggestion.requestedQuantity}/${suggestion.childCount} pares — se puede rellenar después",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))

                // Cancel
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("CANCELAR", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
