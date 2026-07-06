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
| [0005](0005-evaluation-job-as-top-level-aggregate.md) | Evaluation Job as the top-level aggregate | Accepted |
| [0006](0006-provider-agnostic-model-invocation.md) | Provider-agnostic model invocation | Accepted |
| [0007](0007-immutable-versioned-datasets.md) | Immutable, versioned datasets | Accepted |
| [0008](0008-prompt-templating-and-versioning.md) | Prompt templating and versioning | Accepted |
| [0009](0009-reporting-and-export-architecture.md) | Reporting and export architecture | Accepted |
| [0010](0010-observability-and-opentelemetry-readiness.md) | Observability and OpenTelemetry readiness | Accepted |
| [0011](0011-ai-engineering-advisor.md) | AI Engineering Advisor and the on-read recommendation model | Accepted |
| [0012](0012-root-cause-analysis-engine.md) | Root-cause analysis engine | Accepted |
| [0013](0013-engineering-knowledge-graph.md) | Engineering Knowledge Graph | Accepted |
| [0014](0014-ai-debugger-and-tracing-seam.md) | AI Debugger execution timeline and the tracing seam | Accepted |
| [0015](0015-production-observability-metrics-and-structured-logging.md) | Production observability — Prometheus metrics and structured logging | Accepted |
| [0016](0016-pluggable-email-transport.md) | Pluggable e-mail transport (console for dev, SMTP for production) | Accepted |
| [0017](0017-otp-password-change.md) | Email OTP for password change (supersedes the emailed link) | Accepted |
| [0018](0018-provider-aware-health-checks.md) | Provider-aware health checks and credential connection testing | Accepted |
| [0019](0019-layered-automated-testing-and-qa.md) | Layered automated testing strategy and quality assurance | Accepted |

## Format

```
# <number>. <title>
- Status: Proposed | Accepted | Superseded by ADR-XXXX
- Date: YYYY-MM-DD
## Context
## Decision
## Consequences
```
