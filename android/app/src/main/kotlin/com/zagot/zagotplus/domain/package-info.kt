/**
 * Domain layer containing business logic and models.
 *
 * This package defines the core business entities and rules:
 * - [model] - Domain models (Location, Product, Transaction, etc.)
 * - [repository] - Repository interfaces (implementations in data layer)
 * - [validation] - Input validation and sanitization utilities
 *
 * Domain models are:
 * - Marked @Stable for Compose recomposition optimization
 * - Independent of data/remote implementations
 * - Used by ViewModels and UI layers
 *
 * Key models:
 * - [model.Location] - Warehouse/kiosk locations
 * - [model.Product] - Product catalog items
 * - [model.Transaction] - Purchase/sale/transfer records
 * - [model.PurchaseBatch] - Batch purchase operations
 * - [model.SaleBatch] - Batch sale operations
 * - [model.InventoryItem] - Stock levels by location/product
 */
package com.zagot.zagotplus.domain
