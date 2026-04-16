package com.punteradigital.inventory.data.local.dao

import androidx.room.*
import com.punteradigital.inventory.data.local.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface InventoryDao {

    // ═══════════════════════════════════════════════════════════════
    // PRODUCTS (Individual Units)
    // ═══════════════════════════════════════════════════════════════
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProduct(product: ProductEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProducts(products: List<ProductEntity>)

    @Query("SELECT * FROM products WHERE uuid = :uuid LIMIT 1")
    suspend fun getProductByUuid(uuid: String): ProductEntity?

    @Query("SELECT * FROM products WHERE parentUuid = :parentUuid")
    suspend fun getChildrenOfMasterBox(parentUuid: String): List<ProductEntity>

    @Query("UPDATE products SET status = :status, location = :location, updatedAt = :timestamp WHERE uuid = :uuid")
    suspend fun updateProductStatus(uuid: String, status: String, location: String, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE products SET status = :status, location = :location, updatedAt = :timestamp WHERE parentUuid = :parentUuid AND status = 'AVAILABLE'")
    suspend fun updateChildrenStatus(parentUuid: String, status: String, location: String, timestamp: Long = System.currentTimeMillis())

    @Query("SELECT * FROM products WHERE status = 'AVAILABLE'")
    fun getAvailableProducts(): Flow<List<ProductEntity>>

    @Query("SELECT COUNT(*) FROM products WHERE status = 'AVAILABLE'")
    fun getTotalAvailableCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM products WHERE status = 'AVAILABLE' AND origin = :origin")
    fun getAvailableCountByOrigin(origin: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM products WHERE status = 'STB'")
    fun getTotalStandByCount(): Flow<Int>

    @Query("""
        SELECT lot as batch, model, size, COUNT(*) as count 
        FROM products 
        WHERE status = 'AVAILABLE' 
        GROUP BY lot, model, size
    """)
    fun getInventoryStatusByBatch(): Flow<List<BatchStatus>>

    @Query("""
        SELECT lot as batch, model, size, COUNT(*) as count
        FROM products
        WHERE status = :status
        GROUP BY lot, model, size
    """)
    fun getStatusGrouped(status: String): Flow<List<BatchStatus>>

    // ═══════════════════════════════════════════════════════════════
    // MASTER BOXES
    // ═══════════════════════════════════════════════════════════════
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMasterBox(box: MasterBoxEntity)

    @Query("SELECT * FROM master_boxes WHERE uuid = :uuid LIMIT 1")
    suspend fun getMasterBoxByUuid(uuid: String): MasterBoxEntity?

    @Query("UPDATE master_boxes SET activeChildCount = :count, isComplete = :isComplete WHERE uuid = :uuid")
    suspend fun updateMasterBoxChildCount(uuid: String, count: Int, isComplete: Boolean)

    @Query("UPDATE master_boxes SET activeChildCount = :count, isComplete = :isComplete, status = :status WHERE uuid = :uuid")
    suspend fun updateMasterBoxFull(uuid: String, count: Int, isComplete: Boolean, status: String)

    @Query("SELECT COUNT(*) FROM master_boxes")
    fun getTotalMasterBoxCount(): Flow<Int>

    @Query("SELECT * FROM master_boxes WHERE isComplete = 0 OR status = 'PENDIENTE_POR_RELLENAR'")
    fun getIncompleteMasterBoxes(): Flow<List<MasterBoxEntity>>

    /** Smart Entry: find incomplete boxes matching model+size+origin */
    @Query("""
        SELECT * FROM master_boxes 
        WHERE model = :model AND size = :size AND origin = :origin 
        AND (isComplete = 0 OR status = 'PENDIENTE_POR_RELLENAR')
        ORDER BY activeChildCount DESC
    """)
    suspend fun findCompatibleIncompleteBoxes(model: String, size: String, origin: String): List<MasterBoxEntity>

    // ═══════════════════════════════════════════════════════════════
    // MOVEMENTS (Traceability)
    // ═══════════════════════════════════════════════════════════════
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMovement(movement: MovementEntity)

    @Query("SELECT * FROM movements ORDER BY timestamp DESC")
    fun getAllMovements(): Flow<List<MovementEntity>>

    @Query("SELECT * FROM movements ORDER BY timestamp DESC")
    suspend fun getAllMovementsSync(): List<MovementEntity>

    @Query("SELECT * FROM movements WHERE uuid = :uuid ORDER BY timestamp DESC")
    suspend fun getMovementsByUuid(uuid: String): List<MovementEntity>

    // ═══════════════════════════════════════════════════════════════
    // STAND-BY / DISPATCH QUERIES
    // ═══════════════════════════════════════════════════════════════
    @Query("SELECT * FROM products WHERE status = 'STB' ORDER BY updatedAt DESC")
    fun getStandByProducts(): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products WHERE status = 'MUESTRA' ORDER BY updatedAt DESC")
    fun getMuestrasActivas(): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products WHERE parentUuid IS NULL AND status = 'AVAILABLE' AND model = :model AND size = :size")
    suspend fun getAvailableLooseProducts(model: String, size: String): List<ProductEntity>

    // ═══════════════════════════════════════════════════════════════
    // SYNC QUEUE (Offline-First)
    // ═══════════════════════════════════════════════════════════════
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSyncItem(item: SyncQueueItem)

    @Query("SELECT * FROM sync_queue WHERE status = 'PENDING' ORDER BY createdAt ASC")
    suspend fun getPendingSyncItems(): List<SyncQueueItem>

    @Query("UPDATE sync_queue SET status = 'SYNCED', syncedAt = :syncedAt WHERE id = :id")
    suspend fun markSynced(id: Int, syncedAt: Long = System.currentTimeMillis())

    @Query("UPDATE sync_queue SET status = 'FAILED', retryCount = retryCount + 1 WHERE id = :id")
    suspend fun markFailed(id: Int)

    @Query("SELECT COUNT(*) FROM sync_queue WHERE status = 'PENDING'")
    fun getPendingSyncCount(): Flow<Int>

    // ═══════════════════════════════════════════════════════════════
    // USERS
    // ═══════════════════════════════════════════════════════════════
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserEntity)

    @Query("SELECT * FROM users WHERE pin = :pin LIMIT 1")
    suspend fun getUserByPin(pin: String): UserEntity?

    // ═══════════════════════════════════════════════════════════════
    // ANALYTICS (Traceability Intelligence)
    // ═══════════════════════════════════════════════════════════════

    /** Top models ranked by dispatched count */
    @Query("SELECT model, COUNT(*) as count FROM products WHERE status = 'DISPATCHED' GROUP BY model ORDER BY count DESC")
    fun getTopDispatchedModels(): Flow<List<ModelCount>>

    /** Top sizes ranked by dispatched count */
    @Query("SELECT size, COUNT(*) as count FROM products WHERE status = 'DISPATCHED' GROUP BY size ORDER BY count DESC")
    fun getTopDispatchedSizes(): Flow<List<SizeCount>>

    /** Top clients ranked by dispatch order volume */
    @Query("SELECT cliente, COUNT(*) as count FROM movements WHERE type = 'OUT' AND cliente != '' GROUP BY cliente ORDER BY count DESC")
    fun getTopClients(): Flow<List<ClientCount>>

    /** Inventory breakdown: model × status matrix */
    @Query("SELECT model, status, COUNT(*) as count FROM products GROUP BY model, status")
    fun getModelStatusBreakdown(): Flow<List<ModelStatusBreakdown>>

    /** All models with available (in-stock) count */
    @Query("SELECT model, COUNT(*) as count FROM products WHERE status = 'AVAILABLE' GROUP BY model ORDER BY count DESC")
    fun getAllModelsInventory(): Flow<List<ModelCount>>

    /** All sizes with available count, ordered numerically */
    @Query("SELECT size, COUNT(*) as count FROM products WHERE status = 'AVAILABLE' GROUP BY size ORDER BY CAST(size AS INTEGER)")
    fun getAllSizesInventory(): Flow<List<SizeCount>>

    /** Total dispatched products */
    @Query("SELECT COUNT(*) FROM products WHERE status = 'DISPATCHED'")
    fun getTotalDispatchedCount(): Flow<Int>

    /** Total movement records */
    @Query("SELECT COUNT(*) FROM movements")
    fun getTotalMovementCount(): Flow<Int>
}

data class BatchStatus(
    val batch: String,
    val model: String,
    val size: String,
    val count: Int
)
