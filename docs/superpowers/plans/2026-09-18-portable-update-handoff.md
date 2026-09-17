# Portable update handoff from the running application

Baseline local main bc6e060a5 / 0.2.18. The prior iteration finishes at a verified
download. Implement the next user-visible step while retaining manual installation
for installed distributions and custom external portable profiles.

Embed the current trusted updater script in the application JAR. An application
handoff owns a UUID-named sibling directory, script and log outside the installation.
The updater stages/checks the archive and probes the candidate while the caller
still runs. A ready/commit/abort handshake precedes caller shutdown and profile copy.
Bind the caller to its executable path and start timestamp, reject nonzero/timeout
exit, then run the existing locked copy/journal/swap/rollback. Persist a result and
restart the available unguarded application after success or recoverable failure.

Keep a token-only receipt pointer in the profile; after restart About can show the
last result and log. Offer this only for a real packaged EXE with .portable and the
default in-installation data directory. Reuse the verified checksum at application
time, retain single-flight state, and leave the app open if preparation fails.

- [x] Regress handshake readiness, cancellation, caller identity/exit failure and timeout.
- [x] Implement launcher, receipt, presenter/UI action and graceful exit integration.
- [ ] Run source and packaged regressions, real isolated EXE upgrade/rollback, record evidence.

No user installation is modified by tests. Installed-profile rollback, historical
release validation and clean Windows acceptance remain part of the full goal.
