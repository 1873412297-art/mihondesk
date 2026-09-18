# Long chapter reader validation

Baseline: main 6dafdc522, version 0.2.18. The independent 7z cache experiment is not included.

Existing packaged soak uses chapters with 1–8 pages and exercises decoding/session state without
rendering ReaderScreen. The synthetic frame probe measures a moving circle. Neither proves long
chapter navigation or actual reader rendering. Preserve both results and add a distinct opt-in probe.

- [x] Build isolated deterministic fixtures: 256 distinct JPEG pages per chapter, one directory and
  one CBZ, alternating 800×1200 and 1200×1800. Record each file's SHA-256. Never use a user library.
- [x] Import through DesktopRuntime, create a real DesktopReaderHandle, and display ReaderScreen
  with DecodedReaderPage in a native ComposeWindow. Observe the production page image store to
  ensure requested pages have decoded and entered composition. Do not call a separate decoder
  to satisfy the observation.
- [x] Exercise all pages in both chapters, reverse boundary navigation, representative distant
  positions in all six modes, and close/reopen progress restoration. Capture each action's
  image-availability latency, core budget and native bridge budget separately.
- [x] Record display-clock callback intervals while the reader operates. Label these as callbacks,
  not actual GPU presentation FPS. Core dispatch bypasses keyboard/mouse event routing, so this
  probe does not replace input/DPI/full-app acceptance. Report failures and partial observations.
- [x] Compile and execute only after the active 30-minute EXE memory control terminates. Verify
  code origins when rerunning against package JARs; keep source/package/install evidence separate.

Acceptance for this probe: real images become available at every requested target; both chapters
have 256 pages; both chapter boundaries and persisted progress agree; no budget violation or
uncaught reader error. Performance values are observations until the machine/display conditions
and the appropriate product thresholds have been assessed. A short window run is not a 30-minute
reading plateau result and is not a release approval.

Inspection found that ContinuousReader reports list positions but has no scroll-to-anchor path
after initial composition. Before changing production code, run ContinuousReaderNavigationTest
against main to test distant selection, chapter replacement and list-to-core feedback. The native
probe then validates the repaired path with decoded images. A passing fake/static layout alone
does not establish the real session/renderer result.

The window stage and progress restoration completed successfully (exit 0, `windowStageStatus: SUCCEEDED`,
`progress-restored.txt: chapter=2, page=173`). See the [validation evidence](../evidence/2026-09-18-long-reader-window-validation.md).
