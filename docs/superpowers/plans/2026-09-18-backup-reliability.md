# Backup reliability iteration

**Goal:** Preserve usable restore points and schedule the next automatic backup from successful completion.

**Context:** Updated to local `main@b02fc9e0b` (0.2.18) following the user's baseline correction; fetched `origin/main` still points to 0.2.13. Work branch `codex/backup-reliability`. The newer main includes atomic preference updates and ANGLE rendering; preserve both. The broader Suwayomi evolution plan remains incomplete; this iteration addresses part of T8.

**Design:** Keep the existing SQLite snapshot and atomic gzip/ProtoBuf publisher. Fix the scheduler's duplicate timestamp write: it currently overwrites the completion timestamp with the time captured before export. A long export can consequently trigger the next backup immediately. Record a single completion time and reuse it for persisted scheduling and the visible result. Verify cancellation/failure preserves old restore points and does not advance successful scheduling. Keep the existing backup format and configured retention behavior.

Alternatives considered: changing the interval to run-start semantics contradicts the existing last-success field; replacing the backup subsystem adds risk without addressing this defect. The narrow scheduler change retains compatibility.

## Steps

- [x] Add a deterministic export that advances the clock by longer than the interval; assert the saved time equals completion and the next check is skipped. Observe failure first.
- [x] Remove the duplicate timestamp write and use one completion value in `DesktopBackupScheduler`.
- [x] Verify manual backup leaves the automatic timestamp unchanged; failed/cancelled snapshot preserves old files and retry eligibility; concurrent due checks produce one backup.
- [x] Exercise atomic publication failure and corrupt input against real files, and run backup/import/settings regression suites.
- [x] Build a fresh application image, run the actual launcher against isolated nonempty profiles, export/import a backup, and verify rejected corrupt input leaves the destination library unchanged.
- [x] Record commands, results, artifact identity, and remaining acceptance gaps.

## Follow-up: protect the selected backup directory

Inspection of 0.2.18 found that backup path keystrokes immediately update persisted settings, unlike the repaired download path editor. A scheduler may export into a partially typed directory. Invalid custom paths also silently fall back to the default directory, reporting success at an unexpected location.

Use an explicit draft/save flow with a directory chooser, asynchronous absolute-directory/write checks, localized success/error feedback, and default-folder reset. A saved change takes effect on the next backup without relocating old restore points. Reuse the existing download directory validation under a general storage name; preserve download behavior. The scheduler must reject nonblank invalid or relative paths instead of silently changing their destination. Blank still selects the default.

- [x] Reproduce draft persistence and invalid-path fallback with UI/scheduler regressions.
- [x] Implement validated save and explicit runtime path rejection; retain atomic preference updates.
- [x] Verify default reset, concurrent preference edits, three languages, and desktop/narrow layout through Compose UI tests and renders.

## Verification

Use Corretto 23 at `C:\Users\18734\.jdks\corretto-23.0.2`; serialize Gradle with `--max-workers=1` and `-Pkotlin.compiler.execution.strategy=in-process`.

Run `:desktop-app:test --tests '*DesktopBackupSchedulerTest'` for the red/green cycle. Then run `:desktop-library-data:test`, backup/import/settings/CLI desktop tests, both modules' `spotlessCheck`, `:desktop-app:createDistributable`, and `:desktop-app:verifyCleanDistribution`. All runtime fixtures live under ignored `build/backup-reliability-evidence`; never use the user's profile for recovery tests.
