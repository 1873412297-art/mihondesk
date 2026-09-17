# In-app release discovery and verified downloads

Baseline: local main 78b1130d3, version 0.2.18. The existing update service has no
UI caller, accepts arbitrary ZIP fallbacks, swallows cancellation and permits
unchecked downloads. The portable swap/recovery implementation is already separate.

Add an explicit Check for updates action to About. Keep its presenter at application
scope so navigation does not discard a download. Display stable release notes,
matching distribution, progress, cancellation, retry and the saved file location.
Use a native Save dialog and require the selected release's SHA256SUMS.txt entry.
Validate release identity, filenames, URL origin/path, byte count and SHA-256 before
atomically publishing a unique temporary download. Preserve existing destination
files on failure. Do not launch installers as part of downloading.

The completed download offers its containing folder and the official release page;
copy explicitly explains manual installation/portable updater use. Automatic updater
handoff, installer profile rollback, and native installation verification remain
subsequent work in the ongoing product goal.

- [x] Add regressions for malformed releases, missing/ambiguous checksum, cancellation,
      truncated downloads, existing destination preservation and single-flight state.
- [x] Implement verified service and persistent presenter.
- [x] Wire About UI with English, Simplified and Traditional Chinese copy.
- [ ] Verify source tests and packaged classes; record evidence and merge local main.
