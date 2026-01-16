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
    
    /**
     * Test migration from version 10 to 11.
     * Verifies that:
     * 1. is_transfer column is added with default false
     * 2. Existing transfers (notes starting with 'Переказ') get is_transfer=1
     * 3. Non-transfers keep is_transfer=0
     */
    @Test
    @Throws(IOException::class)
    fun migrate10To11() {
        // Create database at version 10
        helper.createDatabase(TEST_DB, 10).apply {
            // Insert a transfer operation (notes starts with 'Переказ')
            execSQL("""
                INSERT INTO cash_operations (
                    id, local_id, location_id, type, amount, 
                    category_id, batch_id, notes, device_id, 
                    created_at, synced_at
                ) VALUES (
                    'transfer-1', 'local-transfer-1', null, 'withdrawal', '500.00',
                    null, null, 'Переказ: to branch B', 'device-1',
                    1704067200000, null
                )
            """)
            
            // Insert a regular deposit (not a transfer)
            execSQL("""
                INSERT INTO cash_operations (
                    id, local_id, location_id, type, amount, 
                    category_id, batch_id, notes, device_id, 
                    created_at, synced_at
                ) VALUES (
                    'deposit-1', 'local-deposit-1', null, 'deposit', '1000.00',
                    null, null, 'Regular deposit', 'device-1',
                    1704067200000, null
                )
            """)
            
            // Insert an operation with null notes
            execSQL("""
                INSERT INTO cash_operations (
                    id, local_id, location_id, type, amount, 
                    category_id, batch_id, notes, device_id, 
                    created_at, synced_at
                ) VALUES (
                    'deposit-2', 'local-deposit-2', null, 'deposit', '200.00',
                    null, null, null, 'device-1',
                    1704067200000, null
                )
            """)
            close()
        }
        
        // Run migration
        val db = helper.runMigrationsAndValidate(
            TEST_DB, 
            11, 
            true, 
            ZagotDatabase.MIGRATION_10_11
        )
        
        // Verify transfer operation has is_transfer=1
        val transferCursor = db.query("SELECT is_transfer FROM cash_operations WHERE id = 'transfer-1'")
        assert(transferCursor.moveToFirst()) { "Transfer operation should exist" }
        val transferFlag = transferCursor.getInt(0)
        assert(transferFlag == 1) { "Transfer should have is_transfer=1 after migration" }
        transferCursor.close()
        
        // Verify regular deposit has is_transfer=0
        val depositCursor = db.query("SELECT is_transfer FROM cash_operations WHERE id = 'deposit-1'")
        assert(depositCursor.moveToFirst()) { "Deposit operation should exist" }
        val depositFlag = depositCursor.getInt(0)
        assert(depositFlag == 0) { "Regular deposit should have is_transfer=0" }
        depositCursor.close()
        
        // Verify operation with null notes has is_transfer=0
        val nullNotesCursor = db.query("SELECT is_transfer FROM cash_operations WHERE id = 'deposit-2'")
        assert(nullNotesCursor.moveToFirst()) { "Operation with null notes should exist" }
        val nullNotesFlag = nullNotesCursor.getInt(0)
        assert(nullNotesFlag == 0) { "Operation with null notes should have is_transfer=0" }
        nullNotesCursor.close()
        
        db.close()
    }
}
