# Mihon-style download animation

Branch: `codex/feat-download-animation`. Target version: 0.2.14.

The reference is the checked-in Mihon Android `ChapterDownloadIndicator.kt`:
queued/zero-progress downloads use an indeterminate ring, downloading uses a
smoothly filled circle with a down arrow, completion uses a check, and errors
use an error icon. Desktop additionally stops the spinner and shows pause when
the downloader stops, including queued tasks. The same component is used in
chapter rows and download cards. Card bars also animate real progress.

Chapter detail previously showed only a static download/check icon. The app now
projects its already-observed download queue into the shared detail state,
filtered to the selected manga. No extra database or filesystem work occurs on
progress ticks. Library and online detail surfaces receive the same state.
Download actions and persistent downloader behavior are unchanged.

The initial queue animation test failed because no indicator existed. UI tests
then verified intermediate progress frames, actual spinner pixel movement,
static paused pixels, queued tasks stopping with the downloader, error/retry
reset, completion, and chapter updates without reloading metadata. A rapid
retry/completion test exposed one frame of stale progress; terminal and reset
values now render immediately while only forward active progress animates.

Desktop-width render inspection found a black inherited download-page heading
in AMOLED. A Surface now provides the matching foreground. The UI test checks
the white heading, and renders cover 20%, an intermediate frame, 80%, error,
completion and pause. Nine focused UI tests passed, followed by all 271 desktop
UI tests with no failures, errors, or skips. Spotless and git diff checks passed.
Source renders and XML results are under `build/download-animation-evidence/`.
Installation evidence will be appended after verification.
