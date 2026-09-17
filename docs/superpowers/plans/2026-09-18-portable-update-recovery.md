# Recoverable portable application updates

Baseline: main `bd8d2dc13`, version 0.2.18. T11 currently has only a two-rename
PowerShell updater and a bad-checksum/invalid-executable test. There is no durable
record if the updater dies between renames, and copying `data` does not take the
application's profile lock. The update service has no UI caller yet.

## Design

Keep the approved manual portable updater interface. Serialize operations per target,
validate the archive and candidate before replacing the installation, then acquire
the same byte-range profile lock as the app. Copy the full portable data tree while
it is quiescent; retain the untouched old program/data directory as rollback material.
Reject reparse points and unsafe archive paths rather than following them.

Persist an atomic, flushed operation journal outside the target. Derive stage/backup/
failed paths from a validated operation ID under the target's parent. Add a startup
guard to new builds so only the updater's validation probe can launch while a swap is
pending. A failed probe restores the old directory; preserve the failed installation
too. An interrupted transaction can be rolled back with `-Recover`, and rerunning an
update first resolves its pending journal. Validate the copied user's database with
the real new EXE before committing. Release locks before restarting.

Windows validation confirmed that a directory with open child handles cannot be
renamed here. Hold the byte-range lock during copying, set both guards, then release
the handle immediately before the directory renames. New application startup checks
the guard both before and after acquiring its profile lock. Reject incoming packages
that do not enforce this guard. Close all old-version instances before updating;
older executables do not implement the new startup protocol.

This is a recoverable two-step same-volume directory replacement, not a claim that
Windows supports an indivisible swap of two directory names. Installer/MSI updates,
external custom data directories, and the missing application-update UI remain
separate work. No user installation is modified during verification.

- [ ] Reproduce profile-in-use rejection gap and verify checksum rollback baseline.
- [ ] Add journal/locks/path validation, guarded probe, rollback and recovery.
- [ ] Add app startup guard and localized pending-update guidance.
- [ ] Test successful replacement, active-profile rejection, malformed archive,
      failed post-swap validation and interrupted transaction recovery.
- [ ] Build portable artifact and verify real EXE/profile upgrade and rollback.
- [ ] Record evidence and integrate verified code into local main.
