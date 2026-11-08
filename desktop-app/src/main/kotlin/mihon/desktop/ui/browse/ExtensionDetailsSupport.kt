package mihon.desktop.ui.browse

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.coroutines.launch
import mihon.desktop.extension.ExtensionTrustStatus
import mihon.desktop.extension.ExtensionTrustStore
import mihon.desktop.extension.InstalledExtension
import mihon.desktop.i18n.LocalStrings
import mihon.desktop.i18n.locale
import mihon.desktop.logging.DesktopLogger
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

internal class ExtensionDetailsEnvironment(
    val snackbarHostState: SnackbarHostState,
    val showSnackbar: (String) -> Unit,
    val icon: ImageBitmap?,
    val dateFormatter: DateTimeFormatter,
    val trustStatus: ExtensionTrustStatus,
    val trust: () -> Unit,
    val revoke: () -> Unit,
    val showTrustButton: Boolean,
    val showRevokeButton: Boolean,
    val menuShowsTrust: Boolean,
    val menuShowsRevoke: Boolean,
    val repoUrl: String?,
)

internal class ExtensionTrustUi(
    val status: ExtensionTrustStatus,
    val trust: () -> Unit,
    val revoke: () -> Unit,
)

@Composable
internal fun rememberExtensionDetailsEnvironment(
    extension: InstalledExtension,
    extensionTrustStatus: ExtensionTrustStatus?,
    extensionTrustState: ExtensionTrustStatus?,
    trustStore: ExtensionTrustStore?,
    onTrust: (() -> Unit)?,
    onTrustExtension: ((InstalledExtension) -> Unit)?,
    onTrustPackage: ((String) -> Unit)?,
    onRevoke: (() -> Unit)?,
    onRevokeExtension: ((InstalledExtension) -> Unit)?,
    onRevokePackage: ((String) -> Unit)?,
): ExtensionDetailsEnvironment {
    val strings = LocalStrings.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val trustUi = rememberExtensionTrustUi(
        extension = extension,
        extensionTrustStatus = extensionTrustStatus,
        extensionTrustState = extensionTrustState,
        trustStore = trustStore,
        onTrust = onTrust,
        onTrustExtension = onTrustExtension,
        onTrustPackage = onTrustPackage,
        onRevoke = onRevoke,
        onRevokeExtension = onRevokeExtension,
        onRevokePackage = onRevokePackage,
    )
    val icon = remember(extension.iconPath) { loadExtensionIcon(extension.iconPath) }
    val dateFormatter = remember(strings) {
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
            .withLocale(strings.locale)
            .withZone(ZoneId.systemDefault())
    }
    val repoUrl = remember(extension.repoUrl) { extensionRepoUrl(extension.repoUrl) }
    val showTrustButton = onTrust != null || onTrustExtension != null || onTrustPackage != null ||
        trustUi.status != ExtensionTrustStatus.TRUSTED
    val showRevokeButton = onRevoke != null || onRevokeExtension != null || onRevokePackage != null ||
        trustUi.status == ExtensionTrustStatus.TRUSTED
    return ExtensionDetailsEnvironment(
        snackbarHostState = snackbarHostState,
        showSnackbar = { message -> scope.launch { snackbarHostState.showSnackbar(message) } },
        icon = icon,
        dateFormatter = dateFormatter,
        trustStatus = trustUi.status,
        trust = trustUi.trust,
        revoke = trustUi.revoke,
        showTrustButton = showTrustButton,
        showRevokeButton = showRevokeButton,
        menuShowsTrust = trustUi.status != ExtensionTrustStatus.TRUSTED || onTrust != null,
        menuShowsRevoke = trustUi.status == ExtensionTrustStatus.TRUSTED || onRevoke != null,
        repoUrl = repoUrl,
    )
}

@Composable
private fun rememberExtensionTrustUi(
    extension: InstalledExtension,
    extensionTrustStatus: ExtensionTrustStatus?,
    extensionTrustState: ExtensionTrustStatus?,
    trustStore: ExtensionTrustStore?,
    onTrust: (() -> Unit)?,
    onTrustExtension: ((InstalledExtension) -> Unit)?,
    onTrustPackage: ((String) -> Unit)?,
    onRevoke: (() -> Unit)?,
    onRevokeExtension: ((InstalledExtension) -> Unit)?,
    onRevokePackage: ((String) -> Unit)?,
): ExtensionTrustUi {
    val activeTrustStore = trustStore
    val trustRevision = activeTrustStore?.revision?.collectAsState()?.value ?: 0L
    val fingerprints = remember(
        extension.signatureFingerprints,
        extension.signatureFingerprint,
    ) {
        extension.signatureFingerprints.ifEmpty {
            listOfNotNull(extension.signatureFingerprint.takeIf { it.isNotBlank() })
        }
    }
    val status = remember(
        extension.pkg,
        fingerprints,
        extension.signingKey,
        extension.trustStatus,
        extensionTrustStatus,
        extensionTrustState,
        trustRevision,
    ) {
        resolveEffectiveTrustStatus(
            activeTrustStore = activeTrustStore,
            extension = extension,
            fingerprints = fingerprints,
            overrideStatus = extensionTrustStatus ?: extensionTrustState,
        )
    }
    return ExtensionTrustUi(
        status = status,
        trust = resolveTrustAction(
            extension,
            activeTrustStore,
            fingerprints,
            onTrust,
            onTrustExtension,
            onTrustPackage,
        ),
        revoke = resolveRevokeAction(extension, activeTrustStore, onRevoke, onRevokeExtension, onRevokePackage),
    )
}

private fun resolveEffectiveTrustStatus(
    activeTrustStore: ExtensionTrustStore?,
    extension: InstalledExtension,
    fingerprints: List<String>,
    overrideStatus: ExtensionTrustStatus?,
): ExtensionTrustStatus {
    val storeStatus = activeTrustStore?.status(
        pkg = extension.pkg,
        fingerprints = fingerprints,
        trustedSigningKeys = listOfNotNull(extension.signingKey.takeIf { it.isNotBlank() }),
        signatureValid = extension.trustStatus != ExtensionTrustStatus.INVALID,
    )
    val storeHasOpinion = activeTrustStore != null && (
        activeTrustStore.getTrustedFingerprints(extension.pkg).isNotEmpty() ||
            activeTrustStore.isRevoked(extension.pkg) ||
            ExtensionTrustStore.normalizeFingerprint(extension.signingKey).isNotBlank()
        )
    return overrideStatus ?: if (storeHasOpinion) (storeStatus ?: extension.trustStatus) else extension.trustStatus
}

private fun resolveTrustAction(
    extension: InstalledExtension,
    activeTrustStore: ExtensionTrustStore?,
    fingerprints: List<String>,
    onTrust: (() -> Unit)?,
    onTrustExtension: ((InstalledExtension) -> Unit)?,
    onTrustPackage: ((String) -> Unit)?,
): () -> Unit = onTrust
    ?: onTrustExtension?.let { callback -> { callback(extension) } }
    ?: onTrustPackage?.let { callback -> { callback(extension.pkg) } }
    ?: {
        val store = activeTrustStore
        val trustFingerprints = fingerprints.ifEmpty {
            listOfNotNull(extension.signingKey.takeIf { it.isNotBlank() })
        }
        if (trustFingerprints.isEmpty()) {
            store?.trustUnsigned(extension.pkg)
        } else {
            store?.trust(extension.pkg, trustFingerprints)
        }
    }

private fun resolveRevokeAction(
    extension: InstalledExtension,
    activeTrustStore: ExtensionTrustStore?,
    onRevoke: (() -> Unit)?,
    onRevokeExtension: ((InstalledExtension) -> Unit)?,
    onRevokePackage: ((String) -> Unit)?,
): () -> Unit = onRevoke
    ?: onRevokeExtension?.let { callback -> { callback(extension) } }
    ?: onRevokePackage?.let { callback -> { callback(extension.pkg) } }
    ?: {
        (activeTrustStore)?.revoke(extension.pkg)
    }

internal fun extensionRepoUrl(repoUrl: String): String? {
    val raw = repoUrl.trim()
    if (raw.isBlank()) return null
    val regex = """https://raw\.githubusercontent\.com/(.+?)/(.+?)/.+""".toRegex()
    return regex.find(raw)?.let {
        val (user, repo) = it.destructured
        "https://github.com/$user/$repo"
    } ?: raw
}

internal fun openExtensionRepository(url: String) {
    runCatching {
        if (java.awt.Desktop.isDesktopSupported()) {
            java.awt.Desktop.getDesktop().browse(java.net.URI(url))
        }
    }.onFailure { error ->
        DesktopLogger.warn("ExtensionDetails", "Failed to open repository $url", error)
    }
}

internal fun copyExtensionDebugInfo(extension: InstalledExtension, trustStatus: ExtensionTrustStatus) {
    val info = buildString {
        appendLine(
            "Extension name: ${extension.manifest.name} " +
                "(lang: ${extension.manifest.lang}; package: ${extension.pkg})",
        )
        appendLine(
            "Extension version: ${extension.manifest.version} " +
                "(lib: ${extension.manifest.libVersion}; version code: ${extension.manifest.versionCode})",
        )
        appendLine("NSFW: ${extension.manifest.isNsfw}")
        appendLine("Enabled: ${extension.isEnabled}")
        appendLine("Trust status: ${trustStatus.label}")
        if (extension.repoUrl.isNotBlank()) {
            appendLine("Repository: ${extension.repoUrl}")
        }
    }
    runCatching {
        val selection = StringSelection(info)
        Toolkit.getDefaultToolkit().systemClipboard.setContents(selection, selection)
    }.onFailure { error ->
        DesktopLogger.warn("ExtensionDetails", "Failed to copy debug info", error)
    }
}

internal fun loadExtensionIcon(path: String?): ImageBitmap? {
    if (path.isNullOrBlank()) return null
    return runCatching {
        val file = File(path)
        if (!file.isFile) return null
        org.jetbrains.skia.Image.makeFromEncoded(file.readBytes()).use { image -> image.toComposeImageBitmap() }
    }.onFailure { error ->
        DesktopLogger.warn("ExtensionDetails", "Failed to load extension icon at $path", error)
    }.getOrNull()
}
