package mihon.desktop.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CollectionsBookmark
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import mihon.desktop.category.SYSTEM_ALL_CATEGORY
import mihon.desktop.i18n.LocalStrings
import mihon.desktop.i18n.UiText
import mihon.desktop.i18n.text
import mihon.desktop.ui.onboarding.StartAction
import mihon.desktop.ui.onboarding.StartActionCards

@Composable
internal fun EmptyState(
    state: LibraryUiState,
    onClearFilters: () -> Unit,
    onImportBackup: () -> Unit,
    onImportLocal: () -> Unit,
    onPairPhoneSync: (() -> Unit)?,
    modifier: Modifier,
) {
    val strings = LocalStrings.current
    val query = state.query
    val filtered =
        query.isNotBlank() || state.filterState.hasActiveFilters || state.selectedCategoryId != SYSTEM_ALL_CATEGORY.id
    Box(modifier, contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(32.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                modifier = Modifier.size(80.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (query.isBlank()) Icons.Rounded.CollectionsBookmark else Icons.Rounded.Search,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            if (!filtered) {
                Text(
                    strings.libraryEmptyTitle,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    strings.libraryEmptySubtitle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(modifier = Modifier.height(4.dp))
                // First-run bookshelf: offer the same three entry points as onboarding, wired to the
                // existing import / restore flows and to the sync pairing settings.
                StartActionCards(
                    onAction = { action ->
                        when (action) {
                            StartAction.ImportLocal -> onImportLocal()
                            StartAction.RestoreBackup -> onImportBackup()
                            StartAction.PairPhone -> onPairPhoneSync?.invoke()
                        }
                    },
                    testTagPrefix = "library-empty",
                    actions = StartAction.entries.filter { it != StartAction.PairPhone || onPairPhoneSync != null },
                )
            } else {
                Text(
                    if (query.isNotBlank()) {
                        strings.libraryNoMatchTitle(
                            query,
                        )
                    } else {
                        strings.text(UiText.LibraryFilteredEmpty)
                    },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    if (query.isNotBlank()) {
                        strings.libraryNoMatchSubtitle
                    } else {
                        strings.text(
                            UiText.LibraryFilteredHint,
                        )
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
                TextButton(onClick = onClearFilters, modifier = Modifier.testTag("library-clear-filters")) {
                    Text(strings.text(UiText.ClearFilters))
                }
            }
        }
    }
}

@Composable
internal fun ErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier,
) {
    val strings = LocalStrings.current
    Box(modifier, contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = message,
                modifier = Modifier.testTag("library-error"),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyLarge,
            )
            FilledTonalButton(
                onClick = onRetry,
                modifier = Modifier.testTag("library-retry"),
            ) {
                Text(strings.libraryRetry)
            }
        }
    }
}
