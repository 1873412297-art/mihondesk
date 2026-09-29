package mihon.desktop.ui.browse

import mihon.desktop.extension.ExtensionDownloadException
import mihon.desktop.extension.ExtensionSignatureException
import mihon.desktop.extension.ExtensionSignatureMismatchException
import mihon.desktop.extension.ExtensionStoreUnavailableException
import mihon.desktop.extension.ExtensionTrustRequiredException
import mihon.extension.ipc.findNetworkFailure
import java.io.IOException

/** Why an extension install or update stopped, in the terms the user has to act on. */
enum class ExtensionInstallFailure {
    /** The package could not be downloaded (network, proxy or HTTP failure). */
    DownloadFailed,

    /** The package was rejected by the signature/trust policy. */
    SignatureRejected,

    /** No configured store offers the requested package/version anymore. */
    StoreUnavailable,

    /** Anything else: malformed packages, filesystem problems, unexpected errors. */
    Unknown,
}

/** Maps an install failure to the outcome to report. */
internal fun classifyInstallFailure(failure: Throwable): ExtensionInstallFailure {
    val chain = generateSequence(failure) { it.cause }.take(16).toList()
    return when {
        chain.any {
            it is ExtensionTrustRequiredException ||
                it is ExtensionSignatureMismatchException ||
                it is ExtensionSignatureException
        } -> ExtensionInstallFailure.SignatureRejected
        chain.any { it is ExtensionStoreUnavailableException } -> ExtensionInstallFailure.StoreUnavailable
        chain.any { it is ExtensionDownloadException || it is IOException } -> ExtensionInstallFailure.DownloadFailed
        chain.any { it.findNetworkFailure() != null } -> ExtensionInstallFailure.DownloadFailed
        else -> ExtensionInstallFailure.Unknown
    }
}
