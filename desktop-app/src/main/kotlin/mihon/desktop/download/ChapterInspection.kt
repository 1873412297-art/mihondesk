package mihon.desktop.download

import java.nio.file.Files

/** Checks whether a published chapter is complete, preferring the cheap manifest metadata. */
internal object ChapterInspection {

    fun inspect(
        layout: DownloadPathLayout,
        sourceId: Long,
        mangaTitle: String,
        chapterName: String,
        expectedPageIndexes: List<Int>,
        mangaId: Long? = null,
        chapterId: Long? = null,
    ): DownloadChapterInspection {
        val entry = layout.findChapterEntry(sourceId, mangaTitle, chapterName, mangaId, chapterId)
            ?: return DownloadChapterInspection(expectedPageIndexes.size, emptyMap())
        return if (entry.archive) {
            DownloadArchiveCodec.inspectAt(entry.path, expectedPageIndexes)
        } else {
            DownloadPageFiles.inspectChapterAt(entry.path, expectedPageIndexes)
        }
    }

    fun isDownloaded(
        layout: DownloadPathLayout,
        sourceId: Long,
        mangaTitle: String,
        chapterName: String,
        mangaId: Long? = null,
        chapterId: Long? = null,
    ): Boolean {
        val entry = layout.findChapterEntry(sourceId, mangaTitle, chapterName, mangaId, chapterId) ?: return false
        if (entry.archive) return DownloadArchiveCodec.isChapterComplete(entry.path)
        val chapterDir = entry.path
        val manifestFile = DownloadArchiveCodec.manifestPath(chapterDir)
        val manifest = DownloadArchiveCodec.readManifest(chapterDir)
        if (Files.exists(manifestFile) && manifest == null) return false
        if (manifest != null && manifest.totalPages > 0) {
            return DownloadPageFiles.inspectChapterAt(
                chapterDir,
                manifest.pages.map(DownloadedPageMetadata::index),
            ).isComplete
        }
        // Older downloads may predate manifests. The saved queue migrates them during startup.
        return runCatching {
            Files.list(chapterDir).use { paths ->
                paths.anyMatch { path ->
                    Files.isRegularFile(path) &&
                        path.fileName.toString() != MANIFEST_FILE &&
                        !path.fileName.toString().endsWith(".part") &&
                        runCatching { Files.size(path) > 0L }.getOrDefault(false)
                }
            }
        }.getOrDefault(false)
    }
}
