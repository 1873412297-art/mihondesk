package mihon.desktop.extension

/**
 * Canonical repository identity: trims trailing slashes, removes a known index-file suffix, and
 * rewrites GitHub raw/tree URLs to raw.githubusercontent.com. Shared by repository storage,
 * candidate installability and mirror selection so one URL never has two identities.
 */
internal fun normalizeRepositoryUrl(raw: String): String {
    var url = raw.trim().trimEnd('/')
    val knownSuffixes = listOf("/index.min.json", "/index.json", "/index.pb", "/repo.json")
    for (suffix in knownSuffixes) {
        if (url.endsWith(suffix)) {
            url = url.removeSuffix(suffix).trimEnd('/')
            break
        }
    }
    val githubRawRegex = Regex("""^https?://github\.com/([^/]+)/([^/]+)/(?:raw|tree)/(.+)$""")
    val match = githubRawRegex.matchEntire(url)
    if (match != null) {
        val (user, repo, branch) = match.destructured
        url = "https://raw.githubusercontent.com/$user/$repo/$branch"
    }
    return url.trimEnd('/')
}
