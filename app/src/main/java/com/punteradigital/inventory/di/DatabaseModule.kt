package com.punteradigital.inventory.di

import android.content.Context
import androidx.room.Room
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

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): InventoryDatabase {
        return Room.databaseBuilder(
            context,
            InventoryDatabase::class.java,
            "inventory_db"
        )
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun provideInventoryDao(database: InventoryDatabase): InventoryDao {
        return database.inventoryDao()
    }
}
