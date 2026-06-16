package com.punteradigital.inventory.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.punteradigital.inventory.data.local.InventoryDatabase
import com.punteradigital.inventory.data.local.dao.InventoryDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    /**
     * Migration v5 → v6: Creates the `labels` table for the Empaque (Pre-Entry) module.
     */
    private val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `labels` (
                    `uuid` TEXT NOT NULL PRIMARY KEY,
                    `batchId` TEXT NOT NULL,
                    `origin` TEXT NOT NULL,
                    `model` TEXT NOT NULL,
                    `size` TEXT NOT NULL,
                    `lot` TEXT NOT NULL,
                    `labelType` TEXT NOT NULL,
                    `labelFormat` TEXT NOT NULL,
                    `parentLabelUuid` TEXT,
                    `status` TEXT NOT NULL DEFAULT 'CREATED',
                    `createdBy` TEXT NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    `printedAt` INTEGER,
                    `enteredAt` INTEGER,
                    `enteredBy` TEXT
                )
            """.trimIndent())
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_labels_status` ON `labels` (`status`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_labels_model_size` ON `labels` (`model`, `size`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_labels_batchId` ON `labels` (`batchId`)")
        }
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): InventoryDatabase {
        return Room.databaseBuilder(
            context,
            InventoryDatabase::class.java,
            "inventory_db"
        )
            .addMigrations(MIGRATION_5_6)
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun provideInventoryDao(database: InventoryDatabase): InventoryDao {
        return database.inventoryDao()
    }
}
