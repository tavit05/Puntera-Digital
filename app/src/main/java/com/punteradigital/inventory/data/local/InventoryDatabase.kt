package com.punteradigital.inventory.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.punteradigital.inventory.data.local.dao.InventoryDao
import com.punteradigital.inventory.data.local.entity.*

@Database(
    entities = [
        ProductEntity::class,
        MasterBoxEntity::class,
        MovementEntity::class,
        SyncQueueItem::class,
        UserEntity::class
    ],
    version = 5,   // v5: Added indices on products(status, parentUuid, origin+status),
                   //     master_boxes(isComplete+status, model+size+origin),
                   //     movements(type+timestamp, uuid)
    exportSchema = false
)
abstract class InventoryDatabase : RoomDatabase() {
    abstract fun inventoryDao(): InventoryDao
}
