package mihon.desktop.image

/**
 * Equivalent mirror URLs for GitHub-hosted assets, tried in order when the primary host is unreachable
 * (cdn.jsdelivr.net and raw.githubusercontent.com are commonly blocked or TLS-reset on some networks).
 */
internal fun cdnMirrorCandidates(url: String): List<String> {
    val jsdelivr =
        Regex("""^https?://(?:cdn|fastly|gcore|testingcf)\.jsdelivr\.net/gh/([^/]+)/([^/@]+)@([^/]+)/(.+)$""")
    val raw = Regex("""^https?://raw\.githubusercontent\.com/([^/]+)/([^/]+)/([^/]+)/(.+)$""")
    val (user, repo, ref, path) = (jsdelivr.find(url) ?: raw.find(url))?.destructured ?: return listOf(url)
    val all = listOf(
        "https://cdn.jsdelivr.net/gh/$user/$repo@$ref/$path",
        "https://fastly.jsdelivr.net/gh/$user/$repo@$ref/$path",
        "https://gcore.jsdelivr.net/gh/$user/$repo@$ref/$path",
        "https://raw.githubusercontent.com/$user/$repo/$ref/$path",
    )
    return (listOf(url) + all).distinct()
}
