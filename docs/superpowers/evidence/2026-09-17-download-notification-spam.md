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

## Installed verification

`packageMsi`, `verifyCleanDistribution` and `scripts/verify-msi-package.ps1` passed.
Windows Installer returned 0. Preferences, download queue and library database
SHA-256 values matched the pre-upgrade copies before restarting the application.

MSI: `desktop-app/build/compose/binaries/main/msi/mihondesk-0.2.15.msi`.
SHA-256: `480629CB5444F75884FD2371CE9B04EFA37FC5259DFF40C1B2E15CBD6E20AA63`.

The installed desktop JAR matches the packaged JAR:
`E423CB5CA7458BD5689AB70407A80AD3270A81A1992DFB8455E305C6C4DDE7EE`.
Embedded version: 0.2.15; revision:
`4ec47590b1b16d54f43e127433fbb025375c6760`; dirty=false.

All 11 notification, animation and chapter-detail tests passed with installed
JARs first on the classpath, with no failures/errors/skips. The regression asserts
that the notification service is loaded from the installed JAR. Its 145 progress
events request zero native messages; completion and failure each request one.
This validates the delivery boundary without generating 145 real Windows popups.
Animation tests also assert installed class origin and verify intermediate fill,
spinner movement, static pause, reset and completion.

Installer logs, build identity, profile hashes, source and installed XML results,
and installed animation renders are retained in `build/notification-spam-evidence/`.
The installed executable was relaunched; its window title is `mihondesk` and
the app process reports Responding=true. Launch evidence is saved alongside the
installation checks. No GitHub release was published for this local repair.
