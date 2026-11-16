package mihon.desktop.ui.library

import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent

/*
 * Presenter tests run on virtual time: the presenter lives in `backgroundScope` with
 * `ioDispatcher = StandardTestDispatcher(testScheduler)`. Instead of polling with a wall-clock timeout,
 * run everything that is due and check the state it settled on. `advanceUntilIdle()` would not run
 * backgroundScope work, hence `runCurrent()`.
 */

internal fun TestScope.settledState(
    presenter: LibraryPresenter,
    predicate: (LibraryUiState) -> Boolean,
): LibraryUiState {
    runCurrent()
    val state = presenter.state.value
    check(predicate(state)) { "Presenter settled on an unexpected state: $state" }
    return state
}

internal fun TestScope.settledDetail(
    presenter: LibraryPresenter,
    predicate: (MangaDetailUiState) -> Boolean,
): MangaDetailUiState {
    runCurrent()
    val state = presenter.detailState.value
    check(predicate(state)) { "Presenter settled on an unexpected detail state: $state" }
    return state
}
