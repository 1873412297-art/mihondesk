# Detail loading cancellation

Verified on Windows on 2026-09-18. Baseline main `468cd9784`, version 0.2.18.
Implementation and packaged build `695d2a38c5e6578120954dfa6b529dc9de7d8b74`,
dirty=false.

## Problem and result

The shared detail loading state had no back action. Online detail requests were
launched in the outer BrowseContentView scope, so returning to source browsing did
not cancel them. A delayed request could complete after another manga was selected,
persist its result and invoke the shared detail-selection callbacks.

The loading state now exposes localized Back and handles Esc when `showBack` is
true. Online load and membership jobs belong to the detail destination and are
cancelled before back navigation and on disposal. Starting a new detail load cancels
the preceding load. Cancellation is rethrown rather than rendered as a fetch error;
active-status checks guard successful/error publication. Online synchronization also
checks cancellation before chapter fetching and before database persistence.

Viewing remains separate from explicit library membership. This change does not
promise to roll back a transaction already committing or forcibly terminate an
arbitrary non-cooperative extension function.

## Verification

- Initial regression: four navigation cases fail because the loading back action is
  absent. After adding only the button, the same four cases still fail: cooperative
  source work remains alive past the 1500 ms cancellation deadline, while delayed
  results add `/slow` after navigation to `/fast`. Both failing runs are retained.
- **34 source tests pass**, zero failures/errors/skips. Five connected tests run the
  real BrowseContentView, DesktopSourceManager, OnlineMangaSyncService and SQLite
  repository with deterministic in-process source fixtures. They cover metadata and
  chapter waits, cooperative and non-cooperative returns, mouse back/Esc, opening a
  different manga, and disposal of the whole Browse surface. Cancelled loads do not
  reopen details or create favorites.
- Four additional service cases cover late metadata/chapter results during both
  read preparation and explicit add-to-library. Each cancels the operation, releases
  the non-cooperative fixture and joins the request before asserting that no manga
  or chapter rows were persisted.
- Three loading UI cases verify English/Simplified Chinese/Traditional Chinese,
  480/1024 px, light/dark display, mouse and keyboard callbacks, and absence of a back
  action when `showBack=false`. Packaged Compose renders were inspected; these are
  component renders, not a native GUI or production-source acceptance claim.
- Existing shared detail action, online membership, local-source navigation and
  Browse source UI regressions are included in the 34-test suite.
- **34 packaged tests pass**, zero failures/errors/skips. Origin checks confirm
  BrowseContentView, MangaDetailScreen and OnlineMangaSyncService come from the
  built application JAR. The ZIP's application JAR matches this tested image.
- `spotlessCheck`, `createDistributable`, `packagePortableZip` and clean-image
  verification pass. The native EXE's isolated `--version` invocation exits 0 and
  reports `mihondesk 0.2.18 (Windows x64)`; this is CLI startup evidence only.

Workspace evidence:

- `build/detail-cancel-red.log`, `build/detail-cancel-lifecycle-red.log`
- `build/detail-cancel-source.log`, `build/detail-cancel-packaged.log`
- `build/detail-cancel-package.log`
- `build/detail-cancel-evidence/{red,lifecycle-red,source,packaged}/`
- `build/detail-cancel-evidence/images/` (three packaged renders)
- `build/detail-cancel-evidence/package.json`, `version.stdout`, `version.stderr`

## Artifact and remaining work

`desktop-app/build/compose/binaries/main/portable/mihondesk-0.2.18-windows-x64-portable.zip`

- Size: 493159107 bytes.
- ZIP SHA-256: `ec8044479d13d60f8a322eab77ee45677e1a8c42a71556e82ac8ee5f6fdb0ca2`.
- App JAR SHA-256: `f7e0620582d7c5daacd10180d01e1a636da13a37a12cdfbec72c81c03f45a353`.

No publication or installation into the user's profile was performed. Existing MSI
and installer EXE outputs are from the separate unaccepted rollback candidate,
not this build. This closes a detail-navigation defect; T9's broader reader/host
recovery and production-source matrix, T11 and the overall goal remain open.
