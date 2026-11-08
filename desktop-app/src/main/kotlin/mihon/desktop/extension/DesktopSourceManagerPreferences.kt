package mihon.desktop.extension

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mihon.desktop.extension.DesktopSourceManager.Companion.CAPABILITY_CONFIGURABLE_SOURCE
import mihon.desktop.extension.DesktopSourceManager.Companion.CAPABILITY_PREFERENCE_PREFIX
import mihon.desktop.extension.DesktopSourceManager.Companion.CAPABILITY_SOURCE_PREFERENCES
import mihon.desktop.extension.DesktopSourceManager.Companion.PREF_PREFIX_SOURCE_PREFERENCE
import mihon.desktop.logging.DesktopLogger
import mihon.desktop.preferences.DesktopPreferenceStore
import mihon.extension.ipc.BooleanPreferenceValueDto
import mihon.extension.ipc.FloatPreferenceValueDto
import mihon.extension.ipc.IntPreferenceValueDto
import mihon.extension.ipc.IpcException
import mihon.extension.ipc.ListPreferenceValueDto
import mihon.extension.ipc.LongPreferenceValueDto
import mihon.extension.ipc.SelectPreferenceValueDto
import mihon.extension.ipc.SourcePreferenceDefinitionDto
import mihon.extension.ipc.SourcePreferenceTypeDto
import mihon.extension.ipc.SourcePreferenceValueDto
import mihon.extension.ipc.StringPreferenceValueDto
import mihon.extension.ipc.UnknownPreferenceValueDto

internal fun DesktopSourceManager.getSourcePreferenceValue(sourceId: Long, key: String): String? {
    remoteSourcePreferences[sourceId]?.find { it.key == key }?.currentValue?.let { return it }
    return getLocalSourcePreferenceValue(sourceId, key)
}

internal fun DesktopSourceManager.setSourcePreferenceValue(sourceId: Long, key: String, value: String) {
    writeProperty(sourcePreferenceKey(sourceId, key), value)
    remoteSourcePreferences[sourceId]?.let { definitions ->
        remoteSourcePreferences[sourceId] = definitions.map { definition ->
            if (definition.key == key) definition.copy(currentValue = value) else definition
        }
    }
}

/**
 * Async set path for real extension-host preferences. Builtin/manifest sources keep using the
 * local [DesktopPreferenceStore] fallback; sources backed by a ConfigurableSource also update
 * the live preference object in the extension host.
 */
internal suspend fun DesktopSourceManager.setSourcePreference(sourceId: Long, key: String, value: String) {
    setSourcePreferenceInternal(sourceId, key, value)
}

internal suspend fun DesktopSourceManager.setSourcePreference(sourceId: Long, key: String, value: Boolean) =
    setSourcePreference(sourceId, key, value.toString())

internal suspend fun DesktopSourceManager.setSourcePreference(sourceId: Long, key: String, value: Int) =
    setSourcePreference(sourceId, key, value.toString())

internal suspend fun DesktopSourceManager.setSourcePreference(sourceId: Long, key: String, value: Long) =
    setSourcePreference(sourceId, key, value.toString())

internal suspend fun DesktopSourceManager.setSourcePreference(sourceId: Long, key: String, value: Float) =
    setSourcePreference(sourceId, key, value.toString())

internal suspend fun DesktopSourceManager.setSourcePreference(sourceId: Long, key: String, value: List<String>) =
    setSourcePreference(sourceId, key, encodeSourcePreferenceListValue(value))

internal fun DesktopSourceManager.getSourcePreferenceBoolean(
    sourceId: Long,
    key: String,
    default: Boolean = false,
): Boolean {
    return getSourcePreferenceValue(sourceId, key)?.toBooleanStrictOrNull() ?: default
}

internal fun DesktopSourceManager.setSourcePreferenceBoolean(sourceId: Long, key: String, value: Boolean) {
    setSourcePreferenceValue(sourceId, key, value.toString())
}

/**
 * Asynchronously loads definitions (with current values) for [sourceId].
 *
 * Extension sources that implement `eu.kanade.tachiyomi.source.ConfigurableSource` are queried
 * through the extension host. Sources without ConfigurableSource support transparently fall
 * back to registered/manifest definitions and locally persisted values.
 */
internal suspend fun DesktopSourceManager.getSourcePreferences(sourceId: Long): List<SourcePreferenceDefinition> =
    getSourcePreferencesSnapshot(sourceId).definitions

internal suspend fun DesktopSourceManager.getSourcePreferencesSnapshot(
    sourceId: Long,
): SourcePreferencesSnapshot = withContext(Dispatchers.IO) {
    val remote = loadRemoteSourcePreferences(sourceId)
    if (remote != null && remote.supported) {
        remotePreferenceSupport.add(sourceId)
        remoteSourcePreferences[sourceId] = remote.definitions
        return@withContext remote
    }

    remotePreferenceSupport.remove(sourceId)
    remoteSourcePreferences.remove(sourceId)
    val fallback = fallbackSourcePreferenceDefinitions(sourceId).map { definition ->
        definition.copy(
            currentValue = getLocalSourcePreferenceValue(sourceId, definition.key)
                ?: definition.defaultValue,
        )
    }
    SourcePreferencesSnapshot(
        definitions = fallback,
        supported = isLocallyConfigurable(sourceId) || fallback.isNotEmpty(),
    )
}

internal fun DesktopSourceManager.getSourcePreferenceDefinitions(sourceId: Long): List<SourcePreferenceDefinition> {
    remoteSourcePreferences[sourceId]?.let { return it }
    return fallbackSourcePreferenceDefinitions(sourceId)
}

internal fun DesktopSourceManager.isSourceConfigurable(sourceId: Long): Boolean {
    if (sourceId in remotePreferenceSupport) return true
    return isLocallyConfigurable(sourceId)
}

/** True when this manager can resolve [sourceId] (builtin or installed extension source). */

internal suspend fun DesktopSourceManager.setSourcePreferenceInternal(sourceId: Long, key: String, value: String) {
    val cachedRemote = if (sourceId in remotePreferenceSupport) remoteSourcePreferences[sourceId] else null
    val definitions = cachedRemote ?: getSourcePreferencesSnapshot(sourceId).definitions
    val definition = definitions.find { it.key == key }
        ?: fallbackSourcePreferenceDefinitions(sourceId).find { it.key == key }
        ?: throw IllegalArgumentException("Unknown source preference '$key'")
    if (definition.isReadOnly || definition.type == SourcePreferenceType.Unsupported) {
        throw IllegalArgumentException("Source preference '$key' is read-only")
    }

    if (sourceId in remotePreferenceSupport && processManager != null) {
        val remoteValue = definition.type.toSourcePreferenceValueDto(value)
        withLoadedExtensionSource(sourceId) { proc ->
            proc.setSourcePreference(sourceId, key, remoteValue)
        }
            ?: throw IllegalStateException("Extension host did not return the updated source preference")
        remoteSourcePreferences[sourceId] = definitions.map { current ->
            if (current.key == key) current.copy(currentValue = value) else current
        }
    }

    writeProperty(sourcePreferenceKey(sourceId, key), value)
}

internal suspend fun DesktopSourceManager.loadRemoteSourcePreferences(sourceId: Long): SourcePreferencesSnapshot? {
    if (builtinSources.containsKey(sourceId)) return null
    val proc = processManager ?: return null
    val installed = installer?.getInstalledExtensions() ?: return null
    val extension = installed.firstOrNull { candidate ->
        candidate.isEnabled && candidate.manifest.sources.any { it.id == sourceId }
    } ?: return null

    return try {
        val response = withLoadedExtensionSource(sourceId) { manager ->
            manager.getSourcePreferencesResult(sourceId)
        }
        val definitions = response.definitions.map { it.toDesktopDefinition() }
        SourcePreferencesSnapshot(
            definitions = definitions,
            supported = response.supported,
        )
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        DesktopLogger.warn("SourceManager", "Source preferences unavailable for $sourceId", error)
        null
    }
}

internal suspend fun DesktopSourceManager.restoreSourcePreferences(
    proc: WindowsExtensionProcessManager,
    sourceId: Long,
) {
    val saved = preferenceStore?.propertiesWithPrefix("$PREF_PREFIX_SOURCE_PREFERENCE$sourceId.").orEmpty()
    if (saved.isEmpty()) return
    val response = proc.getSourcePreferencesResult(sourceId)
    if (!response.supported) return
    val definitions = response.definitions.map { it.toDesktopDefinition() }.map { definition ->
        val stored = saved[sourcePreferenceKey(sourceId, definition.key)]
        if (stored == null || definition.isReadOnly || definition.type == SourcePreferenceType.Unsupported) {
            definition
        } else {
            proc.setSourcePreference(sourceId, definition.key, definition.type.toSourcePreferenceValueDto(stored))
            definition.copy(currentValue = stored)
        }
    }
    remotePreferenceSupport.add(sourceId)
    remoteSourcePreferences[sourceId] = definitions
}

/**
 * Runs one extension-source operation against a registry that contains [sourceId]. The process
 * manager can transparently start a fresh host between validation and the request itself; that
 * new host has an empty source registry. Repair that narrow race by reloading the owner and
 * retrying only the host's explicit missing-source response, at most once.
 */
internal suspend fun <T> DesktopSourceManager.withLoadedExtensionSource(
    sourceId: Long,
    operation: suspend (WindowsExtensionProcessManager) -> T,
): T {
    val proc = processManager ?: throw IllegalStateException("Extension host process manager is unavailable")
    ensureSourceLoaded(sourceId)
    return try {
        operation(proc)
    } catch (error: IpcException) {
        if (!error.isMissingSource(sourceId)) throw error
        val targetExt = installer?.getInstalledExtensions()
            ?.firstOrNull { extension -> extension.manifest.sources.any { it.id == sourceId } }
        if (targetExt == null && installer != null) throw SourceExtensionNotInstalledException(sourceId)
        targetExt?.let { extension -> loadedPackages.remove(extension.pkg) }
        if (targetExt != null && !java.io.File(targetExt.packageFile).exists()) {
            throw IllegalStateException(
                "扩展包文件缺失：${targetExt.packageFile}，请重新安装该扩展",
                error,
            )
        }
        ensureSourceLoaded(sourceId)
        try {
            operation(proc)
        } catch (retryError: IpcException) {
            if (retryError.isMissingSource(sourceId)) {
                if (targetExt != null && !java.io.File(targetExt.packageFile).exists()) {
                    throw IllegalStateException(
                        "扩展包文件缺失：${targetExt.packageFile}，请重新安装该扩展",
                        retryError,
                    )
                }
                if (targetExt == null) throw SourceExtensionNotInstalledException(sourceId)
                throw IllegalStateException(
                    "扩展包文件缺失：${targetExt.packageFile}，请重新安装该扩展",
                    retryError,
                )
            }
            throw retryError
        }
    }
}

internal fun IpcException.isMissingSource(sourceId: Long): Boolean =
    message?.contains("Source with ID $sourceId not found", ignoreCase = true) == true ||
        message == "No isolated host registered for source $sourceId"

internal fun DesktopSourceManager.fallbackSourcePreferenceDefinitions(
    sourceId: Long,
): List<SourcePreferenceDefinition> {
    registeredSourcePreferences[sourceId]?.takeIf { it.isNotEmpty() }?.let { return it }

    val builtin = builtinSources[sourceId]
    if (builtin is DesktopConfigurableSource) {
        return builtin.getPreferenceDefinitions()
    }

    val extension = installer?.getInstalledExtensions()
        ?.firstOrNull { extension -> extension.manifest.sources.any { it.id == sourceId } }
        ?: return emptyList()

    return extension.manifest.capabilities.mapNotNull(::parseSourcePreferenceCapability)
}

internal fun DesktopSourceManager.isLocallyConfigurable(sourceId: Long): Boolean {
    if (registeredSourcePreferences[sourceId]?.isNotEmpty() == true) return true
    if (builtinSources[sourceId] is DesktopConfigurableSource) return true

    val extension = installer?.getInstalledExtensions()
        ?.firstOrNull { extension -> extension.manifest.sources.any { it.id == sourceId } }
        ?: return false

    return extension.manifest.capabilities.any { capability ->
        capability.equals(CAPABILITY_SOURCE_PREFERENCES, ignoreCase = true) ||
            capability.equals(CAPABILITY_CONFIGURABLE_SOURCE, ignoreCase = true) ||
            capability.startsWith(CAPABILITY_PREFERENCE_PREFIX, ignoreCase = true)
    }
}

internal fun DesktopSourceManager.getLocalSourcePreferenceValue(sourceId: Long, key: String): String? {
    return preferenceStore?.property(sourcePreferenceKey(sourceId, key))
}

internal fun SourcePreferenceDefinitionDto.toDesktopDefinition(): SourcePreferenceDefinition {
    return SourcePreferenceDefinition(
        key = key,
        title = title.ifBlank { key },
        summary = summary,
        type = type.toDesktopType(),
        defaultValue = defaultValue.toDisplayString(),
        currentValue = (currentValue ?: defaultValue).toDisplayString(),
        options = options.map { SourcePreferenceOption(label = it.label, value = it.value) },
        isReadOnly = readOnly || type == SourcePreferenceTypeDto.UNKNOWN,
    )
}

internal fun SourcePreferenceTypeDto.toDesktopType(): SourcePreferenceType = when (this) {
    SourcePreferenceTypeDto.BOOLEAN -> SourcePreferenceType.Boolean
    SourcePreferenceTypeDto.STRING -> SourcePreferenceType.String
    SourcePreferenceTypeDto.INT -> SourcePreferenceType.Int
    SourcePreferenceTypeDto.LONG -> SourcePreferenceType.Long
    SourcePreferenceTypeDto.FLOAT -> SourcePreferenceType.Float
    SourcePreferenceTypeDto.SELECT -> SourcePreferenceType.Select
    SourcePreferenceTypeDto.LIST -> SourcePreferenceType.List
    SourcePreferenceTypeDto.UNKNOWN -> SourcePreferenceType.Unsupported
}

internal fun SourcePreferenceValueDto?.toDisplayString(): String = when (this) {
    null -> ""
    is BooleanPreferenceValueDto -> value.toString()
    is StringPreferenceValueDto -> value
    is IntPreferenceValueDto -> value.toString()
    is LongPreferenceValueDto -> value.toString()
    is FloatPreferenceValueDto -> value.toString()
    is SelectPreferenceValueDto -> value
    is ListPreferenceValueDto -> encodeSourcePreferenceListValue(value)
    is UnknownPreferenceValueDto -> value.orEmpty()
}

internal fun SourcePreferenceType.toSourcePreferenceValueDto(raw: String): SourcePreferenceValueDto {
    return when (this) {
        SourcePreferenceType.Boolean -> BooleanPreferenceValueDto(
            raw.toBooleanStrictOrNull()
                ?: when (raw.trim().lowercase()) {
                    "1" -> true
                    "0" -> false
                    else -> throw IllegalArgumentException("Expected a boolean value")
                },
        )
        SourcePreferenceType.String -> StringPreferenceValueDto(raw)
        SourcePreferenceType.Int -> IntPreferenceValueDto(
            raw.toIntOrNull() ?: throw IllegalArgumentException("Expected an int value"),
        )
        SourcePreferenceType.Long -> LongPreferenceValueDto(
            raw.toLongOrNull() ?: throw IllegalArgumentException("Expected a long value"),
        )
        SourcePreferenceType.Float -> FloatPreferenceValueDto(
            raw.toFloatOrNull() ?: throw IllegalArgumentException("Expected a float value"),
        )
        SourcePreferenceType.Select -> SelectPreferenceValueDto(raw)
        SourcePreferenceType.List -> ListPreferenceValueDto(decodeSourcePreferenceListValue(raw))
        SourcePreferenceType.Unsupported -> throw IllegalArgumentException("Unsupported source preference type")
    }
}

internal fun DesktopSourceManager.sourcePreferenceKey(sourceId: Long, key: String): String {
    return "$PREF_PREFIX_SOURCE_PREFERENCE$sourceId.$key"
}
