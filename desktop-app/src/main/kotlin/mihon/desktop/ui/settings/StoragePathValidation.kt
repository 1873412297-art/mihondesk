package mihon.desktop.ui.settings

import mihon.desktop.i18n.UiText
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path

internal data class StoragePathValidation(val path: String = "", val error: UiText? = null)

/** Called on IO only when the user applies the draft. Never relocate existing files. */
internal fun validateStoragePath(draft: String): StoragePathValidation {
    val input = draft.trim()
    if (input.isEmpty()) return StoragePathValidation()
    val path = try {
        Path.of(input).normalize()
    } catch (_: InvalidPathException) {
        return StoragePathValidation(error = UiText.DownloadPathInvalid)
    }
    if (!path.isAbsolute) return StoragePathValidation(error = UiText.DownloadPathAbsolute)
    if (Files.exists(path) &&
        !Files.isDirectory(path)
    ) {
        return StoragePathValidation(error = UiText.DownloadPathNotDirectory)
    }
    return try {
        Files.createDirectories(path)
        val probe = Files.createTempFile(path, ".mihondesk-write-", ".tmp")
        Files.delete(probe)
        StoragePathValidation(path.toString())
    } catch (_: Exception) {
        StoragePathValidation(error = UiText.DownloadPathUnwritable)
    }
}
