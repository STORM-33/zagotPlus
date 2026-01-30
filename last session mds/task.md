# Sale Flow Refactoring: Mode Selection + Tablet Sliding Panel

## Phase 1: Mode Selection
- [ ] Create `SaleMode` enum (REGULAR, WHOLESALE)
- [ ] Update `Destinations.kt` with mode parameter for SaleEntry
- [ ] Update `NavGraph.kt` to pass mode parameter
- [ ] Create mode selection UI in `SaleScreen.kt` (two buttons)
- [ ] Update `SaleViewModel.kt` to handle mode selection navigation

## Phase 2: Regular Mode Tablet
- [ ] Add `saleMode: SaleMode` parameter to `SaleEntryViewModel`
- [ ] Add Regular mode state handling to ViewModel (bypass batch logic)
- [ ] Create `SaleEntryRegularTabletContent.kt` component
- [ ] Update `SaleEntryScreen.kt` to route between modes
- [ ] Test regular flow: product → weight → price → add position

## Phase 3: Wholesale Mode Tablet Sliding Panel
- [ ] Refactor `SaleEntryTabletContent.kt` to Box-based layered layout
- [ ] Implement slide animation for positions panel
- [ ] Add greyed/locked states for Products and Positions panels
- [ ] Add "Cancel Product" button to Weightings panel
- [ ] Wire up state transitions (IDLE ↔ BATCH_ENTRY ↔ FINALIZATION)

## Phase 4: Mobile Layouts
- [ ] Verify Regular mode works on mobile (multi-step wizard)
- [ ] Verify Wholesale mode works on mobile (existing flow)

## Phase 5: Testing & Verification
- [ ] Run existing `SaleEntryViewModelTest.kt` tests
- [ ] Add new tests for mode selection and Regular mode
- [ ] Manual verification on tablet emulator
- [ ] Build verification
