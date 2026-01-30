package com.zagot.zagotplus.ui.navigation

import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class DestinationsTest {

    // === PurchaseEntry.createRoute tests ===

    @Test
    fun `PurchaseEntry createRoute without batchId returns base route`() {
        val route = Destination.PurchaseEntry.createRoute()
        
        assertEquals("purchase_entry?mode=REGULAR", route)
    }

    @Test
    fun `PurchaseEntry createRoute with null batchId returns base route`() {
        val route = Destination.PurchaseEntry.createRoute(batchId = null)
        
        assertEquals("purchase_entry?mode=REGULAR", route)
    }

    @Test
    fun `PurchaseEntry createRoute with batchId includes parameter`() {
        val batchId = UUID.randomUUID().toString()
        val route = Destination.PurchaseEntry.createRoute(batchId = batchId)
        
        assertEquals("purchase_entry?batchId=$batchId&mode=REGULAR", route)
    }

    // === SaleEntry.createRoute tests ===

    @Test
    fun `SaleEntry createRoute without batchId returns base route`() {
        val route = Destination.SaleEntry.createRoute()
        
        assertEquals("sale_entry?mode=WHOLESALE", route)
    }

    @Test
    fun `SaleEntry createRoute with batchId includes parameter`() {
        val batchId = "test-batch-123"
        val route = Destination.SaleEntry.createRoute(batchId = batchId)
        
        assertEquals("sale_entry?batchId=$batchId&mode=WHOLESALE", route)
    }

    // === Transfer.createRoute tests ===

    @Test
    fun `Transfer createRoute with no parameters returns base route`() {
        val route = Destination.Transfer.createRoute()
        
        assertEquals("transfer", route)
    }

    @Test
    fun `Transfer createRoute with productId only`() {
        val productId = UUID.randomUUID().toString()
        val route = Destination.Transfer.createRoute(productId = productId)
        
        assertEquals("transfer?productId=$productId", route)
    }

    @Test
    fun `Transfer createRoute with sourceLocationId only`() {
        val sourceLocationId = UUID.randomUUID().toString()
        val route = Destination.Transfer.createRoute(sourceLocationId = sourceLocationId)
        
        assertEquals("transfer?sourceLocationId=$sourceLocationId", route)
    }

    @Test
    fun `Transfer createRoute with destinationLocationId only`() {
        val destId = UUID.randomUUID().toString()
        val route = Destination.Transfer.createRoute(destinationLocationId = destId)
        
        assertEquals("transfer?destinationLocationId=$destId", route)
    }

    @Test
    fun `Transfer createRoute with all parameters`() {
        val productId = "prod-1"
        val sourceId = "source-1"
        val destId = "dest-1"
        
        val route = Destination.Transfer.createRoute(
            productId = productId,
            sourceLocationId = sourceId,
            destinationLocationId = destId
        )
        
        assertEquals("transfer?productId=$productId&sourceLocationId=$sourceId&destinationLocationId=$destId", route)
    }

    @Test
    fun `Transfer createRoute with partial parameters`() {
        val productId = "prod-1"
        val destId = "dest-1"
        
        val route = Destination.Transfer.createRoute(
            productId = productId,
            destinationLocationId = destId
        )
        
        assertEquals("transfer?productId=$productId&destinationLocationId=$destId", route)
    }

    // === Bottom nav items test ===

    @Test
    fun `bottomNavItems contains correct destinations in order`() {
        val items = Destination.bottomNavItems
        
        assertEquals(4, items.size)
        assertEquals(Destination.Purchase, items[0])
        assertEquals(Destination.Sale, items[1])
        assertEquals(Destination.Inventory, items[2])
        assertEquals(Destination.History, items[3])
    }

    // === Route constants tests ===

    @Test
    fun `all destinations have non-empty routes`() {
        val destinations = listOf(
            Destination.Purchase,
            Destination.PurchaseEntry,
            Destination.Sale,
            Destination.SaleEntry,
            Destination.Inventory,
            Destination.History,
            Destination.Cash,
            Destination.Products,
            Destination.Reports,
            Destination.Settings,
            Destination.Transfer
        )
        
        destinations.forEach { dest ->
            assertTrue("Route should not be blank for ${dest::class.simpleName}", dest.route.isNotBlank())
        }
    }

    @Test
    fun `all destinations have non-empty titles`() {
        val destinations = listOf(
            Destination.Purchase,
            Destination.Sale,
            Destination.Inventory,
            Destination.History,
            Destination.Cash,
            Destination.Products,
            Destination.Reports,
            Destination.Settings,
            Destination.Transfer
        )
        
        destinations.forEach { dest ->
            assertTrue("Title should not be blank for ${dest::class.simpleName}", dest.title.isNotBlank())
        }
    }

    // === Route argument constants tests ===

    @Test
    fun `PurchaseEntry has correct route with args pattern`() {
        assertEquals("purchase_entry?batchId={batchId}&mode={mode}", Destination.PurchaseEntry.ROUTE_WITH_ARGS)
        assertEquals("batchId", Destination.PurchaseEntry.ARG_BATCH_ID)
    }

    @Test
    fun `SaleEntry has correct route with args pattern`() {
        assertEquals("sale_entry?batchId={batchId}&mode={mode}", Destination.SaleEntry.ROUTE_WITH_ARGS)
        assertEquals("batchId", Destination.SaleEntry.ARG_BATCH_ID)
    }

    @Test
    fun `Transfer has correct route with args pattern`() {
        assertEquals(
            "transfer?productId={productId}&sourceLocationId={sourceLocationId}&destinationLocationId={destinationLocationId}",
            Destination.Transfer.ROUTE_WITH_ARGS
        )
        assertEquals("productId", Destination.Transfer.ARG_PRODUCT_ID)
        assertEquals("sourceLocationId", Destination.Transfer.ARG_SOURCE_LOCATION_ID)
        assertEquals("destinationLocationId", Destination.Transfer.ARG_DESTINATION_LOCATION_ID)
    }
}
