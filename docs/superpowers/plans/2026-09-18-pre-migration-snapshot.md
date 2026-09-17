# Database upgrade recovery snapshots

Baseline: main `24975595d`, desktop version 0.2.18. The previous iteration completed
restore progress and cooperative cancellation. T8/T11 still require upgrade recovery.

## Decision

Before an existing database changes schema, produce a standalone SQLite snapshot in
its sibling `migration-backups` directory. Use SQLite `VACUUM INTO`, not a raw copy of
the main file, so committed WAL content is included. Validate integrity and old schema
version, flush the file, then publish it with an atomic rename. Any failure prevents
migration. Keep successful snapshots even if migration later fails; do not overwrite
or automatically delete recovery material. Fresh/current/future/invalid schema opens
must not create snapshots. Existing migration transactions remain responsible for
automatic rollback. Application startup already owns the profile lock.

This protects database schema upgrades, not all application-version changes or every
profile file. Do not claim an installer rollback or full profile snapshot from it.
Database restore instructions must close the application and preserve the current
database and its journal sidecars before using a snapshot.

## Implementation and validation

- [ ] Regression: preserved v1 rows and WAL changes, old schema in standalone snapshot.
- [ ] Regression: snapshot destination/write failure prevents any schema changes.
- [ ] Snapshot creation, validation, durable atomic publication and typed failure.
- [ ] Migration failure retains snapshot; fresh/current/rejected schemas avoid copies.
- [ ] Verify restored snapshot can migrate and preserve all business data.
- [ ] Source regression, packaged-class verification and actual EXE upgrade fixture.
- [ ] Record evidence and merge verified changes into local main.

Reference: https://www.sqlite.org/lang_vacuum.html, section 2.1 (consistent snapshots,
output publication risks on interruption, SQL filename expressions, active-transaction
restrictions). Snapshot content is logical SQLite state; it is not byte-identical to
the input file, and this does not claim durability under faulty storage hardware.
