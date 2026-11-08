package mihon.desktop.ui.library

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import mihon.desktop.library.model.MangaDetails
import mihon.desktop.library.model.MangaReaderSettings
import mihon.desktop.library.model.MangaReaderSettingsOverride
import mihon.desktop.library.repository.LibraryMutationPort
import mihon.desktop.library.repository.LibraryRepository
import mihon.desktop.preferences.DesktopPreferenceStore

/**
 * Chapter-settings overrides for the open manga: in-memory state plus ordered persistence.
 * Writes read-modify-write the manga row, so they run one at a time away from the UI thread.
 */
internal class ChapterSettingsCoordinator(
    private val scope: CoroutineScope,
    private val repository: LibraryRepository,
    private val mutationPort: LibraryMutationPort?,
    private val preferences: DesktopPreferenceStore?,
    private val detailMangaId: () -> Long?,
    private val detailManga: () -> MangaDetails?,
    private val onPersisted: () -> Unit,
) : AutoCloseable {
    val overrides = MutableStateFlow(ChapterSettingsOverrides())

    fun updateCurrent(transform: (ChapterSettings) -> ChapterSettings) {
        val mangaId = detailMangaId() ?: return
        update(mangaId, transform)
    }

    fun saveOverride(override: MangaReaderSettingsOverride) {
        val mangaId = detailMangaId() ?: return
        scope.launch(Dispatchers.IO) {
            val currentManga = runCatching { repository.mangaSnapshot(mangaId) }.getOrNull()
                ?: detailManga()?.takeIf { it.id == mangaId }
                ?: return@launch
            val updatedViewerFlags = MangaReaderSettings.encodeViewerFlags(
                currentManga.viewerFlags,
                override.readingMode,
            )
            val updatedMemo = MangaReaderSettings.encode(currentManga.memoJson, override)
            val updatedRecord = currentManga.toMangaRecord().copy(
                viewerFlags = updatedViewerFlags,
                memoJson = updatedMemo,
            )
            mutationPort?.updateManga(updatedRecord)
            onPersisted()
        }
    }

    fun clearOverride() {
        val mangaId = detailMangaId() ?: return
        scope.launch(Dispatchers.IO) {
            val currentManga = runCatching { repository.mangaSnapshot(mangaId) }.getOrNull()
                ?: detailManga()?.takeIf { it.id == mangaId }
                ?: return@launch
            val updatedViewerFlags = MangaReaderSettings.encodeViewerFlags(currentManga.viewerFlags, null)
            val updatedMemo = MangaReaderSettings.encode(currentManga.memoJson, null)
            val updatedRecord = currentManga.toMangaRecord().copy(
                viewerFlags = updatedViewerFlags,
                memoJson = updatedMemo,
            )
            mutationPort?.updateManga(updatedRecord)
            onPersisted()
        }
    }

    fun setAsDefault(applyToExisting: Boolean) {
        val mangaId = detailMangaId() ?: return
        val currentOverrides = overrides.value
        val inMemorySettings = if (currentOverrides.mangaId == mangaId && currentOverrides.settings != null) {
            currentOverrides.settings
        } else {
            detailManga()?.takeIf { it.id == mangaId }?.toChapterSettings()
        }
        if (inMemorySettings != null) {
            preferences?.update {
                setProperty(CHAPTER_DEFAULT_FLAGS_KEY, encodeChapterFlags(0L, inMemorySettings).toString())
                setProperty(CHAPTER_DEFAULT_SHOW_MISSING_KEY, inMemorySettings.showMissingChapters.toString())
            }
        }
        if (applyToExisting || inMemorySettings == null) {
            scope.launch(Dispatchers.IO) {
                val settings = inMemorySettings ?: currentChapterSettings(mangaId)
                if (inMemorySettings == null) {
                    preferences?.update {
                        setProperty(CHAPTER_DEFAULT_FLAGS_KEY, encodeChapterFlags(0L, settings).toString())
                        setProperty(CHAPTER_DEFAULT_SHOW_MISSING_KEY, settings.showMissingChapters.toString())
                    }
                }
                if (applyToExisting) {
                    val now = System.currentTimeMillis()
                    val records = repository.allMangaSnapshot().filter { it.favorite }
                    mutationPort?.transaction {
                        for (record in records) {
                            updateManga(
                                record.copy(
                                    chapterFlags = encodeChapterFlags(record.chapterFlags, settings),
                                    memoJson = encodeShowMissingChapters(record.memoJson, settings.showMissingChapters),
                                ),
                            )
                        }
                    }
                    onPersisted()
                }
            }
        }
    }

    fun resetToDefault() {
        val mangaId = detailMangaId() ?: return
        val defaults = defaultSettings().copy(excludedScanlators = emptySet())
        overrides.update { it.copy(mangaId = mangaId, settings = defaults) }
        persistInBackground(mangaId, defaults)
    }

    private fun update(mangaId: Long, transform: (ChapterSettings) -> ChapterSettings) {
        val updated = transform(currentChapterSettings(mangaId))
        overrides.update { it.copy(mangaId = mangaId, settings = updated) }
        persistInBackground(mangaId, updated)
    }

    /**
     * Chapter settings writes read-modify-write the manga row, so they are applied one at a time in
     * call order away from the UI thread; overlapping writes could discard the newer choice.
     */
    private val writes = Channel<Pair<Long, ChapterSettings>>(Channel.UNLIMITED)

    init {
        scope.launch(Dispatchers.IO) {
            for ((mangaId, settings) in writes) {
                persistChapterSettings(mangaId, settings)
            }
        }
    }

    private fun persistInBackground(mangaId: Long, settings: ChapterSettings) {
        writes.trySend(mangaId to settings)
    }

    private fun currentChapterSettings(mangaId: Long): ChapterSettings {
        val overrides = overrides.value
        val overrideSettings = overrides.settings
        if (overrides.mangaId == mangaId && overrideSettings != null) return overrideSettings
        val detailManga = detailManga()
        if (detailManga?.id == mangaId) return detailManga.toChapterSettings()
        val persisted = runCatching { repository.mangaSnapshot(mangaId) }.getOrNull()?.toChapterSettings()
            ?: defaultSettings()
        return persisted
    }

    private fun persistChapterSettings(mangaId: Long, settings: ChapterSettings) {
        val record = runCatching { repository.mangaSnapshot(mangaId) }.getOrNull()
            ?: detailManga()?.takeIf { it.id == mangaId }
            ?: return
        val now = System.currentTimeMillis()
        mutationPort?.updateManga(
            record.toMangaRecord().copy(
                chapterFlags = encodeChapterFlags(record.chapterFlags, settings),
                excludedScanlatorsJson = encodeExcludedScanlators(settings.excludedScanlators),
                memoJson = encodeShowMissingChapters(record.memoJson, settings.showMissingChapters),
            ),
        )
        onPersisted()
    }

    fun defaultSettings(): ChapterSettings {
        val flags = preferences?.property(CHAPTER_DEFAULT_FLAGS_KEY)?.toLongOrNull() ?: 0L
        val showMissing = preferences?.property(CHAPTER_DEFAULT_SHOW_MISSING_KEY)?.toBooleanStrictOrNull() ?: true
        return chapterSettingsFromFlags(chapterFlags = flags, showMissingChapters = showMissing)
    }

    override fun close() {
        writes.close()
    }
}
