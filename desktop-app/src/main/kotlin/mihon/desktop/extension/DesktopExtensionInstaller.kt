package mihon.desktop.extension

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import mihon.desktop.preferences.DesktopPreferenceStore
import mihon.extension.model.ExtensionManifest
import mihon.extension.network.withTlsFallback
import mihon.extension.validator.ExtensionValidationException
import okhttp3.OkHttpClient
import java.io.File

/** Thrown when the extension package itself could not be downloaded (network or HTTP failure). */
class ExtensionDownloadException(message: String, cause: Throwable? = null) :
    ExtensionValidationException(message, cause)

class DesktopExtensionInstaller(
    val installRoot: File,
    val preferenceStore: DesktopPreferenceStore,
    policyProvider: () -> DesktopNetworkPolicy = { DesktopNetworkPolicy() },
    internal val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .callTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .proxySelector(DesktopPolicyProxySelector(policyProvider))
        .withTlsFallback(),
    signatureVerifier: ExtensionVerifier = ExtensionSignatureVerifier(),
    trustStore: ExtensionTrustStore = ExtensionTrustStore(preferenceStore),
    /** Alias constructor parameter for callers/tests that name the fake verifier `verifier`. */
    verifier: ExtensionVerifier? = null,
    /** Signing keys trusted without a prompt (official keiyoushi by default, like Mihon). */
    internal val officialSigningKeys: Set<String> = OFFICIAL_SIGNING_KEYS,
) {
    private val lifecycleMutex = Mutex()

    internal suspend fun <T> withLifecycleLock(block: suspend () -> T): T = lifecycleMutex.withLock { block() }

    val signatureVerifier: ExtensionVerifier = verifier ?: signatureVerifier
    val verifier: ExtensionVerifier get() = signatureVerifier
    val trustStore: ExtensionTrustStore = trustStore
    val extensionTrustStore: ExtensionTrustStore get() = trustStore

    companion object {
        // SHA-256 signing certificate fingerprint of the official keiyoushi extensions
        // (keiyoushi repo.json meta.signingKeyFingerprint, matches their published APKs).
        val OFFICIAL_SIGNING_KEYS: Set<String> = setOf(
            "9add655a78e96c4ec7a53ef89dccb557cb5d767489fac5e785d671a5a75d4da2",
        )

        const val PREF_KEY_PENDING_CLEANUP = "extension.pending_cleanup"
        const val PREF_KEY_INSTALLED_EXTENSIONS = "extension.installed_list"
        const val PREF_KEY_INSTALLED_EXTENSIONS_CORRUPT = "extension.installed_list.corrupt"
        const val PREF_KEY_TRUSTED_EXTENSIONS = ExtensionTrustStore.PREF_KEY_TRUSTED_EXTENSIONS
        const val PREF_KEY_REVOKED_EXTENSIONS = ExtensionTrustStore.PREF_KEY_REVOKED_EXTENSIONS
    }

    internal val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        prettyPrint = true
    }

    @Volatile
    private var cachedInstalledExtensions: List<InstalledExtension>? = null

    init {
        if (!installRoot.exists()) {
            installRoot.mkdirs()
        }
        this.retryPendingCleanupInternal()
    }

    @Synchronized
    fun getInstalledExtensions(): List<InstalledExtension> {
        cachedInstalledExtensions?.let { return it }
        val raw = preferenceStore.property(PREF_KEY_INSTALLED_EXTENSIONS) ?: return emptyList()
        val decoded = decodeInstalledExtensions(raw, json) { corrupt ->
            preferenceStore.update { setProperty(PREF_KEY_INSTALLED_EXTENSIONS_CORRUPT, corrupt) }
        }
        cachedInstalledExtensions = decoded
        return decoded
    }

    @Synchronized
    internal fun saveInstalledExtensions(list: List<InstalledExtension>) {
        val raw = json.encodeToString(list)
        preferenceStore.update {
            setProperty(PREF_KEY_INSTALLED_EXTENSIONS, raw)
        }
        cachedInstalledExtensions = list
    }

    @Synchronized
    fun reconcileInstalledExtensions(): List<InstalledExtension> {
        cachedInstalledExtensions = null
        val current = getInstalledExtensions()
        val (valid, missing) = current.partition { File(it.packageFile).exists() }
        if (missing.isNotEmpty()) {
            missing.forEach { ext ->
                System.err.println(
                    "Reconcile: Extension package file missing for ${ext.pkg} at ${ext.packageFile}, removing from installed list",
                )
            }
            saveInstalledExtensions(valid)
        } else {
            cachedInstalledExtensions = valid
        }
        return valid
    }

    /**
     * One-time repair for extensions installed before icons were extracted: takes icon.png from the
     * installed package when present, otherwise asks [fetchRemote] (repo icon). Returns how many were fixed.
     */
    suspend fun backfillMissingIcons(fetchRemote: suspend (InstalledExtension) -> ByteArray?): Int =
        backfillMissingIconsInternal(fetchRemote)

    suspend fun downloadAndInstall(
        downloadUrl: String,
        expectedSha256: String?,
        repoUrl: String = "",
        storeItem: ExtensionStoreItem? = null,
        trustOnInstall: Boolean = false,
        allowUntrusted: Boolean = false,
        explicitTrust: Boolean = false,
        trustUnknown: Boolean = false,
        onDownloadComplete: () -> Unit = {},
    ): InstalledExtension = downloadAndInstallInternal(
        downloadUrl = downloadUrl,
        expectedSha256 = expectedSha256,
        repoUrl = repoUrl,
        storeItem = storeItem,
        trustOnInstall = trustOnInstall,
        allowUntrusted = allowUntrusted,
        explicitTrust = explicitTrust,
        trustUnknown = trustUnknown,
        onDownloadComplete = onDownloadComplete,
    )

    suspend fun installFromLocalFile(
        file: File,
        expectedSha256: String? = null,
        repoUrl: String = "",
        storeItem: ExtensionStoreItem? = null,
        trustOnInstall: Boolean = false,
        allowUntrusted: Boolean = false,
        explicitTrust: Boolean = false,
        trustUnknown: Boolean = false,
    ): InstalledExtension = installFromLocalFileInternal(
        file = file,
        expectedSha256 = expectedSha256,
        repoUrl = repoUrl,
        storeItem = storeItem,
        trustOnInstall = trustOnInstall,
        allowUntrusted = allowUntrusted,
        explicitTrust = explicitTrust,
        trustUnknown = trustUnknown,
    )

    /** Alias for [installFromLocalFile]. */
    suspend fun installFromFile(
        file: File,
        expectedSha256: String? = null,
        repoUrl: String = "",
        storeItem: ExtensionStoreItem? = null,
        trustOnInstall: Boolean = false,
        allowUntrusted: Boolean = false,
        explicitTrust: Boolean = false,
        trustUnknown: Boolean = false,
    ): InstalledExtension = installFromLocalFile(
        file,
        expectedSha256,
        repoUrl,
        storeItem,
        trustOnInstall,
        allowUntrusted,
        explicitTrust,
        trustUnknown,
    )

    suspend fun uninstall(pkg: String): Boolean = uninstallInternal(pkg)

    suspend fun setExtensionEnabled(pkg: String, enabled: Boolean): Boolean =
        setExtensionEnabledInternal(pkg, enabled)

    fun getTrustedFingerprints(pkg: String): Set<String> = trustStore.getTrustedFingerprints(pkg)

    fun isExtensionTrusted(extension: InstalledExtension): Boolean =
        getExtensionTrustStatus(extension) == ExtensionTrustStatus.TRUSTED

    fun isExtensionTrusted(
        pkg: String,
        fingerprints: Collection<String> = emptyList(),
        signingKey: String? = null,
    ): Boolean = getTrustStatus(pkg, fingerprints, signingKey) == ExtensionTrustStatus.TRUSTED

    fun isExtensionTrusted(pkg: String, fingerprint: String?): Boolean =
        getTrustStatus(pkg, fingerprint) == ExtensionTrustStatus.TRUSTED

    fun revokeAllExtensions() = trustStore.revokeAll()

    /** Trusts all supplied signer fingerprints for [pkg]. */
    fun trustExtension(pkg: String, fingerprint: String?) = trustStore.trust(pkg, fingerprint)

    fun trustExtension(pkg: String, fingerprints: Collection<String>) = trustStore.trust(pkg, fingerprints)

    fun trust(pkg: String, fingerprint: String?) = trustExtension(pkg, fingerprint)

    fun trustUnsignedExtension(pkg: String) = trustStore.trustUnsigned(pkg)

    /** Revokes explicit user trust (and marks the package revoked) for [pkg]. */
    fun revokeExtension(pkg: String) = trustStore.revoke(pkg)

    fun revoke(pkg: String) = revokeExtension(pkg)

    fun getExtensionTrustStatus(extension: InstalledExtension): ExtensionTrustStatus =
        trustStore.statusFor(extension)

    fun getTrustStatus(extension: InstalledExtension): ExtensionTrustStatus = getExtensionTrustStatus(extension)

    fun getTrustStatus(pkg: String, fingerprint: String?): ExtensionTrustStatus =
        getTrustStatus(pkg, listOfNotNull(fingerprint?.takeIf { it.isNotBlank() }))

    fun getTrustStatus(pkg: String, fingerprint: String?, signingKey: String?): ExtensionTrustStatus =
        getTrustStatus(pkg, listOfNotNull(fingerprint?.takeIf { it.isNotBlank() }), signingKey)

    fun getTrustStatus(
        pkg: String,
        fingerprints: Collection<String> = emptyList(),
        signingKey: String? = null,
        signatureValid: Boolean = true,
    ): ExtensionTrustStatus = trustStore.status(
        pkg = pkg,
        fingerprints = fingerprints,
        trustedSigningKeys = listOfNotNull(signingKey?.takeIf { it.isNotBlank() }),
        signatureValid = signatureValid,
    )
}
