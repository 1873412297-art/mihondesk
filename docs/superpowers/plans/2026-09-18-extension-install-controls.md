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

- [ ] Regress duplicate download requests, cancel/retry, commit phase and batch order.
- [ ] Add the download-to-install boundary, presenter states and localized banner.
- [ ] Connect the screen and disable competing install controls while busy.
- [ ] Verify connected UI at desktop/narrow widths and source/packaged behavior.

Do not claim cancellation rolls back an installation already committing. The
separate MSI rollback candidate remains unmerged and is outside this change.
