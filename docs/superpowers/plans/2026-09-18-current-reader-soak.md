# Current packaged reader soak

Revalidate the current 0.2.18 portable build rather than treating a historical 0.2.0
soak or the 256 MiB reader budget as current total-process memory evidence.

- [x] Generate the deterministic seven-asset fixture and run a short actual-EXE preflight.
- [x] Add a reproducible runner that records package identity, isolated data, stderr,
  reader heap/core samples and separate process-tree working-set/private bytes.
- [x] Validate the summary and PNG/SVG plot generation against the completed preflight.
- [ ] Complete 1800 seconds in one packaged runtime, with repeated decoding, mode
  changes, chapter transitions and persistence checks; require empty stderr/exit 0.
- [ ] Inspect five-minute heap and process-memory windows and record the actual
  result without extrapolating to Compose frames, production content or clean Windows.

Current package identity is recorded in `build/reader-soak-0218/full/identity.json`.
The preflight passed; the full run is pending until its real process exits and
`result.json` is written. Do not interpret a sampling file as completion.

After completion run `scripts/summarize-reader-soak.ps1 -OutputDirectory <run>` and
`python scripts/plot-reader-soak.py <run>` (Python with Matplotlib; validated with
Matplotlib 3.10.8). Plotting requires a completed accepted result and labels the
separate sampler clocks and excluded Compose-frame scope.
