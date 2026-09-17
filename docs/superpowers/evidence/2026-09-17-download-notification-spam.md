# Download progress notification regression

Branch: `codex/fix-download-notification-spam`. Version: 0.2.15.

The installed 0.2.14 downloader invoked its progress callback after each saved
page. DesktopRuntime forwarded that callback to WindowsDesktopNotificationService,
whose shared dispatch method always called AWT TrayIcon.displayMessage. Each
page therefore requested another Windows popup; a 145-page gallery could request
145 progress popups before its final result notification.

Progress events now stop before system delivery while remaining available in
the existing in-app notification state. Chapter completion, errors, library
updates and extension update notifications retain their delivery path. Download
queue state and animation rendering are unchanged. The master notification switch
now says "Show desktop notifications" / "显示桌面通知" / "顯示桌面通知" to reflect its
actual scope; the saved preference key and value are preserved.

A replaceable system message sink lets tests inspect the native delivery boundary
without showing 145 real popups. Before the fix, the regression failed with
"Collection should have size 0 but has size 145". After the fix, all 145 progress
events produce zero system messages; chapter completion and failure each produce
one. Additional coverage verifies disabled notifications and hidden chapter content.

The targeted notification, downloader, animation, chapter-detail and settings suite
reported 60 tests: 59 passed, one existing skip, zero failures/errors. Spotless and
git diff checks passed. XML results and logs are retained under
`build/notification-spam-evidence/` and `build/notification-spam-*.log`.

Packaging and installed verification are recorded below once completed.
