# Source pagination recovery

Append failure currently leaves page=N but Retry reloads N as a replacement. Page
requests also use editable search text, mixing an unsubmitted draft into an existing
result set. Preserve the submitted query independently of the text field and record
whether the failed operation was append or replacement. Retry that operation and
keep successful accumulated items. A new submitted search resets the result set.

- [ ] Reproduce page-3 failure/retry after two successful pages and query draft edits.
- [ ] Consolidate page loading and preserve request/query/append semantics.
- [ ] Verify connected UI, existing browsing/detail tests and packaged execution.

This addresses T9 source browsing recovery; production-source and full performance
acceptance remain separate.
