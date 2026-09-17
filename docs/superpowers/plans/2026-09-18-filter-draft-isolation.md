# Source filter draft isolation

The dialog currently edits active mutable filters directly. Cancel and Reset can
therefore affect later pagination without applying a new search, and the mutation
counter is written but never read so checkbox/group rendering can remain stale.

Use the existing IPC filter DTO mapping to create independent editable drafts.
Apply only the selected state back to the source's original objects, preserving
custom filter subclasses. Capture source defaults on initial filter loading; Reset
changes the draft only. Explicit Apply retains the existing page-1 search behavior.

- [ ] Regress edit/cancel, checkbox feedback, apply/pagination and reset/cancel/apply
  through BrowseContentView with a source that requires a custom filter subclass.
- [ ] Isolate dialog drafts and cached defaults while preserving source types.
- [ ] Verify relevant source/filter/browse tests and the rebuilt portable package.
