# REST API

Everything the web application does is available over REST. There is no private API.

**Interactive reference:** `http://localhost:8080/swagger-ui.html` — generated from the running
service, so it is always current. This page covers conventions and the endpoint map.

## Conventions

- **Base path** `/api/v1` — versioned, and a breaking change means a new version.
- **JSON** in and out, UTF-8.
- **Resource-oriented, plural nouns**: `/agents`, `/prompts`, `/evaluation-jobs`.
- **Tenancy in the path**, never in a header:
  `/api/v1/organizations/{organizationId}/projects/{projectId}/...`
- **UUIDs** for every identifier.
- **ISO-8601 UTC** for every timestamp.

## Authentication

Two mechanisms.

**JWT** — for the web app and interactive use.

```bash
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"you@example.com","password":"..."}'
# → { "accessToken": "...", "refreshToken": "..." }

curl http://localhost:8080/api/v1/organizations \
  -H 'Authorization: Bearer <accessToken>'
```

**API keys** — for CI and automation. Create one under a project, and send it as configured. Keys
are hashed at rest and shown once at creation.

## Authorization and tenancy

Membership is checked before anything is read, and the owning module re-scopes the entity when it
loads it — two independent checks.

A resource in an organization you do not belong to returns **404, not 403**. The existence of a
resource is itself information.

Roles: `OWNER` · `ADMIN` · `MEMBER` · `VIEWER`.

## Pagination

List endpoints accept `page` (0-based), `size` and `sort`:

```
GET /api/v1/organizations/{org}/projects/{proj}/agents?page=0&size=20&sort=createdAt,desc
```

Responses carry `content`, `page`, `size`, `totalElements`, `totalPages`.

## Errors

A consistent problem shape:

```json
{
  "timestamp": "2026-08-04T09:14:22Z",
  "status": 404,
  "error": "Not Found",
  "message": "Evaluation 3f2a… was not found",
  "path": "/api/v1/organizations/…/investigations/evaluation/3f2a…"
}
```

| Status | Meaning |
| --- | --- |
| 400 | Validation failed |
| 401 | Missing or invalid credentials |
| 403 | Authenticated, not permitted |
| 404 | Not found, **or** not yours |
| 409 | Conflict — duplicate slug, concurrent modification |
| 429 | Rate limited |
| 500 | Unexpected server error |

## Endpoint map

### Identity

```
POST   /api/v1/auth/register
POST   /api/v1/auth/login
POST   /api/v1/auth/refresh
GET    /api/v1/users/me
GET    /api/v1/organizations
POST   /api/v1/organizations
GET    /api/v1/organizations/{org}/members
GET    /api/v1/organizations/{org}/projects
POST   /api/v1/organizations/{org}/projects
```

### Artifacts

Under `/api/v1/organizations/{org}/projects/{proj}`:

```
GET|POST   /agents                       /agents/{id}/versions
GET|POST   /prompts                      /prompts/{id}/versions
GET|POST   /datasets                     /datasets/{id}/versions
GET|POST   /providers
           /agents/{id}/credentials
GET|POST   /api-keys
```

### Evaluation

```
GET|POST   /evaluation-jobs
GET        /evaluation-jobs/{id}
GET        /evaluation-jobs/{id}/runs
GET|POST   /evaluation-profiles
GET|POST   /benchmarks
GET|POST   /regression-checks
GET        /analytics
GET        /dashboard
GET        /root-cause/jobs/{jobId}
GET        /root-cause/regressions/{checkId}
GET        /advisor
GET        /debugger
GET        /reports
GET        /search
```

### Platform V2 — the engineering record

Under `/api/v1/organizations/{org}/platform`:

```
GET  /health                              platform status
GET  /registry                            the artifact + knowledge catalog
GET  /registry/types                      available artifact types
GET  /knowledge                           derived knowledge, paginated
GET  /graph                               the Forge Graph
GET  /graph?include=knowledge             with the reasoning overlay
GET  /intelligence/{type}/{entityId}      observations, claims, decisions, evidence, knowledge, memory
GET  /evolution/{type}/{entityId}         lineage, dependents, impact
GET  /revisions/{type}/{entityId}         the AI Git timeline
```

### Brok

Under `/api/v1/organizations/{org}/brok`:

```
POST /ask                 { question, projectId?, focus?, history[] }
GET  /suggestions         questions worth asking now
GET  /context             the resolved engineering context
GET  /briefs              the eight briefs and their availability
GET  /brief/{kind}        one brief
```

`POST /ask` is a POST despite changing nothing: the question, its focus and its conversation history
are a body, not a URL.

```bash
curl -X POST "http://localhost:8080/api/v1/organizations/$ORG/brok/ask" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{
        "question": "Has this happened before?",
        "projectId": "'"$PROJECT"'",
        "focus": "evaluation:'"$EVAL"'",
        "history": []
      }'
```

### Root Cause Explorer

```
GET /api/v1/organizations/{org}/investigations/evaluation/{evaluationId}?projectId={proj}
```

`projectId` is optional — it is resolved from the evaluation when omitted.

## Response shapes worth knowing

**A Brok answer** carries `verdict`, `reasoning[]` (each step with `status` and `basis`), `impact`,
`evidence[]`, `references{artifacts,knowledge,decisions,evaluations,revisions}`,
`recommendations[]` (each with an `action`), `memory[]`, `followUps[]` and `context`.

**An investigation** carries `subject`, `verdict`, `timeline[]`, `causes[]` (each with a `layer`),
`story[]` (the eight questions), `impact`,
`references{artifacts,evidence,knowledge,decisions,revisions,precedents,relatedEvaluations}`,
`memory[]`, `recommendations[]`, `followUps[]` and `context`.

Both use the **same vocabulary** — verdict states, epistemic statuses, the confidence ladder and the
action catalogue — so one client renders both.

## Actions

Recommendations and causes carry an `action` naming a surface that already exists. Clients resolve
it to a route; the API never returns URLs.

`openGraph` · `openExecutionGraph` · `openFailureGraph` · `openEvaluation` · `openIntelligence` ·
`openEvolution` · `openRevisions` · `compareRevisions` · `openKnowledge` · `openRegistry` ·
`openAnalytics` · `openInsights` · `startInvestigation`

See also: [API Guidelines](/docs/api-guidelines) · [Data Model](/docs/data-model) ·
[Extension Points](/docs/extension-points)
