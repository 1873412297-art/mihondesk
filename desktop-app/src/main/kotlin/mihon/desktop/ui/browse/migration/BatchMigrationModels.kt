package mihon.desktop.ui.browse.migration

import mihon.desktop.library.model.LibraryManga
import mihon.extension.model.SourceDescriptor

sealed interface BatchMigrationState {
    data object Idle : BatchMigrationState

    data class Running(
        val targetSource: SourceDescriptor,
        val currentIndex: Int,
        val totalCount: Int,
        val currentManga: LibraryManga,
        val successCount: Int,
        val failureCount: Int,
    ) : BatchMigrationState {
        val progress: Float
            get() = if (totalCount > 0) currentIndex.toFloat() / totalCount.toFloat() else 0f
    }

    data class Completed(
        val targetSource: SourceDescriptor,
        val totalCount: Int,
        val successCount: Int,
        val failures: List<BatchMigrationFailure>,
        val wasCancelled: Boolean,
    ) : BatchMigrationState {
        val isAllSuccess: Boolean get() = failures.isEmpty() && !wasCancelled
    }
}

data class BatchMigrationFailure(
    val manga: LibraryManga,
    val reason: BatchMigrationFailureReason,
    val detailMessage: String? = null,
)

enum class BatchMigrationFailureReason {
    NoMatchFound,
    AmbiguousMatches,
    MigrationError,
}
