package com.zagot.zagotplus.integration

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.zagot.zagotplus.data.local.ZagotDatabase
import com.zagot.zagotplus.data.local.entity.LocationEntity
import com.zagot.zagotplus.data.local.entity.ProductEntity
import com.zagot.zagotplus.data.local.entity.TransactionEntity
import com.zagot.zagotplus.data.remote.dto.CashOperationDto
import com.zagot.zagotplus.data.remote.dto.ExpenseCategoryDto
import com.zagot.zagotplus.data.remote.dto.LocationDto
import com.zagot.zagotplus.data.remote.dto.ProductDto
import com.zagot.zagotplus.data.remote.dto.PurchaseBatchDto
import com.zagot.zagotplus.data.remote.dto.SaleBatchDto
import com.zagot.zagotplus.data.remote.dto.TransactionDto
import com.zagot.zagotplus.domain.model.LocationType
import com.zagot.zagotplus.sync.SyncDataSource
import com.zagot.zagotplus.sync.SyncPreferences
import com.zagot.zagotplus.sync.SyncResult
import com.zagot.zagotplus.sync.SyncService
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.UUID

/**
 * Integration tests for timezone handling in sync scenarios.
 * 
 * Tests ensure that devices in different timezones can sync correctly:
 * 1. Timestamps are stored in UTC (epoch millis) and are timezone-agnostic
 * 2. ISO-8601 strings with various timezone formats parse correctly
 * 3. server_updated_at from Supabase (always UTC) is used for sync filtering
 * 4. Devices don't miss records due to timezone differences
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class TimezoneHandlingIntegrationTest {

    private lateinit var database: ZagotDatabase
    private lateinit var fakeSyncDataSource: FakeSyncDataSource
    private lateinit var syncPreferences: SyncPreferences
    private lateinit var syncService: SyncService
    private lateinit var context: Context

    // Test data IDs
    private val locationId = UUID.fromString("11111111-1111-1111-1111-111111111111")
    private val productId = UUID.fromString("22222222-2222-2222-2222-222222222222")

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()

        database = Room.inMemoryDatabaseBuilder(context, ZagotDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        fakeSyncDataSource = FakeSyncDataSource()
        syncPreferences = mockk(relaxed = true)
        every { syncPreferences.getLastSyncTimestamp() } returns Instant.EPOCH

        syncService = SyncService(
            database = database,
            syncDataSource = fakeSyncDataSource,
            transactionDao = database.transactionDao(),
            purchaseBatchDao = database.purchaseBatchDao(),
            saleBatchDao = database.saleBatchDao(),
            locationDao = database.locationDao(),
            productDao = database.productDao(),
            expenseCategoryDao = database.expenseCategoryDao(),
            cashOperationDao = database.cashOperationDao(),
            syncPreferences = syncPreferences
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    // ==================== Setup Helpers ====================

    private suspend fun insertRequiredEntities() {
        database.locationDao().insert(
            LocationEntity(
                id = locationId,
                name = "Склад Рівне",
                type = LocationType.KIOSK.name,
                createdAt = Instant.EPOCH
            )
        )
        database.productDao().insert(
            ProductEntity(
                id = productId,
                localId = "product-local",
                name = "Горіх",
                defaultBuyPrice = BigDecimal("45.00"),
                defaultSellPrice = BigDecimal("55.00"),
                isActive = true,
                createdAt = Instant.EPOCH
            )
        )
        // Pre-populate fake remote with required reference data
        fakeSyncDataSource.seedLocations(listOf(
            LocationDto(
                id = locationId.toString(),
                name = "Склад Рівне",
                type = LocationType.KIOSK.name,
                createdAt = Instant.EPOCH.toString()
            )
        ))
        fakeSyncDataSource.seedProducts(listOf(
            ProductDto(
                id = productId.toString(),
                localId = "product-local",
                name = "Горіх",
                defaultBuyPrice = 45.0,
                defaultSellPrice = 55.0,
                isActive = true,
                createdAt = Instant.EPOCH.toString()
            )
        ))
    }

    // ==================== ISO-8601 Timezone Parsing Tests ====================

    @Test
    fun `DTO parses UTC timestamp correctly`() {
        val utcTimestamp = "2024-01-15T10:30:00Z"
        val dto = TransactionDto(
            id = UUID.randomUUID().toString(),
            localId = "tx-utc",
            locationId = locationId.toString(),
            type = "purchase",
            transferLocationId = null,
            productId = productId.toString(),
            weightKg = 10.0,
            pricePerKg = 45.0,
            totalAmount = 450.0,
            notes = null,
            deviceId = "device-1",
            createdAt = utcTimestamp,
            syncedAt = utcTimestamp,
            serverUpdatedAt = utcTimestamp,
            batchId = null,
            saleBatchId = null
        )

        val entity = dto.toEntity()

        // UTC timestamp should parse to the correct Instant
        val expected = Instant.parse("2024-01-15T10:30:00Z")
        assertEquals(expected, entity.createdAt)
        assertEquals(expected, entity.syncedAt)
    }

    @Test
    fun `DTO parses positive offset timestamp correctly`() {
        // Kyiv timezone (UTC+2)
        val kyivTimestamp = "2024-01-15T12:30:00+02:00"
        val dto = TransactionDto(
            id = UUID.randomUUID().toString(),
            localId = "tx-kyiv",
            locationId = locationId.toString(),
            type = "purchase",
            transferLocationId = null,
            productId = productId.toString(),
            weightKg = 10.0,
            pricePerKg = 45.0,
            totalAmount = 450.0,
            notes = null,
            deviceId = "device-kyiv",
            createdAt = kyivTimestamp,
            syncedAt = kyivTimestamp,
            serverUpdatedAt = "2024-01-15T10:30:00Z", // Server always UTC
            batchId = null,
            saleBatchId = null
        )

        val entity = dto.toEntity()

        // 12:30 in Kyiv (UTC+2) = 10:30 UTC
        val expected = Instant.parse("2024-01-15T10:30:00Z")
        assertEquals(expected, entity.createdAt)
    }

    @Test
    fun `DTO parses negative offset timestamp correctly`() {
        // New York timezone (UTC-5)
        val nyTimestamp = "2024-01-15T05:30:00-05:00"
        val dto = TransactionDto(
            id = UUID.randomUUID().toString(),
            localId = "tx-ny",
            locationId = locationId.toString(),
            type = "sale",
            transferLocationId = null,
            productId = productId.toString(),
            weightKg = 5.0,
            pricePerKg = 55.0,
            totalAmount = 275.0,
            notes = null,
            deviceId = "device-ny",
            createdAt = nyTimestamp,
            syncedAt = nyTimestamp,
            serverUpdatedAt = "2024-01-15T10:30:00Z",
            batchId = null,
            saleBatchId = null
        )

        val entity = dto.toEntity()

        // 05:30 in NY (UTC-5) = 10:30 UTC
        val expected = Instant.parse("2024-01-15T10:30:00Z")
        assertEquals(expected, entity.createdAt)
    }

    // ==================== Sync Timestamp Tests ====================

    @Test
    fun `sync uses server_updated_at for next sync timestamp`() = runTest {
        insertRequiredEntities()
        val timestampSlot = slot<Instant>()
        every { syncPreferences.setLastSyncTimestamp(capture(timestampSlot)) } returns Unit

        // Add a remote transaction with server_updated_at
        val serverTime = Instant.parse("2024-01-15T15:00:00Z")
        fakeSyncDataSource.transactions.add(
            TransactionDto(
                id = UUID.randomUUID().toString(),
                localId = "tx-remote-1",
                locationId = locationId.toString(),
                type = "purchase",
                transferLocationId = null,
                productId = productId.toString(),
                weightKg = 20.0,
                pricePerKg = 45.0,
                totalAmount = 900.0,
                notes = null,
                deviceId = "other-device",
                createdAt = "2024-01-15T12:00:00+03:00", // Different timezone
                syncedAt = "2024-01-15T12:00:00+03:00",
                serverUpdatedAt = serverTime.toString(), // Server UTC time
                batchId = null,
                saleBatchId = null
            )
        )

        val result = syncService.sync()

        assertTrue(result is SyncResult.Success)
        verify { syncPreferences.setLastSyncTimestamp(any()) }
        // The saved timestamp should be the server_updated_at, not the createdAt
        assertEquals(serverTime, timestampSlot.captured)
    }

    @Test
    fun `devices in different timezones sync same transactions`() = runTest {
        insertRequiredEntities()

        // Simulate transactions created by devices in different timezones
        // All represent the same moment in time
        val utcTime = Instant.parse("2024-01-15T10:00:00Z")

        // Device in Kyiv (UTC+2) - local time 12:00
        val kyivTx = TransactionDto(
            id = UUID.randomUUID().toString(),
            localId = "tx-kyiv-device",
            locationId = locationId.toString(),
            type = "purchase",
            transferLocationId = null,
            productId = productId.toString(),
            weightKg = 10.0,
            pricePerKg = 45.0,
            totalAmount = 450.0,
            notes = "Created in Kyiv",
            deviceId = "device-kyiv",
            createdAt = "2024-01-15T12:00:00+02:00",
            syncedAt = utcTime.toString(),
            serverUpdatedAt = utcTime.toString(),
            batchId = null,
            saleBatchId = null
        )

        // Device in London (UTC+0) - local time 10:00
        val londonTx = TransactionDto(
            id = UUID.randomUUID().toString(),
            localId = "tx-london-device",
            locationId = locationId.toString(),
            type = "sale",
            transferLocationId = null,
            productId = productId.toString(),
            weightKg = 5.0,
            pricePerKg = 55.0,
            totalAmount = 275.0,
            notes = "Created in London",
            deviceId = "device-london",
            createdAt = "2024-01-15T10:00:00Z",
            syncedAt = utcTime.toString(),
            serverUpdatedAt = utcTime.plusSeconds(1).toString(),
            batchId = null,
            saleBatchId = null
        )

        fakeSyncDataSource.transactions.addAll(listOf(kyivTx, londonTx))

        val result = syncService.sync()

        assertTrue(result is SyncResult.Success)
        assertEquals(2, (result as SyncResult.Success).pulled)

        // Both transactions should be in local database
        val localTransactions = database.transactionDao().getAllPaginated(limit = 1000, offset = 0)
        assertEquals(2, localTransactions.size)

        // Verify both have the correct UTC timestamp (same moment in time)
        val kyivEntity = localTransactions.find { it.localId == "tx-kyiv-device" }
        val londonEntity = localTransactions.find { it.localId == "tx-london-device" }

        assertNotNull(kyivEntity)
        assertNotNull(londonEntity)
        assertEquals(utcTime, kyivEntity!!.createdAt)
        assertEquals(utcTime, londonEntity!!.createdAt)
    }

    @Test
    fun `transaction near midnight syncs correctly across timezone boundaries`() = runTest {
        insertRequiredEntities()

        // Transaction created at 23:30 Kyiv time (Jan 15) = 21:30 UTC (Jan 15)
        // For a London device, this is still Jan 15
        val lateNightKyivTx = TransactionDto(
            id = UUID.randomUUID().toString(),
            localId = "tx-late-night",
            locationId = locationId.toString(),
            type = "purchase",
            transferLocationId = null,
            productId = productId.toString(),
            weightKg = 15.0,
            pricePerKg = 45.0,
            totalAmount = 675.0,
            notes = "Late night Kyiv purchase",
            deviceId = "device-kyiv-late",
            createdAt = "2024-01-15T23:30:00+02:00",
            syncedAt = "2024-01-15T21:30:00Z",
            serverUpdatedAt = "2024-01-15T21:30:00Z",
            batchId = null,
            saleBatchId = null
        )

        // Transaction created at 00:30 Kyiv time (Jan 16) = 22:30 UTC (Jan 15)
        // For a London device, this is still Jan 15!
        val earlyMorningKyivTx = TransactionDto(
            id = UUID.randomUUID().toString(),
            localId = "tx-early-morning",
            locationId = locationId.toString(),
            type = "purchase",
            transferLocationId = null,
            productId = productId.toString(),
            weightKg = 20.0,
            pricePerKg = 45.0,
            totalAmount = 900.0,
            notes = "Early morning Kyiv purchase (Jan 16 local)",
            deviceId = "device-kyiv-early",
            createdAt = "2024-01-16T00:30:00+02:00",
            syncedAt = "2024-01-15T22:30:00Z",
            serverUpdatedAt = "2024-01-15T22:30:00Z",
            batchId = null,
            saleBatchId = null
        )

        fakeSyncDataSource.transactions.addAll(listOf(lateNightKyivTx, earlyMorningKyivTx))

        val result = syncService.sync()

        assertTrue(result is SyncResult.Success)
        assertEquals(2, (result as SyncResult.Success).pulled)

        val localTransactions = database.transactionDao().getAllPaginated(limit = 1000, offset = 0)
        assertEquals(2, localTransactions.size)

        // Verify UTC timestamps are stored correctly
        val lateNight = localTransactions.find { it.localId == "tx-late-night" }
        val earlyMorning = localTransactions.find { it.localId == "tx-early-morning" }

        // 23:30+02:00 = 21:30 UTC
        assertEquals(Instant.parse("2024-01-15T21:30:00Z"), lateNight!!.createdAt)
        // 00:30+02:00 = 22:30 UTC (still Jan 15!)
        assertEquals(Instant.parse("2024-01-15T22:30:00Z"), earlyMorning!!.createdAt)

        // Both are on Jan 15 in UTC, even though one is Jan 16 in Kyiv
        val jan15Start = Instant.parse("2024-01-15T00:00:00Z")
        val jan16Start = Instant.parse("2024-01-16T00:00:00Z")
        assertTrue(lateNight.createdAt.isAfter(jan15Start) && lateNight.createdAt.isBefore(jan16Start))
        assertTrue(earlyMorning.createdAt.isAfter(jan15Start) && earlyMorning.createdAt.isBefore(jan16Start))
    }

    @Test
    fun `incremental sync does not miss records due to timezone`() = runTest {
        insertRequiredEntities()
        val timestampSlot = slot<Instant>()
        every { syncPreferences.setLastSyncTimestamp(capture(timestampSlot)) } returns Unit

        // First sync - one transaction
        val firstServerTime = Instant.parse("2024-01-15T10:00:00Z")
        fakeSyncDataSource.transactions.add(
            TransactionDto(
                id = UUID.randomUUID().toString(),
                localId = "tx-first",
                locationId = locationId.toString(),
                type = "purchase",
                transferLocationId = null,
                productId = productId.toString(),
                weightKg = 10.0,
                pricePerKg = 45.0,
                totalAmount = 450.0,
                notes = null,
                deviceId = "device-1",
                createdAt = "2024-01-15T12:00:00+02:00",
                syncedAt = firstServerTime.toString(),
                serverUpdatedAt = firstServerTime.toString(),
                batchId = null,
                saleBatchId = null
            )
        )

        val firstResult = syncService.sync()
        assertTrue(firstResult is SyncResult.Success)
        assertEquals(1, (firstResult as SyncResult.Success).pulled)

        // Update syncPreferences to return the saved timestamp for next sync
        every { syncPreferences.getLastSyncTimestamp() } returns timestampSlot.captured

        // Second sync - new transaction with later server_updated_at
        val secondServerTime = Instant.parse("2024-01-15T11:00:00Z")
        fakeSyncDataSource.transactions.add(
            TransactionDto(
                id = UUID.randomUUID().toString(),
                localId = "tx-second",
                locationId = locationId.toString(),
                type = "sale",
                transferLocationId = null,
                productId = productId.toString(),
                weightKg = 5.0,
                pricePerKg = 55.0,
                totalAmount = 275.0,
                notes = null,
                deviceId = "device-2",
                createdAt = "2024-01-15T06:00:00-05:00", // NY time (11:00 UTC)
                syncedAt = secondServerTime.toString(),
                serverUpdatedAt = secondServerTime.toString(),
                batchId = null,
                saleBatchId = null
            )
        )

        val secondResult = syncService.sync()

        assertTrue(secondResult is SyncResult.Success)
        // Should only pull the new transaction, not the first one again
        // (assuming FakeSyncDataSource properly filters by since timestamp)
        val localTransactions = database.transactionDao().getAllPaginated(limit = 1000, offset = 0)
        assertEquals(2, localTransactions.size)
    }

    // ==================== Edge Cases ====================

    @Test
    fun `handles timestamp with milliseconds`() {
        val timestampWithMillis = "2024-01-15T10:30:00.123Z"
        val dto = TransactionDto(
            id = UUID.randomUUID().toString(),
            localId = "tx-millis",
            locationId = locationId.toString(),
            type = "purchase",
            transferLocationId = null,
            productId = productId.toString(),
            weightKg = 10.0,
            pricePerKg = 45.0,
            totalAmount = 450.0,
            notes = null,
            deviceId = "device-1",
            createdAt = timestampWithMillis,
            syncedAt = timestampWithMillis,
            serverUpdatedAt = timestampWithMillis,
            batchId = null,
            saleBatchId = null
        )

        val entity = dto.toEntity()

        // Should preserve milliseconds
        val expected = Instant.parse("2024-01-15T10:30:00.123Z")
        assertEquals(expected, entity.createdAt)
    }

    @Test
    fun `handles timestamp with microseconds from Supabase`() {
        // Supabase often returns timestamps with microsecond precision
        val timestampWithMicros = "2024-01-15T10:30:00.123456Z"
        val dto = TransactionDto(
            id = UUID.randomUUID().toString(),
            localId = "tx-micros",
            locationId = locationId.toString(),
            type = "purchase",
            transferLocationId = null,
            productId = productId.toString(),
            weightKg = 10.0,
            pricePerKg = 45.0,
            totalAmount = 450.0,
            notes = null,
            deviceId = "device-1",
            createdAt = timestampWithMicros,
            syncedAt = timestampWithMicros,
            serverUpdatedAt = timestampWithMicros,
            batchId = null,
            saleBatchId = null
        )

        val entity = dto.toEntity()

        // Should handle microseconds (Instant.parse supports them)
        val expected = Instant.parse("2024-01-15T10:30:00.123456Z")
        assertEquals(expected, entity.createdAt)
    }

    @Test
    fun `entity stores epoch millis which is timezone agnostic`() = runTest {
        insertRequiredEntities()

        // Create a local transaction
        val txId = UUID.randomUUID()
        val now = Instant.now()
        database.transactionDao().insert(
            TransactionEntity(
                id = txId,
                localId = "tx-local",
                locationId = locationId,
                type = "purchase",
                transferLocationId = null,
                productId = productId,
                weightKg = BigDecimal("10.00"),
                pricePerKg = BigDecimal("45.00"),
                totalAmount = BigDecimal("450.00"),
                notes = null,
                deviceId = "test-device",
                createdAt = now,
                syncedAt = null,
                batchId = null,
                saleBatchId = null
            )
        )

        // Read it back
        val retrieved = database.transactionDao().getByLocalId("tx-local")

        assertNotNull(retrieved)
        // The timestamp should be exactly the same regardless of system timezone
        assertEquals(now.toEpochMilli(), retrieved!!.createdAt.toEpochMilli())
    }
}
