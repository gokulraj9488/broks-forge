# Brok's Forge — End-to-End Tests (Playwright)

Browser-level regression tests that drive the **real UI** against a **running stack**. This project
is intentionally **self-contained** — it has its own `package.json` and dependencies and lives
outside `frontend/`, so it never affects the Next.js app build (`npm ci` in `frontend/` is untouched).

## Prerequisites

The full stack must be running and reachable:

```bash
# from the repo root
docker compose up --build   # brings up postgres, redis, backend (:8080), frontend (:3000)
```

## Install & run

```bash
cd e2e
npm install
npm run install:browsers      # one-time: downloads the Chromium binary
npm test                      # run the whole suite headless
npm run test:ui               # interactive UI mode
npm run report                # open the last HTML report
```

Point the tests at a different environment with env vars (or a local `.env`, see `.env.example`):

```bash
BASE_URL=https://staging.broksforge.example API_URL=https://api-staging.broksforge.example npm test
```

## How it's organised

- `playwright.config.ts` — Chromium project, HTML + list reporters, trace/screenshot/video on failure.
- `fixtures/api.ts` — backend API helpers used to **seed** state fast (register a user, create an
  org / project / agent) so each spec focuses on the behaviour it asserts.
- `fixtures/test.ts` — a `signedInUser` fixture (registers via API, logs in via the real UI) and a
  `loginViaUi` helper.
- `tests/*.spec.ts` — one file per feature area.

## Coverage

| File | Flows |
|------|-------|
| `auth.spec.ts` | register, login, wrong credentials, logout, forgot password, reset-password (invalid token), auth redirect |
| `session-timeout.spec.ts` | session-expired banner, protected-route redirect |
| `otp-password-change.spec.ts` | OTP wizard advances to code entry |
| `organizations-projects.spec.ts` | org + project appear after creation |
| `agents.spec.ts` | agent list, register via dialog, "setup required" badge |
| `datasets-prompts.spec.ts` | dataset + prompt appear after creation |
| `evaluations-benchmarks.spec.ts` | evaluations + benchmarks pages load |
| `theme.spec.ts` | dark / light theme |
| `navigation.spec.ts` | sidebar navigation across sections |
| `not-found.spec.ts` | custom 404 |
| `seo-metadata.spec.ts` | title, meta, OG/Twitter, robots.txt, sitemap.xml, manifest |
| `responsive.spec.ts` | mobile viewport + drawer |

## Notes on selectors

Specs prefer **accessible selectors** (`getByRole`, `getByLabel`, `getByText`) which are resilient to
markup changes. A few deep CRUD flows are seeded via the API and then verified in the UI. If the UI
copy changes, adjust the corresponding `getByRole`/`getByText` matcher. For maximum stability over
time, add `data-testid` attributes to the primary action buttons and switch those specs to
`getByTestId`.
