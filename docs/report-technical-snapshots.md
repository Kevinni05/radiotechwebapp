# Technical context captured at report submission

New reports carry a server-owned `technicalSnapshot` with schema version 1. It captures the asset identity, name, equipment identifiers, location and four supported technical specifications; the submitting operator identity; and task title, checklist text and checklist version when the source task supplies them. Only an explicit field whitelist is copied. Credentials and unrelated metadata are excluded. The capture time is the original submission time.

Snapshots supplied by a client are overwritten. An idempotent replay returns the first stored report and snapshot, even after the source asset or task has been edited. Task reports cannot substitute a different antenna, including another antenna belonging to the same tenant.

New integrity hashes use a `v2:` prefix and include the snapshot and display names. Changing these fields, or deleting the snapshot, invalidates the public verification token. Existing reports retain the original hash algorithm and remain verifiable without an invented historical snapshot. These are application integrity checks, not qualified electronic signatures.

Firestore emulator regression tests cover wrong-asset submission without writes, a forged client snapshot, changes to source records, idempotent replay, display-name tampering, snapshot deletion and verification of a legacy report. No production records are rewritten.

Remaining acceptance work: equipment units are not inferred where the source schema does not define them; historical checklist definitions cannot be reconstructed from a boolean result alone. The mobile-generated PDF must be reconciled with the accepted server snapshot and exercised on real Android devices before the full B10 requirement can be closed.
