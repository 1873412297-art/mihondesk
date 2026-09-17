# Extension installation controls

Provide a localized active-operation banner with the extension name, downloading /
installing / cancelling states, and cancellation during acquisition. After the HTTP
writer closes, an explicit callback transitions to installation; the cancel action
and this transition share the presenter monitor so cancellation cannot race into
package publication. Local file installs start in the non-cancellable installation
phase. Cancellation is feedback, not an error.

Keep one presenter installation job. Disable competing UI install actions and reject
duplicate requests in the presenter. Update-all runs sequentially in the same job;
cancellation stops remaining downloads, and failure leaves later updates pending.
Clear busy state only after the owned job and network cleanup have completed.

- [x] Regress duplicate download requests, cancel/retry, commit phase and batch order.
- [x] Add the download-to-install boundary, presenter states and localized banner.
- [x] Connect the screen and disable competing install controls while busy.
- [x] Verify connected UI at desktop/narrow widths and source/packaged behavior.

50 source and 50 packaged tests pass. Narrow-window screenshot inspection also
identified clipped header actions; wrapping now keeps those actions visible.
See [verification evidence](../evidence/2026-09-18-extension-install-controls.md).

Do not claim cancellation rolls back an installation already committing. The
separate MSI rollback candidate remains unmerged and is outside this change.
