package mihon.desktop.extension

import mihon.extension.source.WindowsHttpSource

internal fun DesktopSourceManager.ownsSource(sourceId: Long): Boolean {
    if (builtinSources.containsKey(sourceId)) return true
    return installer?.getInstalledExtensions()
        ?.any { extension -> extension.manifest.sources.any { it.id == sourceId } }
        ?: false
}

// ---------------------------------------------------------------------
// Cookies
// ---------------------------------------------------------------------

/** Removes stored cookies for every domain declared by the extension containing [sourceId]. */
internal fun DesktopSourceManager.clearSourceCookies(sourceId: Long): Int {
    val store = activeCookieStore ?: return 0
    val owner = installer?.getInstalledExtensions()
        ?.firstOrNull { extension -> extension.manifest.sources.any { it.id == sourceId } }?.pkg ?: "builtin"
    store.clearSession(owner)
    var cleared = 0
    cookieDomainsForSource(sourceId).forEach { domain ->
        if (store.getDomainConfig(domain) != null) {
            store.removeCookies(domain)
            cleared++
        }
    }
    return cleared
}

internal fun DesktopSourceManager.clearExtensionCookies(pkg: String): Int {
    val extension = installer?.getInstalledExtensions()?.firstOrNull { it.pkg == pkg } ?: return 0
    return extension.manifest.sources.sumOf { source -> clearSourceCookies(source.id) }
}

/** Alias for [clearSourceCookies]. */
internal fun DesktopSourceManager.clearCookies(sourceId: Long): Int = clearSourceCookies(sourceId)

/** Alias for [clearExtensionCookies]. */
internal fun DesktopSourceManager.clearCookiesForExtension(pkg: String): Int = clearExtensionCookies(pkg)

internal fun DesktopSourceManager.cookieDomainsForSource(sourceId: Long): Set<String> {
    val domains = linkedSetOf<String>()

    val extension = installer?.getInstalledExtensions()
        ?.firstOrNull { extension -> extension.manifest.sources.any { it.id == sourceId } }
    extension?.manifest?.declaredDomains?.forEach { domain ->
        if (domain.isNotBlank() && domain != "*") {
            domains += domain
        }
    }

    val builtin = builtinSources[sourceId]
    if (builtin is WindowsHttpSource) {
        runCatching { java.net.URI(builtin.baseUrl).host }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?.let { domains += it }
    }

    return domains
}

// ---------------------------------------------------------------------
// Source loading / browsing
// ---------------------------------------------------------------------
