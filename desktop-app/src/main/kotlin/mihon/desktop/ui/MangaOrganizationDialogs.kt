package mihon.desktop.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import mihon.desktop.category.DesktopCategory
import mihon.desktop.category.SYSTEM_ALL_CATEGORY
import mihon.desktop.i18n.DesktopStrings
import mihon.desktop.i18n.LocalStrings
import mihon.desktop.track.DesktopTrackerManager
import mihon.desktop.track.toDesktopTrackRecord
import mihon.desktop.track.toTrackingRecord
import mihon.desktop.ui.category.EditMangaCategoriesDialog
import mihon.desktop.ui.category.ManageCategoriesDialog
import mihon.desktop.ui.library.LibraryUiState
import mihon.desktop.ui.library.MangaDetailUiState
import mihon.desktop.ui.track.TrackingDialog

/** Category, tracking and "add to library first" dialogs of the manga details. */
@Composable
internal fun MangaOrganizationDialogs(
    ctx: AppShellContext,
    mangaDetailState: MangaDetailUiState,
    libraryState: LibraryUiState,
    categories: List<DesktopCategory>,
) {
    ManageCategoriesHost(ctx, libraryState, categories)
    EditCategoriesHost(ctx, mangaDetailState, categories)
    TrackingHost(ctx, mangaDetailState)
    ctx.session.pendingMangaOrganizationAction?.let {
        OrganizationConfirmation(ctx, it)
    }
}

@Composable
private fun EditCategoriesHost(
    ctx: AppShellContext,
    detail: MangaDetailUiState,
    categories: List<DesktopCategory>,
) {
    val manga = detail.manga
    val service = ctx.runtime.categoryService
    if (!ctx.session.isEditMangaCategoriesDialogOpen || manga == null || service == null) return
    EditMangaCategoriesDialog(
        allCategories = categories.sortedBy { it.order },
        assignedCategoryIds = manga.categories.map { it.id }.toSet(),
        onDismiss = { ctx.session.isEditMangaCategoriesDialogOpen = false },
        onSave = { service.setMangaCategories(manga.id, it) },
    )
}

@Composable
private fun TrackingHost(ctx: AppShellContext, detail: MangaDetailUiState) {
    val manga = detail.manga
    val manager = ctx.runtime.trackerManager
    if (!ctx.session.isTrackingDialogOpen || manga == null || manager == null) return
    val tracksFlow = remember(manga.id) { ctx.runtime.library.observeTracking(manga.id) }
    val tracksList by tracksFlow.collectAsState(initial = emptyList())
    TrackingDialog(
        mangaTitle = manga.title,
        trackers = manager.trackers,
        currentTracks = tracksList.map { it.toDesktopTrackRecord() },
        onDismiss = { ctx.session.isTrackingDialogOpen = false },
        onSaveTrack = { saveTrack(ctx, manga.id, manager, it) },
        onUnbindTrack = { ctx.runtime.library.deleteTracking(manga.id, it) },
        onSearchTrack = { tracker, query -> tracker.search(query) },
    )
}

private fun saveTrack(
    ctx: AppShellContext,
    mangaId: Long,
    manager: DesktopTrackerManager,
    track: mihon.desktop.track.DesktopTrackRecord,
) {
    ctx.presenterScope.launch {
        val tracker = manager.get(track.trackerId)
        val local = track.copy(mangaId = mangaId)
        val resolved = if (tracker != null && tracker.isLoggedIn) {
            try {
                tracker.updateRemote(local)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                mihon.desktop.logging.DesktopLogger.warn(
                    "MangaOrganizationDialogs",
                    "Remote tracking update failed; queuing local change",
                    error,
                )
                ctx.runtime.trackingQueue?.enqueue(local)
                local
            }
        } else {
            ctx.runtime.trackingQueue?.enqueue(local)
            local
        }
        // The insert upserts, preserving an existing manga/tracker binding's row id.
        ctx.runtime.library.insertTracking(resolved.toTrackingRecord())
    }
}

@Composable
private fun OrganizationConfirmation(ctx: AppShellContext, action: MangaOrganizationAction) {
    val strings = LocalStrings.current
    AlertDialog(
        onDismissRequest = { ctx.session.pendingMangaOrganizationAction = null },
        confirmButton = {
            TextButton(
                onClick = { confirmOrganization(ctx, action, strings) },
                modifier = Modifier.testTag("manga-organization-confirm"),
            ) { Text(strings.mangaDetailAddToLibrary) }
        },
        dismissButton = {
            TextButton(
                onClick = { ctx.session.pendingMangaOrganizationAction = null },
                modifier = Modifier.testTag("manga-organization-cancel"),
            ) { Text(strings.dialogCancel) }
        },
        title = { Text(strings.mangaDetailAddToLibrary) },
        text = { Text(strings.mangaDetailOrganizationRequiresLibrary) },
        modifier = Modifier.testTag("manga-organization-confirmation"),
    )
}

private fun confirmOrganization(ctx: AppShellContext, action: MangaOrganizationAction, strings: DesktopStrings) {
    ctx.session.pendingMangaOrganizationAction = null
    ctx.libraryPresenter.setDetailFavorite(true) { added ->
        if (added) {
            if (action == MangaOrganizationAction.Categories) {
                ctx.session.isEditMangaCategoriesDialogOpen = true
            } else {
                ctx.session.isTrackingDialogOpen = true
            }
        } else {
            ctx.presenterScope.launch {
                ctx.snackbarHostState.showSnackbar("Unable to add this manga to the library")
            }
        }
    }
}

@Composable
private fun ManageCategoriesHost(ctx: AppShellContext, library: LibraryUiState, categories: List<DesktopCategory>) {
    val session = ctx.session
    val service = ctx.runtime.categoryService
    if (!session.isManageCategoriesDialogOpen || service == null) return
    ManageCategoriesDialog(
        categories = categories.sortedBy { it.order },
        onDismiss = { session.isManageCategoriesDialogOpen = false },
        onCreateCategory = service::createCategory,
        onRenameCategory = service::renameCategory,
        onDeleteCategory = {
            if (library.selectedCategoryId == it) ctx.libraryPresenter.selectCategory(SYSTEM_ALL_CATEGORY.id)
            service.deleteCategory(it)
        },
        onMoveCategory = { category, index -> moveCategory(service, categories, category, index) },
    )
}

private fun moveCategory(
    service: mihon.desktop.category.DesktopCategoryService,
    categories: List<DesktopCategory>,
    category: DesktopCategory,
    index: Int,
) {
    val ordered = categories.sortedBy { it.order }.toMutableList()
    val current = ordered.indexOfFirst { it.id == category.id }
    if (current == -1 || index !in ordered.indices || current == index) return
    ordered.add(index, ordered.removeAt(current))
    ordered.forEachIndexed { position, item ->
        if (item.order != position.toLong()) service.reorderCategory(item.id, position.toLong())
    }
}
