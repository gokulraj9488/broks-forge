# V2 Conceptual Architecture Decision Records

This directory records the founding conceptual decisions of Brok's Forge V2 — one ADR per
kernel primitive. These are **conceptual ADRs**: each justifies *why* a primitive exists and
what alternatives were rejected, never how it is implemented. Implementation-level ADRs
continue in the main series at [../../adr/](../../adr/README.md).

Each record is immutable once accepted; a changed decision is a new ADR that supersedes the
old one. The normative context for all of them is [../MANIFESTO.md](../MANIFESTO.md); the
derivation is [../../FORGE_KERNEL.md](../../FORGE_KERNEL.md).

| ADR | Title | Status |
|-----|-------|--------|
| [V2-0001](V2-0001-forge-graph-as-sole-substrate.md) | The Forge Graph as the sole substrate | Accepted |
| [V2-0002](V2-0002-epistemic-typing.md) | Epistemic Typing — the four kernel kinds | Accepted |
| [V2-0003](V2-0003-claim-law.md) | The Claim Law (the Explanation Envelope as physics) | Accepted |
| [V2-0004](V2-0004-decision-model.md) | The Decision as a kernel kind | Accepted |
| [V2-0005](V2-0005-closure-system-snapshot.md) | The Closure — System Snapshot as a derived operation | Accepted |
| [V2-0006](V2-0006-names-as-only-mutable-state.md) | Names as the only mutable state | Accepted |
| [V2-0007](V2-0007-six-operation-kernel.md) | The six-operation kernel | Accepted |
| [V2-0008](V2-0008-log-as-event-bus-subscriptions.md) | The log as the event bus; subscription as the sole autonomy model | Accepted |
| [V2-0009](V2-0009-the-trail.md) | The Trail — investigations as first-class objects | Accepted |
| [V2-0010](V2-0010-generic-component-registry.md) | One generic component registry with typed views | Accepted |

## Format

```
# V2-<number>. <title>
- Status: Proposed | Accepted | Superseded by ADR-V2-XXXX
- Date: YYYY-MM-DD
- Level: Conceptual (no implementation content)
## Context
## Alternatives considered
## Decision
## Consequences
```
