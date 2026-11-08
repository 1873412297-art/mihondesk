package mihon.desktop.ui.browse

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import mihon.desktop.extension.getChapterList
import mihon.desktop.extension.getMangaDetails
import mihon.desktop.extension.loadFilterList
import mihon.desktop.extension.searchManga
import mihon.desktop.library.model.ChapterRecord
import mihon.desktop.library.model.LibraryManga
import mihon.desktop.library.model.MangaRecord
import mihon.desktop.ui.browse.migration.MigrationMatcher
import mihon.extension.model.SourceDescriptor
import mihon.extension.source.model.FilterList
import mihon.extension.source.model.SManga

internal fun BrowsePresenter.refreshMigrationCounts() {
    scope.launch {
        try {
            val allManga = withContext(Dispatchers.IO) { libraryRepository.librarySnapshot(null) }
            val countsBySource = allManga
                .groupBy { it.sourceId }
                .mapValues { it.value.size }

            val sourceList = stateUpdates.value.sources
            val sourceMap = sourceList.associateBy { it.id }

            val counts = countsBySource.map { (sourceId, count) ->
                SourceWithMangaCount(
                    sourceId = sourceId,
                    sourceName = sourceMap[sourceId]?.name ?: "Source #$sourceId",
                    mangaCount = count,
                )
            }.sortedByDescending { it.mangaCount }

            stateUpdates.update { it.copy(sourcesWithMangaCounts = counts) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            mihon.desktop.logging.DesktopLogger.warn("Browse", "Failed to count manga per source", error)
        }
    }
}

internal fun BrowsePresenter.selectMigrationSource(source: SourceWithMangaCount?) {
    if (source == null) {
        stateUpdates.update {
            it.copy(selectedMigrationSource = null, mangasForSelectedMigrationSource = emptyList())
        }
        return
    }

    scope.launch {
        val allManga = withContext(Dispatchers.IO) { libraryRepository.librarySnapshot(null) }
        val mangas = allManga.filter { it.sourceId == source.sourceId }
        stateUpdates.update {
            it.copy(
                selectedMigrationSource = source,
                mangasForSelectedMigrationSource = mangas,
            )
        }
    }
}

internal suspend fun BrowsePresenter.searchTargetMigrationSource(targetSourceId: Long, query: String): List<SManga> {
    return try {
        val page = sourceManager.searchManga(targetSourceId, 1, query)
        page.mangas
    } catch (_: Exception) {
        emptyList()
    }
}

internal suspend fun BrowsePresenter.autoMatchTargetSource(
    targetSourceId: Long,
    manga: LibraryManga,
): MigrationMatcher.MatchEvaluation {
    return try {
        var mangas = searchTargetMigrationSource(targetSourceId, manga.title)
        if (mangas.isEmpty()) {
            val cleaned = MigrationMatcher.cleanTitle(manga.title)
            if (cleaned != manga.title && cleaned.isNotBlank()) {
                mangas = searchTargetMigrationSource(targetSourceId, cleaned)
            }
        }
        MigrationMatcher.evaluateCandidates(manga.title, mangas)
    } catch (_: Exception) {
        MigrationMatcher.MatchEvaluation(emptyList(), null, false)
    }
}

/**
 * Loads the filter list for a source when the source browse screen is opened. Builtin sources
 * resolve immediately; extension sources are loaded through the extension host and decoded from
 * the IPC filter DTOs.
 */
internal suspend fun BrowsePresenter.loadSourceFilters(sourceId: Long): FilterList {
    return sourceManager.loadFilterList(sourceId)
}

internal fun BrowsePresenter.performMigration(
    oldManga: LibraryManga,
    targetSource: SourceDescriptor,
    targetManga: SManga,
) {
    scope.launch {
        stateUpdates.update { it.copy(isLoading = true) }
        val strings = stringsProvider()
        try {
            performMigrationInternal(oldManga, targetSource, targetManga)
            snackbarUpdates.emit(strings.migrateSuccessSnackbar(oldManga.title, targetSource.name))
            refresh()
            stateUpdates.value.selectedMigrationSource?.let { selectMigrationSource(it) }
            refreshMigrationCounts()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            stateUpdates.update { it.copy(installFailure = null, errorMessage = "Migration failed: ${e.message}") }
            snackbarUpdates.emit(strings.migrateFailedSnackbar(oldManga.title, e.message ?: "Unknown error"))
        } finally {
            stateUpdates.update { it.copy(isLoading = false) }
        }
    }
}

internal suspend fun BrowsePresenter.performMigrationInternal(
    oldManga: LibraryManga,
    targetSource: SourceDescriptor,
    targetManga: SManga,
): Long = withContext(Dispatchers.IO) {
    val detailedTarget = try {
        sourceManager.getMangaDetails(targetSource.id, targetManga)
    } catch (_: Exception) {
        targetManga
    }

    val targetChapters = try {
        sourceManager.getChapterList(targetSource.id, detailedTarget)
    } catch (_: Exception) {
        emptyList()
    }

    val oldChapters = libraryRepository.chapterSnapshot(oldManga.id)
    val oldDetails = libraryRepository.mangaSnapshot(oldManga.id)
    val oldRecord = libraryRepository.findManga(oldManga.sourceId, oldManga.url)
    val oldTrackings = libraryRepository.trackingSnapshot(oldManga.id)
    val sourceCategoryIds = oldDetails?.categories?.map { it.id }
        ?: libraryRepository.mangaCategoryLinksSnapshot()[oldManga.id].orEmpty()

    val existingTarget = libraryRepository.findManga(targetSource.id, detailedTarget.url)
    val existingTargetChapters = if (existingTarget != null) {
        libraryRepository.chapterSnapshot(existingTarget.id).associateBy { it.url }
    } else {
        emptyMap()
    }
    val existingTargetCategoryIds = if (existingTarget != null) {
        libraryRepository.mangaSnapshot(existingTarget.id)?.categories?.map { it.id }?.toSet().orEmpty()
    } else {
        emptySet()
    }

    val now = System.currentTimeMillis()

    libraryRepository.transaction {
        val newMangaId = if (existingTarget == null) {
            insertManga(
                MangaRecord(
                    id = 0L,
                    sourceId = targetSource.id,
                    url = detailedTarget.url,
                    title = detailedTarget.title,
                    artist = detailedTarget.artist,
                    author = detailedTarget.author,
                    description = detailedTarget.description,
                    genreJson = json.encodeToString(detailedTarget.genre),
                    status = detailedTarget.status.toLong(),
                    thumbnailUrl = detailedTarget.thumbnailUrl,
                    favorite = true,
                    dateAdded = oldDetails?.dateAdded ?: now,
                    lastModifiedAt = now,
                    favoriteModifiedAt = now,
                    initialized = true,
                    viewerFlags = oldDetails?.viewerFlags ?: 0L,
                    chapterFlags = oldDetails?.chapterFlags ?: 0L,
                    memoJson = oldDetails?.memoJson ?: "{}",
                ),
            )
        } else {
            val updatedViewerFlags = if (existingTarget.viewerFlags !=
                0L
            ) {
                existingTarget.viewerFlags
            } else {
                (oldDetails?.viewerFlags ?: 0L)
            }
            val updatedChapterFlags = if (existingTarget.chapterFlags !=
                0L
            ) {
                existingTarget.chapterFlags
            } else {
                (oldDetails?.chapterFlags ?: 0L)
            }
            val updatedMemoJson = if (existingTarget.memoJson.isNotBlank() &&
                existingTarget.memoJson != "{}"
            ) {
                existingTarget.memoJson
            } else {
                (oldDetails?.memoJson ?: "{}")
            }
            updateManga(
                existingTarget.copy(
                    favorite = true,
                    favoriteModifiedAt = now,
                    lastModifiedAt = now,
                    viewerFlags = updatedViewerFlags,
                    chapterFlags = updatedChapterFlags,
                    memoJson = updatedMemoJson,
                ),
            )
            existingTarget.id
        }

        val newChapterRecords = targetChapters.mapIndexed { index, sc ->
            val match = oldChapters.find { oc ->
                (sc.chapterNumber > 0f && oc.chapterNumber == sc.chapterNumber.toDouble()) ||
                    oc.name.trim().equals(sc.name.trim(), ignoreCase = true)
            }

            val existingChapter = existingTargetChapters[sc.url]
            ChapterRecord(
                id = existingChapter?.id ?: 0L,
                mangaId = newMangaId,
                url = sc.url,
                name = sc.name,
                scanlator = sc.scanlator,
                read = (existingChapter?.read == true) || (match?.read == true),
                bookmark = (existingChapter?.bookmark == true) || (match?.bookmark == true),
                lastPageRead = maxOf(existingChapter?.lastPageRead ?: 0L, match?.lastPageRead ?: 0L),
                chapterNumber = sc.chapterNumber.toDouble(),
                sourceOrder = index.toLong(),
                dateFetch = now,
                dateUpload = sc.dateUpload,
                lastModifiedAt = now,
            )
        }

        for (record in newChapterRecords) {
            if (record.id == 0L) {
                insertChapter(record)
            } else {
                updateChapter(record)
            }
        }

        for (categoryId in sourceCategoryIds) {
            if (categoryId !in existingTargetCategoryIds) {
                linkCategory(newMangaId, categoryId)
            }
        }

        for (tracking in oldTrackings) {
            val existingTracking = findTracking(newMangaId, tracking.trackerId)
            if (existingTracking == null) {
                insertTracking(tracking.copy(id = 0L, mangaId = newMangaId))
            } else {
                updateTracking(
                    existingTracking.copy(
                        lastChapterRead = maxOf(existingTracking.lastChapterRead, tracking.lastChapterRead),
                        score = if (existingTracking.score != 0.0) existingTracking.score else tracking.score,
                        status = if (existingTracking.status != 0L) existingTracking.status else tracking.status,
                        remoteId = if (existingTracking.remoteId !=
                            0L
                        ) {
                            existingTracking.remoteId
                        } else {
                            tracking.remoteId
                        },
                    ),
                )
            }
            deleteTracking(oldManga.id, tracking.trackerId)
        }

        if (oldRecord != null) {
            updateManga(
                oldRecord.copy(
                    favorite = false,
                    lastModifiedAt = now,
                    favoriteModifiedAt = now,
                ),
            )
        }

        newMangaId
    }
}

internal fun BrowsePresenter.startBatchMigration(targetSource: SourceDescriptor, delayMs: Long = 500L): Job? {
    val mangas = stateUpdates.value.mangasForSelectedMigrationSource
    if (mangas.isEmpty()) return null

    batchMigrationJob?.cancel()
    val job = batchMigrationRunner.start(
        mangas = mangas,
        targetSource = targetSource,
        searchFn = { query -> searchTargetMigrationSource(targetSource.id, query) },
        migrateFn = { oldManga, targetManga ->
            performMigrationInternal(oldManga, targetSource, targetManga)
        },
        delayMs = delayMs,
        onComplete = {
            refresh()
            stateUpdates.value.selectedMigrationSource?.let { selectMigrationSource(it) }
            refreshMigrationCounts()
        },
    )
    batchMigrationJob = job
    return job
}

internal fun BrowsePresenter.cancelBatchMigration() {
    batchMigrationRunner.cancel()
    batchMigrationJob?.cancel()
    batchMigrationJob = null
}

internal fun BrowsePresenter.dismissBatchMigrationReport() {
    batchMigrationRunner.reset()
}
