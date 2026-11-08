package mihon.desktop.i18n

/** App-owned labels. Source metadata and diagnostic details are not translated here. */
open class UiText internal constructor() {
    companion object : UiTextCatalogE()
}

/** English label for [this] entry. */
val UiText.english: String get() = UI_TEXT_ENGLISH.getValue(this)

/** Simplified Chinese label for [this] entry. */
val UiText.simplified: String get() = UI_TEXT_SIMPLIFIED.getValue(this)

/** Traditional Chinese label for [this] entry. */
val UiText.traditional: String get() = UI_TEXT_TRADITIONAL.getValue(this)

private val placeholderPattern = Regex("\\{(\\d+)\\}")

fun DesktopStrings.text(key: UiText, vararg values: Any): String {
    val template = when (this) {
        SimplifiedChineseStrings -> key.simplified
        TraditionalChineseStrings -> key.traditional
        else -> key.english
    }
    return placeholderPattern.replace(template) { match ->
        values.getOrNull(match.groupValues[1].toInt())?.toString() ?: match.value
    }
}

val DesktopStrings.locale: java.util.Locale
    get() = when (this) {
        SimplifiedChineseStrings -> java.util.Locale.SIMPLIFIED_CHINESE
        TraditionalChineseStrings -> java.util.Locale.TRADITIONAL_CHINESE
        else -> java.util.Locale.ENGLISH
    }

fun DesktopStrings.chapterCountLabel(count: Long): String =
    if (this === EnglishStrings && count == 1L) "1 chapter" else text(UiText.ChapterCount, count)

fun DesktopStrings.trustStatusLabel(status: mihon.desktop.extension.ExtensionTrustStatus): String = text(
    when (status) {
        mihon.desktop.extension.ExtensionTrustStatus.TRUSTED -> UiText.Trusted
        mihon.desktop.extension.ExtensionTrustStatus.UNTRUSTED -> UiText.Untrusted
        mihon.desktop.extension.ExtensionTrustStatus.UNKNOWN -> UiText.UnknownTrust
        mihon.desktop.extension.ExtensionTrustStatus.INVALID -> UiText.InvalidTrust
    },
)
