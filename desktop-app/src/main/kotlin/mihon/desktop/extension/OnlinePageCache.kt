package mihon.desktop.extension

import mihon.extension.ipc.RequestPriority
import mihon.reader.prefetch.PageLoadPriority
import java.io.File
import java.security.MessageDigest
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/**
 * Raw online page bytes for one manga. Decoded tiles stay in the reader memory budget.
 * Files live under `online-pages/manga-<id>/` so leaving that manga can delete only its bytes.
 */
internal object OnlinePageCacheFiles {
    const val MAX_MANGA_BYTES = 80L * 1024 * 1024
    const val PREVIOUS_TAIL_PAGES = 8

    fun mangaDirectory(root: File, mangaId: Long): File = File(root, "manga-$mangaId")

    fun pageFile(root: File, mangaId: Long, sourceId: Long, chapterUrl: String, index: Int): File =
        File(mangaDirectory(root, mangaId), "online_${digest(sourceId, chapterUrl)}_$index.img")

    fun chapterFiles(root: File, mangaId: Long, sourceId: Long, chapterUrl: String): List<File> {
        val prefix = "online_${digest(sourceId, chapterUrl)}_"
        val dir = mangaDirectory(root, mangaId)
        return dir.listFiles()?.filter { it.isFile && it.name.startsWith(prefix) }.orEmpty()
    }

    fun directoryBytes(dir: File): Long {
        if (!dir.isDirectory) return 0L
        return dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    /** Drop crash leftovers and the pre-0.3.26 flat `online_*.img` files. Not library downloads. */
    fun sweep(root: File) {
        if (!root.isDirectory) return
        root.listFiles()?.forEach { child ->
            when {
                child.isDirectory && child.name.startsWith("manga-") -> child.deleteRecursively()
                child.isFile && child.name.startsWith("online_") && child.name.endsWith(".img") -> child.delete()
                child.isFile && child.name.startsWith("page-") && child.name.endsWith(".tmp") -> child.delete()
            }
        }
    }

    private fun digest(sourceId: Long, chapterUrl: String): String {
        val identity = "$sourceId\n$chapterUrl"
        return MessageDigest.getInstance("SHA-256").digest(identity.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }
}

/** Online chapter source that can drop its raw image files without going through the network. */
interface OnlineImageCache {
    fun cachedImageFiles(): List<File>
    fun deleteCachedImages()
}

/** Forces a fetch priority. Prefetch of another chapter must not take the reader's last host slot. */
internal class OnlineFetchPriority(val value: Int) : AbstractCoroutineContextElement(OnlineFetchPriority) {
    companion object Key : CoroutineContext.Key<OnlineFetchPriority>
}

internal fun onlineImagePriority(context: CoroutineContext): Int {
    context[OnlineFetchPriority]?.value?.let { return it }
    val visible = context[PageLoadPriority]?.isVisible?.invoke() ?: true
    return if (visible) RequestPriority.READER else RequestPriority.NORMAL
}
