package mihon.desktop.image

import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties
import java.util.concurrent.ConcurrentHashMap

/**
 * Remembers which CDN mirror host worked last and which hosts failed recently, persisted across launches,
 * so icon loads go straight to a reachable host instead of waiting on a blocked one.
 */
internal class MirrorHealth(
    private val file: Path?,
    private val failureTtlMillis: Long = 24L * 60 * 60 * 1000,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val failures = ConcurrentHashMap<String, Long>()

    @Volatile
    var lastGoodHost: String? = null
        private set

    init {
        load()
    }

    /** Healthy hosts first (last-good at the front); recently failed hosts are skipped unless nothing else is left. */
    fun order(candidates: List<String>): List<String> {
        val now = clock()
        val healthy = candidates.filter { candidate ->
            val failedAt = failures[hostOf(candidate)] ?: return@filter true
            now - failedAt >= failureTtlMillis
        }
        val pool = healthy.ifEmpty { candidates }
        val good = lastGoodHost
        return pool.sortedByDescending { hostOf(it) == good }
    }

    fun isRecentlyFailed(host: String): Boolean = failures[host]?.let { clock() - it < failureTtlMillis } ?: false

    fun recordSuccess(host: String) {
        if (host.isBlank()) return
        val changed = lastGoodHost != host || failures.remove(host) != null
        lastGoodHost = host
        if (changed) save()
    }

    fun recordFailure(host: String) {
        if (host.isBlank()) return
        failures[host] = clock()
        if (lastGoodHost == host) lastGoodHost = null
        save()
    }

    private fun hostOf(url: String): String = runCatching { URI(url).host }.getOrNull().orEmpty()

    private fun load() {
        val path = file ?: return
        runCatching {
            if (!Files.isRegularFile(path)) return
            val props = Properties()
            Files.newInputStream(path).use { props.load(it) }
            lastGoodHost = props.getProperty("lastGood")?.takeIf { it.isNotBlank() }
            props.stringPropertyNames().filter { it.startsWith("fail.") }.forEach { key ->
                props.getProperty(key).toLongOrNull()?.let { failures[key.removePrefix("fail.")] = it }
            }
        }
    }

    @Synchronized
    private fun save() {
        val path = file ?: return
        runCatching {
            val props = Properties()
            lastGoodHost?.let { props.setProperty("lastGood", it) }
            failures.forEach { (host, at) -> props.setProperty("fail.$host", at.toString()) }
            Files.createDirectories(path.parent)
            val tmp = path.resolveSibling("${path.fileName}.tmp")
            Files.newOutputStream(tmp).use { props.store(it, "CDN mirror health") }
            Files.move(tmp, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        }
    }
}
