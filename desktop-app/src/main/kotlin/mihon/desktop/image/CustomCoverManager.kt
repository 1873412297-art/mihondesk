package mihon.desktop.image

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.concurrent.ConcurrentHashMap

class CustomCoverManager(
    private val coversDir: Path,
) {
    val customDir: Path = coversDir.resolve("custom")
    private val coverCache = ConcurrentHashMap<Long, Path>()
    private val negativeCache = ConcurrentHashMap<Long, Boolean>()

    init {
        Files.createDirectories(customDir)
    }

    fun getCustomCover(mangaId: Long): Path? {
        coverCache[mangaId]?.let { path ->
            if (Files.isRegularFile(path) && Files.size(path) > 0) return path
            coverCache.remove(mangaId)
        }
        if (negativeCache[mangaId] == true) return null

        val extensions = listOf(".jpg", ".png", ".webp", ".jpeg")
        for (ext in extensions) {
            val file = customDir.resolve("custom_$mangaId$ext")
            if (Files.isRegularFile(file) && Files.size(file) > 0) {
                negativeCache.remove(mangaId)
                coverCache[mangaId] = file
                return file
            }
        }
        negativeCache[mangaId] = true
        return null
    }

    fun setCustomCover(mangaId: Long, sourceFile: Path): Path {
        require(Files.isRegularFile(sourceFile)) { "Source file must be a regular file: $sourceFile" }
        removeCustomCover(mangaId)
        val ext = sourceFile.fileName.toString().substringAfterLast('.', "jpg").lowercase()
        val target = customDir.resolve("custom_$mangaId.$ext")
        Files.copy(sourceFile, target, StandardCopyOption.REPLACE_EXISTING)
        negativeCache.remove(mangaId)
        coverCache[mangaId] = target
        return target
    }

    fun removeCustomCover(mangaId: Long): Boolean {
        coverCache.remove(mangaId)
        negativeCache.remove(mangaId)
        var removed = false
        val extensions = listOf(".jpg", ".png", ".webp", ".jpeg")
        for (ext in extensions) {
            val file = customDir.resolve("custom_$mangaId$ext")
            if (Files.deleteIfExists(file)) {
                removed = true
            }
        }
        return removed
    }

    fun hasCustomCover(mangaId: Long): Boolean = getCustomCover(mangaId) != null
}
