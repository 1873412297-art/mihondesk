# Mihon-style desktop download animation

The requested reference is the repository's Android
`ChapterDownloadIndicator.kt`: queued/preparing downloads spin, known progress
fills a circle smoothly around a down arrow, completion shows a check, and
failure shows an error icon. Adapt those visuals to the existing desktop theme
and Chinese strings. Desktop pause additionally freezes progress and shows a
pause icon; queued tasks must also stop spinning while the queue is stopped.

Use one reusable visual indicator in chapter rows and download cards. Keep the
download card's page count and actions, and animate its existing linear bar.
The chapter screen receives a lightweight projection of the queue already
observed by the application, filtered to the selected manga. Progress ticks must
not cause additional filesystem scans or mutate download records. Existing
download, retry, pause, cancel, and completed-chapter actions remain authoritative.

Validate live queue-to-paused-to-completed changes, an intermediate animation
frame after progress changes, error/retry reset, chapter-row progress updates,
and readable dark/AMOLED colors. Use synthetic pages and a desktop-sized render,
then package and install the new version after focused regression checks.
