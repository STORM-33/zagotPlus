package com.zagot.zagotplus.testutil

import com.zagot.zagotplus.domain.model.InventoryItem
import com.zagot.zagotplus.domain.model.Location
import com.zagot.zagotplus.domain.model.LocationType
import com.zagot.zagotplus.domain.model.Product
import com.zagot.zagotplus.domain.model.PurchaseBatch
import com.zagot.zagotplus.domain.model.Transaction
import com.zagot.zagotplus.domain.model.TransactionType
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * Shared test data fixtures to reduce duplication across tests.
 * All test data uses Ukrainian locale strings to match production usage.
 */
object TestData {

    // Fixed UUIDs for predictable test behavior
    val PRODUCT_ID_1: UUID = UUID.fromString("11111111-1111-1111-1111-111111111111")
    val PRODUCT_ID_2: UUID = UUID.fromString("22222222-2222-2222-2222-222222222222")
    val PRODUCT_ID_3: UUID = UUID.fromString("33333333-3333-3333-3333-333333333333")
    val LOCATION_ID_1: UUID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")
    val LOCATION_ID_2: UUID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb")
    val TRANSACTION_ID_1: UUID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc")
    val BATCH_ID_1: UUID = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd")

    // Standard test timestamp
    val TEST_INSTANT: Instant = Instant.parse("2024-01-15T10:30:00Z")

    // Products
    fun createProduct(
        id: UUID = PRODUCT_ID_1,
        name: String = "Горіх білий",
        defaultBuyPrice: BigDecimal = BigDecimal("45.00"),
        defaultSellPrice: BigDecimal = BigDecimal("55.00"),
        isActive: Boolean = true,
        createdAt: Instant = TEST_INSTANT
    ) = Product(
        id = id,
        name = name,
        defaultBuyPrice = defaultBuyPrice,
        defaultSellPrice = defaultSellPrice,
        isActive = isActive,
        createdAt = createdAt
    )

    val PRODUCT_WHITE_WALNUT = createProduct()
    val PRODUCT_RED_WALNUT = createProduct(
        id = PRODUCT_ID_2,
        name = "Горіх червоний",
        defaultBuyPrice = BigDecimal("50.00"),
        defaultSellPrice = BigDecimal("65.00")
    )
    val PRODUCT_SUNFLOWER = createProduct(
        id = PRODUCT_ID_3,
        name = "Насіння соняшника",
        defaultBuyPrice = BigDecimal("15.00"),
        defaultSellPrice = BigDecimal("20.00")
    )

    fun allProducts() = listOf(PRODUCT_WHITE_WALNUT, PRODUCT_RED_WALNUT, PRODUCT_SUNFLOWER)

    // Locations
    fun createLocation(
        id: UUID = LOCATION_ID_1,
        name: String = "Склад №1",
        type: LocationType = LocationType.KIOSK,
        createdAt: Instant = TEST_INSTANT
    ) = Location(
        id = id,
        name = name,
        type = type,
        createdAt = createdAt
    )

    val LOCATION_WAREHOUSE_1 = createLocation()
    val LOCATION_WAREHOUSE_2 = createLocation(
        id = LOCATION_ID_2,
        name = "Склад №2"
    )

    fun allLocations() = listOf(LOCATION_WAREHOUSE_1, LOCATION_WAREHOUSE_2)

    // Transactions
    fun createTransaction(
        id: UUID = TRANSACTION_ID_1,
        localId: String = "local-${id}",
        locationId: UUID? = LOCATION_ID_1,
        type: TransactionType = TransactionType.PURCHASE,
        transferLocationId: UUID? = null,
        productId: UUID = PRODUCT_ID_1,
        weightKg: BigDecimal = BigDecimal("100.00"),
        pricePerKg: BigDecimal = BigDecimal("45.00"),
        totalAmount: BigDecimal = BigDecimal("4500.00"),
        notes: String? = null,
        deviceId: String? = "test-device",
        createdAt: Instant = TEST_INSTANT,
        syncedAt: Instant? = null,
        batchId: UUID? = null
    ) = Transaction(
        id = id,
        localId = localId,
        locationId = locationId,
        type = type,
        transferLocationId = transferLocationId,
        productId = productId,
        weightKg = weightKg,
        pricePerKg = pricePerKg,
        totalAmount = totalAmount,
        notes = notes,
        deviceId = deviceId,
        createdAt = createdAt,
        syncedAt = syncedAt,
        batchId = batchId
    )

    fun createSaleTransaction(
        id: UUID = UUID.randomUUID(),
        productId: UUID = PRODUCT_ID_1,
        weightKg: BigDecimal = BigDecimal("-30.00"),
        pricePerKg: BigDecimal = BigDecimal("55.00")
    ) = createTransaction(
        id = id,
        type = TransactionType.SALE,
        productId = productId,
        weightKg = weightKg,
        pricePerKg = pricePerKg,
        totalAmount = weightKg.abs().multiply(pricePerKg)
    )

    fun createTransferTransaction(
        id: UUID = UUID.randomUUID(),
        fromLocationId: UUID = LOCATION_ID_1,
        toLocationId: UUID = LOCATION_ID_2,
        productId: UUID = PRODUCT_ID_1,
        weightKg: BigDecimal = BigDecimal("-50.00")
    ) = createTransaction(
        id = id,
        locationId = fromLocationId,
        type = TransactionType.TRANSFER_OUT,
        transferLocationId = toLocationId,
        productId = productId,
        weightKg = weightKg,
        pricePerKg = BigDecimal.ZERO,
        totalAmount = BigDecimal.ZERO
    )

    // Purchase Batch
    fun createPurchaseBatch(
        id: UUID = BATCH_ID_1,
        localId: String = "batch-local-$id",
        locationId: UUID? = LOCATION_ID_1,
        notes: String? = null,
        totalWeightKg: BigDecimal = BigDecimal("200.00"),
        totalAmount: BigDecimal = BigDecimal("9000.00"),
        itemCount: Int = 2,
        deviceId: String? = "test-device",
        createdAt: Instant = TEST_INSTANT,
        syncedAt: Instant? = null
    ) = PurchaseBatch(
        id = id,
        localId = localId,
        locationId = locationId,
        notes = notes,
        totalWeightKg = totalWeightKg,
        totalAmount = totalAmount,
        itemCount = itemCount,
        deviceId = deviceId,
        createdAt = createdAt,
        syncedAt = syncedAt
    )

    // Inventory
    fun createInventoryItem(
        productId: UUID = PRODUCT_ID_1,
        locationId: UUID = LOCATION_ID_1,
        totalWeightKg: BigDecimal = BigDecimal("500.00")
    ) = InventoryItem(
        productId = productId,
        locationId = locationId,
        totalWeightKg = totalWeightKg
    )
}
