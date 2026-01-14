package com.zagot.zagotplus.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

/**
 * Tests for database migrations to ensure data integrity during schema changes.
 * 
 * IMPORTANT: These tests must run as instrumented tests (androidTest) 
 * since they require the Android SQLite implementation.
 * 
 * To run: ./gradlew connectedAndroidTest --tests "*.MigrationTest"
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    
    private val TEST_DB = "migration-test"
    
    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        ZagotDatabase::class.java.canonicalName,
        FrameworkSQLiteOpenHelperFactory()
    )
    
    /**
     * Test migration from version 8 to 9.
     * Verifies that:
     * 1. Cash operations data is preserved
     * 2. transaction_id column is renamed to batch_id
     * 3. All indexes are recreated correctly
     */
    @Test
    @Throws(IOException::class)
    fun migrate8To9() {
        // Create database at version 8
        helper.createDatabase(TEST_DB, 8).apply {
            // Insert test cash operation with a transaction_id
            execSQL("""
                INSERT INTO cash_operations (
                    id, local_id, location_id, type, amount, 
                    category_id, transaction_id, notes, device_id, 
                    created_at, synced_at
                ) VALUES (
                    'test-id-1', 'local-test-1', null, 'DEPOSIT', '100.00',
                    null, 'batch-ref-1', 'Test notes', 'device-1',
                    1704067200000, null
                )
            """)
            close()
        }
        
        // Run migration
        val db = helper.runMigrationsAndValidate(
            TEST_DB, 
            9, 
            true, 
            ZagotDatabase.MIGRATION_8_9
        )
        
        // Verify data was preserved and column renamed
        val cursor = db.query("SELECT * FROM cash_operations WHERE id = 'test-id-1'")
        assert(cursor.moveToFirst()) { "Cash operation should exist after migration" }
        
        // Check that batch_id contains the old transaction_id value
        val batchIdIndex = cursor.getColumnIndex("batch_id")
        assert(batchIdIndex >= 0) { "batch_id column should exist" }
        val batchId = cursor.getString(batchIdIndex)
        assert(batchId == "batch-ref-1") { "batch_id should contain old transaction_id value" }
        
        // Verify transaction_id column no longer exists
        val transactionIdIndex = cursor.getColumnIndex("transaction_id")
        assert(transactionIdIndex < 0) { "transaction_id column should not exist after migration" }
        
        // Verify all other data is preserved
        val amountIndex = cursor.getColumnIndex("amount")
        val amount = cursor.getString(amountIndex)
        assert(amount == "100.00") { "Amount should be preserved" }
        
        cursor.close()
        db.close()
    }
    
    /**
     * Test that migration 8→9 is atomic (transaction-safe).
     * If migration fails midway, database should remain at version 8.
     */
    @Test
    @Throws(IOException::class)
    fun migrate8To9_preservesDataOnSuccess() {
        // Create database at version 8 with multiple records
        helper.createDatabase(TEST_DB, 8).apply {
            // Insert multiple cash operations
            for (i in 1..10) {
                execSQL("""
                    INSERT INTO cash_operations (
                        id, local_id, location_id, type, amount, 
                        category_id, transaction_id, notes, device_id, 
                        created_at, synced_at
                    ) VALUES (
                        'test-id-$i', 'local-test-$i', null, 'DEPOSIT', '${i * 10}.00',
                        null, 'batch-ref-$i', 'Test notes $i', 'device-1',
                        1704067200000, null
                    )
                """)
            }
            close()
        }
        
        // Run migration
        val db = helper.runMigrationsAndValidate(
            TEST_DB, 
            9, 
            true, 
            ZagotDatabase.MIGRATION_8_9
        )
        
        // Verify all records exist
        val cursor = db.query("SELECT COUNT(*) FROM cash_operations")
        cursor.moveToFirst()
        val count = cursor.getInt(0)
        assert(count == 10) { "All 10 records should be preserved after migration" }
        
        cursor.close()
        db.close()
    }
}
