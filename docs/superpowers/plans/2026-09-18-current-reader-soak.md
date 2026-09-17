# Current packaged reader soak

Revalidate the current 0.2.18 portable build rather than treating a historical 0.2.0
soak or the 256 MiB reader budget as current total-process memory evidence.

- [x] Generate the deterministic seven-asset fixture and run a short actual-EXE preflight.
- [x] Add a reproducible runner that records package identity, isolated data, stderr,
  reader heap/core samples and separate process-tree working-set/private bytes.
- [x] Validate the summary and PNG/SVG plot generation against the completed preflight.
- [x] Check a single runtime identity and monotonic progress, require sample boundaries
  to match the completed workload, and report maximum reader/process sampling gaps.
  The completed preflight passes; four copied evidence streams with a changed PID,
  final cycle count, backwards time or missing reader process are correctly rejected.
- [x] Complete 1800 seconds in one packaged runtime, with repeated decoding, mode
  changes, chapter transitions and persistence checks; require empty stderr/exit 0.
- [x] Inspect five-minute heap and process-memory windows and record the actual
  result without extrapolating to Compose frames, production content or clean Windows.

The full run completed 1800.684 seconds, 520 cycles and 19,760 decoded tiles with
both processes exiting 0 and empty stderr. The last five-minute memory medians are
higher, so the memory plateau target remains unproven. See the [current evidence](../evidence/2026-09-18-current-reader-soak.md), including archived raw samples.

- [ ] Attribute the higher late-window process memory using separate GC/allocation
  diagnostics, address any demonstrated cause, then repeat memory acceptance.

The first separate 180-second diagnostic completed with GC logging/NMT, unchanged
heap limits and no forced collection: 273 pauses, 162 for humongous allocation,
no Full GC. Its samples support investigating allocation churn and heap resizing;
allocation call sites and the late-window cause remain unresolved. Raw diagnostics
are archived with the current evidence. The shipped Java 17 compact runtime lacks
`jdk.jfr`; any JFR runtime must be kept separate and explicitly identified.

After completion run `scripts/summarize-reader-soak.ps1 -OutputDirectory <run>` and
`python scripts/plot-reader-soak.py <run>` (Python with Matplotlib; validated with
Matplotlib 3.10.8). Plotting requires a completed accepted result and labels the
separate sampler clocks and excluded Compose-frame scope.
