package mihon.desktop.platform

import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path

/**
 * The single place that decides whether this launch is portable and where its data lives.
 *
 * A launch is portable when any of these is present beside the executable:
 * - `--portable` on the command line;
 * - a `.portable` marker file (the layout every release so far shipped, data in `data/`);
 * - a `portable` marker file (same layout, a name Explorer does not hide);
 * - a `portable-data/` directory, which is then itself the data root.
 *
 * `portable-data/` wins over `data/` so a user who creates it gets exactly the folder they made.
 * Symlinks are not followed: a link planted beside the executable must not redirect the profile.
 */
data class PortableLayout(val portable: Boolean, val root: Path?) {
    companion object {
        const val LEGACY_MARKER = ".portable"
        const val MARKER = "portable"
        const val DATA_DIRECTORY = "portable-data"
        const val LEGACY_DATA_DIRECTORY = "data"

        fun detect(args: Array<String>, executableDirectory: Path): PortableLayout {
            val directory = executableDirectory.resolve(DATA_DIRECTORY)
            if (Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) return PortableLayout(true, directory)
            val marked = "--portable" in args ||
                isMarker(executableDirectory.resolve(LEGACY_MARKER)) ||
                isMarker(executableDirectory.resolve(MARKER))
            return if (marked) {
                PortableLayout(true, executableDirectory.resolve(LEGACY_DATA_DIRECTORY))
            } else {
                PortableLayout(false, null)
            }
        }

        // A directory called `portable` is not a marker; only `portable-data/` selects a directory.
        private fun isMarker(path: Path): Boolean = Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
    }

    val mode: DistributionMode get() = if (portable) DistributionMode.Portable else DistributionMode.Installed
}
