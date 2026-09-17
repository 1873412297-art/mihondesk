# Backup restore progress and cancellation

Baseline: local `main@2da243c47`, version 0.2.18. Production commit:
`19e14ad75fa006da1aa153c2784f1b520c8fa169`.

## Defect and delivered behavior

The previous UI showed only an indeterminate import dialog with no cancellation action.
Its IO coroutine called a synchronous importer. A regression paused the real importer
just before commit, cancelled the calling coroutine, then let it continue. The old
implementation still committed the manga and report. The regression failed with the
unexpected persisted `MangaRecord` and passes after the change.

Backup restore from library and settings now shares a single presenter and localized
progress dialog. It shows reading, validation, restored manga count, saving, cancellation
in progress, and the final result. A second restore cannot start while the first is active.
Chooser cancellation remains idle; local-folder importing retains its existing path.

The importer retains one SQLite transaction and its two-argument CLI API. A per-operation
control checks cancellation during compressed/expanded reads, record validation and
merging. A requested cancellation rolls back this import before reporting Cancelled.
The final commit gate rejects new cancellation requests; the dialog disables Cancel while
saving. A cancelled coroutine or disposed UI after that gate cannot relabel a committed
restore as cancelled. Terminal state is published after the operation releases ownership.

Cancellation is cooperative: an individual ProtoBuf decoding call or blocking filesystem
operation returns before the next checkpoint. The UI displays cancellation in progress
while waiting; no thread is forcibly stopped and no partial result is reported as success.

## Source verification

Corretto 23, serialized Gradle with `--max-workers=1` and
`-Pkotlin.compiler.execution.strategy=in-process`.

| Scope | Tests | Failures/errors | Skipped |
| --- | ---: | ---: | ---: |
| All desktop-library-data tests | 122 | 0 | 0 |
| Desktop library UI, settings, shell, CLI and backup tests | 155 | 0 | 0 |
| Total | 277 | 0 | 0 |

Both modules' Spotless checks and `git diff --check` passed. This is affected-module
coverage, not a claim of an unfiltered full application run.

Regressions cover cancellation before commit, complete transaction rollback, cancelled
decode resource closure, subsequent retry, duplicate starts, dismiss while rolling back,
late cancellation, and disposal after commit. Progress and commit behavior use real
SQLite/importer/controller paths rather than only simulated UI states.

A synthetic scale test contains 10,000 manga and 40,000 chapters. Cancelling after manga
250 preserves the pre-existing library and produces no report or chapters. A fresh restore
then imports all records with monotonic progress and preserves all bookmarks/page numbers.
The source run recorded 1,778 ms for the successful restore and less than 1 ms for rollback
after the cancellation checkpoint. These are one-run local measurements, not performance
guarantees for other machines, external drives or all backup formats.

Six UI cases cover English/Simplified/Traditional Chinese, 480/1024-pixel test windows,
light and AMOLED themes. The test scene itself is constrained to the target width, and
the dialog bounds are asserted to fit. Narrow captures show a 440-pixel dialog and wrapped
English text. Cancel is usable while restoring and disabled while cancelling/committing;
the cancelled result can be dismissed. Source renders were visually inspected.

Logs: `build/restore-progress-red.log`, `restore-progress-core.log`,
`restore-progress-regression.log`. XML snapshots and images are under ignored
`build/restore-progress-evidence/regression/` and `renders/`.

## Packaged verification

`createDistributable` and `verifyCleanDistribution` passed. The application image is
`desktop-app/build/compose/binaries/main/app/mihondesk/` and contains no user profile
or installed extensions. Embedded metadata: version 0.2.18, production commit above,
`dirty=false`.

| File | SHA-256 |
| --- | --- |
| mihondesk.exe | 329b002304fa801d368a14c3875b92780c37bf81d3c101efaa5ff8a8287aef88 |
| desktop-app JAR | a5aa17837b893bc526c93429546ab9c6e9ee27b9f8742af2a900642f6cc35434 |
| desktop-library-data JAR | 8afdba63ca31a91e6fd9f7d653478d8107cebbd7813242d711aa7e83bf63b848 |

With packaged JARs first on the classpath, 53 focused tests passed (30 application,
23 data), zero failures/errors/skips. Presenter tests assert the presenter, importer
and control classes come from the application image; rendering tests assert the dialog's
class origin. The 10,000-manga restore also passes against packaged classes, recording
1,969 ms in that run. Packaged renders are in `packaged-renders/`; this is Compose
rendering evidence, not a claim of native mouse interaction.

The actual EXE and bundled runtime passed ten isolated command invocations: nonempty
import/export/restore, duplicate import, corrupt gzip rejection, locked-destination
export failure preserving old bytes, custom-folder automatic backup, immediate skip,
automatic-backup restore and invalid-folder rejection. Six business tables were compared
row by row and SQLite integrity/foreign-key checks passed. This reuses the synthetic
fixture harness from the previous backup iteration; no user profile is opened.

Detailed result: `build/backup-reliability-evidence/packaged-20260918-030256/result.json`;
this iteration also retains `build/restore-progress-evidence/packaged-exe.log`,
`packaged-tests.log`, and packaged XML snapshots.

## Remaining overall acceptance

This completes the desktop backup progress/cancellation slice of T8. Real Android and
Suwayomi application-produced backup round trips, automatic pre-upgrade consistency
snapshots, clean Win10/Win11 acceptance and full release packaging remain separate gates.
The new image is a development build on 0.2.18; no new MSI/installer EXE/portable ZIP,
installation replacement or GitHub publication is claimed in this iteration.
