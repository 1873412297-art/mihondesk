# Extension package download cancellation

Verified 2026-09-18 on the current Windows workspace. Baseline main `f6539fa6e`;
implementation/build `e5a403537d62dbd8c1fccc4e6dcb2d2a6f2fc178`, version 0.2.18,
dirty=false.

## Result

Cancelling `DesktopExtensionInstaller.downloadAndInstall` now cancels its OkHttp
call during both response-header and response-body waits. Blocking download work
runs in a structured IO child; the parent waits for the file writer to close before
deleting the temporary package. Socket-close IO exceptions caused by cancellation
remain coroutine cancellation rather than becoming installation failures.

The installed-package, digest, signature and trust logic is unchanged. This covers
cancellation of acquisition by its caller/scope. It adds no visible UI cancel button
and does not claim cancellation of the separate installation commit phase.

## Verification

- Before the fix, both local-server cancellation regressions fail the 1000 ms
  completion deadline. Server read timeout is 30 seconds; the body case waits for
  OkHttp's body-start event and then withholds the rest of the advertised content.
- After the fix, **32 source tests pass**, none skipped, failed or errored. Includes
  the two cancellation cases, valid download/install, wrong SHA-256, HTTP 503,
  existing installer/trust tests and converter compatibility/cache tests.
- **32 packaged tests pass**, none skipped, failed or errored. The five new cases
  assert `DesktopExtensionInstaller` comes from the actual app-image JAR. No
  extension is installed by cancelled or rejected downloads.
- The temporary download inventory before/after the related run contains no new
  `mext_dl_*.mext` files. This is a live resource check, distinct from the per-test
  assertions on installed metadata and directories.
- `spotlessCheck`, `createDistributable`, `packagePortableZip` and the clean-image
  check pass. ZIP app-JAR bytes match the image used by packaged tests.
- The rebuilt native EXE exits 0 for `--version` with an isolated `--data-dir`,
  reporting `mihondesk 0.2.18 (Windows x64)`. This is a CLI startup check, not GUI
  acceptance; stdout/stderr are retained beside the package evidence.

Evidence:

- `build/extension-download-cancel-red.log` and
  `build/extension-download-cancel-evidence/red/`.
- `build/extension-download-cancel-source-final.log`,
  `build/extension-download-cancel-evidence/source/`.
- `build/extension-download-cancel-packaged.log`,
  `build/extension-download-cancel-evidence/packaged/`.
- `build/extension-download-cancel-evidence/temp-after.json`, `package.json`.
- `build/extension-download-cancel-package.log`.

## Artifact and remaining scope

`desktop-app/build/compose/binaries/main/portable/mihondesk-0.2.18-windows-x64-portable.zip`

- Size: 493138670 bytes.
- ZIP SHA-256: `12bedd1c2f4e79b039da1712afe9c80060d1f7600417aa4f87d638acebd8f2c8`.
- App JAR SHA-256: `baa9411e4a503ab217ed0959f71abe753b622e328a2de120dbe07d612fd60d8f`.

No publish or user installation was performed. MSI/installer EXE outputs remain
from the separate, unaccepted `4fba75078` rollback candidate; they are not artifacts
of this change. That investigation is retained on `codex/msi-upgrade-rollback` at
`521bdee61`: file restoration succeeds but native product-registration rollback
still fails, including the user's default installation drive and MSI-version-500
control. It remains unmerged. T3's real sample matrix, T11 and the overall product
goal remain incomplete.
