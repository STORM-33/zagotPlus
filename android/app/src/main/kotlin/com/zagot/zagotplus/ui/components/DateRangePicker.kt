package com.zagot.zagotplus.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Predefined date range options for quick selection.
 */
enum class DateRangePreset(val label: String) {
    TODAY("Сьогодні"),
    YESTERDAY("Вчора"),
    LAST_7_DAYS("7 днів"),
    LAST_30_DAYS("30 днів"),
    THIS_MONTH("Цей місяць"),
    LAST_MONTH("Минулий місяць"),
    CUSTOM("Вибрати")
}

/**
 * Data class representing a date range.
 */
data class DateRange(
    val startDate: LocalDate,
    val endDate: LocalDate,
    val preset: DateRangePreset = DateRangePreset.CUSTOM
) {
    companion object {
        fun today(): DateRange {
            val today = LocalDate.now()
            return DateRange(today, today, DateRangePreset.TODAY)
        }

        fun yesterday(): DateRange {
            val yesterday = LocalDate.now().minusDays(1)
            return DateRange(yesterday, yesterday, DateRangePreset.YESTERDAY)
        }

        fun last7Days(): DateRange {
            val today = LocalDate.now()
            return DateRange(today.minusDays(6), today, DateRangePreset.LAST_7_DAYS)
        }

        fun last30Days(): DateRange {
            val today = LocalDate.now()
            return DateRange(today.minusDays(29), today, DateRangePreset.LAST_30_DAYS)
        }

        fun thisMonth(): DateRange {
            val today = LocalDate.now()
            val firstOfMonth = today.withDayOfMonth(1)
            return DateRange(firstOfMonth, today, DateRangePreset.THIS_MONTH)
        }

        fun lastMonth(): DateRange {
            val today = LocalDate.now()
            val firstOfLastMonth = today.minusMonths(1).withDayOfMonth(1)
            val lastOfLastMonth = today.withDayOfMonth(1).minusDays(1)
            return DateRange(firstOfLastMonth, lastOfLastMonth, DateRangePreset.LAST_MONTH)
        }

        fun fromPreset(preset: DateRangePreset): DateRange {
            return when (preset) {
                DateRangePreset.TODAY -> today()
                DateRangePreset.YESTERDAY -> yesterday()
                DateRangePreset.LAST_7_DAYS -> last7Days()
                DateRangePreset.LAST_30_DAYS -> last30Days()
                DateRangePreset.THIS_MONTH -> thisMonth()
                DateRangePreset.LAST_MONTH -> lastMonth()
                DateRangePreset.CUSTOM -> today()
            }
        }
    }
}

/**
 * A reusable date range picker component.
 * Shows the current date range and allows selecting from presets or custom dates.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateRangePicker(
    dateRange: DateRange,
    onDateRangeChange: (DateRange) -> Unit,
    modifier: Modifier = Modifier,
    showPresets: Boolean = true
) {
    val dateFormatter = remember { DateTimeFormatter.ofPattern("dd.MM.yyyy") }
    var showStartDatePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        // Main display card
        OutlinedCard(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.DateRange,
                        contentDescription = "Період"
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        if (dateRange.startDate == dateRange.endDate) {
                            Text(
                                text = dateFormatter.format(dateRange.startDate),
                                style = MaterialTheme.typography.titleMedium
                            )
                        } else {
                            Text(
                                text = "${dateFormatter.format(dateRange.startDate)} - ${dateFormatter.format(dateRange.endDate)}",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                        if (dateRange.preset != DateRangePreset.CUSTOM) {
                            Text(
                                text = dateRange.preset.label,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                Icon(
                    imageVector = Icons.Filled.ArrowDropDown,
                    contentDescription = "Розгорнути"
                )
            }
        }

        // Expanded presets
        if (expanded && showPresets) {
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                ) {
                    DateRangePreset.entries.forEach { preset ->
                        if (preset != DateRangePreset.CUSTOM) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onDateRangeChange(DateRange.fromPreset(preset))
                                        expanded = false
                                    }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = preset.label,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = if (dateRange.preset == preset)
                                        MaterialTheme.colorScheme.primary
                                    else
                                        MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                    
                    // Custom date selection
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showStartDatePicker = true
                            }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = DateRangePreset.CUSTOM.label,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (dateRange.preset == DateRangePreset.CUSTOM)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }

    // Start date picker dialog
    if (showStartDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = dateRange.startDate
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        )

        DatePickerDialog(
            onDismissRequest = { showStartDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val date = Instant.ofEpochMilli(millis)
                            .atZone(ZoneId.systemDefault())
                            .toLocalDate()
                        onDateRangeChange(
                            DateRange(
                                startDate = date,
                                endDate = if (date.isAfter(dateRange.endDate)) date else dateRange.endDate,
                                preset = DateRangePreset.CUSTOM
                            )
                        )
                        showStartDatePicker = false
                        showEndDatePicker = true
                    }
                }) {
                    Text("Далі")
                }
            },
            dismissButton = {
                TextButton(onClick = { showStartDatePicker = false }) {
                    Text("Скасувати")
                }
            }
        ) {
            DatePicker(
                state = datePickerState,
                title = { Text("Початкова дата", modifier = Modifier.padding(16.dp)) }
            )
        }
    }

    // End date picker dialog
    if (showEndDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = dateRange.endDate
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        )

        DatePickerDialog(
            onDismissRequest = { showEndDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val date = Instant.ofEpochMilli(millis)
                            .atZone(ZoneId.systemDefault())
                            .toLocalDate()
                        onDateRangeChange(
                            DateRange(
                                startDate = dateRange.startDate,
                                endDate = if (date.isBefore(dateRange.startDate)) dateRange.startDate else date,
                                preset = DateRangePreset.CUSTOM
                            )
                        )
                        showEndDatePicker = false
                        expanded = false
                    }
                }) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEndDatePicker = false }) {
                    Text("Скасувати")
                }
            }
        ) {
            DatePicker(
                state = datePickerState,
                title = { Text("Кінцева дата", modifier = Modifier.padding(16.dp)) }
            )
        }
    }
}

/**
 * Simplified single date picker for selecting a single date.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SingleDatePicker(
    selectedDate: LocalDate,
    onDateSelected: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    val dateFormatter = remember { DateTimeFormatter.ofPattern("dd.MM.yyyy") }
    var showDatePicker by remember { mutableStateOf(false) }

    OutlinedCard(
        modifier = modifier
            .fillMaxWidth()
            .clickable { showDatePicker = true }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = dateFormatter.format(selectedDate),
                style = MaterialTheme.typography.titleLarge
            )
            Icon(
                imageVector = Icons.Filled.DateRange,
                contentDescription = "Вибрати дату"
            )
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = selectedDate
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        )

        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val date = Instant.ofEpochMilli(millis)
                            .atZone(ZoneId.systemDefault())
                            .toLocalDate()
                        onDateSelected(date)
                    }
                    showDatePicker = false
                }) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Скасувати")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}
