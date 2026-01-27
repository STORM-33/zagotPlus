package com.zagot.zagotplus.data.remote.dto

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull

/**
 * Flexible serializer that handles decimal values coming as either:
 * - JSON string: "30.00"
 * - JSON number: 30.00
 * 
 * Always serializes as string for consistency when pushing to server.
 */
object FlexibleDecimalSerializer : KSerializer<String> {
    override val descriptor: SerialDescriptor = 
        PrimitiveSerialDescriptor("FlexibleDecimal", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): String {
        return when (decoder) {
            is JsonDecoder -> {
                val element = decoder.decodeJsonElement()
                if (element is JsonPrimitive) {
                    // Try as number first, then as string
                    element.doubleOrNull?.toString() ?: element.content
                } else {
                    element.toString()
                }
            }
            else -> decoder.decodeString()
        }
    }

    override fun serialize(encoder: Encoder, value: String) {
        encoder.encodeString(value)
    }
}

/**
 * Nullable version of FlexibleDecimalSerializer.
 */
@OptIn(ExperimentalSerializationApi::class)
object FlexibleDecimalSerializerNullable : KSerializer<String?> {
    override val descriptor: SerialDescriptor = 
        PrimitiveSerialDescriptor("FlexibleDecimalNullable", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): String? {
        return when (decoder) {
            is JsonDecoder -> {
                val element = decoder.decodeJsonElement()
                if (element is JsonPrimitive) {
                    if (element.isString && element.content == "null") {
                        null
                    } else {
                        element.doubleOrNull?.toString() ?: element.content
                    }
                } else {
                    element.toString().takeIf { it != "null" }
                }
            }
            else -> decoder.decodeString()
        }
    }

    override fun serialize(encoder: Encoder, value: String?) {
        if (value != null) {
            encoder.encodeString(value)
        } else {
            encoder.encodeNull()
        }
    }
}
