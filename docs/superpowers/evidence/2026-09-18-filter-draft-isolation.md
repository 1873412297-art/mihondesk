# Source filter draft isolation

Verified on Windows on 2026-09-18. Baseline main `af260c41c`, version 0.2.18.
Implementation/build `de1b8bd6bb2312641fa1197abd9eb085cec32e02`, dirty=false.

## Result

The source filter dialog now edits a separate draft produced with the existing IPC
DTO mapping. Cancel discards it; Apply copies only state back onto the source's
original filter instances. This preserves custom filter classes, including nested
group children, instead of passing generic reconstructed filters to built-in sources.

Reset restores the defaults captured when this source view loads its filters and
changes only the draft. Cancelling after Reset preserves the active settings.
Draft changes replace the observable draft value, fixing stale checkbox/group
rendering caused by the old write-only mutation counter. Applying filters retains
the existing page-1 search behavior; subsequent pagination uses those applied values.

## Verification

- Two baseline regressions fail separately: selection remains visually Off after
  clicking the checkbox; cancelling and reopening instead finds the active value
  changed to On. Failing XML and logs are retained.
- **23 source tests pass**, zero failures/errors/skips. The new connected cases run
  BrowseContentView and a real source manager with a deterministic custom source.
  They cover edit/cancel/reopen, immediate checkbox and group feedback, Apply and
  next-page search, Reset/cancel/reopen, and Reset/Apply starting a fresh first page.
- The fixture casts top-level and nested filters to its own subclasses, so loss of
  source-specific types fails the request. Its nested checkbox defaults to true,
  verifying Reset retains source defaults rather than clearing all values.
- Existing filter DTO/plumbing, browsing, pagination recovery, navigation and detail
  cancellation regressions are included. Viewing/filtering creates no library rows.
- **23 packaged tests pass**, zero failures/errors/skips. Origin assertions confirm
  BrowseContentView, SourceFilterDialog and FilterIpc come from app-image JARs. The
  existing filter host test uses `MIHON_PACKAGED_EXE` to start the actual packaged
  host without the combined Gradle/app classpath exceeding Windows command limits.
- The packaged dialog render was inspected: selected checkbox and deselected group
  chip reflect the pending draft. This is a Compose test render, not production-source
  or native-GUI acceptance.
- `spotlessCheck`, `createDistributable`, `packagePortableZip` and clean-image checks
  pass. The portable ZIP's app JAR matches the tested image. Native EXE `--version`
  with an isolated data directory exits 0 and reports version 0.2.18.

Workspace evidence:

- `build/filter-draft-red.log`, `build/filter-draft-source.log`
- `build/filter-draft-package.log`, `build/filter-draft-packaged.log`
- `build/filter-draft-evidence/{red,source,packaged}/`
- `build/filter-draft-evidence/images/filter-draft.png`
- `build/filter-draft-evidence/package.json`, `version.stdout`, `version.stderr`

## Artifact and remaining scope

`desktop-app/build/compose/binaries/main/portable/mihondesk-0.2.18-windows-x64-portable.zip`

- Size: 493156304 bytes.
- ZIP SHA-256: `240d8e8f000202c07a57a55c8655272dc558d13d121e56c93f2dd8a4a2f6e237`.
- App JAR SHA-256: `9a77edcf86c5cdd82e7ef05ed3c4650c1166ab48ba19da379b18cc3c38a0dfaa`.

No publication or installation into the user's profile was performed. MSI and
installer EXE outputs remain from the separate unaccepted rollback candidate.
Production-source compatibility and complete T9/T11 acceptance remain open; this
change does not establish completion of the overall product goal.
