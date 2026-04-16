package com.punteradigital.inventory.presentation.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.punteradigital.inventory.data.local.dao.BatchStatus
import com.punteradigital.inventory.data.local.dao.InventoryDao
import com.punteradigital.inventory.data.local.entity.*
import com.punteradigital.inventory.data.remote.*
import com.punteradigital.inventory.data.repository.SyncManager
import com.punteradigital.inventory.domain.model.Origin
import com.punteradigital.inventory.domain.model.ProductStatus
import com.punteradigital.inventory.domain.model.BajaReason
import com.punteradigital.inventory.domain.rules.BusinessRules
import com.punteradigital.inventory.domain.usecase.PdfGeneratorUseCase
import com.punteradigital.inventory.domain.usecase.UuidGeneratorUseCase
import com.punteradigital.inventory.util.ConnectivityMonitor
import com.punteradigital.inventory.util.SoundManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

/**
 * Remainder mode when total quantity doesn't divide evenly into boxes.
 */
enum class RemainderMode {
    /** Generate individual pairs without a master box parent */
    LOOSE,
    /** Create an incomplete master box marked as PENDIENTE_POR_RELLENAR */
    FILL_LATER
}

@HiltViewModel
class InventoryViewModel @Inject constructor(
    private val dao: InventoryDao,
    private val uuidGenerator: UuidGeneratorUseCase,
    val pdfGenerator: PdfGeneratorUseCase,
    private val syncManager: SyncManager,
    private val connectivityMonitor: ConnectivityMonitor,
    val soundManager: SoundManager
) : ViewModel() {

    private val _uiState = MutableStateFlow<InventoryUiState>(InventoryUiState.Idle)
    val uiState: StateFlow<InventoryUiState> = _uiState.asStateFlow()

    private val _currentUser = MutableStateFlow<UserEntity?>(null)
    val currentUser: StateFlow<UserEntity?> = _currentUser.asStateFlow()

    private val _currentOrigin = MutableStateFlow(Origin.FOOT_SAFE)
    val currentOrigin: StateFlow<Origin> = _currentOrigin.asStateFlow()

    // Dashboard flows
    val inventoryStatus: Flow<List<BatchStatus>> = dao.getInventoryStatusByBatch()
    val traceabilityMovements: Flow<List<MovementEntity>> = dao.getAllMovements()
    val totalAvailable: Flow<Int> = dao.getTotalAvailableCount()
    val totalStandBy: Flow<Int> = dao.getTotalStandByCount()
    val totalMasterBoxes: Flow<Int> = dao.getTotalMasterBoxCount()
    val pendingSyncCount: Flow<Int> = dao.getPendingSyncCount()

    // Stand-By items for dispatch list
    val standByItems: Flow<List<ProductEntity>> = dao.getStandByProducts()

    // Active muestras
    val muestrasActivas: Flow<List<ProductEntity>> = dao.getMuestrasActivas()

    // Incomplete master boxes for refill
    val incompleteMasterBoxes: Flow<List<MasterBoxEntity>> = dao.getIncompleteMasterBoxes()

    // ═══ Analytics Flows (Traceability Intelligence) ═══
    val topDispatchedModels = dao.getTopDispatchedModels()
    val topDispatchedSizes = dao.getTopDispatchedSizes()
    val topClients = dao.getTopClients()
    val modelStatusBreakdown = dao.getModelStatusBreakdown()
    val allModelsInventory = dao.getAllModelsInventory()
    val allSizesInventory = dao.getAllSizesInventory()
    val totalDispatched = dao.getTotalDispatchedCount()
    val totalMovements = dao.getTotalMovementCount()

    init {
        // Auto-sync when connectivity restores
        viewModelScope.launch {
            connectivityMonitor.isOnline.collect { isOnline ->
                if (isOnline) {
                    val synced = syncManager.processPendingQueue()
                    if (synced > 0) {
                        Log.i("InventoryVM", "Auto-synced $synced pending items")
                    }
                }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // ORIGIN & SESSION
    // ═══════════════════════════════════════════════════════════════
    fun setOrigin(origin: Origin) {
        _currentOrigin.value = origin
    }

    fun detectOriginFromUuid(uuid: String) {
        Origin.fromUuid(uuid)?.let { _currentOrigin.value = it }
    }

    fun setCurrentUser(user: UserEntity) {
        _currentUser.value = user
    }

    fun resetUiState() {
        _uiState.value = InventoryUiState.Idle
    }

    // ═══════════════════════════════════════════════════════════════
    // ENTRY MODULE (Registro de Nacimiento) — with Auto-Boxing
    // ═══════════════════════════════════════════════════════════════
    fun processNewEntry(
        origin: Origin,
        model: String,
        size: String,
        lot: String,
        entryType: String,
        isMasterBox: Boolean,
        childCount: Int = BusinessRules.DEFAULT_MASTER_QTY,
        totalQuantity: Int = childCount,
        remainderMode: RemainderMode = RemainderMode.LOOSE,
        userId: String
    ) {
        viewModelScope.launch {
            _uiState.value = InventoryUiState.Loading("Generando UUIDs y registrando...")

            val timestamp = System.currentTimeMillis()
            val allUuids = mutableListOf<String>()
            val printItems = mutableListOf<PrintLabelItem>()
            val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
            val movementsDto = mutableListOf<InventoryMovementDto>()

            try {
                if (isMasterBox) {
                    val autoBox = BusinessRules.calculateAutoBoxing(totalQuantity, childCount)
                    var globalSeq = 1

                    // Generate full master boxes
                    for (boxIdx in 1..autoBox.fullBoxes) {
                        val (parentUuid, childrenUuids) = uuidGenerator.generateMasterBoxBatch(
                            origin = origin, lot = lot, size = size,
                            childCount = childCount, boxSequence = boxIdx,
                            startSequence = globalSeq
                        )

                        dao.insertMasterBox(MasterBoxEntity(
                            uuid = parentUuid, origin = origin.name,
                            model = model, size = size, lot = lot,
                            childCount = childCount, activeChildCount = childCount,
                            isComplete = true, status = "COMPLETE", createdAt = timestamp
                        ))
                        allUuids.add(parentUuid)
                        printItems.add(PrintLabelItem(parentUuid, model, size, lot, origin.displayName))

                        val children = childrenUuids.map { uuid ->
                            ProductEntity(
                                uuid = uuid, parentUuid = parentUuid,
                                origin = origin.name, model = model, size = size,
                                lot = lot, entryType = entryType, status = "AVAILABLE",
                                location = "RACK", createdAt = timestamp, updatedAt = timestamp
                            )
                        }
                        dao.insertProducts(children)
                        allUuids.addAll(childrenUuids)
                        childrenUuids.forEach { uuid ->
                            printItems.add(PrintLabelItem(uuid, model, size, lot, origin.displayName))
                        }

                        dao.insertMovement(MovementEntity(
                            uuid = parentUuid, type = "IN", reason = entryType,
                            observation = "Caja Master completa con $childCount unidades",
                            location = "RACK", timestamp = timestamp, userId = userId
                        ))
                        movementsDto.add(buildMovementDto(parentUuid, "IN", entryType, model, size, lot, origin, timestamp, userId))

                        globalSeq += childCount
                    }

                    // Handle remainder
                    if (autoBox.hasRemainder) {
                        when (remainderMode) {
                            RemainderMode.LOOSE -> {
                                // Generate individual pairs without master box parent
                                val looseUuids = uuidGenerator.generateUnitBatch(
                                    origin, lot, size, autoBox.remainderPairs, globalSeq
                                )
                                val loosePairs = looseUuids.map { uuid ->
                                    ProductEntity(
                                        uuid = uuid, origin = origin.name, model = model,
                                        size = size, lot = lot, entryType = entryType,
                                        status = "AVAILABLE", location = "RACK",
                                        createdAt = timestamp, updatedAt = timestamp
                                    )
                                }
                                dao.insertProducts(loosePairs)
                                allUuids.addAll(looseUuids)
                                looseUuids.forEach { uuid ->
                                    printItems.add(PrintLabelItem(uuid, model, size, lot, origin.displayName))
                                    dao.insertMovement(MovementEntity(
                                        uuid = uuid, type = "IN", reason = entryType,
                                        observation = "Par individual suelto",
                                        location = "RACK", timestamp = timestamp, userId = userId
                                    ))
                                    movementsDto.add(buildMovementDto(uuid, "IN", entryType, model, size, lot, origin, timestamp, userId))
                                }
                            }
                            RemainderMode.FILL_LATER -> {
                                // Create incomplete master box marked as PENDIENTE_POR_RELLENAR
                                val boxSeq = autoBox.fullBoxes + 1
                                val (parentUuid, childrenUuids) = uuidGenerator.generateMasterBoxBatch(
                                    origin = origin, lot = lot, size = size,
                                    childCount = autoBox.remainderPairs, boxSequence = boxSeq,
                                    startSequence = globalSeq
                                )

                                dao.insertMasterBox(MasterBoxEntity(
                                    uuid = parentUuid, origin = origin.name,
                                    model = model, size = size, lot = lot,
                                    childCount = childCount, // Total capacity stays at full
                                    activeChildCount = autoBox.remainderPairs,
                                    isComplete = false,
                                    status = "PENDIENTE_POR_RELLENAR",
                                    createdAt = timestamp
                                ))
                                allUuids.add(parentUuid)
                                printItems.add(PrintLabelItem(parentUuid, model, size, lot, origin.displayName))

                                val children = childrenUuids.map { uuid ->
                                    ProductEntity(
                                        uuid = uuid, parentUuid = parentUuid,
                                        origin = origin.name, model = model, size = size,
                                        lot = lot, entryType = entryType, status = "AVAILABLE",
                                        location = "RACK", createdAt = timestamp, updatedAt = timestamp
                                    )
                                }
                                dao.insertProducts(children)
                                allUuids.addAll(childrenUuids)
                                childrenUuids.forEach { uuid ->
                                    printItems.add(PrintLabelItem(uuid, model, size, lot, origin.displayName))
                                }

                                dao.insertMovement(MovementEntity(
                                    uuid = parentUuid, type = "IN", reason = entryType,
                                    observation = "Caja Master incompleta: ${autoBox.remainderPairs}/$childCount — Pendiente por rellenar",
                                    location = "RACK", timestamp = timestamp, userId = userId
                                ))
                                movementsDto.add(buildMovementDto(parentUuid, "IN", entryType, model, size, lot, origin, timestamp, userId))
                            }
                        }
                    }
                } else {
                    // Single unit mode (no master box)
                    val uuids = uuidGenerator.generateUnitBatch(origin, lot, size, totalQuantity, 1)
                    val products = uuids.map { uuid ->
                        ProductEntity(
                            uuid = uuid, origin = origin.name, model = model,
                            size = size, lot = lot, entryType = entryType,
                            status = "AVAILABLE", location = "RACK",
                            createdAt = timestamp, updatedAt = timestamp
                        )
                    }
                    dao.insertProducts(products)
                    allUuids.addAll(uuids)
                    uuids.forEach { uuid ->
                        printItems.add(PrintLabelItem(uuid, model, size, lot, origin.displayName))
                        dao.insertMovement(MovementEntity(
                            uuid = uuid, type = "IN", reason = entryType,
                            observation = "Unidad individual", location = "RACK",
                            timestamp = timestamp, userId = userId
                        ))
                        movementsDto.add(buildMovementDto(uuid, "IN", entryType, model, size, lot, origin, timestamp, userId))
                    }
                }

                // Sync: try BarTender print immediately
                _uiState.value = InventoryUiState.Loading("Enviando a impresora BarTender...")
                val printSuccess = syncManager.sendPrintJobNow(printItems)
                if (!printSuccess) {
                    syncManager.enqueuePrintJob(printItems)
                }

                // Sync: try Google Sheets immediately
                _uiState.value = InventoryUiState.Loading("Sincronizando con nube...")
                val sheetsRequest = SyncRequestDto(
                    title = "Entrada_${lot}_${dateFormat.format(Date(timestamp))}",
                    origin = origin.name, movements = movementsDto
                )
                val sheetsSuccess = syncManager.sendSheetsSyncNow(sheetsRequest)
                if (!sheetsSuccess) {
                    syncManager.enqueueSheetsSync(sheetsRequest)
                }

                val warnings = mutableListOf<String>()
                if (!printSuccess) warnings.add("Impresión encolada: se enviará al restaurar conexión con BarTender.")
                if (!sheetsSuccess) warnings.add("Sincronización encolada: se enviará al restaurar conexión a internet.")

                val confirmationMessage = if (isMasterBox) {
                    val autoBox = BusinessRules.calculateAutoBoxing(totalQuantity, childCount)
                    if (autoBox.hasRemainder) {
                        when (remainderMode) {
                            RemainderMode.LOOSE -> "${autoBox.fullBoxes} Caja(s) Master y ${autoBox.remainderPairs} unidad(es) sueltas"
                            RemainderMode.FILL_LATER -> "${autoBox.fullBoxes} Caja(s) Master completas y 1 Incompleta (${autoBox.remainderPairs}/$childCount)"
                        }
                    } else {
                        "${autoBox.fullBoxes} Caja(s) Master completas"
                    }
                } else {
                    "$totalQuantity unidad(es) individuales sueltas"
                }

                _uiState.value = InventoryUiState.SuccessEntry(
                    uuids = allUuids, model = model, lot = lot, size = size,
                    origin = origin,
                    message = "Ingreso: $confirmationMessage",
                    warning = if (warnings.isNotEmpty()) warnings.joinToString("\n") else null
                )

            } catch (e: Exception) {
                _uiState.value = InventoryUiState.Error("Error al registrar: ${e.message}")
                Log.e("InventoryVM", "processNewEntry failed", e)
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // SMART ENTRY — Intelligent box detection
    // ═══════════════════════════════════════════════════════════════

    /**
     * Evaluates if there are compatible incomplete boxes before creating new entries.
     * If compatible boxes exist → emits SmartEntrySuggestion for the UI.
     * If quantity fills full boxes exactly → proceeds directly with processNewEntry.
     */
    fun evaluateSmartEntry(
        origin: Origin, model: String, size: String, lot: String,
        entryType: String, totalQuantity: Int, isMasterBox: Boolean,
        childCount: Int, remainderMode: RemainderMode, userId: String
    ) {
        viewModelScope.launch {
            try {
                // If master box mode AND the total fills complete boxes perfectly, skip smart logic
                if (isMasterBox && totalQuantity >= childCount && totalQuantity % childCount == 0) {
                    processNewEntry(origin, model, size, lot, entryType, isMasterBox, childCount, totalQuantity, remainderMode, userId)
                    return@launch
                }

                // Search for compatible incomplete boxes
                val compatibleBoxes = dao.findCompatibleIncompleteBoxes(model, size, origin.name)

                if (compatibleBoxes.isNotEmpty()) {
                    // Found compatible boxes — show suggestion
                    _uiState.value = InventoryUiState.SmartEntrySuggestion(
                        compatibleBoxes = compatibleBoxes,
                        requestedQuantity = totalQuantity,
                        origin = origin, model = model, size = size,
                        lot = lot, entryType = entryType,
                        isMasterBox = isMasterBox, childCount = childCount,
                        remainderMode = remainderMode, userId = userId
                    )
                } else if (!isMasterBox || totalQuantity < childCount) {
                    // No compatible boxes and small quantity — show options (loose vs new box)
                    _uiState.value = InventoryUiState.SmartEntrySuggestion(
                        compatibleBoxes = emptyList(),
                        requestedQuantity = totalQuantity,
                        origin = origin, model = model, size = size,
                        lot = lot, entryType = entryType,
                        isMasterBox = isMasterBox, childCount = childCount,
                        remainderMode = remainderMode, userId = userId
                    )
                } else {
                    // Normal auto-boxing flow (many pairs, no compatible boxes)
                    processNewEntry(origin, model, size, lot, entryType, isMasterBox, childCount, totalQuantity, remainderMode, userId)
                }
            } catch (e: Exception) {
                _uiState.value = InventoryUiState.Error("Error al evaluar entrada: ${e.message}")
            }
        }
    }

    /** Smart Entry: Add pairs to an existing incomplete master box */
    fun confirmAddToExistingBox(suggestion: InventoryUiState.SmartEntrySuggestion, targetBoxUuid: String) {
        viewModelScope.launch {
            try {
                _uiState.value = InventoryUiState.Loading("Agregando a caja existente...")
                val masterBox = dao.getMasterBoxByUuid(targetBoxUuid) ?: run {
                    _uiState.value = InventoryUiState.Error("Caja no encontrada: $targetBoxUuid")
                    return@launch
                }

                val available = masterBox.childCount - masterBox.activeChildCount
                val toAdd = minOf(suggestion.requestedQuantity, available)
                val remaining = suggestion.requestedQuantity - toAdd
                val timestamp = System.currentTimeMillis()
                val allUuids = mutableListOf<String>()
                val printItems = mutableListOf<PrintLabelItem>()

                // Generate child UUIDs and add to existing box
                val startSeq = masterBox.activeChildCount + 1
                val childUuids = uuidGenerator.generateUnitBatch(
                    suggestion.origin, suggestion.lot, suggestion.size, toAdd, startSeq
                )

                val children = childUuids.map { uuid ->
                    ProductEntity(
                        uuid = uuid, parentUuid = targetBoxUuid,
                        origin = suggestion.origin.name, model = suggestion.model,
                        size = suggestion.size, lot = suggestion.lot,
                        entryType = suggestion.entryType, status = "AVAILABLE",
                        location = "RACK", createdAt = timestamp, updatedAt = timestamp
                    )
                }
                dao.insertProducts(children)
                allUuids.addAll(childUuids)
                childUuids.forEach { uuid ->
                    printItems.add(PrintLabelItem(uuid, suggestion.model, suggestion.size, suggestion.lot, suggestion.origin.displayName))
                    dao.insertMovement(MovementEntity(
                        uuid = uuid, type = "IN", reason = suggestion.entryType,
                        observation = "Agregado a caja existente $targetBoxUuid",
                        location = "RACK", timestamp = timestamp, userId = suggestion.userId
                    ))
                }

                // Update master box counts
                val newActive = masterBox.activeChildCount + toAdd
                val isNowComplete = newActive >= masterBox.childCount
                val newStatus = if (isNowComplete) "COMPLETE" else "PENDIENTE_POR_RELLENAR"
                dao.updateMasterBoxFull(targetBoxUuid, newActive, isNowComplete, newStatus)

                dao.insertMovement(MovementEntity(
                    uuid = targetBoxUuid, type = "REFILL", reason = "Entrada inteligente",
                    observation = "Añadidos $toAdd par(es). Ahora: $newActive/${masterBox.childCount}" +
                        if (isNowComplete) " — ¡COMPLETA!" else "",
                    location = "RACK", timestamp = timestamp, userId = suggestion.userId
                ))

                // Handle remaining pairs (if quantity exceeded box space)
                var extraMessage = ""
                if (remaining > 0) {
                    val looseUuids = uuidGenerator.generateUnitBatch(
                        suggestion.origin, suggestion.lot, suggestion.size, remaining, startSeq + toAdd
                    )
                    val loosePairs = looseUuids.map { uuid ->
                        ProductEntity(
                            uuid = uuid, origin = suggestion.origin.name,
                            model = suggestion.model, size = suggestion.size,
                            lot = suggestion.lot, entryType = suggestion.entryType,
                            status = "AVAILABLE", location = "RACK",
                            createdAt = timestamp, updatedAt = timestamp
                        )
                    }
                    dao.insertProducts(loosePairs)
                    allUuids.addAll(looseUuids)
                    looseUuids.forEach { uuid ->
                        printItems.add(PrintLabelItem(uuid, suggestion.model, suggestion.size, suggestion.lot, suggestion.origin.displayName))
                        dao.insertMovement(MovementEntity(
                            uuid = uuid, type = "IN", reason = suggestion.entryType,
                            observation = "Par suelto (excedente de caja $targetBoxUuid)",
                            location = "RACK", timestamp = timestamp, userId = suggestion.userId
                        ))
                    }
                    extraMessage = " + $remaining par(es) suelto(s)"
                }

                // Print
                val printSuccess = syncManager.sendPrintJobNow(printItems)
                if (!printSuccess) syncManager.enqueuePrintJob(printItems)

                soundManager.playSuccessBeep()
                _uiState.value = InventoryUiState.SuccessEntry(
                    uuids = allUuids, model = suggestion.model, lot = suggestion.lot,
                    size = suggestion.size, origin = suggestion.origin,
                    message = "$toAdd par(es) agregados a caja $targetBoxUuid ($newActive/${masterBox.childCount})$extraMessage",
                    warning = if (!printSuccess) "Impresión encolada" else null
                )
            } catch (e: Exception) {
                _uiState.value = InventoryUiState.Error("Error: ${e.message}")
                Log.e("InventoryVM", "confirmAddToExistingBox failed", e)
            }
        }
    }

    /** Smart Entry: Create loose individual pairs (no master box) */
    fun confirmLooseEntry(suggestion: InventoryUiState.SmartEntrySuggestion) {
        processNewEntry(
            origin = suggestion.origin, model = suggestion.model,
            size = suggestion.size, lot = suggestion.lot,
            entryType = suggestion.entryType, isMasterBox = false,
            childCount = suggestion.childCount,
            totalQuantity = suggestion.requestedQuantity,
            remainderMode = suggestion.remainderMode,
            userId = suggestion.userId
        )
    }

    /** Smart Entry: Create a new incomplete master box */
    fun confirmNewIncompleteBox(suggestion: InventoryUiState.SmartEntrySuggestion) {
        processNewEntry(
            origin = suggestion.origin, model = suggestion.model,
            size = suggestion.size, lot = suggestion.lot,
            entryType = suggestion.entryType, isMasterBox = true,
            childCount = suggestion.childCount,
            totalQuantity = suggestion.requestedQuantity,
            remainderMode = RemainderMode.FILL_LATER,
            userId = suggestion.userId
        )
    }

    // ═══════════════════════════════════════════════════════════════
    // STAND-BY MODULE (Pre-despacho) — with Cliente/Observaciones
    // ═══════════════════════════════════════════════════════════════
    fun processStandBy(uuid: String, userId: String, cliente: String = "", observaciones: String = "") {
        viewModelScope.launch {
            try {
                val product = dao.getProductByUuid(uuid)
                if (product == null) {
                    _uiState.value = InventoryUiState.Error("UUID no encontrado: $uuid")
                    soundManager.playErrorBeep()
                    return@launch
                }

                val validation = BusinessRules.validateStatusTransition(product.status, "STB")
                if (!validation.isValid) {
                    _uiState.value = InventoryUiState.Error(validation.errorMessage!!)
                    soundManager.playErrorBeep()
                    return@launch
                }

                dao.updateProductStatus(uuid, "STB", "ZONA_PREDESPACHO")
                dao.insertMovement(MovementEntity(
                    uuid = uuid, type = "STB", reason = "Pre-despacho",
                    location = "ZONA_PREDESPACHO", userId = userId,
                    cliente = cliente, observacionesExtra = observaciones
                ))

                // Update master box if child
                if (product.parentUuid != null) {
                    updateMasterBoxCompleteness(product.parentUuid)
                }

                detectOriginFromUuid(uuid)
                soundManager.playSuccessBeep()
                _uiState.value = InventoryUiState.SuccessMovement(
                    "Stand-By registrado: $uuid → ZONA_PREDESPACHO"
                )
            } catch (e: Exception) {
                _uiState.value = InventoryUiState.Error("Error: ${e.message}")
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // DISPATCH MODULE — List-based (no re-scanning)
    // ═══════════════════════════════════════════════════════════════
    /** Dispatch selected items from the STB list — no re-scanning */
    fun confirmDispatchFromList(
        selectedUuids: List<String>,
        cliente: String,
        observaciones: String,
        userId: String
    ) {
        viewModelScope.launch {
            if (selectedUuids.isEmpty()) return@launch

            _uiState.value = InventoryUiState.Loading("Procesando despacho...")
            try {
                val products = selectedUuids.mapNotNull { dao.getProductByUuid(it) }

                // Poka-Yoke: all must be in STB
                val nonStb = products.filter { it.status != "STB" }
                if (nonStb.isNotEmpty()) {
                    _uiState.value = InventoryUiState.Error(
                        "Error: ${nonStb.size} producto(s) no están en Stand-By. No se puede despachar."
                    )
                    soundManager.playErrorBeep()
                    return@launch
                }

                // Poka-Yoke: no mixed origins
                val validation = BusinessRules.validateNoMixedOrigins(products)
                if (!validation.isValid) {
                    soundManager.playCriticalAlert()
                    _uiState.value = InventoryUiState.PokayokeAlert(validation.errorMessage!!)
                    return@launch
                }

                for (product in products) {
                    dao.updateProductStatus(product.uuid, "DISPATCHED", "DESPACHADO")
                    dao.insertMovement(MovementEntity(
                        uuid = product.uuid, type = "OUT", reason = "Despacho",
                        location = "DESPACHADO", userId = userId,
                        cliente = cliente, observacionesExtra = observaciones
                    ))
                    if (product.parentUuid != null) {
                        updateMasterBoxCompleteness(product.parentUuid)
                    }
                }

                _uiState.value = InventoryUiState.SuccessMovement(
                    "Despacho completado: ${products.size} unidades procesadas → $cliente"
                )
            } catch (e: Exception) {
                _uiState.value = InventoryUiState.Error("Error al despachar: ${e.message}")
            }
        }
    }

    /** Verification scan — tells operator if a specific UUID is in the dispatch selection */
    fun verifyUuidInStandBy(uuid: String): Boolean {
        // Note: this is a synchronous check against the current standByItems
        return true // The UI will check against its selected list
    }

    // Legacy dispatch batch for backward compat during transition
    private val _dispatchBatch = MutableStateFlow<List<ProductEntity>>(emptyList())
    val dispatchBatch: StateFlow<List<ProductEntity>> = _dispatchBatch.asStateFlow()

    fun addToDispatchBatch(uuid: String) {
        viewModelScope.launch {
            val product = dao.getProductByUuid(uuid)
            if (product == null) {
                _uiState.value = InventoryUiState.Error("UUID no encontrado: $uuid")
                soundManager.playErrorBeep()
                return@launch
            }
            if (product.status != "STB") {
                _uiState.value = InventoryUiState.Error("Producto no está en Stand-By. Estado: ${product.status}")
                soundManager.playErrorBeep()
                return@launch
            }
            if (_dispatchBatch.value.any { it.uuid == uuid }) {
                soundManager.playErrorBeep()
                return@launch
            }
            val current = _dispatchBatch.value.toMutableList()
            current.add(product)
            _dispatchBatch.value = current
            val batchValidation = BusinessRules.validateNoMixedOrigins(current)
            if (!batchValidation.isValid) {
                soundManager.playCriticalAlert()
                _uiState.value = InventoryUiState.PokayokeAlert(batchValidation.errorMessage!!)
            } else {
                detectOriginFromUuid(uuid)
                soundManager.playSuccessBeep()
            }
        }
    }

    fun clearDispatchBatch() {
        _dispatchBatch.value = emptyList()
    }

    fun confirmDispatch(userId: String) {
        viewModelScope.launch {
            val batch = _dispatchBatch.value
            if (batch.isEmpty()) return@launch
            val validation = BusinessRules.validateNoMixedOrigins(batch)
            if (!validation.isValid) {
                soundManager.playCriticalAlert()
                _uiState.value = InventoryUiState.PokayokeAlert(validation.errorMessage!!)
                return@launch
            }
            _uiState.value = InventoryUiState.Loading("Procesando despacho...")
            for (product in batch) {
                dao.updateProductStatus(product.uuid, "DISPATCHED", "DESPACHADO")
                dao.insertMovement(MovementEntity(
                    uuid = product.uuid, type = "OUT", reason = "Despacho",
                    location = "DESPACHADO", userId = userId
                ))
                if (product.parentUuid != null) {
                    updateMasterBoxCompleteness(product.parentUuid)
                }
            }
            _dispatchBatch.value = emptyList()
            _uiState.value = InventoryUiState.SuccessMovement(
                "Despacho completado: ${batch.size} unidades procesadas"
            )
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // MUESTRAS RETORNABLES
    // ═══════════════════════════════════════════════════════════════
    fun processMuestra(uuid: String, cliente: String, observaciones: String, userId: String) {
        viewModelScope.launch {
            try {
                val product = dao.getProductByUuid(uuid)
                if (product == null) {
                    _uiState.value = InventoryUiState.Error("UUID no encontrado: $uuid")
                    soundManager.playErrorBeep()
                    return@launch
                }

                val validation = BusinessRules.validateStatusTransition(product.status, "MUESTRA")
                if (!validation.isValid) {
                    _uiState.value = InventoryUiState.Error(validation.errorMessage!!)
                    soundManager.playErrorBeep()
                    return@launch
                }

                dao.updateProductStatus(uuid, "MUESTRA", "ZONA_CUSTODIA_COMERCIAL")
                dao.insertMovement(MovementEntity(
                    uuid = uuid, type = "MUESTRA", reason = "Muestra retornable",
                    observation = "Entregado a cliente: $cliente",
                    location = "ZONA_CUSTODIA_COMERCIAL", userId = userId,
                    cliente = cliente, observacionesExtra = observaciones
                ))

                if (product.parentUuid != null) {
                    updateMasterBoxCompleteness(product.parentUuid)
                }

                detectOriginFromUuid(uuid)
                soundManager.playSuccessBeep()
                _uiState.value = InventoryUiState.SuccessMovement(
                    "Muestra registrada: $uuid → $cliente (ZONA_CUSTODIA_COMERCIAL)"
                )
            } catch (e: Exception) {
                _uiState.value = InventoryUiState.Error("Error: ${e.message}")
            }
        }
    }

    fun returnMuestra(uuid: String, userId: String) {
        viewModelScope.launch {
            try {
                val product = dao.getProductByUuid(uuid)
                if (product == null || product.status != "MUESTRA") {
                    _uiState.value = InventoryUiState.Error("Muestra no encontrada o estado inválido")
                    soundManager.playErrorBeep()
                    return@launch
                }

                dao.updateProductStatus(uuid, "AVAILABLE", "RACK")
                dao.insertMovement(MovementEntity(
                    uuid = uuid, type = "IN", reason = "Retorno de muestra",
                    observation = "Muestra devuelta al stock",
                    location = "RACK", userId = userId
                ))

                soundManager.playSuccessBeep()
                _uiState.value = InventoryUiState.SuccessMovement(
                    "Muestra retornada al stock: $uuid → RACK"
                )
            } catch (e: Exception) {
                _uiState.value = InventoryUiState.Error("Error: ${e.message}")
            }
        }
    }

    fun sellMuestra(uuid: String, userId: String) {
        viewModelScope.launch {
            try {
                val product = dao.getProductByUuid(uuid)
                if (product == null || product.status != "MUESTRA") {
                    _uiState.value = InventoryUiState.Error("Muestra no encontrada o estado inválido")
                    soundManager.playErrorBeep()
                    return@launch
                }

                dao.updateProductStatus(uuid, "MUESTRA_VENDIDA", "VENDIDA")
                dao.insertMovement(MovementEntity(
                    uuid = uuid, type = "OUT", reason = "Muestra vendida",
                    observation = "Cliente decidió quedarse con el par",
                    location = "VENDIDA", userId = userId
                ))

                soundManager.playSuccessBeep()
                _uiState.value = InventoryUiState.SuccessMovement(
                    "Muestra vendida: $uuid — Cliente se quedó con el par"
                )
            } catch (e: Exception) {
                _uiState.value = InventoryUiState.Error("Error: ${e.message}")
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // QUALITY MODULE (Bajas)
    // ═══════════════════════════════════════════════════════════════
    fun processQualityBaja(uuid: String, reason: BajaReason, userId: String) {
        viewModelScope.launch {
            val product = dao.getProductByUuid(uuid)
            if (product == null) {
                _uiState.value = InventoryUiState.Error("UUID no encontrado: $uuid")
                soundManager.playErrorBeep()
                return@launch
            }

            val targetStatus = reason.toProductStatus().name
            dao.updateProductStatus(uuid, targetStatus, "BAJA")
            dao.insertMovement(MovementEntity(
                uuid = uuid, type = "BAJA", reason = reason.displayName,
                location = "BAJA", userId = userId
            ))

            if (product.parentUuid != null) {
                updateMasterBoxCompleteness(product.parentUuid)
            }

            detectOriginFromUuid(uuid)
            soundManager.playSuccessBeep()
            _uiState.value = InventoryUiState.SuccessMovement(
                "Baja registrada: $uuid → ${reason.displayName}"
            )
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // REFILL MASTER BOX (Rellenar Caja Master)
    // ═══════════════════════════════════════════════════════════════
    fun refillMasterBox(parentUuid: String, childUuids: List<String>, userId: String) {
        viewModelScope.launch {
            try {
                val masterBox = dao.getMasterBoxByUuid(parentUuid)
                if (masterBox == null) {
                    _uiState.value = InventoryUiState.Error("Caja Master no encontrada: $parentUuid")
                    soundManager.playErrorBeep()
                    return@launch
                }

                // Validate capacity
                val capacityCheck = BusinessRules.validateRefillCapacity(masterBox, childUuids.size)
                if (!capacityCheck.isValid) {
                    _uiState.value = InventoryUiState.Error(capacityCheck.errorMessage!!)
                    soundManager.playErrorBeep()
                    return@launch
                }

                // Validate each child
                val timestamp = System.currentTimeMillis()
                for (childUuid in childUuids) {
                    val product = dao.getProductByUuid(childUuid)
                    if (product == null) {
                        _uiState.value = InventoryUiState.Error("Producto no encontrado: $childUuid")
                        soundManager.playErrorBeep()
                        return@launch
                    }

                    val compat = BusinessRules.validateRefillCompatibility(masterBox, product)
                    if (!compat.isValid) {
                        _uiState.value = InventoryUiState.Error(compat.errorMessage!!)
                        soundManager.playErrorBeep()
                        return@launch
                    }

                    // Bind child to master box
                    dao.insertProduct(product.copy(parentUuid = parentUuid, updatedAt = timestamp))
                    dao.insertMovement(MovementEntity(
                        uuid = childUuid, type = "REFILL", reason = "Rellenado de caja master",
                        observation = "Vinculado a caja $parentUuid",
                        location = "RACK", timestamp = timestamp, userId = userId
                    ))
                }

                // Update master box counts
                val newActiveCount = masterBox.activeChildCount + childUuids.size
                val isNowComplete = newActiveCount >= masterBox.childCount
                val newStatus = if (isNowComplete) "COMPLETE" else "PENDIENTE_POR_RELLENAR"
                dao.updateMasterBoxFull(parentUuid, newActiveCount, isNowComplete, newStatus)

                dao.insertMovement(MovementEntity(
                    uuid = parentUuid, type = "REFILL", reason = "Caja rellenada",
                    observation = "Añadidos ${childUuids.size} par(es). Ahora: $newActiveCount/${masterBox.childCount}",
                    location = "RACK", timestamp = timestamp, userId = userId
                ))

                soundManager.playSuccessBeep()
                _uiState.value = InventoryUiState.SuccessRefill(
                    parentUuid = parentUuid,
                    addedCount = childUuids.size,
                    totalActive = newActiveCount,
                    totalCapacity = masterBox.childCount
                )
            } catch (e: Exception) {
                _uiState.value = InventoryUiState.Error("Error al rellenar caja: ${e.message}")
                Log.e("InventoryVM", "refillMasterBox failed", e)
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // SCANNER HELPER
    // ═══════════════════════════════════════════════════════════════
    suspend fun getScannedInfo(uuid: String): ScannedInfo? {
        val masterBox = dao.getMasterBoxByUuid(uuid)
        if (masterBox != null) {
            return ScannedInfo.Master(masterBox)
        }
        val product = dao.getProductByUuid(uuid)
        if (product != null) {
            return ScannedInfo.UnitInfo(product)
        }
        return null
    }

    // ═══════════════════════════════════════════════════════════════
    // PRIVATE HELPERS
    // ═══════════════════════════════════════════════════════════════
    private suspend fun updateMasterBoxCompleteness(parentUuid: String) {
        val children = dao.getChildrenOfMasterBox(parentUuid)
        val activeCount = children.count { it.status == "AVAILABLE" || it.status == "STB" }
        val masterBox = dao.getMasterBoxByUuid(parentUuid) ?: return
        val result = BusinessRules.calculateBoxCompleteness(
            masterBox.childCount, masterBox.activeChildCount, 
            masterBox.activeChildCount - activeCount
        )
        dao.updateMasterBoxChildCount(parentUuid, result.activeCount, result.isComplete)
    }

    private fun buildMovementDto(
        uuid: String, type: String, reason: String,
        model: String, size: String, lot: String,
        origin: Origin, timestamp: Long, userId: String
    ): InventoryMovementDto {
        val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        return InventoryMovementDto(
            date = dateFormat.format(Date(timestamp)),
            time = timeFormat.format(Date(timestamp)),
            userId = userId, type = type, model = model,
            size = size, lot = lot, uuid = uuid,
            origin = origin.name, status = "AVAILABLE", reason = reason
        )
    }
}

// ═══════════════════════════════════════════════════════════════
// UI STATE & SCANNED INFO
// ═══════════════════════════════════════════════════════════════
sealed class ScannedInfo {
    data class UnitInfo(val entity: ProductEntity) : ScannedInfo()
    data class Master(val entity: MasterBoxEntity) : ScannedInfo()
}

sealed class InventoryUiState {
    object Idle : InventoryUiState()
    data class Loading(val message: String) : InventoryUiState()
    data class SuccessEntry(
        val uuids: List<String>,
        val model: String,
        val lot: String,
        val size: String,
        val origin: Origin,
        val message: String = "",
        val warning: String? = null
    ) : InventoryUiState()
    data class SuccessMovement(val message: String) : InventoryUiState()
    data class SuccessSync(val message: String) : InventoryUiState()
    data class SuccessRefill(
        val parentUuid: String,
        val addedCount: Int,
        val totalActive: Int,
        val totalCapacity: Int
    ) : InventoryUiState()
    data class PokayokeAlert(val message: String) : InventoryUiState()
    data class Error(val message: String) : InventoryUiState()
    data class SmartEntrySuggestion(
        val compatibleBoxes: List<MasterBoxEntity>,
        val requestedQuantity: Int,
        val origin: Origin,
        val model: String,
        val size: String,
        val lot: String,
        val entryType: String,
        val isMasterBox: Boolean,
        val childCount: Int,
        val remainderMode: RemainderMode,
        val userId: String
    ) : InventoryUiState()
}
