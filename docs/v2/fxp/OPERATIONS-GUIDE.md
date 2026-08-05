# Forge Experience Platform — Operations Guide

**Deliverable 13.** Running FXP in production. Operations are simple because the platform is an
append-only, hash-chained, content-addressed log: there is exactly one source of truth, and every
answer is reproducible.

## Health & integrity
- `forge validate` (or `GET /health`) runs the kernel's chain verification + integrity scan and reports
  `healthy=true/false`. Wire it to your liveness/readiness probes and an alert.
- A `chainValid=false` is a **sev-1**: it means the tamper-evident hash chain broke — treat the store as
  compromised and restore from backup; do not accept writes until green.

## Observability
- **Notifications/audit:** subscribe to the log (`GET /events`, kernel `subscribe`) for operational
  signals — e.g. every `Deployment` targeting `prod`. A missed event is recovered by re-reading from a
  `LogPosition`; the log *is* the event stream, so nothing is lost.
- **Metrics:** per-experience request rate/latency at the gateway; fold time and log size for the read
  path (the O(n) fold is the primary latency driver — see Capacity).
- **Every answer is self-describing:** results carry `asOf`; attach it to logs/traces so any support
  case is reproducible by replaying the exact prefix.

## Capacity & performance
- The read path folds the org log per operation (O(n)). Enable the `(query, asOf)` cache (safe by
  construction) to serve hot answers in O(1); size the cache to the working set of recently-queried
  subjects.
- When fold latency exceeds SLO at scale, that is the signal to adopt the platform's indexed-read
  amendment (the known FKGE enumeration gap) — an amendment, not an app change. Track log size as the
  leading indicator.

## Backup & recovery
- Back up the Postgres kernel store; the append-only log makes point-in-time restore exact. There is no
  app-tier state to back up.
- To reproduce any historical answer: query with `?asOf=<position>` — no restore needed for read-time
  time travel.

## Incident runbook (using the platform on itself)
1. Record the incident: `Studio.recordObservation(INCIDENT, …)` and link the suspected cause
   (`caused`/`triggered`).
2. `forge root-cause <incident>` → the causal chain (log-position-sound; anomalies flagged).
3. `forge provenance <cause>` and `forge impact <cause>` → what it rests on and what else it endangers.
4. `forge explain <decision>` → why the change shipped; `forge confidence <decision>` → how strong the
   evidence was.
5. Capture the `asOf` — the whole investigation is reproducible for the postmortem.

## Change management
- Every deployment is a `Deployment` decision resting on evaluation claims (or an explicit
  judgment-call); every approval is an `Approval` (`approves`/`rejects`). Your change record *is* the
  graph — no separate CAB spreadsheet.
- Promotions/rollbacks are `Promotion`/`Rollback` decisions; the history is the audit trail.

## Security operations
- Rotate gateway tokens; the principal is the kernel actor on every write, so the log is a complete,
  attributed audit trail.
- Copilot never sends the graph to the model (only proofs) — no additional data-egress control is needed
  for the reasoning path.
