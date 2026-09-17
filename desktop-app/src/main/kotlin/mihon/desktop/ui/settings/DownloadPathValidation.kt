package mihon.desktop.ui.settings

import mihon.desktop.i18n.UiText
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path

internal data class DownloadPathValidation(val path: String = "", val error: UiText? = null)

/** Called on IO only when the user applies the draft. Never relocate existing downloads. */
internal fun validateDownloadPath(draft: String): DownloadPathValidation {
    val input = draft.trim()
    if (input.isEmpty()) return DownloadPathValidation()
    val path = try {
        Path.of(input).normalize()
    } catch (_: InvalidPathException) {
        return DownloadPathValidation(error = UiText.DownloadPathInvalid)
    }
    if (!path.isAbsolute) return DownloadPathValidation(error = UiText.DownloadPathAbsolute)
    if (Files.exists(path) &&
        !Files.isDirectory(path)
    ) {
        return DownloadPathValidation(error = UiText.DownloadPathNotDirectory)
    }
    return try {
        Files.createDirectories(path)
        val probe = Files.createTempFile(path, ".mihondesk-write-", ".tmp")
        Files.delete(probe)
        DownloadPathValidation(path.toString())
    } catch (_: Exception) {
        DownloadPathValidation(error = UiText.DownloadPathUnwritable)
    }
}
