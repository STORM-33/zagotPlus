package com.zagot.zagotplus.data.preferences

import android.content.Context
import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.UUID

/**
 * Unit tests for ProductOrderPreferences.
 */
class ProductOrderPreferencesTest {

    private lateinit var context: Context
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var editor: SharedPreferences.Editor
    private lateinit var productOrderPreferences: ProductOrderPreferences

    private val testProductId1 = UUID.fromString("550e8400-e29b-41d4-a716-446655440001")
    private val testProductId2 = UUID.fromString("550e8400-e29b-41d4-a716-446655440002")
    private val testProductId3 = UUID.fromString("550e8400-e29b-41d4-a716-446655440003")

    @Before
    fun setup() {
        context = mockk(relaxed = true)
        sharedPreferences = mockk(relaxed = true)
        editor = mockk(relaxed = true)

        every { context.getSharedPreferences("zagot_product_order_prefs", Context.MODE_PRIVATE) } returns sharedPreferences
        every { sharedPreferences.edit() } returns editor
        every { editor.putString(any(), any()) } returns editor
        every { editor.apply() } returns Unit
        every { sharedPreferences.getString("product_order", null) } returns null

        productOrderPreferences = ProductOrderPreferences(context)
    }

    // getProductOrder Tests

    @Test
    fun `getProductOrder returns empty list when not set`() {
        every { sharedPreferences.getString("product_order", null) } returns null

        productOrderPreferences = ProductOrderPreferences(context)
        val result = productOrderPreferences.getProductOrder()

        assertTrue(result.isEmpty())
    }

    @Test
    fun `getProductOrder returns empty list when empty string`() {
        every { sharedPreferences.getString("product_order", null) } returns ""

        productOrderPreferences = ProductOrderPreferences(context)
        val result = productOrderPreferences.getProductOrder()

        assertTrue(result.isEmpty())
    }

    @Test
    fun `getProductOrder returns empty list when blank string`() {
        every { sharedPreferences.getString("product_order", null) } returns "   "

        productOrderPreferences = ProductOrderPreferences(context)
        val result = productOrderPreferences.getProductOrder()

        assertTrue(result.isEmpty())
    }

    @Test
    fun `getProductOrder parses single UUID correctly`() {
        every { sharedPreferences.getString("product_order", null) } returns testProductId1.toString()

        productOrderPreferences = ProductOrderPreferences(context)
        val result = productOrderPreferences.getProductOrder()

        assertEquals(1, result.size)
        assertEquals(testProductId1, result[0])
    }

    @Test
    fun `getProductOrder parses multiple UUIDs correctly`() {
        val orderString = "$testProductId1,$testProductId2,$testProductId3"
        every { sharedPreferences.getString("product_order", null) } returns orderString

        productOrderPreferences = ProductOrderPreferences(context)
        val result = productOrderPreferences.getProductOrder()

        assertEquals(3, result.size)
        assertEquals(testProductId1, result[0])
        assertEquals(testProductId2, result[1])
        assertEquals(testProductId3, result[2])
    }

    @Test
    fun `getProductOrder handles invalid UUID gracefully`() {
        val orderString = "$testProductId1,invalid-uuid,$testProductId2"
        every { sharedPreferences.getString("product_order", null) } returns orderString

        productOrderPreferences = ProductOrderPreferences(context)
        val result = productOrderPreferences.getProductOrder()

        // Should skip invalid UUID
        assertEquals(2, result.size)
        assertEquals(testProductId1, result[0])
        assertEquals(testProductId2, result[1])
    }

    @Test
    fun `getProductOrder handles whitespace in UUIDs`() {
        val orderString = " $testProductId1 , $testProductId2 "
        every { sharedPreferences.getString("product_order", null) } returns orderString

        productOrderPreferences = ProductOrderPreferences(context)
        val result = productOrderPreferences.getProductOrder()

        assertEquals(2, result.size)
        assertEquals(testProductId1, result[0])
        assertEquals(testProductId2, result[1])
    }

    // setProductOrder Tests

    @Test
    fun `setProductOrder stores order correctly`() {
        val order = listOf(testProductId1, testProductId2)
        val capturedOrder = slot<String>()
        every { editor.putString("product_order", capture(capturedOrder)) } returns editor

        productOrderPreferences.setProductOrder(order)

        verify { editor.putString("product_order", any()) }
        verify { editor.apply() }
        assertEquals("$testProductId1,$testProductId2", capturedOrder.captured)
    }

    @Test
    fun `setProductOrder handles empty list`() {
        val capturedOrder = slot<String>()
        every { editor.putString("product_order", capture(capturedOrder)) } returns editor

        productOrderPreferences.setProductOrder(emptyList())

        assertEquals("", capturedOrder.captured)
    }

    // productOrderFlow Tests

    @Test
    fun `productOrderFlow emits initial value`() = runTest {
        val orderString = "$testProductId1,$testProductId2"
        every { sharedPreferences.getString("product_order", null) } returns orderString

        productOrderPreferences = ProductOrderPreferences(context)
        val result = productOrderPreferences.productOrderFlow.first()

        assertEquals(2, result.size)
        assertEquals(testProductId1, result[0])
    }

    @Test
    fun `setProductOrder updates flow`() = runTest {
        every { sharedPreferences.getString("product_order", null) } returns null

        productOrderPreferences = ProductOrderPreferences(context)
        productOrderPreferences.setProductOrder(listOf(testProductId1))
        val result = productOrderPreferences.productOrderFlow.first()

        assertEquals(1, result.size)
        assertEquals(testProductId1, result[0])
    }

    // applyOrder Tests

    data class TestProduct(val id: UUID, val name: String)

    @Test
    fun `applyOrder returns original list when no order saved`() {
        every { sharedPreferences.getString("product_order", null) } returns null

        productOrderPreferences = ProductOrderPreferences(context)
        val products = listOf(
            TestProduct(testProductId1, "Product 1"),
            TestProduct(testProductId2, "Product 2")
        )

        val result = productOrderPreferences.applyOrder(products) { it.id }

        assertEquals(products, result)
    }

    @Test
    fun `applyOrder reorders products according to saved order`() {
        val orderString = "$testProductId2,$testProductId1"
        every { sharedPreferences.getString("product_order", null) } returns orderString

        productOrderPreferences = ProductOrderPreferences(context)
        val products = listOf(
            TestProduct(testProductId1, "Product 1"),
            TestProduct(testProductId2, "Product 2")
        )

        val result = productOrderPreferences.applyOrder(products) { it.id }

        assertEquals(testProductId2, result[0].id)
        assertEquals(testProductId1, result[1].id)
    }

    @Test
    fun `applyOrder puts unordered products at end`() {
        val orderString = testProductId1.toString()
        every { sharedPreferences.getString("product_order", null) } returns orderString

        productOrderPreferences = ProductOrderPreferences(context)
        val products = listOf(
            TestProduct(testProductId3, "Product 3"),
            TestProduct(testProductId1, "Product 1"),
            TestProduct(testProductId2, "Product 2")
        )

        val result = productOrderPreferences.applyOrder(products) { it.id }

        // Product 1 (ordered) should be first
        assertEquals(testProductId1, result[0].id)
        // Product 3 and 2 (unordered) should follow in original order
        assertEquals(testProductId3, result[1].id)
        assertEquals(testProductId2, result[2].id)
    }

    @Test
    fun `applyOrder handles order with missing products`() {
        // Order contains products not in the list
        val missingId = UUID.fromString("999e8400-e29b-41d4-a716-446655440999")
        val orderString = "$missingId,$testProductId1"
        every { sharedPreferences.getString("product_order", null) } returns orderString

        productOrderPreferences = ProductOrderPreferences(context)
        val products = listOf(
            TestProduct(testProductId1, "Product 1"),
            TestProduct(testProductId2, "Product 2")
        )

        val result = productOrderPreferences.applyOrder(products) { it.id }

        // Product 1 is in order, should be first
        assertEquals(testProductId1, result[0].id)
        // Product 2 is unordered, should follow
        assertEquals(testProductId2, result[1].id)
    }

    @Test
    fun `applyOrder handles empty products list`() {
        val orderString = testProductId1.toString()
        every { sharedPreferences.getString("product_order", null) } returns orderString

        productOrderPreferences = ProductOrderPreferences(context)
        val result = productOrderPreferences.applyOrder(emptyList<TestProduct>()) { it.id }

        assertTrue(result.isEmpty())
    }
}
