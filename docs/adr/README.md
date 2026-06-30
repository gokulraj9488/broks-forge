# Architecture Decision Records

This directory captures the significant architectural decisions for Brok's Forge using
lightweight [ADRs](https://adr.github.io/). Each record is immutable once accepted; if a
decision changes, add a new ADR that supersedes the old one rather than editing history.

| ADR | Title | Status |
|-----|-------|--------|
| [0001](0001-modular-monolith.md) | Modular monolith over microservices | Accepted |
| [0002](0002-agent-as-central-entity.md) | Agent as the central platform entity | Accepted |
| [0003](0003-credential-encryption-vs-hashing.md) | Encrypt agent credentials instead of hashing | Accepted |
| [0004](0004-ssrf-protection-for-agent-endpoints.md) | SSRF protection for outbound agent calls | Accepted |

## Format

```
# <number>. <title>
- Status: Proposed | Accepted | Superseded by ADR-XXXX
- Date: YYYY-MM-DD
## Context
## Decision
## Consequences
```
