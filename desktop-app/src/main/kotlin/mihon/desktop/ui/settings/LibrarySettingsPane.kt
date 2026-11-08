package mihon.desktop.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mihon.desktop.i18n.LocalStrings
import mihon.desktop.i18n.UiText
import mihon.desktop.i18n.locale
import mihon.desktop.i18n.text
import mihon.desktop.preferences.DesktopPreferenceStore
import mihon.desktop.preferences.DesktopPreferences

@Composable
internal fun LibrarySettingsPane(
    preferenceStore: DesktopPreferenceStore,
    presenter: SettingsPresenter,
    updateScheduler: mihon.desktop.library.update.LibraryUpdateScheduler?,
    onPreferencesChanged: ((DesktopPreferences) -> Unit)?,
) {
    val strings = LocalStrings.current
    val scope = rememberCoroutineScope()
    val preferences = presenter.preferences.value
    var updateInProgress by remember { mutableStateOf(false) }
    var updateResultMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            strings.libraryTitle,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        PreferredScanlatorsCard(preferenceStore)

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(strings.libraryUpdateTitle, fontWeight = FontWeight.Bold)
                    Text(
                        strings.libraryUpdating,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // Frequency options
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(strings.libraryUpdateInterval, fontWeight = FontWeight.Medium)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf(
                            0 to strings.libraryUpdateIntervalManual,
                            6 to strings.libraryUpdateInterval6Hours,
                            12 to strings.libraryUpdateInterval12Hours,
                            24 to strings.libraryUpdateIntervalDaily,
                            48 to strings.libraryUpdateInterval2Days,
                            168 to strings.libraryUpdateIntervalWeekly,
                        ).forEach { (hours, label) ->
                            val isSelected = preferences.libraryUpdateIntervalHours == hours
                            if (isSelected) {
                                Button(
                                    onClick = {},
                                    modifier = Modifier.testTag("update-interval-$hours"),
                                ) {
                                    Text(label)
                                }
                            } else {
                                OutlinedButton(
                                    onClick = {
                                        val updated = presenter.updatePreferences {
                                            it.copy(libraryUpdateIntervalHours = hours)
                                        }

                                        onPreferencesChanged?.invoke(updated)
                                    },
                                    modifier = Modifier.testTag("update-interval-$hours"),
                                ) {
                                    Text(label)
                                }
                            }
                        }
                    }
                }

                HorizontalDivider()

                // Filter switches
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(strings.libraryUpdateSkipCompleted)
                    Switch(
                        checked = preferences.libraryUpdateSkipCompleted,
                        onCheckedChange = { checked ->
                            val updated = presenter.updatePreferences {
                                it.copy(libraryUpdateSkipCompleted = checked)
                            }

                            onPreferencesChanged?.invoke(updated)
                        },
                        modifier = Modifier.testTag("skip-completed-switch"),
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(strings.libraryUpdateSkipUnread)
                    Switch(
                        checked = preferences.libraryUpdateSkipUnread,
                        onCheckedChange = { checked ->
                            val updated = presenter.updatePreferences {
                                it.copy(libraryUpdateSkipUnread = checked)
                            }

                            onPreferencesChanged?.invoke(updated)
                        },
                        modifier = Modifier.testTag("skip-unread-switch"),
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(strings.text(UiText.SkipNotStarted))
                    Switch(
                        checked = preferences.libraryUpdateSkipStarted,
                        onCheckedChange = { checked ->
                            val updated = presenter.updatePreferences {
                                it.copy(libraryUpdateSkipStarted = checked)
                            }

                            onPreferencesChanged?.invoke(updated)
                        },
                        modifier = Modifier.testTag("skip-started-switch"),
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(strings.libraryUpdateSkipOutsideReleasePeriod)
                    Switch(
                        checked = preferences.libraryUpdateSkipOutsideReleasePeriod,
                        onCheckedChange = { checked ->
                            val updated = presenter.updatePreferences {
                                it.copy(libraryUpdateSkipOutsideReleasePeriod = checked)
                            }
                            onPreferencesChanged?.invoke(updated)
                        },
                        modifier = Modifier.testTag("skip-outside-release-period-switch"),
                    )
                }

                CategoryIdListField(
                    label = strings.text(UiText.IncludeCategoryIds),
                    selected = preferences.libraryUpdateCategories,
                    onSelectedChange = { categories ->
                        val updated = presenter.updatePreferences {
                            it.copy(libraryUpdateCategories = categories)
                        }
                        onPreferencesChanged?.invoke(updated)
                    },
                    modifier = Modifier.fillMaxWidth().testTag("update-include-categories"),
                )

                CategoryIdListField(
                    label = strings.text(UiText.ExcludeCategoryIds),
                    selected = preferences.libraryUpdateCategoriesExclude,
                    onSelectedChange = { categories ->
                        val updated = presenter.updatePreferences {
                            it.copy(libraryUpdateCategoriesExclude = categories)
                        }
                        onPreferencesChanged?.invoke(updated)
                    },
                    modifier = Modifier.fillMaxWidth().testTag("update-exclude-categories"),
                )

                HorizontalDivider()

                // Auto download new chapters
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(strings.libraryAutoDownloadNew)
                    Switch(
                        checked = preferences.autoDownloadNewChapters,
                        onCheckedChange = { checked ->
                            val updated = presenter.updatePreferences {
                                it.copy(autoDownloadNewChapters = checked)
                            }

                            onPreferencesChanged?.invoke(updated)
                        },
                        modifier = Modifier.testTag("auto-download-new-switch"),
                    )
                }

                // Only run scheduled library updates while on AC power (desktops count as AC)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(strings.libraryUpdateOnlyOnAcPower)
                    Switch(
                        checked = preferences.libraryUpdateOnlyOnAcPower,
                        onCheckedChange = { checked ->
                            val updated = presenter.updatePreferences {
                                it.copy(libraryUpdateOnlyOnAcPower = checked)
                            }

                            onPreferencesChanged?.invoke(updated)
                        },
                        modifier = Modifier.testTag("library-update-only-on-ac-power"),
                    )
                }

                // Desktop notifications
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(strings.notificationsDesktopEnabled)
                    Switch(
                        checked = preferences.desktopNotificationsEnabled,
                        onCheckedChange = { checked ->
                            val updated = presenter.updatePreferences {
                                it.copy(desktopNotificationsEnabled = checked)
                            }

                            onPreferencesChanged?.invoke(updated)
                        },
                        modifier = Modifier.testTag("desktop-notifications-switch"),
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(strings.text(UiText.HideNotificationContent))
                    Switch(
                        checked = preferences.desktopNotificationsHideContent,
                        onCheckedChange = { checked ->
                            val updated = presenter.updatePreferences {
                                it.copy(desktopNotificationsHideContent = checked)
                            }

                            onPreferencesChanged?.invoke(updated)
                        },
                        modifier = Modifier.testTag("desktop-notifications-hide-content-switch"),
                    )
                }

                HorizontalDivider()

                // Last update & trigger button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val lastUpdateStr = if (preferences.lastLibraryUpdateEpochMillis > 0) {
                        val timestampFormatter = remember(strings) {
                            java.time.format.DateTimeFormatter
                                .ofLocalizedDateTime(
                                    java.time.format.FormatStyle.MEDIUM,
                                    java.time.format.FormatStyle.SHORT,
                                )
                                .withLocale(strings.locale)
                        }
                        java.time.Instant.ofEpochMilli(preferences.lastLibraryUpdateEpochMillis)
                            .atZone(java.time.ZoneId.systemDefault())
                            .format(timestampFormatter)
                    } else {
                        strings.backupNever
                    }
                    Text(
                        text = "${strings.libraryLastUpdate} $lastUpdateStr",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Button(
                        onClick = {
                            if (updateScheduler != null && !updateInProgress) {
                                scope.launch {
                                    updateInProgress = true
                                    updateResultMessage = null
                                    try {
                                        val report = withContext(Dispatchers.IO) {
                                            updateScheduler.triggerUpdateNow()
                                        }
                                        presenter.reloadPreferences()
                                        updateResultMessage = if (report != null) {
                                            strings.settingsLibraryUpdateResult(
                                                report.totalMangaChecked,
                                                report.newChaptersTotal,
                                            )
                                        } else {
                                            strings.settingsLibraryUpdateCompleted
                                        }
                                    } catch (cancelled: CancellationException) {
                                        throw cancelled
                                    } catch (e: Exception) {
                                        updateResultMessage = strings.settingsLibraryUpdateFailed(e.message ?: "")
                                    } finally {
                                        updateInProgress = false
                                    }
                                }
                            }
                        },
                        enabled = !updateInProgress,
                        modifier = Modifier.testTag("update-library-now-btn"),
                    ) {
                        if (updateInProgress) {
                            CircularProgressIndicator(
                                modifier = Modifier.height(16.dp).width(16.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp,
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(strings.libraryUpdateNow)
                    }
                }

                updateResultMessage?.let { msg ->
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}
