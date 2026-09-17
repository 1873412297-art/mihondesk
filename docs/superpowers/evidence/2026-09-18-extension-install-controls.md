# Extension installation controls

Verified on Windows on 2026-09-18. Baseline main `90b8478f6` remains version
0.2.18. Implementation and packaged build:
`cc55c59715e76b8446248e4c1c9070e8827dfeff`, dirty=false.

## Behavior

- Browse displays the current extension and localized downloading, installing and
  cancelling states. Cancellation during acquisition returns usable retry feedback
  instead of an installation error. Installing has no cancel action.
- One presenter job owns installation. Duplicate requests are rejected, and file,
  install, update and update-all buttons are disabled while busy. The job clears
  busy state only after download cleanup completes.
- Update-all runs sequentially. Cancellation or failure stops remaining items;
  completed installations remain available. The download-to-install transition and
  explicit cancellation share a monitor to prevent cancel/commit races.
- The header wraps its actions in narrow windows. Screenshot inspection originally
  exposed crushed repository text and a missing refresh action at 480 px.

The explicit cancel action does not roll back an installation already committing.
The existing Browse scope lifetime remains unchanged; navigating away can cancel
that scope. This change does not implement an application-wide background install
queue or resumable downloads.

## Verification

- The initial local-server regression reproduced duplicate requests before the fix.
  Its failing XML and log are retained in the evidence locations below.
- **50 source tests pass**, no failures, errors or skips. Four installer/presenter
  integration cases cover duplicate rejection plus cancellation/retry; complete
  serial installation; cancellation of the second download preserving the first;
  and second-download HTTP failure preserving the first and skipping the third.
  The blocked verifier also confirms explicit cancellation is ignored once the
  presenter enters installation. Cancel/retry completes within a 1500 ms deadline.
- Six connected BrowseScreen cases cover English, Simplified Chinese and
  Traditional Chinese at 1024 and 480 px, across light/dark themes. They verify the
  cancel callback, cancelling/committing controls, retry feedback, competing install
  controls and visible header actions. Screenshots were reviewed at wide and narrow
  widths. These are Compose test renders, not an end-to-end native GUI installation.
- Existing Browse, global search, installer, download cancellation, strings and
  reader localization regressions are included in the 50-test suite.
- **50 packaged tests pass**, no failures, errors or skips, using app-image JARs
  before source outputs on the test classpath. Origin assertions confirm packaged
  BrowsePresenter, BrowseScreen, ExtensionInstallStatus and DesktopExtensionInstaller.
- `spotlessCheck`, `createDistributable`, `packagePortableZip` and clean-distribution
  verification pass. The portable ZIP's application JAR matches the tested image.
- The rebuilt native EXE exits 0 with `--version` and an isolated `--data-dir`,
  reporting `mihondesk 0.2.18 (Windows x64)`. This proves CLI startup only.

Evidence retained in the workspace:

- `build/extension-install-controls-red.log`
- `build/extension-install-controls-source.log`
- `build/extension-install-controls-package.log`
- `build/extension-install-controls-packaged.log`
- `build/extension-install-controls-evidence/{red,source,packaged}/`
- `build/extension-install-controls-evidence/images/` (six packaged renders)
- `build/extension-install-controls-evidence/package.json`
- `build/extension-install-controls-evidence/version.stdout` and `version.stderr`

## Artifact and limits

`desktop-app/build/compose/binaries/main/portable/mihondesk-0.2.18-windows-x64-portable.zip`

- Size: 493151017 bytes.
- ZIP SHA-256: `4a9329c870ddf3432eac8bf4568960406a409c3dc581390f41d50f5065c46e4c`.
- App JAR SHA-256: `4be51e60141eeae4687c2e4840c926a62d1377e4be8bb4eae9a15fe4c0620c8a`.

No GitHub publication or user-profile installation was performed. MSI and installer
EXE outputs still belong to the separate, unaccepted rollback candidate and must
not be presented as deliverables for this change. The real-source compatibility
matrix, MSI registration rollback, T11 and the overall product goal remain open.
