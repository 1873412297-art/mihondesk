package mihon.desktop.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import mihon.desktop.i18n.LocalStrings
import mihon.desktop.i18n.UiText
import mihon.desktop.i18n.text
import mihon.desktop.preferences.DesktopPreferenceStore
import mihon.desktop.preferences.DesktopPreferences
import java.nio.file.Path

@Composable
internal fun DownloadsSettingsPane(
    preferenceStore: DesktopPreferenceStore,
    presenter: SettingsPresenter,
    onPreferencesChanged: ((DesktopPreferences) -> Unit)?,
    downloadsDir: Path?,
    categories: List<mihon.desktop.category.DesktopCategory> = emptyList(),
) {
    val strings = LocalStrings.current
    val preferences = presenter.preferences.value

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            strings.settingsSectionDownloads,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )

        DownloadStoragePathCard(presenter, preferences.downloadStoragePath, downloadsDir) { updated ->
            onPreferencesChanged?.invoke(updated)
        }
        // Parallel downloads card
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(strings.settingsParallelDownloads, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1, 2, 3, 5).forEach { count ->
                        val isSelected = preferences.downloadParallelCount == count
                        if (isSelected) {
                            Button(
                                onClick = {},
                                modifier = Modifier.testTag("parallel-downloads-$count"),
                            ) {
                                Text("$count")
                            }
                        } else {
                            OutlinedButton(
                                onClick = {
                                    val updated = presenter.updatePreferences {
                                        it.copy(downloadParallelCount = count)
                                    }

                                    onPreferencesChanged?.invoke(updated)
                                },
                                modifier = Modifier.testTag("parallel-downloads-$count"),
                            ) {
                                Text("$count")
                            }
                        }
                    }
                }

                Text(strings.text(UiText.ParallelPages), fontWeight = FontWeight.Medium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1, 2, 3, 5).forEach { count ->
                        val isSelected = preferences.downloadPageParallelCount == count
                        if (isSelected) {
                            Button(
                                onClick = {},
                                modifier = Modifier.testTag("parallel-pages-$count"),
                            ) {
                                Text("$count")
                            }
                        } else {
                            OutlinedButton(
                                onClick = {
                                    val updated = presenter.updatePreferences {
                                        it.copy(downloadPageParallelCount = count)
                                    }

                                    onPreferencesChanged?.invoke(updated)
                                },
                                modifier = Modifier.testTag("parallel-pages-$count"),
                            ) {
                                Text("$count")
                            }
                        }
                    }
                }
            }
        }

        // Download ahead card
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(strings.settingsDownloadAheadTitle, fontWeight = FontWeight.Bold)
                Text(
                    strings.settingsDownloadAheadDesc,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        0 to strings.settingsDownloadAheadDisabled,
                        1 to strings.settingsDownloadAheadChapters(1),
                        2 to strings.settingsDownloadAheadChapters(2),
                        3 to strings.settingsDownloadAheadChapters(3),
                        5 to strings.settingsDownloadAheadChapters(5),
                    ).forEach { (count, label) ->
                        val isSelected = preferences.downloadAhead == count
                        if (isSelected) {
                            Button(
                                onClick = {},
                                modifier = Modifier.testTag("download-ahead-$count"),
                            ) {
                                Text(label)
                            }
                        } else {
                            OutlinedButton(
                                onClick = {
                                    val updated = presenter.updatePreferences { it.copy(downloadAhead = count) }

                                    onPreferencesChanged?.invoke(updated)
                                },
                                modifier = Modifier.testTag("download-ahead-$count"),
                            ) {
                                Text(label)
                            }
                        }
                    }
                }
            }
        }

        // Auto-download filters use the same category membership as library updates.
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(strings.libraryAutoDownloadNew, fontWeight = FontWeight.Bold)
                Switch(
                    checked = preferences.autoDownloadNewChapters,
                    onCheckedChange = { checked ->
                        val updated = presenter.updatePreferences { it.copy(autoDownloadNewChapters = checked) }
                        onPreferencesChanged?.invoke(updated)
                    },
                    modifier = Modifier.testTag("download-auto-new-switch"),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(strings.downloadNewUnreadOnly, modifier = Modifier.weight(1f))
                    Switch(
                        checked = preferences.autoDownloadUnreadOnly,
                        onCheckedChange = { checked ->
                            val updated = presenter.updatePreferences {
                                it.copy(autoDownloadUnreadOnly = checked)
                            }
                            onPreferencesChanged?.invoke(updated)
                        },
                        enabled = preferences.autoDownloadNewChapters,
                        modifier = Modifier.testTag("download-new-unread-only-switch"),
                    )
                }
                Text(strings.downloadNewCategories, fontWeight = FontWeight.Medium)
                val selectableCategories = categories.filter { it.id > 0L }
                if (selectableCategories.isEmpty()) {
                    Text(strings.downloadNoCategories, style = MaterialTheme.typography.bodySmall)
                }
                selectableCategories.forEach { category ->
                    val selection = when (category.id) {
                        in preferences.autoDownloadCategories -> strings.downloadCategoryInclude
                        in preferences.autoDownloadCategoriesExclude -> strings.downloadCategoryExclude
                        else -> strings.downloadCategoryAny
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(category.name, modifier = Modifier.weight(1f))
                        OutlinedButton(
                            onClick = {
                                val updated = presenter.updatePreferences { current ->
                                    when (category.id) {
                                        in current.autoDownloadCategories -> current.copy(
                                            autoDownloadCategories = current.autoDownloadCategories - category.id,
                                            autoDownloadCategoriesExclude =
                                            current.autoDownloadCategoriesExclude + category.id,
                                        )
                                        in current.autoDownloadCategoriesExclude -> current.copy(
                                            autoDownloadCategoriesExclude =
                                            current.autoDownloadCategoriesExclude - category.id,
                                        )
                                        else -> current.copy(
                                            autoDownloadCategories = current.autoDownloadCategories + category.id,
                                        )
                                    }
                                }
                                onPreferencesChanged?.invoke(updated)
                            },
                            enabled = preferences.autoDownloadNewChapters,
                            modifier = Modifier.testTag("download-category-${category.id}"),
                        ) { Text(selection) }
                    }
                }
            }
        }

        // Delete read chapters card
        Card(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(strings.settingsDeleteReadChaptersTitle, fontWeight = FontWeight.Bold)
                    Text(
                        strings.settingsDeleteReadChaptersDesc,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = preferences.deleteDownloadedRead,
                    onCheckedChange = { checked ->
                        val updated = presenter.updatePreferences { it.copy(deleteDownloadedRead = checked) }

                        onPreferencesChanged?.invoke(updated)
                    },
                    modifier = Modifier.testTag("delete-downloaded-read-switch"),
                )
            }
        }

        // CBZ archive card
        Card(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(strings.settingsSaveChapterAsCbzTitle, fontWeight = FontWeight.Bold)
                    Text(
                        strings.settingsSaveChapterAsCbzDesc,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Checkbox(
                    checked = preferences.saveChapterAsCbz,
                    onCheckedChange = { checked ->
                        val updated = presenter.updatePreferences { it.copy(saveChapterAsCbz = checked) }

                        onPreferencesChanged?.invoke(updated)
                    },
                    modifier = Modifier.testTag("save-downloaded-chapter-as-cbz-checkbox"),
                )
            }
        }

        // Tall image split card
        Card(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(strings.settingsSplitTallImagesTitle, fontWeight = FontWeight.Bold)
                    Text(
                        strings.settingsSplitTallImagesDesc,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Checkbox(
                    checked = preferences.splitTallImages,
                    onCheckedChange = { checked ->
                        val updated = presenter.updatePreferences { it.copy(splitTallImages = checked) }

                        onPreferencesChanged?.invoke(updated)
                    },
                    modifier = Modifier.testTag("split-tall-images-checkbox"),
                )
            }
        }
    }
}
