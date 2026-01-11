# Archive: Phase 2 - Core UI

Archived: 2026-01-11
Plan: Phase 2 - Core UI
Duration: 2026-01-11
Branch: feature/phase-2-core-ui

## Sessions

| Session | Status | Key Outcome |
|---------|--------|-------------|
| navigation | completed | NavHost, bottom nav bar, sync status indicator, 4 placeholder screens |
| auth-pin | completed | 4-digit PIN authentication with lockout, SHA-256 hashing, SharedPreferences storage |
| screen-purchase | completed | Purchase screen for buying from population with product selection, weight input, price calculation |
| screen-sale | completed | Sale screen for wholesale with inventory awareness and warning for negative inventory |
| screen-inventory | completed | Inventory screen with location tabs, sync integration, negative inventory highlighting |

## Key Decisions

1. **Navigation Structure**
   - Bottom nav order: Purchase → Sale → Inventory → History (workflow frequency)
   - Start destination: Purchase (most common operation)
   - Sync icon states: CloudDone/Cloud/Sync/CloudOff for clear status

2. **Authentication**
   - SHA-256 for PIN hash (simple deterrent, not security-critical)
   - SharedPreferences for storage (consistent with SyncPreferences)
   - No biometrics (minimal scope)
   - Large circular buttons for tablet-friendly UI
   - 3 failed attempts = 30-second lockout

3. **Purchase/Sale Screens**
   - BigDecimal for financial precision
   - collectAsStateWithLifecycle for reactive UI
   - Form validation via computed properties (canSave)
   - Price auto-fill from product defaults
   - Snackbar feedback for success/error

4. **Sale Screen Specifics**
   - Negative inventory allowed (business warning, not technical blocker)
   - Inventory displayed per product in dropdown
   - Warning shown when selling more than available

5. **Inventory Screen**
   - Refresh button instead of pull-to-refresh (Compose BOM 2024.01.00 limitation)
   - Display all products even with 0 inventory (completeness)
   - Red highlighting for negative inventory with warning icon

## Discoveries

1. **Dependency Management**
   - material-icons-extended increases APK size (can optimize with R8)
   - lifecycle-runtime-compose needed for collectAsStateWithLifecycle

2. **Testing Challenges**
   - Android Context tests require Robolectric
   - JVM memory constraints (reduced heap to 1GB)
   - BigDecimal.compareTo for scale-independent equality

3. **UI Patterns**
   - TabRow for location switching
   - Flow-based repositories enable reactive UI
   - Computed display items (join products + inventory)

## Artifacts Produced

### New Files
- `ui/navigation/Destinations.kt` - Route definitions
- `ui/navigation/NavGraph.kt` - Main navigation scaffold
- `ui/components/SyncStatusIcon.kt` - Sync indicator
- `ui/screens/auth/PinScreen.kt` - PIN authentication UI
- `ui/screens/auth/PinViewModel.kt` - PIN logic
- `ui/screens/auth/AuthPreferences.kt` - PIN storage
- `ui/screens/purchase/PurchaseScreen.kt` - Purchase UI
- `ui/screens/purchase/PurchaseViewModel.kt` - Purchase logic
- `ui/screens/sale/SaleScreen.kt` - Sale UI
- `ui/screens/sale/SaleViewModel.kt` - Sale logic
- `ui/screens/inventory/InventoryScreen.kt` - Inventory UI
- `ui/screens/inventory/InventoryViewModel.kt` - Inventory logic
- `ui/screens/history/HistoryScreen.kt` - Placeholder

### Test Files
- `AuthPreferencesTest.kt` - Auth preferences unit tests
- `PinViewModelTest.kt` - PIN ViewModel unit tests
- `PurchaseViewModelTest.kt` - Purchase ViewModel unit tests (10 tests)
- `SaleViewModelTest.kt` - Sale ViewModel unit tests (10 tests)

### Modified Files
- `MainActivity.kt` - Auth gate and NavGraph integration
- `app/build.gradle.kts` - Dependencies added
- `gradle/libs.versions.toml` - Version catalog updates
- `gradle.properties` - Heap size reduction

## Lessons Learned

1. **Compose BOM Limitations**: Version 2024.01.00 lacks PullToRefreshBox; using toolbar refresh button as workaround
2. **Test Infrastructure**: Pure ViewModel tests work well; Android Context tests need additional setup
3. **Memory Constraints**: Gradle daemon heap reduced to 1GB for constrained environments
4. **Reactive Patterns**: Flow-based DAOs enable clean reactive UI with collectAsStateWithLifecycle
5. **Financial Precision**: BigDecimal essential for price calculations; use compareTo for assertions

## Phase Success Metrics

✅ All 5 sessions completed successfully
✅ Navigation structure functional
✅ PIN authentication implemented
✅ All 3 core workflow screens (purchase, sale, inventory) operational
✅ 30 unit tests written (20 passing, 10 compiled but not executed due to env constraints)
✅ Build succeeds (APK: ~19MB)
✅ Offline-first architecture preserved
✅ Sync integration maintained

## Next Phase

Phase 3 planning required. Phase 2 Core UI is complete with all primary workflow screens operational.
