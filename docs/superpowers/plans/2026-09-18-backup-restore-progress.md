# Cancellable backup restore with progress

**Baseline:** local main `2da243c47` / 0.2.18, clean. Continue the approved T8 recovery objective; do not claim full-port completion.

**Goal:** A large backup has visible reading, validation, restore and commit stages; cancellation before commit rolls the entire import back. Finished/cancelled jobs cannot overwrite a subsequent operation.

**Design:** Keep synchronous CLI import and the existing SQLite transaction. Add a per-import control with checkpoints, progress and a serialized cancel/commit gate. Decode checks between reads; validation and merging check per record. Report completion only after the transaction returns. Coroutine lifecycle cancellation is observed at checkpoints. Once the final commit gate is crossed, no new user cancellation is accepted; UI shows finishing instead. No thread termination or partial commits.

Desktop backup restore gets a single active operation presenter and localized dialog. Chooser cancellation stays idle. Reuse this entry in library/settings, preserve local-folder import behavior, and expose Cancelled only after rollback. Progress counts completed manga, with indeterminate reading/validation and committing stages. Raw paths and source metadata do not appear in progress labels.

Alternatives: merely cancelling a coroutine does not stop synchronous SQLite work; committing one manga at a time leaves partial restore data. Both are rejected. The existing full transaction plus cooperative checkpoints preserves compatibility.

- [ ] Reproduce lifecycle cancellation committing data via a real importer/controller regression; observe failure on the current implementation.
- [ ] Add per-operation control and rollback checkpoints; retain existing two-argument import API for CLI and callers.
- [ ] Add progress/cancel/commit-gate, rollback and repeated import regression tests against real SQLite.
- [ ] Add presenter with single-operation ownership, safe cancel and terminal state; test duplicate starts and completion boundaries.
- [ ] Wire shared desktop restore progress UI and localized cancel/finishing/result states; verify interaction, narrow/desktop layout and contrast.
- [ ] Run affected data/app tests and Spotless, build a clean application image, verify packaged behavior, and record authoritative evidence.

Use Corretto 23 and serialized Gradle (`--max-workers=1`, `-Pkotlin.compiler.execution.strategy=in-process`). Verification profiles and render captures stay in ignored `build/restore-progress-evidence/`; never import into the user's library.
