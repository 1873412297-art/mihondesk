# Pre-migration database snapshot verification

Date: 2026-09-18. Baseline: local main `24975595d`, desktop 0.2.18.
Implementation: `0278fff36e07a1d619b28d2f873af535eeccafc7` on
`codex/pre-migration-snapshot`. This is a T8/T11 database recovery increment;
the overall Suwayomi-guided evolution and release acceptance remain incomplete.

## Behavior

Opening an existing older schema now creates a standalone SQLite snapshot in
`database/migration-backups/` before any migration statements run. `VACUUM INTO`
includes committed WAL data. The snapshot is checked with `integrity_check`, its
old schema version is checked, the file is flushed, and a unique `.tmp` file is
published as `.db` by atomic rename. Failures stop the upgrade and clean the
pending file. Completed snapshots survive migration failures and retries.

Fresh/current/unsupported/invalid-version databases do not enter this path. Normal
startup already holds the profile lock before opening SQLite. The migration and
schema-version change still use the existing SQLite transaction. This does not
coordinate arbitrary third-party programs that bypass the profile lock.

Interactive startup reports localized recovery guidance (English, Simplified and
Traditional Chinese) using the saved language preference. Headless/background
commands return nonzero status and a specific `DATABASE_SNAPSHOT_FAILED` or
`DATABASE_MIGRATION_FAILED` category without opening a modal window. Raw causes
and private profile paths are not inserted into structured stdout; stderr retains
the existing diagnostic stack trace. Migration diagnostics include the retained
snapshot path. Native dialog rendering/clicking is not claimed from the injected
message-sink tests.

See [database recovery instructions](../../database-recovery.md), which preserve the
original directory and journal sidecars and first verify the snapshot in a separate
profile. These are database snapshots, not `.tachibk` imports or full profile copies.
They are retained automatically; no old recovery snapshot is deliberately pruned.

Reference: [SQLite VACUUM INTO](https://www.sqlite.org/lang_vacuum.html). Consistency
does not imply byte-identical copies or a guarantee against faulty storage hardware.

## Source verification

The original implementation failed four new migration regressions: no saved snapshot,
no snapshot after failed migration, and no protection when snapshot creation is
unavailable/interrupted. Final tests also cover corrupted pending snapshots, failed
publication, repeated migration, old schema/version preservation, Unicode/quoted
paths, WAL content, all logical table rows, and restoring the saved database.

Executed with Corretto 23, `--max-workers=1`,
`-Pkotlin.compiler.execution.strategy=in-process`, `--console=plain`:

```text
:desktop-library-data:spotlessKotlinApply :desktop-app:spotlessKotlinApply
:desktop-library-data:spotlessCheck :desktop-app:spotlessCheck
:desktop-library-data:test :desktop-app:test
```

The unfiltered run succeeded in 6m 38s: data 129 passed; app 721 discovered, 706
passed and 15 skipped; zero failures/errors. The skipped tests include installed
source/download fixtures, packaged-extension scenarios, some process-manager cases,
background scheduling, frame pacing and WebView checks. They are not counted as
verified passes. Other Windows AppContainer tests did run; thread snapshots showed
normal progression through runtime staging/ACL setup while the suite was active.

Evidence: `build/pre-migration-{red,core,regression,full}.log`,
`build/pre-migration-evidence/source-results.json` and archived XML in
`build/pre-migration-evidence/source-results/`. `git diff --check` passed.

## Packaged verification

`:desktop-app:createDistributable :desktop-app:verifyCleanDistribution` succeeded
in 18s. The application image contains no user profiles, installed extensions or
saved user configuration. Packaged build metadata:

```text
version=0.2.18
revision=0278fff36e07a1d619b28d2f873af535eeccafc7
dirty=false
```

Actual image: `desktop-app/build/compose/binaries/main/app/mihondesk/mihondesk.exe`.

| Artifact | SHA-256 |
| --- | --- |
| EXE launcher | `329b002304fa801d368a14c3875b92780c37bf81d3c101efaa5ff8a8287aef88` |
| desktop-app JAR | `c0933c6cbd526a77e567caf49b49614579f47e0dd80ef6b5d5dee32155697dc4` |
| desktop-library-data JAR | `520cb15908db4cf43931cd1219158f196e3fba08b17c19814becf1c555c930da` |

Packaged-class tests passed: data migration 17 plus app failure presentation/CLI 20,
zero failures/errors/skips. Factory, snapshot helper and startup-handler code origins
were asserted to point into the image JARs. This uses the existing classpath-first
test init script with `MIHON_RESTORE_APP` and the new `MIHON_UPGRADE_APP` origin check.
Logs and XML: `build/pre-migration-evidence/packaged-tests.log`, `packaged-results/`.

The tracked `scripts/verify-database-upgrade.py` then ran the actual EXE/bundled runtime
seven times against brand-new isolated profiles:

1. Fresh creation, no migration snapshot.
2. Synthetic v1-to-v3 upgrade with committed data still in an open WAL; snapshot
   retains schema v1 and all 13 old-schema tables exactly.
3. Current-version reopen without creating another snapshot or changing data.
4. Copy snapshot into another profile, upgrade again, compare every resulting table.
5. Blocked snapshot directory: exit 1, specific category, all old data/version intact.
6. Malformed migration fixture: exit 1, transaction rollback, intact old-schema snapshot.
7. Repair the isolated fixture and retry: exit 0, original snapshot bytes unchanged.

All scenarios passed, including integrity and foreign-key checks. Results:
`build/pre-migration-evidence/exe-20260918-0325/result.json` and per-command stdout/
stderr; summary `build/pre-migration-evidence/packaged-exe.log`. No user profile was
opened. The v1 fixture is synthetic, not a captured historical installed profile.

## Remaining acceptance

This proves the database-schema snapshot path and recovery fixture, not all app-version
upgrades. Full configuration/extension/media rollback, historical installers, true
Android/Suwayomi-generated backups, clean Win10/Win11 machines and full release
packaging remain separate gates. No installer MSI/EXE/portable ZIP was rebuilt,
installed or published. The EXE path above is the development application image.
