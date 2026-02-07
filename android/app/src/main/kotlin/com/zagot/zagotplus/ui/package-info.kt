/**
 * UI layer implemented with Jetpack Compose.
 *
 * This package contains all user interface components organized by feature:
 * - [components] - Reusable UI components (dialogs, inputs, charts)
 * - [navigation] - Navigation graph and destination definitions
 * - [screens] - Feature screens organized by domain:
 *   - [screens.auth] - PIN authentication
 *   - [screens.purchase] - Purchase batch entry and listing
 *   - [screens.sale] - Sale batch entry and listing
 *   - [screens.inventory] - Stock levels and product inventory
 *   - [screens.history] - Transaction history and search
 *   - [screens.cash] - Cash operations and expense tracking
 *   - [screens.products] - Product catalog management
 *   - [screens.reports] - Business analytics and reports
 *   - [screens.settings] - App configuration
 *   - [screens.transfer] - Inter-location stock transfers
 * - [theme] - Material 3 theme, colors, typography
 *
 * Architecture:
 * - Screens use MVVM pattern with ViewModels in corresponding packages
 * - State flows from ViewModel → UI using Compose's reactive model
 * - Navigation uses type-safe destination classes
 */
package com.zagot.zagotplus.ui
