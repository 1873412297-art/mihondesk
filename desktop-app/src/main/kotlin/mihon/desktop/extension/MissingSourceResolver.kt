package mihon.desktop.extension

import mihon.desktop.extension.compat.TachiyomiExtensionConverter
import mihon.desktop.library.model.MangaRecord

/**
 * Encapsulates information about a manga source missing from the desktop environment.
 */
data class MissingSourceInfo(
    val sourceId: Long,
    val mangaCount: Int,
    val extension: ExtensionStoreItem?,
)

object MissingSourceResolver {

    /**
     * Identifies sources referenced by library mangas that are not installed on the desktop.
     * Skips local source (sourceId == 0).
     */
    fun findMissingSources(
        mangas: List<MangaRecord>,
        installedSourceIds: Set<Long>,
        available: List<ExtensionStoreItem>,
    ): List<MissingSourceInfo> {
        val missingCounts = mangas
            .filter { it.sourceId != 0L && it.sourceId !in installedSourceIds }
            .groupingBy { it.sourceId }
            .eachCount()

        return missingCounts.map { (sourceId, count) ->
            val ext = available.firstOrNull { item ->
                item.sources.any { s ->
                    s.id == sourceId ||
                        (s.id == 0L && TachiyomiExtensionConverter.generateSourceId(s.name, s.lang) == sourceId)
                }
            }
            MissingSourceInfo(
                sourceId = sourceId,
                mangaCount = count,
                extension = ext,
            )
        }
    }

    /**
     * Resolves available extensions from [storeService] if [cached] is empty.
     * Fails silently returning empty list on errors or when no store service / repos configured.
     */
    suspend fun ensureAvailableExtensions(
        storeService: ExtensionStoreService?,
        cached: List<ExtensionStoreItem> = emptyList(),
    ): List<ExtensionStoreItem> {
        if (cached.isNotEmpty()) return cached
        if (storeService == null) return emptyList()
        return try {
            storeService.fetchAvailableExtensions()
        } catch (_: Exception) {
            emptyList()
        }
    }
}

fun findMissingSources(
    mangas: List<MangaRecord>,
    installedSourceIds: Set<Long>,
    available: List<ExtensionStoreItem>,
): List<MissingSourceInfo> = MissingSourceResolver.findMissingSources(mangas, installedSourceIds, available)

suspend fun ensureAvailableExtensions(
    storeService: ExtensionStoreService?,
    cached: List<ExtensionStoreItem> = emptyList(),
): List<ExtensionStoreItem> = MissingSourceResolver.ensureAvailableExtensions(storeService, cached)
