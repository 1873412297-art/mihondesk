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

## Installed verification

`packageMsi` and `verifyCleanDistribution` passed. Windows Installer returned 0
for 0.2.14. Preferences, download queue, and library database hashes matched the
pre-upgrade copies before restarting the app.

MSI: `desktop-app/build/compose/binaries/main/msi/mihondesk-0.2.14.msi`, SHA-256
`588FC766B599398F4CE0B503CE01E8B3E2AB9161ECDA39B94C18071022A40366`.
The installed desktop JAR matches the packaged JAR, SHA-256
`6B953E9CE2EE905F7C92BB5960401A90B062D79909E05ADC7DA0BA7779BBAC1C`.
Embedded version: 0.2.14; revision:
`ed41b91765524202bcfab01db25273140e4df816`; dirty=false.

All seven animation and manga-detail UI tests passed with installed JARs first
on the classpath. Animation tests assert they load the production indicator from
`C:\Users\18734\AppData\Local\mihondesk\app`, including pixel motion, paused
pixel stability, intermediate fill frames, and reset/completion behavior.
The tests use synthetic content and do not mutate the real profile. Installed
renders, XML results, installer logs and profile-hash evidence are saved under
`build/download-animation-evidence/`. The installed EXE was restarted afterward.
