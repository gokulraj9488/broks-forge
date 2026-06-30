<!--
  Thanks for contributing to Brok's Forge!
  Please fill in the sections below and tick the checklists that apply.
  Keep the PR focused; large unrelated changes are harder to review and merge.
-->

## Summary

<!-- What does this PR do, and why? Link the issue it closes, e.g. "Closes #123". -->

## Type of change

<!-- Tick all that apply. -->

- [ ] `feat` — new feature
- [ ] `fix` — bug fix
- [ ] `docs` — documentation only
- [ ] `refactor` — no behavior change
- [ ] `perf` — performance improvement
- [ ] `test` — tests only
- [ ] `build` / `ci` — build system or CI
- [ ] `chore` — tooling / maintenance
- [ ] Breaking change (requires a version bump and a `CHANGELOG.md` entry)

## How has this been tested?

<!-- Describe the tests you added/ran. Reference the relevant TESTING_STRATEGY.md section. -->

---

## Definition-of-done checklist

- [ ] **Conventional Commits** — commits follow `type(scope): summary`.
- [ ] **Tests added/updated** — new behavior is covered; existing tests pass (`mvn -B -ntp -f backend/pom.xml verify` and/or `npm run lint && npx tsc --noEmit && npm run build`).
- [ ] **Schema parity** — the app boots under `ddl-auto=validate`; every new entity matches its migration.
- [ ] **Migrations are append-only** — no released migration (`V1..Vn`) was edited, renumbered, or deleted; new changes use the next number.
- [ ] **Docs updated** — `docs/ROADMAP.md` / `docs/FEATURE_DECISIONS.md` and module docs updated; a new ADR added for any architectural/security decision.
- [ ] **No regressions** — no existing feature was removed, simplified, or weakened.

## Security checklist (per `docs/SECURITY_GUIDE.md` §14)

<!-- Required for any change that touches an endpoint, service, query, or outbound call. -->

- [ ] **AuthN** — `@PreAuthorize("isAuthenticated()")` present; no anonymous business access.
- [ ] **AuthZ** — service-layer `requireMembership` / `requireRole` with the **least** sufficient role.
- [ ] **Tenant isolation** — entities loaded via the `(id, projectId, organizationId)` tuple; foreign id → 404 (no load-by-id-then-check).
- [ ] **Query scoping** — every list/aggregate/search query includes `organizationId` (and usually `projectId`).
- [ ] **Validation & mass-assignment** — request DTOs are validated records that omit server-set fields (`ownerId`/`organizationId`/`status`/ids/timestamps).
- [ ] **Injection** — only parameter binding / Specifications; no string-concatenated SQL; template vars treated as data.
- [ ] **Secrets** — no secret stored in plaintext, logged, or returned; responses carry masked hints + metadata only.
- [ ] **Outbound (if any)** — call routed through `OutboundUrlGuard`; credentials decrypted only for the call; sane timeouts.
- [ ] **Export (if any)** — CSV cells defused (formula injection); HTML contextually escaped (XSS); CSP set.
- [ ] **Errors** — mapped to a stable `ErrorCode`; no stack trace / internal detail; no existence leak.
- [ ] **No secrets committed** — no keys, tokens, or `.env` values in the diff.
