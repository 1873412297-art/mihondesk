package mihon.desktop.extension

import java.net.URI

/**
 * Label for the store that listed [item].
 *
 * The store's own declared name wins when its index publishes one; otherwise the repository URL is
 * reduced to something recognizable (owner/repository for GitHub URLs, host plus path otherwise).
 * Returns null when neither the item nor its listing knows where it came from.
 */
fun extensionStoreLabel(item: ExtensionStoreItem): String? =
    extensionStoreLabel(item.storeName, item.repoUrl)

/** Label for a store identified by its declared [storeName] and/or its [repoUrl]. */
fun extensionStoreLabel(storeName: String, repoUrl: String): String? {
    val declared = storeName.trim()
    if (declared.isNotEmpty()) return declared
    val raw = repoUrl.trim().trimEnd('/')
    if (raw.isEmpty()) return null
    return githubRepositoryLabel(raw) ?: hostAndPathLabel(raw)
}

private val GITHUB_REPOSITORY = Regex(
    """^https?://(?:raw\.githubusercontent\.com|github\.com)/([^/]+)/([^/]+?)(?:\.git)?(?:/.*)?$""",
)

private fun githubRepositoryLabel(repoUrl: String): String? {
    val match = GITHUB_REPOSITORY.matchEntire(repoUrl) ?: return null
    val (owner, repository) = match.destructured
    return "$owner/$repository"
}

private fun hostAndPathLabel(repoUrl: String): String = try {
    val uri = URI(repoUrl)
    val host = uri.host
    val path = uri.path?.trim('/').orEmpty()
    when {
        host.isNullOrBlank() -> repoUrl
        path.isEmpty() -> host
        else -> "$host/$path"
    }
} catch (_: Exception) {
    repoUrl
}
