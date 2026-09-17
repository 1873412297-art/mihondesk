# Portable application update handoff

Verified 2026-09-18 CST in `D:\my project\mihon-w`. Baseline local main
`bc6e060a5`, version 0.2.18. Implementation `78c705525`; packaged-launcher fix
`eac5ba49a`; final build including Windows replacement-lock recovery
`e4036b8f26648da2e9aefdc2102de6be29f79df4`, dirty=false.

## Result

After a verified download, the default-profile portable application offers **Exit
and update**. Installed distributions, development launches and external data
directories retain the manual workflow. The application uses its embedded trusted
updater, not a script taken from the downloaded archive.

The helper first stages/checks the archive and probes the candidate, while the
original application continues running. A token-bound ready/commit/abort handshake
then allows a normal application exit. The helper checks caller path/start time,
waits for the packaged launcher chain, requires exit code 0 and acquires the profile
lock before copying data and using the existing recoverable replacement protocol.
Shutdown/preparation failure prevents replacement; a recoverable failure after exit
reopens the available old application. A staging cleanup error retains the stage and
logs a warning instead of preventing restart of an otherwise valid application.

Cancellation is available during preparation. Exit/commit state rejects duplicate
operations. Preparation errors leave the existing application open with retry/log
feedback. A token-only profile receipt resolves the result/log in the owned sibling
handoff directory after restart. English, Simplified and Traditional Chinese UI is
covered at 480/1024 px widths in light/dark themes.

## Verification

- Initial related source suite: 85 discovered, **83 passed**, 2 opt-in tests skipped,
  no failures/errors. Includes presenter success/cancel/preparation failure/commit
  failure/exit callback failure, receipt ownership, eligibility and UI states.
- PowerShell 7: **16/16 passed**. Windows PowerShell 5.1: **16/16 passed**. Cases
  include ready-before-exit, cancellation, bad caller identity, failed shutdown,
  post-swap rollback and restart, the actual 120-second no-commit timeout, invalid
  commit token, checksum/profile-lock/archive protections and interrupted recovery.
- Final packaged suite: 89 discovered, **88 passed**, 1 skipped, no failures/errors.
  The skipped case is the optional live GitHub contract check already exercised in
  the preceding iteration. UI tests assert the service, presenter, connected panel,
  card and handoff classes originate in the actual packaged application JAR.
- Windows file-lock regressions and related source tests: **26 passed**. The final
  packaged suite also includes all three real Windows handle-lock cases below.
- `spotlessCheck`, `createDistributable`, `packagePortableZip` and the distribution
  cleanliness check passed. The ZIP's app JAR matches the tested image. The embedded
  updater resource, standalone ZIP updater and source script are byte-identical.

Logs/results:

- `build/portable-handoff-tests.log`, `build/portable-handoff-evidence/source-results/`.
- `build/portable-handoff-shell-final.log` and
  `build/portable-updater-tests-3000416d8e5b456099c07b70eea1d779/results.json`.
- `build/portable-handoff-winps-final.log` and
  `build/portable-updater-tests-1ee6348ffa3249f5bfb00c95d5e8d9e7/results.json`.
- `build/portable-handoff-evidence/packaged-tests-accepted.log`, `packaged-results-accepted/`,
  `packaged-images/`, `package.json`; `build/portable-handoff-package-final.log`.

## Real EXE handoff

An opt-in integration test copies the actual packaged image into an isolated Unicode/
apostrophe-containing directory, creates a portable marker/profile sentinel, opens
the real GUI and invokes the handoff service using its actual window process.

The first run exposed jpackage's two-process structure: the launcher has no window,
while a same-executable child owns the JVM/window. The test now identifies the window
owner, and production handoff walks same-executable ancestors so it waits for the
launcher too. The original failed log is retained at
`build/portable-handoff-evidence/initial-launcher-failure.log`; the source-level fix
was retested at `native-retest.log` before building the final artifact.

The final run proved:

1. A changed archive is rejected while the original GUI stays alive.
2. The valid archive becomes ready before the application exits.
3. Native WM_CLOSE targets only the test-owned window; the launcher exits with code 0.
4. Replacement completes and a new real GUI starts from the target directory.
5. The profile sentinel survives; the complete old program directory with its
   program/profile sentinels remains in the rollback directory.
6. The receipt reports success and points to the helper log; the restarted test
   window closes normally. No test mihondesk process remained after verification.

The second native case removes `mihon-desktop-version.txt` from only the staged
candidate JAR after readiness. The real candidate EXE then fails its post-swap probe
with `Desktop version resource is missing`. The updater restores the original
program/profile sentinels, retains the failed candidate, records failure and restarts
the old real GUI. The test reads the Windows helper log without assuming UTF-8.

Final evidence under `build/portable-handoff-evidence/real-exe-accepted/`:

- `d90ae4fa-805c-47ae-9dcd-8f504b67a46c/result.txt`: success; original launcher
  PID 15844 (exit 0), replacement window PID 77932.
- `f47f2118-592a-46e5-81a4-6306cc6427ef/result.txt`: rollback; original launcher
  PID 56240 (exit 0), restored window PID 32196.

Both result files record their helper log paths. No mihondesk process remained
after final verification.

This is an isolated replacement using the same newly built 0.2.18 image as old/new
programs, distinguished by a sentinel. It is not historical-release compatibility
proof. The native test invokes the handoff service and closes its owned window; UI
button behavior is independently covered by Compose tests. UI screenshots use a
synthetic v0.3.0 release and do not imply such a public release exists.

To reproduce, set `MIHON_HANDOFF_IMAGE` to the absolute image directory,
`MIHON_HANDOFF_ZIP` to the portable ZIP, and `MIHON_HANDOFF_EVIDENCE` to a new evidence
root, then run `*PortableUpdateHandoffIntegrationTest`. For packaged-class tests use
the existing `build/restore-progress-evidence/packaged-tests.init.gradle`, with
`MIHON_RESTORE_APP` and `MIHON_UPDATE_APP` set to the image's `app` directory.

## Windows download replacement locks

Repeated verification reproduced `AccessDeniedException` when atomically replacing
an existing EXE after a valid download. The process holding the conflicting handle
was not identified. Production now retries eligible filesystem failures for up to
five atomic-move attempts, with a total of one second of backoff. Missing-file and
unsupported-atomic-move errors are not retried; cancellation still cleans the temp
file and preserves the existing destination before publication.

Deterministic tests hold a real Windows file handle that denies deletion. They
verify successful replacement after release, bounded failure while held, and
cancellation during backoff, including original-file preservation and temp cleanup.
The original implementation failed the regression before the fix; all three cases
pass with the fix, including against the final packaged classes.

Evidence in `build/portable-handoff-evidence/`: `retry-diagnostic-suite.log`,
`access-denied-reproduction/`, `locked-destination-red.log`,
`locked-destination-green.log`, and `locked-destination-results/`. Temporary repeat
and stack-print diagnostics were removed before the clean build.

## Artifact and remaining scope

`desktop-app/build/compose/binaries/main/portable/mihondesk-0.2.18-windows-x64-portable.zip`

- Size: 493134137 bytes.
- SHA-256: `d384e7e499108b3df00b21a0ded54911d680abe82f770466ace6ed41234f2a58`.
- App JAR SHA-256: `aa7930acb99f273c6ca043aa1bd4ba74675986c4894c34d6fa52642d54a194d4`.
- Updater SHA-256: `f28c3f2fc2312e1b577dd74eafb2a23f5bebce5b6c214d8e606510d992bde305`.

No user installation was changed, no installer EXE/MSI was rebuilt and nothing was
published. Installed-profile rollback/handoff, external portable profiles, historical
package compatibility and clean Win10/Win11 acceptance remain open. T11 and the
overall product goal remain incomplete.
