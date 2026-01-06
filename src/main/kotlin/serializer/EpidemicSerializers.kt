package com.anjo.serializer

import com.anjo.model.dto.DetailedData
import com.anjo.model.dto.HumanType
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.serializer

object HumanTypeSerializer : KSerializer<Map<HumanType, DetailedData>> {
    private val stringSerializer = String.serializer()
    private val detailedDataSerializer = serializer<DetailedData>()
    private val delegate: KSerializer<Map<String, DetailedData>> =
        MapSerializer(stringSerializer, detailedDataSerializer)

    override val descriptor: SerialDescriptor
        get() = delegate.descriptor

    override fun serialize(
        encoder: Encoder,
        value: Map<HumanType, DetailedData>
    ) {
        val map = value.mapKeys { it.key.numberType.toString() }
        encoder.encodeSerializableValue(delegate, map)
    }

    override fun deserialize(decoder: Decoder): Map<HumanType, DetailedData> {
        val map = decoder.decodeSerializableValue(delegate)
        return map.entries.associate { (key: String, value: DetailedData) ->
            HumanType.fromCode(key.toInt()) to value
        }
    }
}
