# Detail loading cancellation

Online details currently launch requests in BrowseContentView's outer scope. Leaving
the detail destination does not cancel them, and the shared loading screen has no
back action. This can leave a delayed request updating the shared detail selection
after the user has left it.

Keep the shared MangaDetailScreen. Add its existing localized back action to the
loading state, respecting showBack. Bind online load and library-action jobs to the
detail destination, cancel superseded loads, preserve coroutine cancellation and
check active status before publishing callbacks. Cancel explicitly before back
navigation as well as on disposal. Do not turn viewing into library membership.

- [ ] Reproduce inability to leave loading and late result callbacks using the real
  BrowseContentView, source manager and online synchronization service.
- [ ] Cover both metadata/chapter waits, cooperative and late-returning sources,
  navigation to another manga and disposal of the entire browse surface.
- [ ] Implement destination-scoped requests and loading back navigation.
- [ ] Verify existing detail/browse/source regressions, packaged tests and rendering.

This closes a detail-navigation slice of T9; full reader/host-restart and production
source acceptance remain separate requirements.
