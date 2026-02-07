package com.zagot.zagotplus.sync.engine.util

import com.zagot.zagotplus.sync.engine.api.Record
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive

/**
 * Shared JSON ↔ Record conversion utilities for the sync engine.
 */
object JsonUtil {

    /**
     * Parse a JSON string payload (from outbox) into a [Record] map.
     */
    fun parsePayload(json: String): Record {
        return Json.decodeFromString<Map<String, JsonElement>>(json)
            .mapValues { (_, v) -> jsonElementToAny(v) }
    }

    /**
     * Convert a [JsonElement] to a native Kotlin type.
     */
    fun jsonElementToAny(element: JsonElement): Any? {
        return when (element) {
            is JsonNull -> null
            is JsonPrimitive -> {
                when {
                    element.isString -> element.content
                    element.content == "true" -> true
                    element.content == "false" -> false
                    element.content.contains(".") -> element.content.toDoubleOrNull()
                    else -> element.content.toLongOrNull() ?: element.content
                }
            }
            else -> element.toString()
        }
    }

    /**
     * Convert a native value to a [JsonElement] for serialization.
     */
    fun anyToJsonElement(value: Any?): JsonElement {
        return when (value) {
            null -> JsonNull
            is String -> JsonPrimitive(value)
            is Number -> JsonPrimitive(value)
            is Boolean -> JsonPrimitive(value)
            else -> JsonPrimitive(value.toString())
        }
    }
}
