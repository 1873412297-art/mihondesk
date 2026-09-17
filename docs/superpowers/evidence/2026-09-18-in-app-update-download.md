# In-app update discovery and verified download evidence

Verified 2026-09-18 CST, repository `D:\my project\mihon-w`.
Baseline local main `78b1130d3`, version 0.2.18. A fresh `git fetch origin main`
returned origin/main `90662a9f7089dd4bb210b140209bd0da4205d69c`, version 0.2.13.
Implementation/build revision: `740e0a0bc2aaae8f640657b12da1a65f7e398d1d`.
The work extends local main; it has not been pushed or released.

## Behavior

- About now has an explicit update check, current/new versions and release notes.
  Opening About alone does not make a network request. Download state belongs to
  the application presenter, so leaving/reopening About preserves progress/results.
- Only stable numeric release versions and recognized same-version distribution
  filenames are offered. No arbitrary EXE/ZIP fallback. Runtime creation passes
  installed/portable mode to the update service.
- A download requires an exact official-repository release URL and a unique matching
  SHA256SUMS.txt entry. Missing sizes, missing/ambiguous hashes and unsupported URLs
  fail before downloading the package. Progressively hash the stream, enforce byte
  count and atomically publish the verified temporary file. Preserve an existing
  target on failure/cancellation; clean uniquely named temporary files.
- Cancellation propagates through the service. While a blocking network read is
  exiting, the UI stays in Cancelling and refuses a second operation. Individual
  HTTP reads have timeouts (metadata 10 seconds, package 30 seconds); cancellation
  is cooperative rather than a claim of instantaneous socket interruption.
- Publishing disables the cancel action before the atomic replacement. Completed
  downloads expose a selectable path, Open folder and Release page. Installation
  remains an explicit manual step; no installer/updater process is launched here.
- English, Simplified and Traditional Chinese copy, wrapping actions, progress,
  cancellation and retry feedback. Corrected the existing `mihondeskindows` typo.

The GitHub release response contract was checked against the official
[REST release documentation](https://docs.github.com/en/rest/releases/releases).

## Source regression

Four regressions first failed against the baseline: unchecked download acceptance,
truncated file overwriting an existing target, swallowed cancellation, and malformed
version being treated as current. After implementation, **70 tests passed**, zero
failures/errors/skips, with spotlessCheck passing.

Test groups: `*AppUpdate*Test`, `*DesktopRuntimeFactoryTest`, `*SettingsScreenTest`,
`*DesktopShellTest`, `*DesktopStringsTest`, `*DesktopLocalizationUiTest`.
The live test was added subsequently and is opt-in via `MIHON_UPDATE_LIVE=1`.

Additional cases cover wrong-version/unrelated/ambiguous assets, draft/prerelease
rejection, strict checksum ownership, cancellation cleanup, rapid duplicate clicks,
retry after an unavailable manifest, cancellation during a blocked read, no network
on opening About, and navigating away/back while downloading. UI tests exercise
480/1024 px widths across all three languages and light/dark themes.

Evidence: `build/app-update-tests.log`, `build/app-update-red.log`,
`build/app-update-evidence/source-results/`, `source-images/`.

## Packaged verification

`createDistributable` and `packagePortableZip` succeeded; their clean-distribution
check reported no profile directories, installed extensions or saved configuration.

The packaged JAR has `mihon-build-info.properties`:

```properties
version=0.2.18
revision=740e0a0bc2aaae8f640657b12da1a65f7e398d1d
dirty=false
```

Using the existing packaged-test initialization script
`build/restore-progress-evidence/packaged-tests.init.gradle`, prepend image `app/*.jar`
to the test classpath and use the image's native/resource directories. Set
`MIHON_RESTORE_APP` and `MIHON_UPDATE_APP` to that absolute app directory,
`MIHON_UPDATE_EVIDENCE` to the screenshot destination, and `MIHON_UPDATE_LIVE=1`.
The same groups above then passed **71 tests**, zero failures/errors/skips.
UI tests assert that the update service, presenter, card and connected panel classes
originate in the actual image JAR. This is packaged-class verification, not a claim
that automated tests clicked Windows' native Save dialog or installed a package.

The read-only live test uses a synthetic current version `0.0.0` to exercise an
available-release result, then fetches the actual official checksum manifest:

| Published asset | Bytes | Manifest SHA-256 |
| --- | ---: | --- |
| mihondesk-0.2.13.exe | 486502912 | b086ec018561197578b07097d48c06b6ef856c4a9332a005be1ebe30f12ddc22 |
| mihondesk-0.2.13-windows-x64-portable.zip | 487263127 | 49bbc9406992533509fecc6967524899588ad40374ecd63ceeb5432228ce7be7 |

These public binaries were not downloaded during this check. Stream validation is
covered using deterministic fixtures. Screenshots use a synthetic v0.3.0 release;
they do not imply v0.3.0 is publicly available.

The real freshly built `mihondesk.exe`, with isolated data under
`build/app-update-evidence/exe-profile`, returned:

- `--version`: exit 0, `mihondesk 0.2.18 (Windows x64)`.
- `--smoke-test`: exit 0, `MIHON_DESKTOP_SMOKE_OK`.

Evidence: `build/app-update-package.log`,
`build/app-update-evidence/package-and-exe.json`, `packaged-tests.log`,
`packaged-results/`, `packaged-images/`.
The 1024 px Simplified Chinese and 480 px English screenshots were visually inspected;
labels, long paths and wrapped actions remain visible.

## Artifact

`desktop-app/build/compose/binaries/main/portable/mihondesk-0.2.18-windows-x64-portable.zip`

- Size: 493097123 bytes.
- SHA-256: `d46164765fac3dcd24bf843a789533be673dd76f72d5203745aba603f998cd99`.
- Embedded desktop-app JAR SHA-256:
  `b5240c8a58e3eb1a8b2dd03deba2405c685c91feed796ad7752967004768759d`.
- The ZIP's application JAR is byte-identical to the tested image JAR.

No MSI/installer EXE was rebuilt, no user installation was changed, and nothing
was published. Automatic portable updater handoff, installed-profile rollback,
historical-package compatibility and clean Win10/Win11 acceptance remain open.
The overall product goal and T11 are not complete.
