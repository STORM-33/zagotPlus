# Session Brief: screen-reports

Type: feature
Phase: phase-4-supporting-ui
Complexity: medium
Created: 2026-01-11

## Objective

Create a Reports screen showing daily summaries with basic export capability.

## Background

Business needs daily operational summaries: how much purchased, sold, transferred. Also needs simple export for bookkeeping.

## Requirements

- [ ] Date selector (default: today)
- [ ] Summary cards: total purchased (kg, UAH), total sold (kg, UAH)
- [ ] Breakdown by product for selected date
- [ ] Breakdown by location for selected date
- [ ] Basic export: copy summary to clipboard as text
- [ ] Optional: share as text to other apps

## Context Files

Load these files before starting:
- `.ctx/memory/project.md`
- `android/app/src/main/kotlin/com/zagot/zagotplus/domain/repository/TransactionRepository.kt`
- `android/app/src/main/kotlin/com/zagot/zagotplus/data/local/dao/TransactionDao.kt`
- `android/app/src/main/kotlin/com/zagot/zagotplus/ui/screens/inventory/InventoryScreen.kt` (for UI patterns)

## Implementation Notes

1. Create ReportsScreen with DatePicker at top
2. Query transactions by date range (start of day to end of day)
3. Compute aggregates in ViewModel (sum by type, product, location)
4. Display as Card components with totals
5. "Копіювати" button generates text summary
6. Use ClipboardManager for copy, Intent.ACTION_SEND for share
7. Consider caching computed summaries if performance is issue

### Report Text Format
```
Звіт за 11.01.2026

ЗАКУПКИ
- Горіх волоський: 45.2 кг × 85.00 = 3 842.00 грн
- Насіння гарбуза: 12.0 кг × 50.00 = 600.00 грн
Разом: 57.2 кг, 4 442.00 грн

ПРОДАЖІ
- Горіх волоський: 20.0 кг × 120.00 = 2 400.00 грн
Разом: 20.0 кг, 2 400.00 грн

ПЕРЕМІЩЕННЯ
- Кіоск → Мобільний: Горіх волоський 10.0 кг
```

## TDD

Mode: encouraged

### Test Plan
- [ ] Test: Daily summary computes correct totals
- [ ] Test: Product breakdown sums correctly
- [ ] Test: Location breakdown sums correctly
- [ ] Test: Empty day shows zero totals
- [ ] Test: Report text format is correct

### Test Command
```
./gradlew :app:testDebugUnitTest --tests "*.ReportsViewModelTest"
```

## Success Criteria

- [ ] Date picker works
- [ ] Summary shows correct totals for purchases
- [ ] Summary shows correct totals for sales
- [ ] Product breakdown is accurate
- [ ] Copy to clipboard works
- [ ] Share intent works
- [ ] No data = shows "Немає даних за цей день"

## Out of Scope

- PDF export
- Charts/graphs
- Multi-day range reports
- Profit calculations
- Print functionality

## Dependencies

- Requires: none
- Blocks: none
