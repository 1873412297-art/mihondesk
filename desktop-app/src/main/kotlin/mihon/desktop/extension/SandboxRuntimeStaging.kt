package mihon.desktop.extension

import mihon.desktop.logging.DesktopLogger
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

/**
 * Copies of a host runtime staged for AppContainer hosts, shared by every launcher in the process.
 *
 * A staged runtime is a full copy of the host runtime (hundreds of megabytes), so launchers that
 * need the same source share one copy: [acquire] counts a lease and [release] drops the copy once
 * the last lease is gone. Copies are created directly in the temporary directory, which means a
 * process that dies before its last [release] strands them there, hence the stale sweep below.
 */
internal class SandboxRuntimeStaging(
    private val root: Path = defaultStagingRoot(),
    private val staleAgeMillis: Long = STALE_RUNTIME_AGE_MILLIS,
    private val liveProcessImages: () -> Set<String> = ::stagedRuntimeImagesInUse,
) {
    private val caches = mutableMapOf<String, File>()
    private val leases = mutableMapOf<String, Int>()
    private var swept = false

    /** Returns a staged copy of [original], reusing the copy already staged for that source. */
    fun acquire(original: File): File = synchronized(caches) {
        val key = original.absolutePath
        caches[key]?.takeIf { it.isDirectory }?.let { cached ->
            leases[key] = (leases[key] ?: 0) + 1
            return@synchronized cached
        }
        sweepStaleRuntimes()
        val target = Files.createTempDirectory(root, RUNTIME_DIRECTORY_PREFIX).toFile()
        try {
            original.walkTopDown().forEach { source ->
                val destination = target.resolve(source.relativeTo(original))
                if (source.isDirectory) destination.mkdirs() else source.copyTo(destination)
            }
        } catch (failure: Throwable) {
            // A half-copied runtime has no lease, so nothing would ever release it again.
            deleteTree(target)
            throw failure
        }
        caches[key] = target
        leases[key] = 1
        target
    }

    /** Drops one lease of [key] and removes the copy when the last lease is gone. Never throws. */
    fun release(key: String) {
        val owned = runCatching {
            synchronized(caches) {
                val remaining = (leases[key] ?: 1) - 1
                if (remaining > 0) {
                    leases[key] = remaining
                    null
                } else {
                    leases.remove(key)
                    caches.remove(key)
                }
            }
        }.getOrElse { failure ->
            DesktopLogger.warn(TAG, "Staged sandbox runtime lease bookkeeping failed for $key", failure)
            null
        }
        owned?.let(::deleteTree)
    }

    /**
     * Removes leftovers of processes that died without releasing their runtime.
     *
     * A copy is created when a host is first launched and dropped when its last launcher closes,
     * so one older than [staleAgeMillis] cannot belong to a host that is still expected to release
     * it. Leases held by this process and directories holding a running host image are skipped as
     * well. Runs once per process, right before the first copy is staged, and never propagates a
     * failure: a sweep that cannot run must not block launching an extension host.
     */
    private fun sweepStaleRuntimes() {
        if (swept) return
        swept = true
        runCatching { removeStaleRuntimes() }
            .onFailure { DesktopLogger.warn(TAG, "Stale sandbox runtime sweep failed", it) }
    }

    private fun removeStaleRuntimes() {
        if (!Files.isDirectory(root)) return
        val inUse = caches.values.mapTo(mutableSetOf()) { it.absolutePath } + liveProcessImages()
        val oldestAllowed = System.currentTimeMillis() - staleAgeMillis
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(SWEEP_BUDGET_MILLIS)
        Files.newDirectoryStream(root, "$RUNTIME_DIRECTORY_PREFIX*").use { candidates ->
            for (candidate in candidates) {
                if (System.nanoTime() > deadline) {
                    DesktopLogger.debug(TAG, "Stale sandbox runtime sweep stopped at its time budget")
                    return
                }
                val directory = candidate.toFile()
                if (isInUse(directory, inUse)) continue
                val modified = runCatching { Files.getLastModifiedTime(candidate).toMillis() }.getOrNull() ?: continue
                if (modified > oldestAllowed) continue
                deleteTree(directory)
                DesktopLogger.info(
                    TAG,
                    "Removed sandbox runtime ${directory.name} left behind by an earlier run",
                )
            }
        }
    }

    /** A staged copy is in use when it is leased here or when a live process runs one of its files. */
    private fun isInUse(directory: File, inUse: Set<String>): Boolean =
        inUse.any { it == directory.absolutePath || it.startsWith(directory.absolutePath + File.separator) }

    /** Best-effort recursive delete: a copy that survives is retried by the next sweep. */
    private fun deleteTree(owned: File) {
        runCatching {
            Files.walk(owned.toPath()).use { paths ->
                paths.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
            }
        }.onFailure { failure ->
            DesktopLogger.warn(
                TAG,
                "Staged sandbox runtime ${owned.absolutePath} could not be removed; it stays until a " +
                    "later sweep takes it (age ${staleAgeMillis / HOUR_MILLIS} h)",
                failure,
            )
        }
    }

    companion object {
        const val RUNTIME_DIRECTORY_PREFIX = "mihonw-sandbox-runtime-"

        /**
         * Age after which a staged runtime counts as abandoned. A live copy is created when its
         * host launches, so anything older is from an instance that already exited — the reported
         * leak was roughly one ~1 GB copy per app run whose JVM was killed before cleanup. One day
         * keeps a long but healthy session (which cleans up on close) well clear of the sweep, and
         * it is only the first guard: live leases and running host images are checked too.
         */
        const val STALE_RUNTIME_AGE_MILLIS = 24L * 60L * 60L * 1000L

        private const val SWEEP_BUDGET_MILLIS = 2_000L
        private const val HOUR_MILLIS = 60L * 60L * 1000L
        private const val TAG = "SandboxRuntime"

        val shared = SandboxRuntimeStaging()
    }
}

private fun defaultStagingRoot(): Path = runCatching {
    Path.of(System.getProperty("java.io.tmpdir").orEmpty())
}.getOrElse { Path.of(".") }

/**
 * Images of running processes inside a staged runtime, i.e. sandbox hosts still using their copy.
 * A best-effort safety net for copies owned by other processes; the age guard covers the rest.
 */
private fun stagedRuntimeImagesInUse(): Set<String> = runCatching {
    mutableSetOf<String>().apply {
        ProcessHandle.allProcesses().forEach { handle ->
            handle.info().command().orElse(null)?.let { image ->
                if (image.contains(SandboxRuntimeStaging.RUNTIME_DIRECTORY_PREFIX)) add(image)
            }
        }
    }
}.getOrDefault(emptySet())
