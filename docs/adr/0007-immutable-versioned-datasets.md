# 7. Immutable, versioned datasets

- Status: Accepted
- Date: 2026-07-01

## Context

Evaluations must be **reproducible**. An `EvaluationJob` (see
./0005-evaluation-job-as-top-level-aggregate.md) pins the data it ran against, and that data
**must never change underneath the job** — otherwise a re-run, a regression comparison or an
audit of a historical result is meaningless. Datasets, however, are living things: users add
rows, fix bad examples and grow test suites over time. We need a model that lets data evolve
while guaranteeing that any past evaluation references a fixed, byte-stable snapshot.

## Alternatives considered

- **Mutable datasets.** A single dataset whose items are edited in place. Simplest to use, but
  destroys reproducibility: a job's results can no longer be explained, because the data it ran on
  may no longer exist.
- **Copy-on-eval.** Snapshot the dataset into the job at execution time. Preserves
  reproducibility but duplicates data per *job* (far more copies than per *version*), and gives no
  shareable, comparable snapshot identity across jobs.
- **Event-sourcing the dataset.** Reconstruct any historical state from an append-only event log.
  Maximally flexible and auditable, but heavyweight: every read must fold events, and we do not
  need arbitrary point-in-time reconstruction — only stable, named snapshots.

## Decision

Model datasets as a **named container with immutable versioned snapshots**:

1. **`Dataset`** — the named, mutable *container*. It carries identity and metadata only; it does
   not hold items directly.
2. **`DatasetVersion`** — an **immutable snapshot**. Once created it never changes. It records
   `version_number`, `item_count`, the `schema`, a content `checksum` (integrity and dedup signal)
   and the `source_format` (`CSV`, `JSON`, or `MANUAL`).
3. **`DatasetItem`** — rows that **belong to a specific `DatasetVersion`**, not to the dataset. An
   item is therefore fixed for the life of that version.
4. **Jobs pin a `datasetVersionId`.** An evaluation references the version, never the container,
   so its inputs are frozen.

**New data means a new version**, never an edit to an existing one. Editing the data of a version
is not an operation the system offers.

## Consequences

**Positive**
- **Reproducibility**: re-running a job against the same `datasetVersionId` uses identical data.
- **Auditability**: a historical result can always be traced to the exact, unchanged rows that
  produced it, with a checksum to prove integrity.
- **Regression comparability**: two jobs are comparable precisely because they can pin the *same*
  version, isolating agent changes from data changes.

**Negative / trade-offs**
- **Storage duplication** across versions: a small change to a large dataset produces a full new
  set of `DatasetItem` rows. We accept this as the direct cost of reproducibility and plan to
  reduce it later (see Future impact); the `checksum` already gives us a handle for deduplication.
- Users must understand that "editing" data creates a new version rather than mutating the old
  one.

## Future impact

- **Dataset-vs-dataset benchmarking**: comparing an agent across two pinned dataset versions.
- **Deduplication and columnar/content-addressed storage** to remove the duplication cost, keyed
  off the per-version `checksum`, without changing the immutability contract.
