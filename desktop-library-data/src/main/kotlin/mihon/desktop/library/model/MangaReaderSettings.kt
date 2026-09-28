package mihon.desktop.library.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import mihon.reader.model.ReadingMode

/**
 * Per-manga reader settings override.
 *
 * Each field is nullable: `null` signifies "follow global settings", while a non-null
 * value represents an explicit override for this specific manga.
 */
@Serializable
data class MangaReaderSettingsOverride(
    val readingMode: ReadingMode? = null,
    val preloadPages: Int? = null,
) {
    val isEmpty: Boolean get() = readingMode == null && preloadPages == null
}

/**
 * Manages encoding and decoding of per-manga reader settings overrides.
 *
 * ReadingMode is aligned with Android Tachiyomi/Mihon `viewer_flags` (lower 3 bits 0x07),
 * ensuring bidirectional compatibility with Android backup imports/exports and sync.
 * Preload pages and extended desktop options are preserved in `memo_json` under the
 * `"readerSettings"` object key.
 */
object MangaReaderSettings {
    const val VIEWER_MASK: Long = 0x00000007L

    fun readingModeFromViewerFlags(flags: Long): ReadingMode? = when ((flags and VIEWER_MASK).toInt()) {
        1 -> ReadingMode.SINGLE_LTR
        2 -> ReadingMode.SINGLE_RTL
        3 -> ReadingMode.VERTICAL
        4, 5 -> ReadingMode.WEBTOON
        6 -> ReadingMode.DUAL_LTR
        7 -> ReadingMode.DUAL_RTL
        else -> null
    }

    fun encodeViewerFlags(currentFlags: Long, mode: ReadingMode?): Long {
        val cleared = currentFlags and VIEWER_MASK.inv()
        val flagValue = when (mode) {
            null -> 0L
            ReadingMode.SINGLE_LTR -> 1L
            ReadingMode.SINGLE_RTL -> 2L
            ReadingMode.VERTICAL -> 3L
            ReadingMode.WEBTOON -> 4L
            ReadingMode.DUAL_LTR -> 6L
            ReadingMode.DUAL_RTL -> 7L
        }
        return cleared or flagValue
    }

    fun parse(viewerFlags: Long, memoJson: String): MangaReaderSettingsOverride {
        val root = runCatching { Json.parseToJsonElement(memoJson) as? JsonObject }.getOrNull()
        val readerObj = root?.get("readerSettings") as? JsonObject
        val memoReadingMode = readerObj?.get("readingMode")?.jsonPrimitive?.contentOrNull?.let { name ->
            runCatching { ReadingMode.valueOf(name) }.getOrNull()
        }
        val readingMode = memoReadingMode ?: readingModeFromViewerFlags(viewerFlags)
        val preloadPages = readerObj?.get("preloadPages")?.jsonPrimitive?.intOrNull
            ?.coerceIn(1, 10)
        return MangaReaderSettingsOverride(
            readingMode = readingMode,
            preloadPages = preloadPages,
        )
    }

    fun encode(memoJson: String, override: MangaReaderSettingsOverride?): String {
        val root = when (val parsed = runCatching { Json.parseToJsonElement(memoJson) }.getOrNull()) {
            null -> JsonObject(emptyMap())
            is JsonObject -> parsed
            // Valid JSON but not an object (e.g. an array or primitive): there is no object to
            // merge the override into, so preserve the original content untouched.
            else -> return memoJson
        }
        if (override == null || override.isEmpty) {
            return Json.encodeToString(JsonObject(root - "readerSettings"))
        }
        val objMap = mutableMapOf<String, JsonPrimitive>()
        if (override.readingMode != null) {
            objMap["readingMode"] = JsonPrimitive(override.readingMode.name)
        }
        if (override.preloadPages != null) {
            objMap["preloadPages"] = JsonPrimitive(override.preloadPages)
        }
        return Json.encodeToString(JsonObject(root + ("readerSettings" to JsonObject(objMap))))
    }
}

val MangaDetails.readerSettingsOverride: MangaReaderSettingsOverride?
    get() = MangaReaderSettings.parse(viewerFlags, memoJson).takeUnless { it.isEmpty }
