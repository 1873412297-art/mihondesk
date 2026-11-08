package mihon.desktop.extension

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.io.File

internal suspend fun DesktopExtensionInstaller.uninstallInternal(pkg: String): Boolean {
    val installer = this
    return withLifecycleLock {
        withContext(Dispatchers.IO) {
            val current = getInstalledExtensions()
            val target = current.find { it.pkg == pkg } ?: return@withContext false

            DesktopSourceManager.releaseInstalledPackage(installer, pkg)
            val targetDir = File(target.installDir)
            val removedDir = File(installRoot, ".removed-$pkg-${java.util.UUID.randomUUID()}")
            if (targetDir.exists()) java.nio.file.Files.move(targetDir.toPath(), removedDir.toPath())
            try {
                saveInstalledExtensions(current.filter { it.pkg != pkg })
            } catch (error: Throwable) {
                if (removedDir.exists()) java.nio.file.Files.move(removedDir.toPath(), targetDir.toPath())
                throw error
            }
            cleanupOrRemember(removedDir)
            true
        }
    }
}

internal suspend fun DesktopExtensionInstaller.setExtensionEnabledInternal(
    pkg: String,
    enabled: Boolean,
): Boolean = withLifecycleLock {
    withContext(Dispatchers.IO) {
        val current = getInstalledExtensions()
        val index = current.indexOfFirst { it.pkg == pkg }
        if (index == -1) return@withContext false

        val updated = current.toMutableList()
        updated[index] = updated[index].copy(isEnabled = enabled)
        saveInstalledExtensions(updated)
        true
    }
}

internal fun DesktopExtensionInstaller.cleanupOrRemember(directory: File) {
    if (!directory.exists() || directory.deleteRecursively()) return
    val pending = runCatching {
        json.decodeFromString<List<String>>(
            preferenceStore.property(DesktopExtensionInstaller.PREF_KEY_PENDING_CLEANUP) ?: "[]",
        )
    }.getOrDefault(emptyList())
    preferenceStore.update {
        setProperty(
            DesktopExtensionInstaller.PREF_KEY_PENDING_CLEANUP,
            json.encodeToString(
                (
                    pending +
                        directory.absolutePath
                    ).distinct(),
            ),
        )
    }
}

internal fun DesktopExtensionInstaller.retryPendingCleanupInternal() {
    val pending = runCatching {
        json.decodeFromString<List<String>>(
            preferenceStore.property(DesktopExtensionInstaller.PREF_KEY_PENDING_CLEANUP) ?: "[]",
        )
    }.getOrDefault(emptyList())
    if (pending.isEmpty()) return
    val root = installRoot.canonicalFile
    val remaining = pending.filter { path ->
        val directory = File(path)
        val safe = directory.canonicalFile.parentFile == root &&
            listOf(".stage-", ".backup-", ".removed-").any { directory.name.startsWith(it) }
        safe && directory.exists() && !directory.deleteRecursively()
    }
    preferenceStore.update {
        setProperty(DesktopExtensionInstaller.PREF_KEY_PENDING_CLEANUP, json.encodeToString(remaining))
    }
}
