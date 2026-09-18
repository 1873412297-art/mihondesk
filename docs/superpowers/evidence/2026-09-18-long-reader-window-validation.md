# Long chapter reader validation

Verified on Windows on 2026-09-18. Baseline main `6dafdc522`, version 0.2.18.

## Problem and result

Previous packaged soaks exercised decoding and session state over 1–8 page chapters without rendering `ReaderScreen` in a native `ComposeWindow`. The synthetic frame probe only measured a moving circle. Neither verified real long-chapter navigation or actual decoded image rendering inside Compose.

This probe adds deterministic 256-page fixtures per chapter (one directory chapter, one CBZ archive chapter, alternating 800×1200 and 1200×1800 JPEGs with SHA-256 manifests) and exercises:
1. All 256 pages in the directory chapter sequentially in `SINGLE_LTR`.
2. Cross-chapter forward navigation to the CBZ chapter and all 256 pages sequentially.
3. Reverse boundary navigation back to the last page of the first chapter.
4. Distant page jumps across all 6 reading modes (`SINGLE_LTR`, `SINGLE_RTL`, `VERTICAL`, `WEBTOON`, `DUAL_LTR`, `DUAL_RTL`) at indices `[0, 1, 127, 128, 254, 255, 128, 1]`.
5. Progress persistence at page 173 and session reopening verifying restored progress.

Three underlying defects were identified and resolved during this validation:
1. **Windows file handle lock during temp codec cleanup**: `ProcessCodecCommandRunner` now explicitly forcibly terminates child process trees, awaits process exit with up to 3 seconds timeout, and `PackagedCodecPageDecoder` executes child processes within `temporaryRoot` rather than subdirectories while retrying directory deletions with exponential backoff, preventing `FileSystemException` on Windows.
2. **ContinuousReader missing post-composition navigation path**: `ContinuousReader` now tracks `state.navigationRequest` and triggers `listState.scrollToItem` upon explicit navigation commands while preserving independent scroll fling state.
3. **PageLoadCoordinator priority-inversion deadlock**: When a visible flight request hit an existing prefetch flight, `awaitVisibleIdle` deadlocked against its own visible demand increment because `idleSignal` was waiting for `visibleDemand.isEmpty()`. `PageLoadCoordinator` now checks whether the flight has been promoted to visible and notifies demand changes, allowing promoted prefetch tasks to proceed immediately.

## Verification

- **LongReaderWindowTest** executed in native `ComposeWindow` (Direct3D, 180 Hz display) with 768m max heap (`source-05`):
  - `windowStageStatus`: `SUCCEEDED`, zero unhandled exceptions.
  - Completed all 512 sequential pages + reverse chapter transitions + 48 mode-jump visits + persistence.
  - 8,027 display-clock callbacks recorded, p95 interval 9.38 ms.
  - Both core memory budget and Skia bridge budget remained strictly within limits (bridge high water mark 8.64 MB, core cache within bounds).
  - `progress-restored.txt` verified: `chapter=2, page=173`.
- **Unit and regression suites**:
  - `ProcessCodecCommandRunnerTest`: Asynchronous process termination and timeout tests pass.
  - `ContinuousReaderNavigationTest`: External selection scrolling and list scroll isolation pass.
  - `ReaderNavigationRequestTest`: Sequence tracking and anchor preservation pass.
  - `PageLoadCoordinatorTest`: Prefetch flight promotion deadlock regression test passes.
  - `:reader-core:test`: 100% pass (clean rerun, 1m 58s).
  - `:desktop-app:test --tests "mihon.desktop.reader.codec.*" --tests "mihon.desktop.ui.reader.*"`: 100% pass.
  - `spotlessCheck`: 144/144 tasks clean and verified.

## Packaging and installation

- **Distribution and portable package**:
  - `:desktop-app:createDistributable` and `:desktop-app:verifyCleanDistribution` passed.
  - `:desktop-app:packagePortableZip` produced `desktop-app/build/compose/binaries/main/portable/mihondesk-0.2.18-windows-x64-portable.zip` (493,164,723 bytes).
- **Windows MSI installer**:
  - `:desktop-app:packageMsi` produced `desktop-app/build/compose/binaries/main/msi/mihondesk-0.2.18.msi` (485,835,952 bytes).
  - Validated with `scripts/verify-msi-package.ps1` (passed).
- **Local user installation**:
  - Installed to `C:\Users\18734\AppData\Local\mihondesk`.
  - Application verified: `mihondesk.exe --version` reports `mihondesk 0.2.18 (Windows x64)` (exit 0) and contains the freshly compiled `reader-core-4e30a2aee86a6e98e8d82981ecda8c.jar`.

Workspace evidence:
- `build/reader-window-evidence/source-05/window-report.json`
- `build/reader-window-evidence/source-05/window-identity.json`
- `build/reader-window-evidence/source-05/progress-restored.txt`
- `build/reader-window-evidence/source-05/callback-nanos.txt`
- `build/reader-window-evidence/source-05/fixture/SHA256SUMS.txt`
- `build/msi-uninstall.log`
- `build/msi-install-new.log`
