package mihon.desktop.i18n

import mihon.desktop.download.DownloadFailureReason

internal val DownloadFailureReason.title: UiText
    get() = when (this) {
        DownloadFailureReason.TLS -> UiText.FailureTls
        DownloadFailureReason.TIMEOUT -> UiText.FailureTimeout
        DownloadFailureReason.OFFLINE -> UiText.FailureOffline
        DownloadFailureReason.PROXY -> UiText.FailureProxy
        DownloadFailureReason.CONNECTION -> UiText.FailureConnection
        DownloadFailureReason.RATE_LIMITED -> UiText.FailureRateLimit
        DownloadFailureReason.AUTHENTICATION -> UiText.FailureAuthentication
        DownloadFailureReason.WEB_VERIFICATION -> UiText.FailureVerification
        DownloadFailureReason.SITE_BLOCKED -> UiText.FailureBlocked
        DownloadFailureReason.DOMAIN_DENIED -> UiText.FailureDomain
        DownloadFailureReason.SOURCE_UNAVAILABLE -> UiText.FailureSource
        DownloadFailureReason.EXTENSION_MISSING -> UiText.FailureExtensionMissing
        DownloadFailureReason.EMPTY_CHAPTER -> UiText.FailureEmpty
        DownloadFailureReason.INVALID_IMAGE -> UiText.FailureImage
        DownloadFailureReason.STORAGE_FULL -> UiText.FailureSpace
        DownloadFailureReason.STORAGE_ACCESS -> UiText.FailureStorage
        DownloadFailureReason.MISSING_FILES -> UiText.FailureMissing
        DownloadFailureReason.REGISTRATION -> UiText.FailureRegistration
        DownloadFailureReason.HTTP -> UiText.FailureHttp
        DownloadFailureReason.UNKNOWN -> UiText.FailureUnknown
    }

internal val DownloadFailureReason.hint: UiText
    get() = when (this) {
        DownloadFailureReason.TLS, DownloadFailureReason.TIMEOUT, DownloadFailureReason.OFFLINE,
        DownloadFailureReason.PROXY, DownloadFailureReason.CONNECTION,
        -> UiText.HintNetwork
        DownloadFailureReason.RATE_LIMITED -> UiText.HintRateLimit
        DownloadFailureReason.AUTHENTICATION -> UiText.HintAuthentication
        DownloadFailureReason.WEB_VERIFICATION -> UiText.HintVerification
        DownloadFailureReason.SITE_BLOCKED -> UiText.HintBlocked
        DownloadFailureReason.DOMAIN_DENIED -> UiText.HintDomain
        DownloadFailureReason.SOURCE_UNAVAILABLE -> UiText.HintSource
        DownloadFailureReason.EXTENSION_MISSING -> UiText.HintExtensionMissing
        DownloadFailureReason.EMPTY_CHAPTER, DownloadFailureReason.INVALID_IMAGE -> UiText.HintSourcePage
        DownloadFailureReason.STORAGE_FULL -> UiText.HintSpace
        DownloadFailureReason.STORAGE_ACCESS -> UiText.HintStorage
        DownloadFailureReason.MISSING_FILES -> UiText.HintMissing
        DownloadFailureReason.HTTP, DownloadFailureReason.REGISTRATION, DownloadFailureReason.UNKNOWN ->
            UiText.HintRetry
    }
