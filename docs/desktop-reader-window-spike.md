# P2-1 Independent reader window — spike notes (deferred from 0.2.29)

Status: **deferred**. No multi-window code shipped in 0.2.29.

## Current state
- `MihonDesktopApp` (`ApplicationScope`) owns a single main `Window`; the reader is a transient
  `DesktopDestination.Reader` route inside `DesktopShell`. `transitionReaderWindow(ReaderWindowMode)`
  only toggles fullscreen/maximized state on that same window.
- Reader state (page loader, preloader, history/progress writes, tracker sync, incognito checks,
  shortcut dispatch, single-instance foregrounding) is wired through the main window's composition.

## Proposed design
1. Add `openReaderWindows: SnapshotStateList<Long>` (chapterIds) in `MihonDesktopApp`; render one
   extra `Window(onCloseRequest = { remove(id) })` per entry under the same `ApplicationScope`.
2. Extract a `ReaderHost(chapterId, runtime, onClose)` composable from the shell's Reader branch so
   both the in-shell route and a standalone window share one implementation.
3. Per-window `ReaderSession` scoped to the window's lifecycle (cancel page loads on close).
4. Shortcuts: per-window `onPreviewKeyEvent`; global shortcuts stay on the main window only.
5. Persist per-window bounds under `reader.window.<n>` keys; cap at 1 detached window initially.

## Risks (why deferred)
- Progress/history writes from two compositions racing on the same manga (needs a single writer).
- Single-instance "bring to front" and tray restore assume one window.
- App-lock must cover every window; currently only the main window is gated.
- Skiko/D3D: each Compose window owns its own render context; GPU memory doubles with large pages.
- Mixed-DPI monitors: window moved between monitors re-reads the OS density; content density no
  longer touches `LocalDensity`, so there is no extra multiplier to reconcile.

## DPI note (P2-2, revised in 0.2.30)
0.2.29 shipped compact density as `Density(osDensity * 0.9)` at the shell root. That shrank text
and the reader along with everything else, so it was reverted in 0.2.30.

- `LocalDensity` is now always the OS / per-monitor value supplied by Compose Desktop.
- *Interface density* (Settings > Appearance, `ui.content_density` = `Standard` | `Compact`) only
  swaps the `LocalSpacing` tokens: page padding 32/16 dp, library grid gaps, list row height and
  padding, settings card padding. Text size is unchanged.
- The reader never reads `LocalSpacing`; a test asserts the reader sees the OS density in compact.
- Legacy `ui.density=Compact` is migrated once to `ui.content_density=Compact` and removed.
- Navigation rail width is a separate `NavRailState` (`ui.navigation_rail_collapsed`, Ctrl+B,
  auto-collapse below 1200 dp with an overlay when expanded on narrow windows).
