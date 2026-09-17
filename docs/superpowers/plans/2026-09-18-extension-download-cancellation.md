# Extension download cancellation

**Goal:** Cancelling extension acquisition must close the active HTTP request,
finish its file writer, remove the temporary download and avoid installing it.
Existing signature, digest and trust checks remain in the installation path.

The current blocking `execute()`/`copyTo()` in `downloadAndInstall` does not react
to coroutine cancellation until the server responds or the read timeout expires.
Use a structured IO child for blocking download work and cancel its OkHttp call
when awaiting it is cancelled. Structured completion must wait for the writer to
exit before the outer temporary-file cleanup. A response-only callback adapter
would stop protecting cancellation after headers, and could race writer cleanup.

- [x] Reproduce cancellation while waiting for headers and while streaming a body
  with an isolated local HTTP server; keep network timeouts long enough that they
  cannot account for a one-second cancellation assertion.
- [x] Implement cancellation through the full stream, retain normal error paths,
  and ensure no install occurs after acquisition cancellation.
- [x] Verify successful download/install and existing checksum/trust regressions.
  Record source evidence separately from package verification.

This change addresses cancellation of the acquisition operation (including caller
scope shutdown). Adding a visible per-download progress/cancel control remains a
separate UI task; do not claim one from this backend change.

Evidence: [source and packaged verification](../evidence/2026-09-18-extension-download-cancellation.md).
