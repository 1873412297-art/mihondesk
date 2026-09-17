# Application experience improvement

Scope confirmed by the user: all application areas. Keep the existing Material 3 desktop navigation and explicit library membership. Preserve downloaded files, queue recovery, source security, reader controls and profile data.

## Acceptance checklist

- [x] Downloads: actionable localized failures, expandable/copyable diagnostics, search/status filters, accurate counts, batch retry that preserves successful pages and unrelated tasks.
- [x] Settings: download path is drafted and validated before saving; distinguish active and pending paths, explain restart and existing file behavior.
- [x] Library and manga details: check desktop proportions, filter/empty states, keyboard access and loading/error feedback; fix verified gaps.
- [x] Browse, global search and extensions: check loading, empty results, network errors and recovery actions; retain source domain restrictions.
- [x] Reader: check dark/light contrast, loading cancellation and readable actionable failures.
- [x] History and updates: check search/filter/no-results feedback and recovery controls.
- [x] Localization and shared feedback: app-owned copy in English, simplified and traditional Chinese; raw diagnostics only behind details where practical.
- [ ] Verification: focused behavior tests, desktop-size light/dark screenshots, relevant regression suites; build and verify the installed artifact separately from source tests.

## First implementation slice: downloads

1. Add regression cases for the reported TLS/domain/host failures and batch retries before implementation.
2. Preserve a typed failure reason in the queue, with a fallback for existing queues. Translate the explanation and suggested action, keep original diagnostics unchanged.
3. Retry all failed entries in one state update; keep ready pages and paused/completed entries. A no-op retry must not start unrelated work.
4. Add search, counted status filters, clear-filter empty state and a batch retry button. Wrap header actions at smaller desktop widths.
5. Place error text and details above the actions so long diagnostics cannot squeeze buttons. Use theme colors for status contrast.
6. Verify legacy queue compatibility, status/filter semantics and user actions. Keep existing progress animation tests.

## Remaining slices

Audit each listed screen against current code and tests, record findings here, implement concrete gaps and verify behavior. Do not claim the entire scope complete after the downloads slice. Installation and GitHub publication are separate outcomes.

## Implemented findings (2026-09-18)

- Downloads previously put unbounded raw exceptions beside actions, offered no search/status filter and required one-at-a-time retry. The new typed reason survives persistence, recognizes older queue strings, and keeps diagnostics expandable/copyable. Counts distinguish waiting, running, paused, completed and failed. Pause also marks waiting tasks paused so retrying failures does not resume those tasks. Existing page progress animation is retained.
- Download settings wrote partial paths on every keystroke. They now keep a draft, require an explicit save, validate absolute directory and write access on IO, preserve concurrently changed preferences and explain that existing chapters remain in their original folders.
- Library empty states confused filtered results with an empty library. They now describe filters and offer clearing query/category/filters together. Search has a clear action. Native inspection also exposed untranslated source fallback/chapter counts; those now use the existing localized label helpers. Existing responsive cards and the shared detail screen are retained.
- Global search could remain busy with zero sources; late results could overwrite a newer search; leaving search did not cancel work. Search now has a cancellation scope/generation guard, correct initial/no-source states, and Enter submission. Website errors use localized recovery explanations. Source listing pagination stops automatic requests on failure until explicit recovery.
- Reader remote image errors now show a concise translated reason and recovery hint, with separate diagnostics. The error panel fits the window and scrolls. Existing loading cancellation and dark/light reader chrome remain covered by regression tests.
- History has a search-clear action that does not delete records. Update failures show a recovery message with separate original details; cancellation and completed results remain intact.
- Windows notification titles, result summaries and privacy-preserving messages now follow the current application language. Raw exceptions are replaced by a localized reason; originals remain in Downloads. Progress continues to generate zero Windows popups.

Validation and installed-artifact evidence are recorded separately in the experience evidence report. This is an application-wide improvement pass, not a claim that every upstream feature or every remote source is supported.
