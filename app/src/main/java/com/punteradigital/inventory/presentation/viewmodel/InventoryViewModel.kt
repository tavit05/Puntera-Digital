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
import com.punteradigital.inventory.data.local.EmpaquePreferences

/**
 * Remainder mode when total quantity doesn't divide evenly into boxes.
 */
enum class RemainderMode {
    /** Generate individual pairs without a master box parent */
    LOOSE,
    /** Create an incomplete master box marked as PENDIENTE_POR_RELLENAR */
    FILL_LATER
}

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class InventoryViewModel @Inject constructor(
    val dao: InventoryDao,
    private val uuidGenerator: UuidGeneratorUseCase,
    val pdfGenerator: PdfGeneratorUseCase,
    private val syncManager: SyncManager,
    private val connectivityMonitor: ConnectivityMonitor,
    val soundManager: SoundManager,
    val empaquePreferences: EmpaquePreferences
) : ViewModel() {

    companion object {
        // Static app-level salt — makes rainbow-table attacks against the 10,000
        // possible 4-digit PINs infeasible without knowing this value.
        // NOTE: If this constant ever changes, all stored PINs become invalid
        // and users will need to be re-created.
        private const val PIN_SALT = "punteradigital_v1_salt"
    }

    // Cached formatters — ThreadLocal ensures thread-safety in coroutines
    private val dateFormat = ThreadLocal.withInitial { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()) }
    private val timeFormat = ThreadLocal.withInitial { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    private val _uiState = MutableStateFlow<InventoryUiState>(InventoryUiState.Idle)
    val uiState: StateFlow<InventoryUiState> = _uiState.asStateFlow()

    private val _currentUser = MutableStateFlow<UserEntity?>(null)
    val currentUser: StateFlow<UserEntity?> = _currentUser.asStateFlow()

    private val _currentOrigin = MutableStateFlow(Origin.FOOT_SAFE)
    val currentOrigin: StateFlow<Origin> = _currentOrigin.asStateFlow()

    // ═══ ALWAYS-ON FLOWS — used globally (HomeScreen counters, nav badge) ═══
    // These are intentionally eager: they are observed on every screen via the bottom AppBar.
    val inventoryStatus: Flow<List<BatchStatus>> = dao.getInventoryStatusByBatch()
    val totalAvailable: Flow<Int> = dao.getTotalAvailableCount()
    val totalStandBy: Flow<Int> = dao.getTotalStandByCount()
    val totalMasterBoxes: Flow<Int> = dao.getTotalMasterBoxCount()
    val pendingSyncCount: Flow<Int> = dao.getPendingSyncCount()

    // ═══ LAZY FLOWS — activated only when the corresponding screen is open ═══
    // Using `by lazy` means Room only registers an InvalidationTracker observer
    // when the screen that collects this flow is actually composed/visible.

    /** Home screen recent movements — LIMIT 20 to avoid loading entire table */
    val recentMovements: Flow<List<MovementEntity>> by lazy { dao.getRecentMovements(20) }

    /** Full movement history — only for Traceability tab (lazy: Room observer starts on first collect) */
    val traceabilityMovements: Flow<List<MovementEntity>> by lazy { dao.getAllMovements() }

    val weeklyMovements: Flow<List<MovementEntity>> by lazy {
        flow {
            emit(System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000L))
        }.flatMapLatest { cutoff ->
            dao.getMovementsSince(cutoff)
        }
    }

    /** Dispatch list screen — STB items */
    val standByItems: Flow<List<ProductEntity>> by lazy { dao.getStandByProducts() }

    /** Muestras screen */
    val muestrasActivas: Flow<List<ProductEntity>> by lazy { dao.getMuestrasActivas() }

    /** Refill screen — incomplete master boxes */
    val incompleteMasterBoxes: Flow<List<MasterBoxEntity>> by lazy { dao.getIncompleteMasterBoxes() }

    /** User management screen */
    val allUsers: Flow<List<UserEntity>> by lazy { dao.getAllUsers() }

    // ═══ ANALYTICS FLOWS — only needed in Traceability screen (tab 3) ═══
    val topDispatchedModels by lazy { dao.getTopDispatchedModels() }
    val topDispatchedSizes by lazy { dao.getTopDispatchedSizes() }
    val topClients by lazy { dao.getTopClients() }
    val modelStatusBreakdown by lazy { dao.getModelStatusBreakdown() }
    val allModelsInventory by lazy { dao.getAllModelsInventory() }
    val allSizesInventory by lazy { dao.getAllSizesInventory() }
    val totalDispatched by lazy { dao.getTotalDispatchedCount() }
    val totalMovements by lazy { dao.getTotalMovementCount() }

    // ═══ RACK MAP — only needed in RackMapScreen ═══
    val rackOccupancy by lazy { dao.getProductCountByLocation() }

    // ═══ QR HISTORY — only needed in QRHistoryScreen ═══
    val entryMovements by lazy { dao.getEntryMovements() }

    // ═══ QR Search State ═══
    private val _qrSearchResults = MutableStateFlow<List<ProductEntity>>(emptyList())
    val qrSearchResults: StateFlow<List<ProductEntity>> = _qrSearchResults.asStateFlow()

    // ═══ Empaque / Labels State ═══
    val pendingLabelBatches: StateFlow<List<LabelBatchSummary>> = dao.getLabelBatchSummaries()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val pendingLabelCount: StateFlow<Int> = dao.getPendingLabelCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    fun searchQRByUuid(query: String) {
        viewModelScope.launch {
            if (query.isBlank()) {
                _qrSearchResults.value = emptyList()
                return@launch
            }
            _qrSearchResults.value = dao.searchProductsByUuid(query)
        }
    }

    suspend fun getProductsAtRack(location: String): List<ProductEntity> {
        return dao.getProductsAtLocation(location)
    }

    init {
        // Auto-sync when connectivity restores — only if there are pending items
        viewModelScope.launch {
            connectivityMonitor.isOnline.collect { isOnline ->
                if (isOnline) {
                    // Check pending count first to avoid unnecessary processing
                    val pendingCount = dao.getPendingSyncItems().size
                    if (pendingCount > 0) {
                        val synced = syncManager.processPendingQueue()
                        if (synced > 0) {
                            Log.i("InventoryVM", "Auto-synced $synced/$pendingCount pending items")
                        }
                    }
                }
            }
        }
    }

    private val _lockedRack = MutableStateFlow<String?>(null)
    val lockedRack: StateFlow<String?> = _lockedRack.asStateFlow()

    private val _isRackLocked = MutableStateFlow(false)
    val isRackLocked: StateFlow<Boolean> = _isRackLocked.asStateFlow()

    fun setLockedRack(rack: String?) {
        _lockedRack.value = rack
    }

    fun setRackLocked(locked: Boolean) {
        _isRackLocked.value = locked
    }

    fun transferLocation(uuid: String, newLocation: String, userId: String) {
        viewModelScope.launch {
            _uiState.value = InventoryUiState.Loading("Procesando traslado...")
            try {
                val timestamp = System.currentTimeMillis()
                val masterBox = dao.getMasterBoxByUuid(uuid)
                if (masterBox != null) {
                    val children = dao.getChildrenOfMasterBox(uuid)
                    children.forEach { child ->
                        dao.updateProductStatus(child.uuid, child.status, newLocation, timestamp)
                        dao.insertMovement(MovementEntity(
                            uuid = child.uuid, type = "TRANSFER", reason = "Traslado Interno a $newLocation",
                            location = newLocation, timestamp = timestamp, userId = userId
                        ))
                    }
                    dao.insertMovement(MovementEntity(
                        uuid = uuid, type = "TRANSFER", reason = "Traslado Interno Caja Master a $newLocation",
                        location = newLocation, timestamp = timestamp, userId = userId
                    ))
                    soundManager.playSuccessBeep()
                    _uiState.value = InventoryUiState.SuccessMovement("Caja Master $uuid y sus ${children.size} pares trasladados a $newLocation.")
                } else {
                    val product = dao.getProductByUuid(uuid)
                    if (product != null) {
                        dao.updateProductStatus(uuid, product.status, newLocation, timestamp)
                        dao.insertMovement(MovementEntity(
                            uuid = uuid, type = "TRANSFER", reason = "Traslado Interno a $newLocation",
                            location = newLocation, timestamp = timestamp, userId = userId
                        ))
                        soundManager.playSuccessBeep()
                        _uiState.value = InventoryUiState.SuccessMovement("Producto $uuid trasladado a $newLocation.")
                    } else {
                        _uiState.value = InventoryUiState.Error("UUID no encontrado para traslado: $uuid")
                        soundManager.playErrorBeep()
                    }
                }
            } catch (e: Exception) {
                _uiState.value = InventoryUiState.Error("Error al realizar traslado: ${e.message}")
                soundManager.playErrorBeep()
            }
        }
    }

    fun adjustAuditInventory(
        location: String,
        scannedUuids: List<String>,
        missingUuids: List<String>,
        extraUuids: List<String>,
        userId: String
    ) {
        viewModelScope.launch {
            _uiState.value = InventoryUiState.Loading("Ajustando inventario de auditoría...")
            try {
                val timestamp = System.currentTimeMillis()
                
                // 1. Move extras to this location
                extraUuids.forEach { uuid ->
                    val product = dao.getProductByUuid(uuid)
                    if (product != null) {
                        dao.updateProductStatus(uuid, "AVAILABLE", location, timestamp)
                        dao.insertMovement(MovementEntity(
                            uuid = uuid, type = "TRANSFER", reason = "Ajuste Auditoría: Extra reubicado a $location",
                            location = location, timestamp = timestamp, userId = userId
                        ))
                        if (product.parentUuid != null) {
                             updateMasterBoxCompleteness(product.parentUuid)
                        }
                    }
                }

                // 2. Mark missing items as BAJA_CONTEO_CICLICO
                missingUuids.forEach { uuid ->
                    val product = dao.getProductByUuid(uuid)
                    if (product != null) {
                        dao.updateProductStatus(uuid, "BAJA_CONTEO_CICLICO", "BAJA", timestamp)
                        dao.insertMovement(MovementEntity(
                            uuid = uuid, type = "BAJA", reason = "Baja por Auditoría: Faltante en $location",
                            location = "BAJA", timestamp = timestamp, userId = userId
                        ))
                        if (product.parentUuid != null) {
                            updateMasterBoxCompleteness(product.parentUuid)
                        }
                    }
                }

                soundManager.playSuccessBeep()
                _uiState.value = InventoryUiState.SuccessMovement("Ajuste de auditoría completado en rack $location. Extras reubicados: ${extraUuids.size}, Faltantes dados de baja: ${missingUuids.size}.")
            } catch (e: Exception) {
                _uiState.value = InventoryUiState.Error("Error al ajustar inventario de auditoría: ${e.message}")
                soundManager.playErrorBeep()
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

    fun logout() {
        _currentUser.value = null
        _uiState.value = InventoryUiState.Idle
        _currentOrigin.value = Origin.FOOT_SAFE
    }

    fun resetUiState() {
        _uiState.value = InventoryUiState.Idle
    }

    fun setUiError(message: String) {
        _uiState.value = InventoryUiState.Error(message)
    }

    // ═══════════════════════════════════════════════════════════════
    // USER MANAGEMENT (Admin-only CRUD)
    // ═══════════════════════════════════════════════════════════════
    // NOTE: allUsers is declared above as a lazy Flow (line ~80)

    fun createUser(name: String, pin: String, role: String) {
        viewModelScope.launch {
            try {
                // Check if PIN already exists
                val hashedPin = hashPin(pin)
                val existing = dao.getUserByPin(hashedPin)
                if (existing != null) {
                    _uiState.value = InventoryUiState.Error("Ya existe un usuario con ese PIN.")
                    return@launch
                }

                val id = name.lowercase().replace(" ", "_") + "_" + System.currentTimeMillis().toString().takeLast(4)
                val user = UserEntity(
                    id = id,
                    name = name,
                    pin = hashedPin,
                    role = role
                )
                dao.insertUser(user)
                _uiState.value = InventoryUiState.SuccessMovement("Usuario '$name' creado exitosamente con rol $role")
            } catch (e: Exception) {
                _uiState.value = InventoryUiState.Error("Error al crear usuario: ${e.message}")
            }
        }
    }

    fun deleteUser(userId: String) {
        viewModelScope.launch {
            try {
                val user = dao.getUserById(userId)
                if (user == null) {
                    _uiState.value = InventoryUiState.Error("Usuario no encontrado.")
                    return@launch
                }

                // Prevent deleting the last admin
                if (user.role == "ADMIN") {
                    val adminCount = dao.getAdminCount()
                    if (adminCount <= 1) {
                        _uiState.value = InventoryUiState.Error("No se puede eliminar el último administrador del sistema.")
                        return@launch
                    }
                }

                // Prevent self-deletion
                if (_currentUser.value?.id == userId) {
                    _uiState.value = InventoryUiState.Error("No puedes eliminarte a ti mismo.")
                    return@launch
                }

                dao.deleteUser(userId)
                _uiState.value = InventoryUiState.SuccessMovement("Usuario '${user.name}' eliminado.")
            } catch (e: Exception) {
                _uiState.value = InventoryUiState.Error("Error al eliminar usuario: ${e.message}")
            }
        }
    }

    /** Dynamic PIN-based authentication against the database */
    suspend fun authenticateByPin(pin: String): UserEntity? {
        val hashedPin = hashPin(pin)
        val user = dao.getUserByPin(hashedPin)
        if (user != null) return user

        // Fail-safe self-healing: If default PINs are used but not yet in database
        // (e.g. database reset, migration, or first launch race condition),
        // seed them dynamically on the fly to guarantee the user can ALWAYS log in.
        if (pin == "1234") {
            val defaultAdmin = UserEntity(
                id = "admin_default",
                name = "Administrador",
                pin = hashedPin,
                role = "ADMIN"
            )
            dao.insertUser(defaultAdmin)
            Log.i("InventoryVM", "Self-healed and logged in default Admin user")
            return defaultAdmin
        }
        if (pin == "0000") {
            val defaultOperator = UserEntity(
                id = "operador_default",
                name = "Operador",
                pin = hashedPin,
                role = "OPERADOR"
            )
            dao.insertUser(defaultOperator)
            Log.i("InventoryVM", "Self-healed and logged in default Operator user")
            return defaultOperator
        }
        if (pin == "8888") {
            val defaultEmpaque = UserEntity(
                id = "empaque_default",
                name = "Operador Empaque",
                pin = hashedPin,
                role = "OPERADOR_EMPAQUE"
            )
            dao.insertUser(defaultEmpaque)
            Log.i("InventoryVM", "Self-healed and logged in default Empaque operator")
            return defaultEmpaque
        }
        return null
    }

    /** Seed the default admin user if the users table is empty */
    fun seedDefaultAdminIfNeeded() {
        viewModelScope.launch {
            // Check if default admin exists
            val admin = dao.getUserById("admin_default")
            if (admin == null) {
                dao.insertUser(UserEntity(
                    id = "admin_default",
                    name = "Administrador",
                    pin = hashPin("1234"),
                    role = "ADMIN"
                ))
                Log.i("InventoryVM", "Seeded default admin user")
            }
            
            // Check if default operator exists
            val operator = dao.getUserById("operador_default")
            if (operator == null) {
                dao.insertUser(UserEntity(
                    id = "operador_default",
                    name = "Operador",
                    pin = hashPin("0000"),
                    role = "OPERADOR"
                ))
                Log.i("InventoryVM", "Seeded default operator user")
            }

            // Check if default empaque operator exists
            val empaque = dao.getUserById("empaque_default")
            if (empaque == null) {
                dao.insertUser(UserEntity(
                    id = "empaque_default",
                    name = "Operador Empaque",
                    pin = hashPin("8888"),
                    role = "OPERADOR_EMPAQUE"
                ))
                Log.i("InventoryVM", "Seeded default empaque operator user")
            }
        }
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
                    title = "Entrada_${lot}_${dateFormat.get()?.format(Date(timestamp))}",
                    origin = origin.name, movements = movementsDto
                )
                val sheetsSuccess = syncManager.sendSheetsSyncNow(sheetsRequest)
                if (!sheetsSuccess) {
                    syncManager.enqueueSheetsSync(sheetsRequest)
                }

                val warnings = mutableListOf<String>()
                if (!printSuccess) warnings.add("Impresión encolada: se enviará al restaurar conexión con BarTender.")
                if (!sheetsSuccess) warnings.add("Sincronización encolada: se enviará al restaurar conexión a internet.")

                // Reuse the autoBox already calculated at the start of the if(isMasterBox) block
                val confirmationMessage = if (isMasterBox) {
                    val boxSummary = BusinessRules.calculateAutoBoxing(totalQuantity, childCount)
                    buildString {
                        append("${boxSummary.fullBoxes} Caja(s) Master")
                        if (boxSummary.hasRemainder) {
                            when (remainderMode) {
                                RemainderMode.LOOSE -> append(" y ${boxSummary.remainderPairs} unidad(es) sueltas")
                                RemainderMode.FILL_LATER -> append(" completas y 1 Incompleta (${boxSummary.remainderPairs}/$childCount)")
                            }
                        } else {
                            append(" completas")
                        }
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
    // EMPAQUE (Pre-Entry Label Generation)
    // ═══════════════════════════════════════════════════════════════

    fun generateLabels(
        origin: Origin,
        model: String,
        size: String,
        lot: String,
        labelType: String,
        labelFormat: String,
        isMasterBox: Boolean,
        childCount: Int,
        totalQuantity: Int,
        userId: String
    ) {
        viewModelScope.launch {
            _uiState.value = InventoryUiState.Loading("Generando etiquetas de empaque...")
            try {
                val timestamp = System.currentTimeMillis()
                val batchId = "BATCH-${dateFormat.get()?.format(Date(timestamp))}-${timestamp}"
                val labels = mutableListOf<LabelEntity>()
                val printItems = mutableListOf<PrintLabelItem>()

                if (isMasterBox) {
                    val autoBox = BusinessRules.calculateAutoBoxing(totalQuantity, childCount)
                    var globalSeq = 1

                    for (boxIdx in 1..autoBox.fullBoxes) {
                        val (parentUuid, childrenUuids) = uuidGenerator.generateMasterBoxBatch(
                            origin, lot, size, childCount, boxIdx, globalSeq
                        )
                        labels.add(LabelEntity(
                            uuid = parentUuid, batchId = batchId, origin = origin.name,
                            model = model, size = size, lot = lot, labelType = "MASTER_BOX",
                            labelFormat = labelFormat, createdBy = userId, createdAt = timestamp
                        ))
                        printItems.add(PrintLabelItem(parentUuid, model, size, lot, origin.displayName))

                        childrenUuids.forEach { childUuid ->
                            labels.add(LabelEntity(
                                uuid = childUuid, batchId = batchId, origin = origin.name,
                                model = model, size = size, lot = lot, labelType = "INDIVIDUAL",
                                labelFormat = labelFormat, parentLabelUuid = parentUuid,
                                createdBy = userId, createdAt = timestamp
                            ))
                            printItems.add(PrintLabelItem(childUuid, model, size, lot, origin.displayName))
                        }
                        globalSeq += childCount
                    }
                    if (autoBox.hasRemainder) {
                        val looseUuids = uuidGenerator.generateUnitBatch(
                            origin, lot, size, autoBox.remainderPairs, globalSeq
                        )
                        looseUuids.forEach { childUuid ->
                            labels.add(LabelEntity(
                                uuid = childUuid, batchId = batchId, origin = origin.name,
                                model = model, size = size, lot = lot, labelType = "INDIVIDUAL",
                                labelFormat = labelFormat, createdBy = userId, createdAt = timestamp
                            ))
                            printItems.add(PrintLabelItem(childUuid, model, size, lot, origin.displayName))
                        }
                    }
                } else {
                    val uuids = uuidGenerator.generateUnitBatch(origin, lot, size, totalQuantity, 1)
                    uuids.forEach { uuid ->
                        labels.add(LabelEntity(
                            uuid = uuid, batchId = batchId, origin = origin.name,
                            model = model, size = size, lot = lot, labelType = "INDIVIDUAL",
                            labelFormat = labelFormat, createdBy = userId, createdAt = timestamp
                        ))
                        printItems.add(PrintLabelItem(uuid, model, size, lot, origin.displayName))
                    }
                }

                dao.insertLabels(labels)

                _uiState.value = InventoryUiState.Loading("Enviando a impresora BarTender...")
                val printSuccess = syncManager.sendPrintJobNow(printItems)
                if (!printSuccess) {
                    syncManager.enqueuePrintJob(printItems)
                }

                // Update status to PRINTED
                labels.forEach { 
                    dao.markLabelPrinted(it.uuid, System.currentTimeMillis())
                }

                _uiState.value = InventoryUiState.SuccessMovement(
                    "Se generaron ${labels.size} etiquetas (Lote: $batchId)." + 
                    if (!printSuccess) "\nImpresión encolada offline." else ""
                )

            } catch (e: Exception) {
                _uiState.value = InventoryUiState.Error("Error al generar etiquetas: ${e.message}")
                Log.e("InventoryVM", "generateLabels failed", e)
            }
        }
    }

    fun getLabelsByBatch(batchId: String): Flow<List<LabelEntity>> {
        return dao.getLabelsByBatch(batchId)
    }

    fun deleteLabelBatch(batchId: String) {
        viewModelScope.launch {
            try {
                dao.deleteLabelBatch(batchId)
                _uiState.value = InventoryUiState.SuccessMovement("Lote de etiquetas eliminado con éxito.")
            } catch (e: Exception) {
                _uiState.value = InventoryUiState.Error("Error al eliminar lote: ${e.message}")
            }
        }
    }

    fun reprintLabelBatch(batchId: String) {
        viewModelScope.launch {
            _uiState.value = InventoryUiState.Loading("Reimprimiendo lote...")
            try {
                val labels = dao.getLabelsByBatch(batchId).first()
                val printItems = labels.map { 
                    PrintLabelItem(it.uuid, it.model, it.size, it.lot, Origin.fromString(it.origin).displayName) 
                }
                val success = syncManager.sendPrintJobNow(printItems)
                if (!success) syncManager.enqueuePrintJob(printItems)
                
                _uiState.value = InventoryUiState.SuccessMovement("Lote reenviado a impresora.")
            } catch (e: Exception) {
                _uiState.value = InventoryUiState.Error("Error al reimprimir: ${e.message}")
            }
        }
    }

    fun confirmPreEntryBatch(batchId: String, userId: String) {
        viewModelScope.launch {
            _uiState.value = InventoryUiState.Loading("Confirmando entrada al almacén...")
            try {
                val labels = dao.getPendingLabelsByBatchSync(batchId)
                if (labels.isEmpty()) {
                    _uiState.value = InventoryUiState.Error("No hay etiquetas pendientes en este lote.")
                    return@launch
                }

                val timestamp = System.currentTimeMillis()
                val products = mutableListOf<ProductEntity>()
                val masterBoxes = mutableListOf<MasterBoxEntity>()
                val movements = mutableListOf<MovementEntity>()
                val movementsDto = mutableListOf<InventoryMovementDto>()

                // Process labels into real inventory
                val masterLabels = labels.filter { it.labelType == "MASTER_BOX" }
                val childLabels = labels.filter { it.labelType == "INDIVIDUAL" }

                masterLabels.forEach { ml ->
                    val childrenCount = childLabels.count { it.parentLabelUuid == ml.uuid }
                    masterBoxes.add(MasterBoxEntity(
                        uuid = ml.uuid, origin = ml.origin, model = ml.model, size = ml.size, lot = ml.lot,
                        childCount = childrenCount, activeChildCount = childrenCount,
                        isComplete = true, status = "COMPLETE", createdAt = timestamp
                    ))
                    movements.add(MovementEntity(
                        uuid = ml.uuid, type = "IN", reason = "PRODUCCION",
                        observation = "Ingreso desde Empaque (Lote: $batchId)",
                        location = "RACK", timestamp = timestamp, userId = userId
                    ))
                    movementsDto.add(buildMovementDto(ml.uuid, "IN", "PRODUCCION", ml.model, ml.size, ml.lot, Origin.fromString(ml.origin), timestamp, userId))
                }

                childLabels.forEach { cl ->
                    products.add(ProductEntity(
                        uuid = cl.uuid, parentUuid = cl.parentLabelUuid, origin = cl.origin,
                        model = cl.model, size = cl.size, lot = cl.lot, entryType = "PRODUCCION",
                        status = "AVAILABLE", location = "RACK", createdAt = timestamp, updatedAt = timestamp
                    ))
                    // Only log movement for loose pairs
                    if (cl.parentLabelUuid == null) {
                        movements.add(MovementEntity(
                            uuid = cl.uuid, type = "IN", reason = "PRODUCCION",
                            observation = "Ingreso individual desde Empaque (Lote: $batchId)",
                            location = "RACK", timestamp = timestamp, userId = userId
                        ))
                        movementsDto.add(buildMovementDto(cl.uuid, "IN", "PRODUCCION", cl.model, cl.size, cl.lot, Origin.fromString(cl.origin), timestamp, userId))
                    }
                }

                dao.insertMasterBoxes(masterBoxes)
                dao.insertProducts(products)
                dao.insertMovements(movements)
                dao.markBatchEntered(batchId, userId, timestamp)

                // Sync Google Sheets
                val originStr = labels.first().origin
                val lot = labels.first().lot
                val sheetsRequest = SyncRequestDto(
                    title = "Entrada_${lot}_${dateFormat.get()?.format(Date(timestamp))}",
                    origin = originStr, movements = movementsDto
                )
                if (!syncManager.sendSheetsSyncNow(sheetsRequest)) {
                    syncManager.enqueueSheetsSync(sheetsRequest)
                }

                _uiState.value = InventoryUiState.SuccessEntry(
                    uuids = labels.map { it.uuid }, model = labels.first().model, 
                    lot = lot, size = labels.first().size, origin = Origin.fromString(originStr),
                    message = "Entrada confirmada correctamente para ${labels.size} unidades.",
                    warning = null
                )

            } catch (e: Exception) {
                _uiState.value = InventoryUiState.Error("Error al confirmar entrada: ${e.message}")
                Log.e("InventoryVM", "confirmPreEntryBatch failed", e)
            }
        }
    }

    fun confirmLabelEntry(uuid: String, location: String, userId: String, checkedChildUuids: List<String>? = null) {
        viewModelScope.launch {
            _uiState.value = InventoryUiState.Loading("Registrando entrada por escaneo...")
            try {
                val label = dao.getLabelByUuid(uuid)
                if (label == null) {
                    _uiState.value = InventoryUiState.Error("Etiqueta no encontrada en el sistema pre-registro.")
                    soundManager.playErrorBeep()
                    return@launch
                }

                if (label.status == "ENTERED") {
                    _uiState.value = InventoryUiState.Error("Esta etiqueta ya fue ingresada al almacén anteriormente.")
                    soundManager.playErrorBeep()
                    return@launch
                }

                val timestamp = System.currentTimeMillis()
                val products = mutableListOf<ProductEntity>()
                val masterBoxes = mutableListOf<MasterBoxEntity>()
                val movements = mutableListOf<MovementEntity>()
                val movementsDto = mutableListOf<InventoryMovementDto>()
                
                val origin = Origin.fromString(label.origin)

                if (label.labelType == "MASTER_BOX") {
                    // It's a master box. Find all child labels generated in the same batch
                    val childLabels = dao.getChildrenLabels(label.uuid)
                    val activeChildLabels = if (checkedChildUuids != null) {
                        childLabels.filter { it.uuid in checkedChildUuids }
                    } else {
                        childLabels
                    }
                    val activeCount = activeChildLabels.size
                    val isComplete = activeCount == childLabels.size

                    masterBoxes.add(MasterBoxEntity(
                        uuid = label.uuid,
                        origin = label.origin,
                        model = label.model,
                        size = label.size,
                        lot = label.lot,
                        childCount = childLabels.size,
                        activeChildCount = activeCount,
                        isComplete = isComplete,
                        status = if (isComplete) "COMPLETE" else "PENDIENTE_POR_RELLENAR",
                        createdAt = timestamp
                    ))

                    activeChildLabels.forEach { cl ->
                        products.add(ProductEntity(
                            uuid = cl.uuid,
                            parentUuid = label.uuid,
                            origin = cl.origin,
                            model = cl.model,
                            size = cl.size,
                            lot = cl.lot,
                            entryType = "PRODUCCION",
                            status = "AVAILABLE",
                            location = location,
                            createdAt = timestamp,
                            updatedAt = timestamp
                        ))
                        dao.markLabelEntered(cl.uuid, userId, timestamp)
                    }

                    // Delete unchecked labels so they don't stay pending forever
                    if (checkedChildUuids != null) {
                        val unchecked = childLabels.filter { it.uuid !in checkedChildUuids }
                        unchecked.forEach { cl ->
                            dao.deleteLabel(cl.uuid)
                        }
                    }

                    movements.add(MovementEntity(
                        uuid = label.uuid,
                        type = "IN",
                        reason = "PRODUCCION",
                        observation = "Ingreso Caja Master escaneada (Lote: ${label.lot}, $activeCount pares)" + if (!isComplete) " — Incompleta" else "",
                        location = location,
                        timestamp = timestamp,
                        userId = userId
                    ))

                    movementsDto.add(buildMovementDto(label.uuid, "IN", "PRODUCCION", label.model, label.size, label.lot, origin, timestamp, userId))

                } else {
                    // Individual pair entry
                    products.add(ProductEntity(
                        uuid = label.uuid,
                        parentUuid = label.parentLabelUuid,
                        origin = label.origin,
                        model = label.model,
                        size = label.size,
                        lot = label.lot,
                        entryType = "PRODUCCION",
                        status = "AVAILABLE",
                        location = location,
                        createdAt = timestamp,
                        updatedAt = timestamp
                    ))

                    movements.add(MovementEntity(
                        uuid = label.uuid,
                        type = "IN",
                        reason = "PRODUCCION",
                        observation = "Ingreso Individual escaneado (Lote: ${label.lot})",
                        location = location,
                        timestamp = timestamp,
                        userId = userId
                    ))

                    movementsDto.add(buildMovementDto(label.uuid, "IN", "PRODUCCION", label.model, label.size, label.lot, origin, timestamp, userId))
                }

                // Database updates
                dao.insertMasterBoxes(masterBoxes)
                dao.insertProducts(products)
                dao.insertMovements(movements)
                dao.markLabelEntered(label.uuid, userId, timestamp)

                // Sync Google Sheets
                val sheetsRequest = SyncRequestDto(
                    title = "Entrada_Escaneo_${label.lot}_${dateFormat.get()?.format(Date(timestamp))}",
                    origin = label.origin,
                    movements = movementsDto
                )
                if (!syncManager.sendSheetsSyncNow(sheetsRequest)) {
                    syncManager.enqueueSheetsSync(sheetsRequest)
                }

                detectOriginFromUuid(label.uuid)
                soundManager.playSuccessBeep()

                val quantityMessage = if (label.labelType == "MASTER_BOX") {
                    "Caja Master con ${products.size} pares"
                } else {
                    "1 par individual"
                }

                _uiState.value = InventoryUiState.SuccessEntry(
                    uuids = products.map { it.uuid } + if (label.labelType == "MASTER_BOX") listOf(label.uuid) else emptyList(),
                    model = label.model,
                    lot = label.lot,
                    size = label.size,
                    origin = origin,
                    message = "Ingreso por Escaneo exitoso: $quantityMessage registrado en Rack $location.",
                    warning = null
                )

            } catch (e: Exception) {
                _uiState.value = InventoryUiState.Error("Error al confirmar ingreso por escaneo: ${e.message}")
                Log.e("InventoryVM", "confirmLabelEntry failed", e)
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

    /** Confirm sales order delivery — marks scanned items as DISPATCHED in Room */
    fun confirmPedidoDelivery(
        selectedUuids: List<String>,
        cliente: String,
        userId: String
    ) {
        viewModelScope.launch {
            if (selectedUuids.isEmpty()) return@launch
            _uiState.value = InventoryUiState.Loading("Confirmando entrega de pedido...")
            try {
                val products = selectedUuids.mapNotNull { dao.getProductByUuid(it) }
                val timestamp = System.currentTimeMillis()
                for (product in products) {
                    dao.updateProductStatus(product.uuid, "DISPATCHED", "DESPACHADO")
                    dao.insertMovement(MovementEntity(
                        uuid = product.uuid, type = "OUT", reason = "Entrega Pedido Venta",
                        location = "DESPACHADO", timestamp = timestamp, userId = userId,
                        cliente = cliente, observacionesExtra = "Entrega de pedido"
                    ))
                    if (product.parentUuid != null) {
                        updateMasterBoxCompleteness(product.parentUuid)
                    }
                }
                _uiState.value = InventoryUiState.SuccessMovement(
                    "Pedido entregado: ${products.size} unidades despachadas."
                )
            } catch (e: Exception) {
                _uiState.value = InventoryUiState.Error("Error al entregar pedido: ${e.message}")
            }
        }
    }

    /** Register return of a sales order — marks scanned items back as AVAILABLE in Room */
    fun confirmPedidoReturn(
        selectedUuids: List<String>,
        userId: String
    ) {
        viewModelScope.launch {
            if (selectedUuids.isEmpty()) return@launch
            _uiState.value = InventoryUiState.Loading("Registrando retorno de pedido...")
            try {
                val products = selectedUuids.mapNotNull { dao.getProductByUuid(it) }
                val timestamp = System.currentTimeMillis()
                for (product in products) {
                    dao.updateProductStatus(product.uuid, "AVAILABLE", "RACK")
                    dao.insertMovement(MovementEntity(
                        uuid = product.uuid, type = "IN", reason = "Retorno Pedido Venta",
                        location = "RACK", timestamp = timestamp, userId = userId,
                        observacionesExtra = "Retorno de pedido devuelto al stock"
                    ))
                    if (product.parentUuid != null) {
                        updateMasterBoxCompleteness(product.parentUuid)
                    }
                }
                _uiState.value = InventoryUiState.SuccessMovement(
                    "Retorno registrado: ${products.size} unidades devueltas al stock."
                )
            } catch (e: Exception) {
                _uiState.value = InventoryUiState.Error("Error al retornar pedido: ${e.message}")
            }
        }
    }

    // verifyUuidInStandBy was removed — it was dead code (always returned true).
    // The UI checks against its own selected list directly.
    // Legacy dispatch batch system (addToDispatchBatch, confirmDispatch, clearDispatchBatch)
    // was removed — superseded by confirmDispatchFromList().

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
            try {
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
            } catch (e: Exception) {
                _uiState.value = InventoryUiState.Error("Error al registrar baja: ${e.message}")
                soundManager.playErrorBeep()
            }
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
        Log.d("InventoryVM", "getScannedInfo lookup: '$uuid'")

        // Try exact master box match
        val masterBox = dao.getMasterBoxByUuid(uuid)
        if (masterBox != null) {
            Log.d("InventoryVM", "Found as MasterBox: ${masterBox.uuid} (${masterBox.model} T.${masterBox.size})")
            return ScannedInfo.Master(masterBox)
        }

        // Try exact product match
        val product = dao.getProductByUuid(uuid)
        if (product != null) {
            Log.d("InventoryVM", "Found as Product: ${product.uuid} (${product.model} T.${product.size} status=${product.status})")
            return ScannedInfo.UnitInfo(product)
        }

        // Try label match (Pre-entry empaque label)
        val label = dao.getLabelByUuid(uuid)
        if (label != null) {
            Log.d("InventoryVM", "Found as Label: ${label.uuid} (${label.model} T.${label.size} status=${label.status})")
            return ScannedInfo.Label(label)
        }

        // Try trimmed UUID (sometimes QR readers add whitespace/newlines)
        val trimmedUuid = uuid.trim()
        if (trimmedUuid != uuid) {
            Log.d("InventoryVM", "Retrying with trimmed UUID: '$trimmedUuid'")
            val trimmedProduct = dao.getProductByUuid(trimmedUuid)
            if (trimmedProduct != null) {
                return ScannedInfo.UnitInfo(trimmedProduct)
            }
            val trimmedBox = dao.getMasterBoxByUuid(trimmedUuid)
            if (trimmedBox != null) {
                return ScannedInfo.Master(trimmedBox)
            }
            val trimmedLabel = dao.getLabelByUuid(trimmedUuid)
            if (trimmedLabel != null) {
                return ScannedInfo.Label(trimmedLabel)
            }
        }

        Log.w("InventoryVM", "UUID NOT FOUND in database: '$uuid' (length=${uuid.length})")
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

    private fun hashPin(pin: String): String {
        val salted = "${PIN_SALT}:${pin}"
        return java.security.MessageDigest.getInstance("SHA-256")
            .digest(salted.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    private fun buildMovementDto(
        uuid: String, type: String, reason: String,
        model: String, size: String, lot: String,
        origin: Origin, timestamp: Long, userId: String
    ): InventoryMovementDto {
        // Uses class-level dateFormat/timeFormat to avoid repeated construction
        return InventoryMovementDto(
            date = dateFormat.get()?.format(Date(timestamp)) ?: "",
            time = timeFormat.get()?.format(Date(timestamp)) ?: "",
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
    data class Label(val entity: LabelEntity) : ScannedInfo()
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
