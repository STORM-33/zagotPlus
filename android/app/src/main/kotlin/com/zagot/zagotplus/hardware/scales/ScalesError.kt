package com.zagot.zagotplus.hardware.scales

/**
 * Errors that can occur during scales operation.
 */
sealed class ScalesError {
    data object NotConfigured : ScalesError()
    data object ConnectionFailed : ScalesError()
    data object ConnectionLost : ScalesError()
    data object Timeout : ScalesError()
    data object Overload : ScalesError()
    data object Underload : ScalesError()
    data object UnstableReading : ScalesError()
    data class ParseError(val raw: String, val message: String) : ScalesError()
    data class NetworkError(val message: String) : ScalesError()
    data class Unknown(val message: String) : ScalesError()

    fun toDisplayMessage(): String = when (this) {
        NotConfigured -> "Ваги не налаштовані"
        ConnectionFailed -> "Не вдалося підключитися"
        ConnectionLost -> "З'єднання втрачено"
        Timeout -> "Час очікування вичерпано"
        Overload -> "Перевантаження ваг"
        Underload -> "Вага нижче нуля"
        UnstableReading -> "Нестабільне зважування"
        is ParseError -> "Помилка даних: $message"
        is NetworkError -> "Мережева помилка: $message"
        is Unknown -> message
    }
}
