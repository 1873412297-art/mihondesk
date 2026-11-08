package mihon.desktop.track

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Field accessors shared by the tracker clients. The strict variants mirror the previous per-tracker
 * helpers (a non-primitive value throws); the safe variants return null instead.
 */
internal fun JsonObject.objField(name: String): JsonObject? = this[name] as? JsonObject

/** Strict variant: a field that exists but is not an object throws, matching Kitsu's old helper. */
internal fun JsonObject.strictObjField(name: String): JsonObject? = this[name]?.jsonObject

internal fun JsonObject.arrayField(name: String): JsonArray? = this[name]?.jsonArray

internal fun JsonObject.arrayFieldOrEmpty(name: String): JsonArray = arrayField(name) ?: JsonArray(emptyList())

internal fun JsonObject.stringField(name: String): String? = this[name]?.jsonPrimitive?.content

internal fun JsonObject.longField(name: String): Long? = stringField(name)?.toLongOrNull()

internal fun JsonObject.doubleField(name: String): Double? = stringField(name)?.toDoubleOrNull()

internal fun JsonObject.safeStringField(name: String): String? =
    (this[name] as? JsonPrimitive)?.content

internal fun JsonObject.safeLongField(name: String): Long? = safeStringField(name)?.toLongOrNull()

internal fun JsonObject.safeBoolField(name: String): Boolean? = safeStringField(name)?.toBooleanStrictOrNull()
