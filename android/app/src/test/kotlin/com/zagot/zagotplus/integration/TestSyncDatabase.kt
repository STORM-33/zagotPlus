package com.zagot.zagotplus.integration

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Upsert
import com.zagot.syncengine.db.SyncMetadataDao
import com.zagot.syncengine.db.SyncMetadataEntity
import com.zagot.syncengine.db.SyncOutboxDao
import com.zagot.syncengine.db.SyncOutboxEntity

@Entity(tableName = "test_products")
data class TestProductEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "server_updated_at") val serverUpdatedAt: String,
)

@Dao
interface TestProductDao {
    @Upsert
    suspend fun upsertAll(entities: List<TestProductEntity>)

    @Query("SELECT * FROM test_products WHERE id = :id")
    suspend fun getById(id: String): TestProductEntity?

    @Query("SELECT * FROM test_products")
    suspend fun getAll(): List<TestProductEntity>

    @Query("DELETE FROM test_products WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Database(
    entities = [SyncOutboxEntity::class, SyncMetadataEntity::class, TestProductEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class TestSyncDatabase : RoomDatabase() {
    abstract fun syncOutboxDao(): SyncOutboxDao
    abstract fun syncMetadataDao(): SyncMetadataDao
    abstract fun testProductDao(): TestProductDao
}
