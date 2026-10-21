package com.seenly.app.core.database

import androidx.room.TypeConverter
import com.seenly.app.core.common.AppJson
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer

class Converters {

    @TypeConverter
    fun fromStringList(value: List<String>?): String =
        AppJson.encodeToString(ListSerializer(String.serializer()), value ?: emptyList())

    @TypeConverter
    fun toStringList(value: String?): List<String> {
        if (value.isNullOrBlank()) return emptyList()
        return runCatching {
            AppJson.decodeFromString(ListSerializer(String.serializer()), value)
        }.getOrDefault(emptyList())
    }
}
