# Source pagination recovery

Append failure currently leaves page=N but Retry reloads N as a replacement. Page
requests also use editable search text, mixing an unsubmitted draft into an existing
result set. Preserve the submitted query independently of the text field and record
whether the failed operation was append or replacement. Retry that operation and
keep successful accumulated items. A new submitted search resets the result set.

- [x] Reproduce page-3 failure/retry after two successful pages and query draft edits.
- [x] Consolidate page loading and preserve request/query/append semantics.
- [x] Verify connected UI, existing browsing/detail tests and packaged execution.

21 source and 21 packaged tests pass; see
[verification evidence](../evidence/2026-09-18-source-pagination-retry.md).

This addresses T9 source browsing recovery; production-source and full performance
acceptance remain separate.
