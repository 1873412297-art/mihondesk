# Portable update and full-profile rollback verification

Date: 2026-09-18. Baseline main: `bd8d2dc13`, desktop version 0.2.18.
Implementation commits: `8b8804d7b` and `db2d47957`, branch
`codex/portable-update-recovery`. The latter removes the external shell's dependency
on discovery of the Windows PowerShell `Get-FileHash` script-module command.

## Behavior and limits

The portable updater now serializes operations per installation, acquires the Java
profile lock's byte range, copies a quiescent portable `data` directory, and retains
the entire old application/data directory. It verifies the staged ZIP digest, refuses
unsafe/duplicate archive paths, bundled data and reparse points, and checks the
candidate executable before moving the old installation.

A flushed, atomically replaced JSON journal lives outside the target. Staging,
rollback and failed-directory paths are derived from its validated operation ID and
the requested target's parent; recovery checks ownership guards before restoring
directories. The original directory remains available even if the new database
cannot migrate. The failed new copy is also retained instead of being deleted.

Windows validation showed that an open child-file handle prevents directory rename
here. Accordingly, the updater holds the profile lock during copying, sets startup
guards on both directories, then releases the handle immediately before the two
renames. New builds check the guard before and after profile-lock acquisition.
Only a version probe with the matching operation token may start during validation.
Incoming packages that ignore the guard are rejected before replacement. Older
running application instances must be closed before updating.

This is a recoverable same-volume two-step replacement, not an indivisible exchange
of directory names. `-Recover` restores an unvalidated transaction, including when
the target directory is absent between renames. Validated transactions finish
cleanup. A subsequent update first handles an existing journal. Restart happens after
the updater releases its locks. Corrupt/foreign operation records are rejected with
recovery material retained.

[Windows release instructions](../../WINDOWS_RELEASE.md) document copying the **new**
updater outside the application directory, manual recovery, retained directory names,
disk-space needs and limitations. They also correct the prior claim of a working
in-app updater: the service exists, but no current UI caller was found. In-app update
checking/downloading/installing is still unfinished.

Protocol references: Microsoft's [FileStream.Lock](https://learn.microsoft.com/en-us/dotnet/api/system.io.filestream.lock)
and [FileShare](https://learn.microsoft.com/en-us/dotnet/api/system.io.fileshare)
documentation. A process-crash recovery test does not prove resilience to arbitrary
filesystem or hardware failure.

## Source and script verification

The initial red test demonstrated that the old updater accepted a replacement while
the fixture's profile lock was held. The revised updater rejects it explicitly before
opening the archive. A separate JVM test holds the actual `DesktopProfileLock` and
invokes the real PowerShell updater, proving cross-process Java/.NET interoperability.

`scripts/tests/portable-updater.tests.ps1 -KeepArtifacts` passed nine scenarios in
both PowerShell 7.6.5 and Windows PowerShell 5.1:

1. Incorrect SHA-256 preserves the original installation.
2. An active profile prevents replacement.
3. Successful replacement preserves the old program and full profile.
4. Post-swap validation failure restores the original and retains the failed copy.
5. An archive containing `data` is rejected.
6. ZIP traversal is rejected before extraction.
7. A profile junction is rejected without following/changing its destination.
8. A real updater and its waiting validation child are terminated, then its actual
   pending journal restores the installation; another updater is rejected while the
   first is alive, invalid operation IDs are rejected, and recovery is repeatable.
9. Using paths from an actual interrupted operation, the fixture recreates the exact
   between-renames filesystem state (missing target), then verifies recovery. This
   boundary is reconstructed, not claimed as a timed physical power-cut test.

These use a compiled C# probe for deterministic process behavior. Final records:

- `build/portable-update-regression.log`
- `build/portable-updater-tests-f5dadd2d6dae406ca640db7adf26cd25/results.json`
- `build/portable-update-windows-powershell.log`
- `build/portable-updater-tests-c3ba1fecfe2e4b51b19de7bad8794292/results.json`

Gradle source checks used Corretto 23, `--max-workers=1`,
`-Pkotlin.compiler.execution.strategy=in-process`, `--console=plain`:

```text
:desktop-app:spotlessKotlinApply :desktop-app:spotlessCheck :desktop-app:test
  --tests '*PortableUpdateGuardTest' --tests '*DatabaseUpgradeFailureTest'
  --tests '*WindowsBackgroundSchedulerTest' --tests '*DesktopRuntimeFactoryTest'
  --tests '*DesktopAppUpdateServiceTest' --tests 'mihon.desktop.cli.*'
```

53 discovered: 52 passed, one opt-in real Task Scheduler registration test skipped,
zero failures/errors. This is the affected regression set, not a new unfiltered app
suite run. Output: `build/portable-update-final-source.log`; preserved XML and summary
in `build/portable-update-evidence/source-results/` and `source-results.json`.
`git diff --check` passed. Localized recovery messages were checked with an injected
message sink; native dialog rendering/clicking is not asserted by these tests.

## Packaged application and ZIP

`:desktop-app:createDistributable :desktop-app:verifyCleanDistribution
:desktop-app:packagePortableZip` passed in 46s. The image clean-distribution check
passed. Artifact metadata:

```text
version=0.2.18
revision=db2d479570948ca45a9e7a2544af93700ad35a78
dirty=false
```

| Artifact | SHA-256 |
| --- | --- |
| `mihondesk-0.2.18-windows-x64-portable.zip` | `1994584d995c560581b9880172782732a8bbae6b65633611ac524e9b7c131585` |
| Updater contained in the ZIP | `c7b8bad936b2e072fb27d14e18b3ff35dc4951c5ca901381077f32c0fca67856` |
| Application JAR | `e0523eb8e81f67f9dac70298ee9e7a782ffbccdac4d542c32ed685d76b88ccbf` |

ZIP path: `desktop-app/build/compose/binaries/main/portable/mihondesk-0.2.18-windows-x64-portable.zip`
(493,039,582 bytes). The app image is in `desktop-app/build/compose/binaries/main/app/mihondesk`.
The same affected tests passed against the packaged classpath: 52 passed, one skipped.
Guard, Java profile lock and recovery-handler class origins were asserted to come
from image JARs. Evidence: `build/portable-update-evidence/packaged-tests.log`,
`packaged-results/`, and `packaged-results.json`.

The tracked `scripts/verify-portable-update.py` extracted the **packaged updater**,
checked its bytes against the source, and used the actual EXE/bundled runtime and
Windows PowerShell 5.1 against two isolated nonempty synthetic schema-v1 profiles:

- Success: retained original application marker and every original profile file's
  exact bytes in the rollback directory; new program starts, migrated schema is v3,
  and its pre-migration snapshot matches every original database table. Language,
  credential-profile identity, private sentinel and media sentinel are preserved.
- Migration failure: a malformed migration fixture makes the post-swap EXE probe
  fail. The original installation is restored, every original profile file hash
  matches, database rows/version match, and the failed new directory is retained.

Both passed, including integrity/foreign-key checks and stage/journal cleanup.
Result: `build/portable-update-evidence/exe-20260918-0420/result.json`; per-command
stdout/stderr are beside it; summary `build/portable-update-evidence/packaged-exe.log`.
Recorded update durations were 17.489s and 16.387s respectively on this machine,
not general performance guarantees. The original application image and incoming
image share this build; a sentinel distinguishes the old application directory.
This does not prove a historical 0.1.3 installer-to-current upgrade.

No user installation/profile was modified, no installer EXE/MSI was rebuilt, and
nothing was uploaded. Full-profile protection here covers in-directory portable data;
external custom data roots, installed-mode rollback, actual historical packages,
clean Win10/Win11 acceptance, native UI flows and in-app update integration remain
separate completion gates.
