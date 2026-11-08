package mihon.desktop.i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import java.util.Locale

enum class AppLanguage(val code: String, val displayName: String) {
    System("system", "Follow System"),
    SimplifiedChinese("zh-CN", "简体中文"),
    TraditionalChinese("zh-TW", "繁體中文"),
    English("en", "English"),
    ;

    companion object {
        fun fromCode(code: String?): AppLanguage =
            entries.firstOrNull { it.code.equals(code, ignoreCase = true) || it.name.equals(code, ignoreCase = true) }
                ?: System
    }
}

interface DesktopStrings : CoreStrings, SettingsStrings, AdvancedStrings {
    companion object {
        fun resolve(language: AppLanguage, defaultLocale: Locale = Locale.getDefault()): DesktopStrings {
            return when (language) {
                AppLanguage.English -> EnglishStrings
                AppLanguage.SimplifiedChinese -> SimplifiedChineseStrings
                AppLanguage.TraditionalChinese -> TraditionalChineseStrings
                AppLanguage.System -> {
                    val lang = defaultLocale.language.lowercase(Locale.ROOT)
                    if (lang == "zh") {
                        val country = defaultLocale.country.uppercase(Locale.ROOT)
                        val script = defaultLocale.script.lowercase(Locale.ROOT)
                        if (country in setOf("TW", "HK", "MO") || script == "hant") {
                            TraditionalChineseStrings
                        } else {
                            SimplifiedChineseStrings
                        }
                    } else {
                        EnglishStrings
                    }
                }
            }
        }
    }
}

object EnglishStrings :
    DesktopStrings,
    CoreStrings by EnglishCoreStrings,
    SettingsStrings by EnglishSettingsStrings(EnglishCoreStrings),
    AdvancedStrings by EnglishAdvancedStrings(EnglishCoreStrings)

object SimplifiedChineseStrings :
    DesktopStrings,
    CoreStrings by SimplifiedChineseCoreStrings,
    SettingsStrings by SimplifiedChineseSettingsStrings(SimplifiedChineseCoreStrings),
    AdvancedStrings by SimplifiedChineseAdvancedStrings(SimplifiedChineseCoreStrings)

object TraditionalChineseStrings :
    DesktopStrings,
    CoreStrings by TraditionalChineseCoreStrings,
    SettingsStrings by TraditionalChineseSettingsStrings(TraditionalChineseCoreStrings),
    AdvancedStrings by TraditionalChineseAdvancedStrings(TraditionalChineseCoreStrings)

val LocalStrings = staticCompositionLocalOf<DesktopStrings> { EnglishStrings }

@Composable
fun ProvideDesktopStrings(
    language: AppLanguage,
    content: @Composable () -> Unit,
) {
    val strings = DesktopStrings.resolve(language)
    CompositionLocalProvider(LocalStrings provides strings) {
        content()
    }
}
