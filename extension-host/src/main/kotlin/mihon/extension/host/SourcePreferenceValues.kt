package mihon.extension.host
import android.content.Context
import android.content.SharedPreferences
import androidx.preference.CheckBoxPreference
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.MultiSelectListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceGroup
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.source.ConfigurableSource
import mihon.extension.compat.TachiyomiCatalogueSourceAdapter
import mihon.extension.ipc.BooleanPreferenceValueDto
import mihon.extension.ipc.FloatPreferenceValueDto
import mihon.extension.ipc.IntPreferenceValueDto
import mihon.extension.ipc.ListPreferenceValueDto
import mihon.extension.ipc.LongPreferenceValueDto
import mihon.extension.ipc.SelectPreferenceValueDto
import mihon.extension.ipc.SourcePreferenceDefinitionDto
import mihon.extension.ipc.SourcePreferenceOptionDto
import mihon.extension.ipc.SourcePreferenceTypeDto
import mihon.extension.ipc.SourcePreferenceValueDto
import mihon.extension.ipc.StringPreferenceValueDto
import mihon.extension.ipc.UnknownPreferenceValueDto
import mihon.extension.source.WindowsSource

internal fun SourcePreferenceModel.orderValues(
    values: List<String>,
    options: List<SourcePreferenceOptionDto>,
): List<String> {
    if (options.isEmpty()) return values
    val selected = values.toSet()
    val ordered = options.map { it.value }.filter { it in selected }
    val extras = values.filter { value -> options.none { it.value == value } }
    return ordered + extras
}

internal fun SourcePreferenceModel.convertValue(
    type: SourcePreferenceTypeDto,
    incoming: SourcePreferenceValueDto,
): Any {
    return when (type) {
        SourcePreferenceTypeDto.BOOLEAN -> when (incoming) {
            is BooleanPreferenceValueDto -> incoming.value
            is StringPreferenceValueDto -> incoming.value.toBooleanStrictOrNull()
                ?: throw IllegalArgumentException("Expected a boolean source preference value")
            else -> throw IllegalArgumentException("Expected a boolean source preference value")
        }
        SourcePreferenceTypeDto.STRING -> when (incoming) {
            is StringPreferenceValueDto -> incoming.value
            is SelectPreferenceValueDto -> incoming.value
            is IntPreferenceValueDto -> incoming.value.toString()
            is LongPreferenceValueDto -> incoming.value.toString()
            is FloatPreferenceValueDto -> incoming.value.toString()
            is BooleanPreferenceValueDto -> incoming.value.toString()
            is UnknownPreferenceValueDto -> incoming.value.orEmpty()
            else -> throw IllegalArgumentException("Expected a string source preference value")
        }
        SourcePreferenceTypeDto.INT -> when (incoming) {
            is IntPreferenceValueDto -> incoming.value
            is LongPreferenceValueDto -> incoming.value.toInt()
            is FloatPreferenceValueDto -> incoming.value.toInt()
            is StringPreferenceValueDto -> incoming.value.toIntOrNull()
                ?: throw IllegalArgumentException("Expected an int source preference value")
            else -> throw IllegalArgumentException("Expected an int source preference value")
        }
        SourcePreferenceTypeDto.LONG -> when (incoming) {
            is LongPreferenceValueDto -> incoming.value
            is IntPreferenceValueDto -> incoming.value.toLong()
            is FloatPreferenceValueDto -> incoming.value.toLong()
            is StringPreferenceValueDto -> incoming.value.toLongOrNull()
                ?: throw IllegalArgumentException("Expected a long source preference value")
            else -> throw IllegalArgumentException("Expected a long source preference value")
        }
        SourcePreferenceTypeDto.FLOAT -> when (incoming) {
            is FloatPreferenceValueDto -> incoming.value
            is IntPreferenceValueDto -> incoming.value.toFloat()
            is LongPreferenceValueDto -> incoming.value.toFloat()
            is StringPreferenceValueDto -> incoming.value.toFloatOrNull()
                ?: throw IllegalArgumentException("Expected a float source preference value")
            else -> throw IllegalArgumentException("Expected a float source preference value")
        }
        SourcePreferenceTypeDto.SELECT -> when (incoming) {
            is SelectPreferenceValueDto -> incoming.value
            is StringPreferenceValueDto -> incoming.value
            is IntPreferenceValueDto -> incoming.value.toString()
            is LongPreferenceValueDto -> incoming.value.toString()
            is FloatPreferenceValueDto -> incoming.value.toString()
            is ListPreferenceValueDto -> incoming.value.firstOrNull().orEmpty()
            else -> throw IllegalArgumentException("Expected a select source preference value")
        }
        SourcePreferenceTypeDto.LIST -> when (incoming) {
            is ListPreferenceValueDto -> incoming.value.toSet()
            is StringPreferenceValueDto -> incoming.value.split(',').map {
                it.trim()
            }.filter { it.isNotEmpty() }.toSet()
            is SelectPreferenceValueDto -> setOf(incoming.value)
            else -> throw IllegalArgumentException("Expected a list source preference value")
        }
        SourcePreferenceTypeDto.UNKNOWN ->
            throw IllegalArgumentException("Unknown source preference types are read-only")
    }
}

internal fun SourcePreferenceModel.persist(type: SourcePreferenceTypeDto, key: String, value: Any) {
    if (key.isBlank()) throw IllegalArgumentException("Cannot persist a source preference without a key")
    val editor = preferences.edit()
    when (type) {
        SourcePreferenceTypeDto.BOOLEAN -> editor.putBoolean(key, value as Boolean)
        SourcePreferenceTypeDto.STRING, SourcePreferenceTypeDto.SELECT -> editor.putString(key, value.toString())
        SourcePreferenceTypeDto.INT -> editor.putInt(key, value as Int)
        SourcePreferenceTypeDto.LONG -> editor.putLong(key, value as Long)
        SourcePreferenceTypeDto.FLOAT -> editor.putFloat(key, value as Float)
        SourcePreferenceTypeDto.LIST -> {
            @Suppress("UNCHECKED_CAST")
            editor.putStringSet(key, value as Set<String>)
        }
        SourcePreferenceTypeDto.UNKNOWN -> throw IllegalArgumentException(
            "Unknown source preference types are read-only",
        )
    }
    editor.apply()
}

internal fun SourcePreferenceModel.applyToPreference(
    preference: Preference,
    type: SourcePreferenceTypeDto,
    value: Any,
) {
    when (preference) {
        is CheckBoxPreference -> preference.isChecked = value as Boolean
        is SwitchPreferenceCompat -> preference.isChecked = value as Boolean
        is EditTextPreference -> preference.text = value.toString()
        is ListPreference -> preference.value = value as String
        is MultiSelectListPreference -> {
            @Suppress("UNCHECKED_CAST")
            preference.values = (value as Set<String>)
        }
        else -> {
            // Unknown/custom preferences are read-only and never reach this branch.
            if (type != SourcePreferenceTypeDto.UNKNOWN) {
                throw IllegalArgumentException("Unsupported source preference class ${preference::class.java.name}")
            }
        }
    }
}
