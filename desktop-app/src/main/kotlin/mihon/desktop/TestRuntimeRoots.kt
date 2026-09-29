package mihon.desktop

import mihon.desktop.logging.DesktopLogger
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Data roots handed out by [DesktopRuntime.forTesting], removed when the JVM exits.
 *
 * A root holds a database, preferences and media files, so it cannot be deleted while its runtime
 * may still use it: [create] only tracks the root, and [releaseAll] removes every tracked root from
 * a single shutdown hook registered with the first root. That covers a JVM that runs the tests to
 * the end; a JVM killed mid-run strands its roots, hence the age-guarded sweep that runs once,
 * before the first root of a process is created.
 */
internal class TestRuntimeRoots(
    tempRoot: Path = defaultTempRoot(),
    private val staleAgeMillis: Long = STALE_ROOT_AGE_MILLIS,
) {
    private val tempRoot = tempRoot.toAbsolutePath().normalize()
    private val tracked = ConcurrentLinkedQueue<Path>()
    private val hookRegistered = AtomicBoolean(false)
    private val swept = AtomicBoolean(false)

    /** Creates a tracked root under [tempRoot], sweeping abandoned roots from earlier runs first. */
    fun create(): Path {
        if (swept.compareAndSet(false, true)) {
            runCatching { sweepStaleRoots() }
                .onFailure { DesktopLogger.warn(TAG, "Stale test runtime sweep failed", it) }
        }
        val root = Files.createTempDirectory(tempRoot, ROOT_DIRECTORY_PREFIX)
        tracked.add(root)
        registerShutdownHook()
        return root
    }

    /** Roots this process created and has not released yet. */
    fun trackedRoots(): List<Path> = tracked.toList()

    /** Untracks [root] and deletes it. Never throws, so a failing delete leaves nothing dangling. */
    fun release(root: Path) {
        tracked.remove(root)
        deleteTree(root)
    }

    /** Untracks and deletes every remaining root; the shutdown hook body, so it never throws. */
    fun releaseAll() {
        while (true) {
            val root = tracked.poll() ?: return
            deleteTree(root)
        }
    }

    /**
     * Removes roots of processes that exited without releasing them.
     *
     * A root is only useful to the test that created it, so one older than [staleAgeMillis] with no
     * owner in this process cannot be in use any more. Roots tracked here are skipped regardless of
     * age. Bounded by a time budget and never propagating a failure: cleanup must not break the
     * test run that triggers it.
     */
    internal fun sweepStaleRoots() {
        if (!Files.isDirectory(tempRoot)) return
        val inUse = tracked.toList()
        val oldestAllowed = System.currentTimeMillis() - staleAgeMillis
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(SWEEP_BUDGET_MILLIS)
        Files.newDirectoryStream(tempRoot, "$ROOT_DIRECTORY_PREFIX*").use { candidates ->
            for (candidate in candidates) {
                if (System.nanoTime() > deadline) {
                    DesktopLogger.debug(TAG, "Stale test runtime sweep stopped at its time budget")
                    return
                }
                if (candidate in inUse) continue
                val modified = runCatching { Files.getLastModifiedTime(candidate).toMillis() }.getOrNull() ?: continue
                if (modified > oldestAllowed) continue
                deleteTree(candidate)
                DesktopLogger.info(
                    TAG,
                    "Removed test runtime ${candidate.fileName} left behind by an earlier run",
                )
            }
        }
    }

    private fun registerShutdownHook() {
        if (!hookRegistered.compareAndSet(false, true)) return
        runCatching {
            Runtime.getRuntime().addShutdownHook(
                Thread(::releaseAll, "mihon-test-runtime-cleanup").apply { isDaemon = true },
            )
        }.onFailure {
            DesktopLogger.warn(TAG, "Test runtime cleanup hook was rejected; roots stay under $tempRoot", it)
        }
    }

    /** Best-effort recursive delete: a root that survives is retried by the next sweep. */
    private fun deleteTree(owned: Path) {
        runCatching {
            Files.walk(owned).use { paths ->
                paths.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
            }
        }.onFailure { failure ->
            DesktopLogger.warn(
                TAG,
                "Test runtime ${owned.fileName} could not be removed; it stays until a later sweep takes " +
                    "it (age ${staleAgeMillis / HOUR_MILLIS} h)",
                failure,
            )
        }
    }

    companion object {
        const val ROOT_DIRECTORY_PREFIX = "mihon-runtime-test"

        /**
         * Age after which an untracked root counts as abandoned. A root lives only as long as the
         * tests using it, so one older than a day cannot belong to a JVM still running tests: the
         * leak this guards was ~14 roots of ~100 KB per full run, which had grown to 1945 roots and
         * 202 MB. One day keeps even a long soak run clear of the sweep and bounds the litter to a
         * single day's worth.
         */
        const val STALE_ROOT_AGE_MILLIS = 24L * 60L * 60L * 1000L

        private const val SWEEP_BUDGET_MILLIS = 2_000L
        private const val HOUR_MILLIS = 60L * 60L * 1000L
        private const val TAG = "TestRuntime"

        val shared = TestRuntimeRoots()
    }
}

private fun defaultTempRoot(): Path = runCatching {
    Path.of(System.getProperty("java.io.tmpdir").orEmpty())
}.getOrElse { Path.of(".") }
