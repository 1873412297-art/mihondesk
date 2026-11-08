package mihon.desktop.ui.settings

import mihon.desktop.i18n.DesktopStrings

internal data class SearchableSettingEntry(
    val id: String,
    val section: SettingsSection,
    val title: String,
    val subtitle: String? = null,
    val keywords: List<String> = emptyList(),
) {
    fun matches(query: String, strings: DesktopStrings): Boolean {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return false
        val localizedSection = section.localized(strings).lowercase()
        val sectionName = section.name.lowercase()
        val matchesTitle = title.lowercase().contains(q)
        val matchesSubtitle = subtitle?.lowercase()?.contains(q) == true
        val matchesSection = localizedSection.contains(q) || sectionName.contains(q)
        val matchesKeywords = keywords.any { it.lowercase().contains(q) }
        return matchesTitle || matchesSubtitle || matchesSection || matchesKeywords
    }
}

internal fun buildSearchableSettings(strings: DesktopStrings): List<SearchableSettingEntry> =
    searchableGeneralEntries(strings) + searchableReaderEntries(strings) + searchableMoreEntries(strings)
