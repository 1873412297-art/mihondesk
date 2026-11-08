package mihon.desktop.library.util

import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/** Replaces [target] atomically where the filesystem allows it, falling back for FAT/SMB shares. */
internal fun moveReplacing(source: Path, target: Path) {
    try {
        Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    } catch (_: AtomicMoveNotSupportedException) {
        Files.move(source, target, StandardCopyOption.REPLACE_EXISTING)
    }
}
