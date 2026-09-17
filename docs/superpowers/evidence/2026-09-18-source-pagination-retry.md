# Source pagination recovery

Verified on Windows on 2026-09-18. Baseline main `39ad19b5c`, version 0.2.18.
Implementation/build `3c10c1dbe3934cc574155951ad5a0d157fec4742`, dirty=false.

## Result

Source page loading now shares one implementation for replacement and append
requests. It records the submitted query, requested page and append mode. Failed
append retries request the failed page again and preserve previously loaded items;
editing the search field alone does not change subsequent page requests. Submitting
a new search replaces the result set from page 1.

Back-page navigation and website-assisted retries use the recorded request. The
Next button emits one navigation callback instead of both append and navigation.
New replacement requests clear stale has-next state; cancelled jobs check active
status before publishing results or failures. Result URLs are deduplicated before
publication to the keyed grid.

## Verification

- Both regressions were reproduced against the baseline. Request traces show
  `1,2,3,2` after a page-3 failure and Retry, instead of `1,2,3,3`. The draft-edit
  case sends page 2 with `draft` instead of the submitted `submitted` query.
- **21 source tests pass**, zero failures/errors/skips. Two new connected cases use
  BrowseContentView, the actual source manager and an in-process paged fixture.
  They verify exact request sequences, preservation of 60 accumulated results after
  retry, replacement with 20 results on a new search, and no automatic library rows.
- The suite also includes existing source filter/plumbing, source UI, navigation,
  unified detail and detail cancellation tests.
- **21 packaged tests pass**, zero failures/errors/skips. Origin assertions confirm
  BrowseContentView and BrowseSourceScreen come from the app-image JAR.
- The initial packaged run omitted `MIHON_PACKAGED_EXE`, causing the existing filter
  host test to exceed the Windows command-line length limit with the combined app
  and Gradle classpath (Win32 206). Rerunning with that existing test option set to
  the actual packaged EXE passes, including isolated host startup. No application
  workaround or sandbox relaxation was introduced.
- `spotlessCheck`, `createDistributable`, `packagePortableZip` and clean-image checks
  pass. The portable ZIP application JAR matches the image used for packaged tests.
- Native EXE `--version`, with an isolated data directory, exits 0 and reports
  `mihondesk 0.2.18 (Windows x64)`. This is a CLI startup check, not a native GUI or
  production-source acceptance claim.

Evidence retained locally:

- `build/source-pagination-red.log`
- `build/source-pagination-source.log`
- `build/source-pagination-package.log`
- `build/source-pagination-packaged.log`
- `build/source-pagination-packaged-host-config-failure.log`
- `build/source-pagination-evidence/{red,source,packaged}/`
- `build/source-pagination-evidence/package.json`, `version.stdout`, `version.stderr`

## Artifact and remaining scope

`desktop-app/build/compose/binaries/main/portable/mihondesk-0.2.18-windows-x64-portable.zip`

- Size: 493157376 bytes.
- ZIP SHA-256: `8f4ea71ea2934d18d820b1e4746d6dad5ca3c768235bae8f101c2afc885db12c`.
- App JAR SHA-256: `f7391ada85428d2fc287b7cf13d587c9028023a958eea4587aefb600a08f8afc`.

No publication or user-profile installation was performed. MSI/installer EXE outputs
remain from the separate unaccepted rollback candidate. Mutable filter-dialog draft
semantics were not changed here. Production-source compatibility, broader T9/T11
acceptance and the overall product goal remain incomplete.
