# Broks Forge Platform V2.0 — Local Verification Runbook

Scope: the V2 platform only — the five standalone Maven modules
`backend/kernel`, `backend/forge-knowledge`, `backend/forge-fvcs`, `backend/forge-fkge`,
`backend/forge-fxp`. These are **libraries verified by their test suites** — there is no server,
daemon, port, or database on the default path. (The Spring Boot app under `backend/src/...` is a
separate, pre-existing codebase and is out of scope here.)

Commands work in Git Bash or PowerShell. In **PowerShell, always quote any `-Dtest=…#…`** argument —
an unquoted `#` starts a comment.

---

## 1. Prerequisites
- **JDK 21+** (`java -version` → 21). The modules compile with `--release 21`.
- **Maven 3.9+** (`mvn -v`).
- **Environment variables:** none required.
- **Services / database:** none required — the default kernel is in-memory (`Kernels.inMemory()`).
  Postgres is optional and only used by the durable-store TCK, which is env-gated **off** by default.

## 2. Build (clean, compile, package, install to local repo)
Each module is standalone; build in dependency order so each is installed for the next:
```bash
cd "backend/kernel"          && mvn -o clean install    # reactor: kernel-api, kernel-core, kernel-tck, kernel-store-postgres 1.0.0
cd "../forge-knowledge"      && mvn -o clean install    # 1.0.0
cd "../forge-fvcs"           && mvn -o clean install    # 1.0.0
cd "../forge-fkge"           && mvn -o clean install    # 1.0.0
cd "../forge-fxp"            && mvn -o clean install    # 2.0.0
```
- `-o` = offline (works because dependencies are already cached). **If Maven reports a missing
  artifact/plugin, drop `-o` for that first build** so it can fetch, then use `-o` again.
- Fast path: add `-DskipTests` to each `install`, then verify with the test commands below.

## 3. Run
**There are no services to start** — no startup order, no expected startup log, no local URLs, no
ports. The platform is consumed in-process through `ForgeClient` / `ForgeCli`, exercised by the test
suites. "Running it" = building and running those suites (sections 4–5).
- **REST API / Python SDK / TypeScript SDK:** specified, not implemented in V2 — nothing to launch.
  Verify the equivalent behavior through the conceptual API and CLI tests below.

## 4. Verification (run from the named module directory)
Whole-platform smoke: each `mvn -o clean install` above ends in `BUILD SUCCESS` with
`Tests run: N, Failures: 0, Errors: 0`.

| Target | Command (`cd backend/forge-fxp`) | Proves |
|--------|----------------------------------|--------|
| **Health check** | `mvn -o test "-Dtest=CliTest#validate"` | `forge validate` → chain verify + integrity scan `healthy=true` |
| **Studio** | `mvn -o test "-Dtest=StudioExplorerTest"` | authoring lawful claims/decisions + versioning + confidence |
| **Explorer** | `mvn -o test "-Dtest=StudioExplorerTest#provenance"` | provenance reaches ancestors, certified |
| **Review** | `mvn -o test "-Dtest=ReviewTest"` | commit review (diff+blast radius), decision/claim review, approval fact |
| **Copilot** | `mvn -o test "-Dtest=CopilotTest"` | grounded answers carry proof; refuses when no proof |
| **CLI** | `mvn -o test "-Dtest=CliTest"` | `explain/impact/validate` output + usage on bad command |
| **REST API** | (not built in V2) — behavior verified via `CliTest`/`CopilotTest` over the same conceptual API | — |

Deeper reasoning checks (optional): `cd backend/forge-fkge && mvn -o test` (22 tests: provenance,
impact/duality, dependency, explanation, confidence, causal soundness, search determinism).

## 5. End-to-end testing (the three reference workflows)
```bash
cd "backend/forge-fxp"
mvn -o test "-Dtest=ReferenceWorkflowsTest"                          # all three
mvn -o test "-Dtest=ReferenceWorkflowsTest#w1_changeToExplanation"   # W1: change→version→eval→claim→decide→promote→explain
mvn -o test "-Dtest=ReferenceWorkflowsTest#w2_incidentToRootCause"   # W2: incident→root cause→provenance→reproducible explanation
mvn -o test "-Dtest=ReferenceWorkflowsTest#w3_whyInProduction"       # W3: "why is this in production?" dossier
```

## 6. Expected results
**Should succeed:**
- Every module: `BUILD SUCCESS`. Known green counts this session — forge-fvcs **13**, forge-fkge
  **22**, forge-fxp **19** (incl. the 3 workflows); forge-knowledge and kernel full suites pass
  (kernel's Postgres TCK auto-skips).
- `CliTest#validate` output contains `healthy=true`.
- W1 produces a **complete** decision explanation; W2 root cause is the deployment and `sound=true`;
  W3 dossier has non-empty provenance, a complete decision proof, and defined confidence.

**Should fail (expected, not a regression):**
- Enabling the kernel Postgres TCK env var **without** a reachable Postgres → those tests fail; leave
  it unset to keep them skipped.
- `-o` (offline) on a machine missing a cached dependency → resolution error; rebuild once without `-o`.
- JDK < 21 → `invalid target release: 21`.

**How Copilot refuses (no fabrication):**
- `mvn -o test "-Dtest=CopilotTest#refusesUngroundedWithoutCallingModel"` — asking for evidence of a
  primary artifact returns `grounded=false`, an **empty proof**, a "cannot answer" message, and the
  language model is **never invoked** (the test uses a model that throws if called).
- `mvn -o test "-Dtest=CopilotTest#refusesUnknownSubject"` — an unknown node is refused, not hallucinated.

## 7. Troubleshooting
- **`BUILD FAILURE … Could not resolve … forge-knowledge/forge-fvcs/forge-fkge`** → you built out of
  order; run the section-2 installs top to bottom.
- **`Cannot access central in offline mode` / missing plugin** → drop `-o` for that one build to fetch,
  then resume with `-o`.
- **`invalid target release: 21` / `release version 21 not supported`** → wrong JDK; install/select JDK 21+.
- **Database issues** → none apply on the default path (in-memory). If a kernel test tries Postgres,
  the durable-store TCK env var is set; unset it.
- **Port conflicts** → not applicable; the V2 platform opens no ports and starts no services.
- **Windows `#` in `-Dtest`** → quote the argument: `"-Dtest=Class#method"`.

## 8. Pre-push checklist (all must hold before commit + push)
- [ ] `mvn -o clean install` succeeds for **all five** modules, in order → `BUILD SUCCESS` each.
- [ ] `cd backend/forge-fxp && mvn -o clean test` → **19 tests, 0 failures**, incl. `ReferenceWorkflowsTest` (W1–W3).
- [ ] `cd backend/forge-fkge && mvn -o clean test` → **22 tests, 0 failures**.
- [ ] Public-API-only audit is clean (no frozen internals leaked):
      `cd backend/forge-fxp/src/main/java && grep -rhoE "com\.broksforge\.kernel\.(core\.(memory|store|codec|node|op)|store\.postgres)|\.impl\.|\.internal\." .`
      → prints **nothing**. (Repeat in `backend/forge-fkge/src/main/java`.)
- [ ] No frozen layer modified: `git status --short | grep -v '^??'` → prints **nothing** (only new
      untracked dirs are added).
- [ ] `git status` shows the intended new paths only: `backend/forge-fxp/`, `backend/forge-fkge/`,
      `backend/forge-fvcs/`, `backend/forge-knowledge/`, `backend/kernel/`, `docs/v2/`.
- [ ] Commit attribution matches your standing rule: **sole author Gokulraj, no Claude co-author trailer.**
