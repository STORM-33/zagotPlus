package com.zagot.zagotplus.hardware.printer

/**
 * Errors that can occur during printer operation.
 */
sealed class PrinterError {
    data object BluetoothDisabled : PrinterError()
    data object BluetoothNotSupported : PrinterError()
    data object PermissionDenied : PrinterError()
    data object DeviceNotFound : PrinterError()
    data object ConnectionFailed : PrinterError()
    data object ConnectionLost : PrinterError()
    data object PrintFailed : PrinterError()
    data object Timeout : PrinterError()
    data class Unknown(val message: String) : PrinterError()

    fun toDisplayMessage(): String = when (this) {
        BluetoothDisabled -> "Bluetooth вимкнено"
        BluetoothNotSupported -> "Bluetooth не підтримується"
        PermissionDenied -> "Немає дозволу на Bluetooth"
        DeviceNotFound -> "Принтер не знайдено"
        ConnectionFailed -> "Не вдалося підключитися"
        ConnectionLost -> "З'єднання втрачено"
        PrintFailed -> "Помилка друку"
        Timeout -> "Час очікування вичерпано"
        is Unknown -> message
    }
}
