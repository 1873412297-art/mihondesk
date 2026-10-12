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
- Mixed-DPI monitors: window moved between monitors re-reads density; untested with the new
  compact density (which multiplies on top of the OS scale).

## DPI note (P2-2)
Compact density is `Density(osDensity * 0.9)` applied at the shell root, so it composes with the
Windows per-monitor scale rather than replacing it; at 100% scale compact text is ~90% size. The
reader route inherits it as well. Revisit if users on 100% displays report text too small.