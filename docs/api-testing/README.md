# API Testing — Postman / Newman

A runnable regression collection for the Brok's Forge REST API. It exercises the core resources with
both happy-path and negative cases, and chains state (token → org → project → agent → credential)
through collection variables so the whole thing runs top-to-bottom unattended.

## Files

| File | Purpose |
|------|---------|
| `broks-forge.postman_collection.json` | The collection (Postman schema v2.1). |
| `broks-forge.postman_environment.json` | Environment with `baseUrl` (defaults to `http://localhost:8080`). |

## Coverage

Ordered so later requests reuse ids captured by earlier ones:

- **Auth** — register (201, captures token) · duplicate register (409) · invalid payload (400) · login (200) · wrong password (401)
- **Organizations** — list without token (401) · create (201) · blank name (400) · get (200) · unknown id (404)
- **Projects** — create (201) · list (200)
- **Agents** — register (201, asserts `credentialConfigured=false`) · invalid URL (400) · get (200) · get without token (401)
- **Credentials** — set (2xx, asserts the secret is never echoed) · list (200, asserts no plaintext secret)
- **Health** — run agent health check (200, asserts a provider-aware `probeStrategy`) · public actuator health (200, `UP`)

Test types represented: **success, unauthorized (401), validation (400), not found (404), duplicate (409)**,
plus secret-confidentiality assertions. Fine-grained role **403 (forbidden)** cases are covered by the
backend `SecurityIntegrationTest` (deny-by-default) and access-guard unit paths.

## Run in Postman

1. Import both JSON files.
2. Select the "Brok's Forge — Local" environment.
3. Ensure the stack is running (`docker compose up`).
4. Run the collection (Runner) — requests execute in order and assertions appear in the Test Results tab.

## Run headless with Newman (CI-friendly)

```bash
npm install -g newman
newman run docs/api-testing/broks-forge.postman_collection.json \
  -e docs/api-testing/broks-forge.postman_environment.json \
  --reporters cli,junit --reporter-junit-export newman-report.xml
```

Newman exits non-zero if any assertion fails, so it drops straight into CI.
