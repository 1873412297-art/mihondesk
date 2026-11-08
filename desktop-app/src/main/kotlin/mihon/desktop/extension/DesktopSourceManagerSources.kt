package mihon.desktop.extension

import mihon.desktop.extension.DesktopSourceManager.Companion.CAPABILITY_PREFERENCE_PREFIX
import mihon.desktop.extension.DesktopSourceManager.Companion.PREF_PREFIX_EXTENSION_INCOGNITO
import mihon.desktop.extension.DesktopSourceManager.Companion.PREF_PREFIX_SOURCE_ENABLED
import mihon.desktop.extension.DesktopSourceManager.Companion.PREF_PREFIX_SOURCE_INCOGNITO
import mihon.desktop.extension.builtin.BundledLocalSource
import mihon.desktop.library.repository.LibraryRepository
import mihon.extension.model.SourceDescriptor
import mihon.extension.source.WindowsCatalogueSource

internal fun DesktopSourceManager.attachLibraryRepository(repository: LibraryRepository?) {
    libraryRepository = repository
    localLibraryRepository = repository
    localMangaRepository = repository
    this.repository = repository
    localRepository = repository
}

/** Alias for [attachLibraryRepository]. */
internal fun DesktopSourceManager.setLibraryRepository(
    repository: LibraryRepository?,
) = attachLibraryRepository(repository)

/** Alias for [attachLibraryRepository] matching local-source terminology. */
internal fun DesktopSourceManager.attachLocalLibraryRepository(
    repository: LibraryRepository?,
) = attachLibraryRepository(repository)

/** Alias for [attachLibraryRepository] matching local-source terminology. */
internal fun DesktopSourceManager.setLocalLibraryRepository(
    repository: LibraryRepository?,
) = attachLibraryRepository(repository)

/** Alias for [attachLibraryRepository] matching local-source terminology. */
internal fun DesktopSourceManager.registerLocalLibraryRepository(
    repository: LibraryRepository?,
) = attachLibraryRepository(repository)

/** Alias for [attachLibraryRepository] matching local-source terminology. */
internal fun DesktopSourceManager.registerLocalSource(
    repository: LibraryRepository?,
) = attachLibraryRepository(repository)

internal fun DesktopSourceManager.registerBuiltinSource(source: WindowsCatalogueSource) {
    builtinSources[source.id] = source
    if (source is DesktopConfigurableSource) {
        registeredSourcePreferences[source.id] = source.getPreferenceDefinitions()
    }
}

internal fun DesktopSourceManager.unregisterBuiltinSource(sourceId: Long) {
    builtinSources.remove(sourceId)
    registeredSourcePreferences.remove(sourceId)
}

/** Registers preference definitions for a source that is not represented by a Kotlin object. */
internal fun DesktopSourceManager.registerSourcePreferences(
    sourceId: Long,
    definitions: List<SourcePreferenceDefinition>,
) {
    registeredSourcePreferences[sourceId] = definitions
}

internal fun DesktopSourceManager.unregisterSourcePreferences(sourceId: Long) {
    registeredSourcePreferences.remove(sourceId)
}

/**
 * Returns every known source with its persisted desktop flags. Sources belonging to a disabled
 * extension are intentionally omitted, mirroring Android's extension enable toggle.
 */
internal fun DesktopSourceManager.getSourceStates(): List<SourceState> {
    val builtins = builtinSources.values.map { source ->
        sourceState(descriptorFor(source), extensionPackage = null)
    }

    val extensionSources = installer?.getInstalledExtensions()
        ?.filter { it.isEnabled }
        ?.flatMap { extension ->
            extension.manifest.sources.map { source ->
                sourceState(source, extensionPackage = extension.pkg)
            }
        }
        ?: emptyList()

    return (builtins + extensionSources).distinctBy { it.source.id }
}

/** Alias for [getSourceStates] matching the Android "sources with state" terminology. */
internal fun DesktopSourceManager.getSourcesWithState(): List<SourceState> = getSourceStates()

/** All sources reported by an installed extension, including sources disabled individually. */
internal fun DesktopSourceManager.getSourceStatesForExtension(pkg: String): List<SourceState> {
    val extension = installer?.getInstalledExtensions()?.firstOrNull { it.pkg == pkg } ?: return emptyList()
    return extension.manifest.sources.map { source ->
        sourceState(source, extensionPackage = extension.pkg)
    }
}

/** Alias for [getSourceStatesForExtension]. */
internal fun DesktopSourceManager.getSourcesForExtension(
    pkg: String,
): List<SourceState> = getSourceStatesForExtension(pkg)

/** Current source list. Disabled sources (extension-level or per-source) are filtered out. */
internal fun DesktopSourceManager.getSources(): List<SourceDescriptor> {
    return getSourceStates().filter { it.isEnabled }.map { it.source }
}

internal fun DesktopSourceManager.findSourceDescriptor(sourceId: Long): SourceDescriptor? {
    return getSources().find { it.id == sourceId }
}

/**
 * Resolves a source descriptor for [sourceId] if it belongs to any builtin source
 * or installed extension (even if disabled). Returns null if uninstalled/missing.
 */
internal fun DesktopSourceManager.get(sourceId: Long): SourceDescriptor? {
    val builtin = builtinSources[sourceId]
    if (builtin != null) return descriptorFor(builtin)
    return installer?.getInstalledExtensions()
        ?.flatMap { it.manifest.sources }
        ?.firstOrNull { it.id == sourceId }
}

/** Set of all source IDs known to the manager (builtins + all installed extensions). */
internal fun DesktopSourceManager.getInstalledSourceIds(): Set<Long> {
    val installed = installer?.getInstalledExtensions()
        ?.flatMap { it.manifest.sources }
        ?.map { it.id }
        ?: emptyList()
    return (installed + builtinSources.keys).toSet()
}

// ---------------------------------------------------------------------
// Per-source / per-extension desktop settings
// ---------------------------------------------------------------------

internal fun DesktopSourceManager.isSourceEnabled(
    sourceId: Long,
): Boolean = readBoolean(sourceEnabledKey(sourceId), default = true)

internal fun DesktopSourceManager.setSourceEnabled(sourceId: Long, enabled: Boolean) {
    writeProperty(sourceEnabledKey(sourceId), enabled.toString())
}

internal fun DesktopSourceManager.toggleSourceEnabled(sourceId: Long): Boolean {
    val updated = !isSourceEnabled(sourceId)
    setSourceEnabled(sourceId, updated)
    return updated
}

internal fun DesktopSourceManager.isSourceIncognito(
    sourceId: Long,
): Boolean = readBoolean(sourceIncognitoKey(sourceId), default = false)

internal fun DesktopSourceManager.setSourceIncognito(sourceId: Long, incognito: Boolean) {
    writeProperty(sourceIncognitoKey(sourceId), incognito.toString())
}

internal fun DesktopSourceManager.toggleSourceIncognito(sourceId: Long): Boolean {
    val updated = !isSourceIncognito(sourceId)
    setSourceIncognito(sourceId, updated)
    return updated
}

internal fun DesktopSourceManager.isExtensionIncognito(
    pkg: String,
): Boolean = readBoolean(extensionIncognitoKey(pkg), default = false)

internal fun DesktopSourceManager.setExtensionIncognito(pkg: String, incognito: Boolean) {
    writeProperty(extensionIncognitoKey(pkg), incognito.toString())
}

internal fun DesktopSourceManager.toggleExtensionIncognito(pkg: String): Boolean {
    val updated = !isExtensionIncognito(pkg)
    setExtensionIncognito(pkg, updated)
    return updated
}

internal fun DesktopSourceManager.descriptorFor(source: WindowsCatalogueSource): SourceDescriptor = SourceDescriptor(
    id = source.id,
    name = source.name,
    lang = source.lang,
    className = source::class.java.name,
    supportsLatest = source.supportsLatest,
)

internal fun DesktopSourceManager.sourceState(
    source: SourceDescriptor,
    extensionPackage: String?,
): SourceState = SourceState(
    source = source,
    isEnabled = isSourceEnabled(source.id),
    isIncognito = isSourceIncognito(source.id),
    isConfigurable = isSourceConfigurable(source.id),
    extensionPackage = extensionPackage,
    isLocal = source.id == BundledLocalSource.ID,
)

internal fun DesktopSourceManager.sourceEnabledKey(sourceId: Long): String = "$PREF_PREFIX_SOURCE_ENABLED$sourceId"

internal fun DesktopSourceManager.sourceIncognitoKey(sourceId: Long): String = "$PREF_PREFIX_SOURCE_INCOGNITO$sourceId"

internal fun DesktopSourceManager.extensionIncognitoKey(pkg: String): String = "$PREF_PREFIX_EXTENSION_INCOGNITO$pkg"

internal fun DesktopSourceManager.readBoolean(key: String, default: Boolean): Boolean {
    return preferenceStore?.property(key)?.toBooleanStrictOrNull() ?: default
}

internal fun DesktopSourceManager.writeProperty(key: String, value: String) {
    preferenceStore?.update {
        setProperty(key, value)
    }
}

internal fun DesktopSourceManager.parseSourcePreferenceCapability(capability: String): SourcePreferenceDefinition? {
    if (!capability.startsWith(CAPABILITY_PREFERENCE_PREFIX, ignoreCase = true)) return null
    val parts = capability.split(":")
    if (parts.size < 4) return null

    val type = when (parts[1].trim().lowercase()) {
        "boolean", "bool" -> SourcePreferenceType.Boolean
        "string", "text" -> SourcePreferenceType.String
        "int", "integer" -> SourcePreferenceType.Int
        "long" -> SourcePreferenceType.Long
        "float", "double" -> SourcePreferenceType.Float
        "select" -> SourcePreferenceType.Select
        "list", "multiselect", "multi" -> SourcePreferenceType.List
        else -> return null
    }

    val key = parts[2].trim()
    if (key.isEmpty()) return null

    val title = parts.drop(3).joinToString(":").trim().ifBlank { key }
    return SourcePreferenceDefinition(
        key = key,
        title = title,
        type = type,
    )
}
