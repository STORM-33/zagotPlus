package com.zagot.syncengine.util

import com.zagot.syncengine.api.Record
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
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
     * Recursively handles arrays and objects (for JSONB columns with nested data).
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
            is JsonArray -> element.map { jsonElementToAny(it) }
            is JsonObject -> element.mapValues { (_, v) -> jsonElementToAny(v) }
        }
    }

    /**
     * Convert a native value to a [JsonElement] for serialization.
     * Recursively handles lists and maps (for JSONB columns with nested data).
     */
    fun anyToJsonElement(value: Any?): JsonElement {
        return when (value) {
            null -> JsonNull
            is String -> JsonPrimitive(value)
            is Number -> JsonPrimitive(value)
            is Boolean -> JsonPrimitive(value)
            is List<*> -> JsonArray(value.map { anyToJsonElement(it) })
            is Map<*, *> -> JsonObject(value.entries.associate { (k, v) ->
                k.toString() to anyToJsonElement(v)
            })
            else -> JsonPrimitive(value.toString())
        }
    }
}
