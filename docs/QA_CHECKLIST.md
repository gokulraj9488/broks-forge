# Brok's Forge — Manual QA Checklist

A professional, release-oriented manual test plan. Work top to bottom before a release, or run the
relevant section after a targeted change. Each case is independent and written so anyone on the team
can execute it without tribal knowledge.

## How to use

- Duplicate this file per release (e.g. `QA_CHECKLIST_v1.1.0.md`) and fill it in, or track results in a sheet.
- Set the **Status** cell to exactly one outcome.
- Record what you actually saw in **Actual**; add environment/repro details in **Notes**.

**Status legend** — mark one per row: `✅ PASS` · `❌ FAIL` · `⛔ BLOCKED` · `⬜ NOT TESTED`.
Every row ships as `⬜ NOT TESTED`.

**Environment under test:** `docker compose up --build` → frontend `http://localhost:3000`, backend
`http://localhost:8080`, Swagger `http://localhost:8080/swagger-ui.html`.

---

## 1. Registration

| ID | Scenario | Steps | Expected Result | Actual | Status | Notes |
|----|----------|-------|-----------------|--------|--------|-------|
| REG-001 | Register a new account | Go to `/register` → fill first/last/email/valid password → Create account | Account created, redirected to `/dashboard`, success toast | | ⬜ NOT TESTED | |
| REG-002 | Password strength meter | Type increasingly strong passwords | Meter updates weak→strong live | | ⬜ NOT TESTED | |
| REG-003 | Weak password rejected | Enter `weak` as password → submit | Inline validation error; no account created | | ⬜ NOT TESTED | |
| REG-004 | Invalid email rejected | Enter `not-an-email` → submit | Inline "valid email" error | | ⬜ NOT TESTED | |
| REG-005 | Duplicate email | Register with an already-registered email | 409 error toast; no duplicate account | | ⬜ NOT TESTED | |
| REG-006 | Caps-lock warning | Focus password, enable Caps Lock | Caps-lock hint shown | | ⬜ NOT TESTED | |
| REG-007 | Show/hide password | Click the eye toggle | Password text toggles visibility | | ⬜ NOT TESTED | |

## 2. Email Verification

| ID | Scenario | Steps | Expected Result | Actual | Status | Notes |
|----|----------|-------|-----------------|--------|--------|-------|
| EMV-001 | Verification email sent on register | Register → check backend logs (dev LoggingEmailService) | A verification link is logged | | ⬜ NOT TESTED | |
| EMV-002 | Verify with valid token | POST `/api/v1/auth/verify-email` with the token | 200; email marked verified | | ⬜ NOT TESTED | |
| EMV-003 | Verify with invalid/expired token | Use a garbage token | 401 invalid/expired | | ⬜ NOT TESTED | |
| EMV-004 | Resend verification | POST `/api/v1/auth/resend-verification` | Always 200 (no account enumeration) | | ⬜ NOT TESTED | |

## 3. Forgot Password

| ID | Scenario | Steps | Expected Result | Actual | Status | Notes |
|----|----------|-------|-----------------|--------|--------|-------|
| FGP-001 | Request reset for existing email | `/forgot-password` → submit registered email | Generic "if an account exists…" message; reset link logged | | ⬜ NOT TESTED | |
| FGP-002 | Request reset for unknown email | Submit an unregistered email | Same generic message (no enumeration) | | ⬜ NOT TESTED | |
| FGP-003 | Contact Developer link present | View the page footer | "Need help? Contact Developer" → https://gokul.quest (new tab) | | ⬜ NOT TESTED | |

## 4. Password Reset

| ID | Scenario | Steps | Expected Result | Actual | Status | Notes |
|----|----------|-------|-----------------|--------|--------|-------|
| RSP-001 | Reset with valid token | Open `/reset-password?token=…` → set a strong new password | Success; can log in with the new password | | ⬜ NOT TESTED | |
| RSP-002 | Reset with invalid token | Use a garbage token | Error (invalid/expired) | | ⬜ NOT TESTED | |
| RSP-003 | Old sessions revoked | Reset password, then reuse an old refresh token | Old token rejected | | ⬜ NOT TESTED | |

## 5. OTP Verification (Password Change — ADR 0017)

| ID | Scenario | Steps | Expected Result | Actual | Status | Notes |
|----|----------|-------|-----------------|--------|--------|-------|
| OTP-001 | Request code | Settings → enter current password → request code | 200; 6-digit code emailed (logged in dev); wizard moves to code step | | ⬜ NOT TESTED | |
| OTP-002 | Wrong current password | Enter an incorrect current password | 401; no code sent | | ⬜ NOT TESTED | |
| OTP-003 | Verify correct code | Enter the emailed code | 200; single-use ticket issued; wizard moves to new-password step | | ⬜ NOT TESTED | |
| OTP-004 | Verify wrong code | Enter an incorrect code | 400; attempts counter increments | | ⬜ NOT TESTED | |
| OTP-005 | Attempt lockout | Enter wrong code 5× | 429 locked; must request a new code | | ⬜ NOT TESTED | |
| OTP-006 | Code expiry | Wait > 5 minutes, then verify | Code rejected as expired | | ⬜ NOT TESTED | |
| OTP-007 | Generation rate limit | Request codes repeatedly | Throttled after the limit (429) | | ⬜ NOT TESTED | |

## 6. Password Change (completion)

| ID | Scenario | Steps | Expected Result | Actual | Status | Notes |
|----|----------|-------|-----------------|--------|--------|-------|
| PWC-001 | Complete change with ticket | Enter a strong new password with a valid ticket | 200; all sessions revoked; must sign in again | | ⬜ NOT TESTED | |
| PWC-002 | Ticket single-use | Reuse a consumed ticket | Rejected | | ⬜ NOT TESTED | |
| PWC-003 | Weak new password | Submit a weak new password | Validation error | | ⬜ NOT TESTED | |
| PWC-004 | Confirmation email | Complete a change | "Password changed" email logged/sent | | ⬜ NOT TESTED | |

## 7. Idle Timeout (FIX 3)

| ID | Scenario | Steps | Expected Result | Actual | Status | Notes |
|----|----------|-------|-----------------|--------|--------|-------|
| IDLE-001 | Activity resets timer | Interact (mouse/keys/scroll) periodically | No logout while active | | ⬜ NOT TESTED | |
| IDLE-002 | Warning modal before logout | Stay idle until ~60s before timeout | "Your session is about to expire" modal with countdown | | ⬜ NOT TESTED | |
| IDLE-003 | Stay signed in | Click "Stay signed in" in the modal | Modal closes; timer resets; stays logged in | | ⬜ NOT TESTED | |
| IDLE-004 | Auto logout | Stay fully idle past the timeout | Logged out → `/login?reason=session-expired` with banner | | ⬜ NOT TESTED | |

## 8. Login & Logout

| ID | Scenario | Steps | Expected Result | Actual | Status | Notes |
|----|----------|-------|-----------------|--------|--------|-------|
| LOGIN-001 | Valid login | Enter valid credentials | Redirect to `/dashboard`; welcome toast | | ⬜ NOT TESTED | |
| LOGIN-002 | Wrong password | Enter a wrong password | Stays on `/login`; error toast; no token | | ⬜ NOT TESTED | |
| LOGIN-003 | Unknown email | Log in with an unregistered email | 401 generic error | | ⬜ NOT TESTED | |
| LOGIN-004 | Session-expired banner | Visit `/login?reason=session-expired` | Inactivity banner shown | | ⬜ NOT TESTED | |
| LOGOUT-001 | Logout | Avatar menu → Log out | Redirect to `/login`; token cleared; back button cannot re-enter | | ⬜ NOT TESTED | |

## 9. Refresh Token

| ID | Scenario | Steps | Expected Result | Actual | Status | Notes |
|----|----------|-------|-----------------|--------|--------|-------|
| REF-001 | Access token refresh | Let the access token expire; make a request | Refresh rotates the pair; request succeeds | | ⬜ NOT TESTED | |
| REF-002 | Rotation invalidates old | Reuse a rotated refresh token | Rejected (401) | | ⬜ NOT TESTED | |
| REF-003 | Logout revokes refresh | Log out, then use the refresh token | Rejected | | ⬜ NOT TESTED | |

## 10. Organizations

| ID | Scenario | Steps | Expected Result | Actual | Status | Notes |
|----|----------|-------|-----------------|--------|--------|-------|
| ORG-001 | Create organization | Create with a valid name | 201; appears in list; creator is OWNER | | ⬜ NOT TESTED | |
| ORG-002 | Blank name rejected | Submit an empty name | 400 validation | | ⬜ NOT TESTED | |
| ORG-003 | Slug uniqueness (live) | Create two orgs with the same name | Second slug auto-suffixed; no error | | ⬜ NOT TESTED | |
| ORG-004 | Reuse slug after delete | Delete an org, recreate with the same name | Succeeds (partial-unique index, V30) | | ⬜ NOT TESTED | |
| ORG-005 | Cross-tenant isolation | Access another user's org id | 404 (not leaked as 403) | | ⬜ NOT TESTED | |
| ORG-006 | Members & roles | Add a member; change role | Role updates; permissions reflect | | ⬜ NOT TESTED | |

## 11. Projects

| ID | Scenario | Steps | Expected Result | Actual | Status | Notes |
|----|----------|-------|-----------------|--------|--------|-------|
| PROJ-001 | Create project | Create under an org | 201; appears under the org | | ⬜ NOT TESTED | |
| PROJ-002 | Unique slug per org | Two projects, same name | Second auto-suffixed | | ⬜ NOT TESTED | |
| PROJ-003 | Reuse slug after delete | Delete then recreate same name | Succeeds (V30) | | ⬜ NOT TESTED | |
| PROJ-004 | Archive / restore | Archive a project, then restore | Status toggles; lists filter correctly | | ⬜ NOT TESTED | |

## 12. Agents

| ID | Scenario | Steps | Expected Result | Actual | Status | Notes |
|----|----------|-------|-----------------|--------|--------|-------|
| AGENT-001 | Register (Custom REST / Python / API Key) | Register with the Groq endpoint scenario | 201; redirected to onboarding on the Credentials tab | | ⬜ NOT TESTED | |
| AGENT-002 | Register (auth = None) | Register a NONE-auth agent | 201; no onboarding; usable immediately | | ⬜ NOT TESTED | |
| AGENT-003 | Reuse slug after delete | Register "X", delete it, register "X" again | Succeeds (V30 fix; previously 409 "data constraint") | | ⬜ NOT TESTED | |
| AGENT-004 | Live duplicate name | Register "X" twice without deleting | Second slug auto-suffixed `-2` | | ⬜ NOT TESTED | |
| AGENT-005 | All frameworks | Register one agent per framework (Spring AI…Custom REST, Other) | All persist | | ⬜ NOT TESTED | |
| AGENT-006 | All languages | Register per language (Java…Rust, Other) | All persist | | ⬜ NOT TESTED | |
| AGENT-007 | All capabilities | Toggle each capability combo | Persist and display correctly | | ⬜ NOT TESTED | |
| AGENT-008 | Setup-required badge | View an auth agent without a credential in `/agents` | "Setup required" badge shown | | ⬜ NOT TESTED | |
| AGENT-009 | Edit agent | Change name/description/visibility | Saved; reflected in list | | ⬜ NOT TESTED | |
| AGENT-010 | Delete agent | Soft-delete an agent | Removed from lists; slug freed | | ⬜ NOT TESTED | |

## 13. Credentials (FIX 1)

| ID | Scenario | Steps | Expected Result | Actual | Status | Notes |
|----|----------|-------|-----------------|--------|--------|-------|
| CRED-001 | Set API-Key credential | Configure name/header/prefix/secret | Saved; credential status shows configured | | ⬜ NOT TESTED | |
| CRED-002 | Secret never returned | Inspect the credentials API response | Only masked hint; no plaintext/ciphertext | | ⬜ NOT TESTED | |
| CRED-003 | Test connection (valid key) | Configure a valid Groq key → Test Connection | Success (probes `GET …/openai/v1/models`) | | ⬜ NOT TESTED | |
| CRED-004 | Test connection (bad key) | Use an invalid key → Test Connection | "Authentication rejected (HTTP 401)" | | ⬜ NOT TESTED | |
| CRED-005 | Test before save | Test a draft credential before saving | Dry-run result without persisting | | ⬜ NOT TESTED | |
| CRED-006 | Update (keep secret) | Edit label, leave secret blank | Existing secret retained | | ⬜ NOT TESTED | |
| CRED-007 | Replace secret | Save a new secret | Rotates; hint updates; test result cleared | | ⬜ NOT TESTED | |
| CRED-008 | Delete credential | Delete the active credential | Removed; agent shows "setup required" again | | ⬜ NOT TESTED | |
| CRED-009 | Auth types | Configure API Key / Bearer / Basic / Custom Header | Each saves and builds the correct header | | ⬜ NOT TESTED | |
| CRED-010 | Non-admin restricted | View credentials as a MEMBER | Restricted (admin-only) | | ⬜ NOT TESTED | |

## 14. Health Checks (FIX 9 / provider-aware)

| ID | Scenario | Steps | Expected Result | Actual | Status | Notes |
|----|----------|-------|-----------------|--------|--------|-------|
| HEALTH-001 | Groq agent probe | Run check on a Groq chat/completions agent | Probes `GET …/models` (GET_MODELS); not 404 | | ⬜ NOT TESTED | |
| HEALTH-002 | Spring AI agent | Run check on a Spring AI agent | Probes `/actuator/health` | | ⬜ NOT TESTED | |
| HEALTH-003 | FastAPI/LangGraph agent | Run check | Probes `/health` | | ⬜ NOT TESTED | |
| HEALTH-004 | Healthy status | Probe returns 2xx | Status HEALTHY; latency shown | | ⬜ NOT TESTED | |
| HEALTH-005 | Unhealthy status | Probe returns 5xx / unreachable | Status UNHEALTHY; failure reason shown | | ⬜ NOT TESTED | |
| HEALTH-006 | History + availability | Run several checks | Availability % + recent history correct | | ⬜ NOT TESTED | |
| HEALTH-007 | No probe during registration | Register an agent | No health check runs at registration time | | ⬜ NOT TESTED | |
| HEALTH-008 | Probe strategy recorded | Inspect a check | probeStrategy + probeUrl stored & displayed | | ⬜ NOT TESTED | |

## 15. Versions

| ID | Scenario | Steps | Expected Result | Actual | Status | Notes |
|----|----------|-------|-----------------|--------|--------|-------|
| VER-001 | Register version | Add a version (model/provider/env) | Created; listed | | ⬜ NOT TESTED | |
| VER-002 | Activate version | Activate a version | Becomes current active | | ⬜ NOT TESTED | |
| VER-003 | Rollback | Roll back to a prior version | Active pointer moves; no data lost | | ⬜ NOT TESTED | |

## 16. Advisor

| ID | Scenario | Steps | Expected Result | Actual | Status | Notes |
|----|----------|-------|-----------------|--------|--------|-------|
| ADV-001 | Agent advisor gated | Open Advisor for an agent lacking credentials | Gated with "configure credentials" prompt | | ⬜ NOT TESTED | |
| ADV-002 | Advisory report renders | Open Advisor for a ready agent | Recommendations render | | ⬜ NOT TESTED | |
| ADV-003 | Project advisory | Open project-level advisor | Aggregated recommendations | | ⬜ NOT TESTED | |

## 17. Datasets

| ID | Scenario | Steps | Expected Result | Actual | Status | Notes |
|----|----------|-------|-----------------|--------|--------|-------|
| DS-001 | Create dataset | Create with a name | 201; appears in `/datasets` | | ⬜ NOT TESTED | |
| DS-002 | Upload version (CSV/JSON) | Add a version with content | Items parsed; item count updates | | ⬜ NOT TESTED | |
| DS-003 | Immutable versions | Attempt to edit an existing version | Not allowed (new version instead) | | ⬜ NOT TESTED | |
| DS-004 | Reuse slug after delete | Delete then recreate same name | Succeeds (V30) | | ⬜ NOT TESTED | |
| DS-005 | Stats | View dataset stats | Coverage/averages correct | | ⬜ NOT TESTED | |

## 18. Prompts

| ID | Scenario | Steps | Expected Result | Actual | Status | Notes |
|----|----------|-------|-----------------|--------|--------|-------|
| PROMPT-001 | Create prompt | Create with a name | 201; listed | | ⬜ NOT TESTED | |
| PROMPT-002 | Version + activate | Add a version, activate it | One active version at a time | | ⬜ NOT TESTED | |
| PROMPT-003 | Templating variables | Use `{{variables}}` | Variables detected/rendered | | ⬜ NOT TESTED | |
| PROMPT-004 | Reuse slug after delete | Delete then recreate | Succeeds (V30) | | ⬜ NOT TESTED | |

## 19. Evaluations (FIX 2)

| ID | Scenario | Steps | Expected Result | Actual | Status | Notes |
|----|----------|-------|-----------------|--------|--------|-------|
| EVAL-001 | Prerequisites empty states | Open "New evaluation" with no data | Guided empty states (create dataset/prompt/agent) — no dead end | | ⬜ NOT TESTED | |
| EVAL-002 | Run when ready | Create a job with agent + dataset | Job created; runs (or PENDING if autoRun off) | | ⬜ NOT TESTED | |
| EVAL-003 | Gate on unusable agent | Select an agent lacking credentials | "Create evaluation" disabled + warning | | ⬜ NOT TESTED | |
| EVAL-004 | Results view | Open a completed job | Runs/results, metrics, pass rate shown | | ⬜ NOT TESTED | |
| EVAL-005 | Profile scoring | Attach a scoring profile | Metrics applied per profile | | ⬜ NOT TESTED | |

## 20. Benchmarks

| ID | Scenario | Steps | Expected Result | Actual | Status | Notes |
|----|----------|-------|-----------------|--------|--------|-------|
| BENCH-001 | Create benchmark | Pick ≥2 completed evaluations | 201; leaderboard built | | ⬜ NOT TESTED | |
| BENCH-002 | Fewer than 2 selected | Select one evaluation | Validation error | | ⬜ NOT TESTED | |
| BENCH-003 | Leaderboard ranking | Open a benchmark | Ranked by chosen metric | | ⬜ NOT TESTED | |
| BENCH-004 | Regression check | Create a regression check | Baseline vs candidate compared | | ⬜ NOT TESTED | |

## 21. Analytics

| ID | Scenario | Steps | Expected Result | Actual | Status | Notes |
|----|----------|-------|-----------------|--------|--------|-------|
| ANALYTICS-001 | Page loads | Open `/analytics` | Charts/metrics render without error | | ⬜ NOT TESTED | |
| ANALYTICS-002 | Empty state | New project with no data | Sensible empty state | | ⬜ NOT TESTED | |

## 22. Knowledge

| ID | Scenario | Steps | Expected Result | Actual | Status | Notes |
|----|----------|-------|-----------------|--------|--------|-------|
| KNOW-001 | Page loads | Open `/knowledge` | Graph/nodes render | | ⬜ NOT TESTED | |
| KNOW-002 | Node relationships | Inspect a node | Edges/related nodes shown | | ⬜ NOT TESTED | |

## 23. Insights

| ID | Scenario | Steps | Expected Result | Actual | Status | Notes |
|----|----------|-------|-----------------|--------|--------|-------|
| INS-001 | Page loads | Open `/insights` | Insights render without error | | ⬜ NOT TESTED | |

## 24. Settings

| ID | Scenario | Steps | Expected Result | Actual | Status | Notes |
|----|----------|-------|-----------------|--------|--------|-------|
| SET-001 | Profile update | Change first/last name | Saved; reflected in the avatar menu | | ⬜ NOT TESTED | |
| SET-002 | Password change card | Open Settings | OTP wizard present and functional | | ⬜ NOT TESTED | |
| SET-003 | Help page | Open `/help` | Renders; Contact Developer link works | | ⬜ NOT TESTED | |

## 25. Accessibility

| ID | Scenario | Steps | Expected Result | Actual | Status | Notes |
|----|----------|-------|-----------------|--------|--------|-------|
| A11Y-001 | Keyboard navigation | Tab through forms and menus | Logical focus order; visible focus ring | | ⬜ NOT TESTED | |
| A11Y-002 | Labels | Inspect form fields | All inputs have associated labels | | ⬜ NOT TESTED | |
| A11Y-003 | Tabs semantics | Use arrow keys on tab strips | Roving tabindex; aria-selected correct | | ⬜ NOT TESTED | |
| A11Y-004 | Contrast | Check text/background in both themes | Meets WCAG AA | | ⬜ NOT TESTED | |
| A11Y-005 | Dialog focus trap | Open a dialog | Focus trapped; Esc closes | | ⬜ NOT TESTED | |

## 26. Responsive UI

| ID | Scenario | Steps | Expected Result | Actual | Status | Notes |
|----|----------|-------|-----------------|--------|--------|-------|
| RESP-001 | Mobile nav drawer | Shrink to mobile → open menu | Drawer opens; nav usable | | ⬜ NOT TESTED | |
| RESP-002 | No horizontal scroll | View key pages at 390px | Body does not scroll horizontally | | ⬜ NOT TESTED | |
| RESP-003 | Tables/wide content | View wide tables on mobile | Scroll within their container | | ⬜ NOT TESTED | |
| RESP-004 | Tablet layout | View at 768px | Layout adapts cleanly | | ⬜ NOT TESTED | |

## 27. Dark Mode

| ID | Scenario | Steps | Expected Result | Actual | Status | Notes |
|----|----------|-------|-----------------|--------|--------|-------|
| DARK-001 | Dark theme applies | Set OS/theme to dark | `.dark` on `<html>`; palette correct | | ⬜ NOT TESTED | |
| DARK-002 | Toasts themed | Trigger a toast in dark mode | Palette-consistent colors | | ⬜ NOT TESTED | |
| DARK-003 | No unreadable text | Scan pages in dark mode | All text legible | | ⬜ NOT TESTED | |

## 28. Light Mode

| ID | Scenario | Steps | Expected Result | Actual | Status | Notes |
|----|----------|-------|-----------------|--------|--------|-------|
| LIGHT-001 | Light theme applies | Set theme to light | Light palette; no dark artifacts | | ⬜ NOT TESTED | |
| LIGHT-002 | Persistence | Reload after choosing a theme | Choice persists | | ⬜ NOT TESTED | |

## 29. Forms (FIX 8)

| ID | Scenario | Steps | Expected Result | Actual | Status | Notes |
|----|----------|-------|-----------------|--------|--------|-------|
| FORM-001 | Inline errors | Submit invalid fields | Errors appear inline next to fields | | ⬜ NOT TESTED | |
| FORM-002 | Disable on submit | Submit a valid form | Button shows loading; no double submit | | ⬜ NOT TESTED | |
| FORM-003 | Auto-focus | Open a form/dialog | First field focused | | ⬜ NOT TESTED | |
| FORM-004 | Password toggle + caps lock | Interact with password fields | Toggle + caps-lock hint work | | ⬜ NOT TESTED | |

## 30. Validation

| ID | Scenario | Steps | Expected Result | Actual | Status | Notes |
|----|----------|-------|-----------------|--------|--------|-------|
| VAL-001 | Client + server agree | Bypass client validation via API | Server returns 400 with field errors | | ⬜ NOT TESTED | |
| VAL-002 | Endpoint URL validation | Register agent with a bad URL | Rejected (`@ValidEndpointUrl`) | | ⬜ NOT TESTED | |
| VAL-003 | Max length | Exceed name/description limits | Rejected with a clear message | | ⬜ NOT TESTED | |

## 31. Toast Notifications

| ID | Scenario | Steps | Expected Result | Actual | Status | Notes |
|----|----------|-------|-----------------|--------|--------|-------|
| TOAST-001 | Success toast | Complete an action | Success toast appears and auto-dismisses | | ⬜ NOT TESTED | |
| TOAST-002 | Error toast | Trigger a failure | Error toast with a helpful message | | ⬜ NOT TESTED | |

## 32. Navigation

| ID | Scenario | Steps | Expected Result | Actual | Status | Notes |
|----|----------|-------|-----------------|--------|--------|-------|
| NAV-001 | Sidebar links | Click each sidebar item | Correct route; active state highlighted | | ⬜ NOT TESTED | |
| NAV-002 | Deep-link guard | Open a protected URL unauthenticated | Redirect to `/login` | | ⬜ NOT TESTED | |
| NAV-003 | Breadcrumb/back | Use back links | Return to the parent scope | | ⬜ NOT TESTED | |
| NAV-004 | Page transitions | Navigate between pages | Fade-in transition; no flicker | | ⬜ NOT TESTED | |

## 33. SEO (FIX 7)

| ID | Scenario | Steps | Expected Result | Actual | Status | Notes |
|----|----------|-------|-----------------|--------|--------|-------|
| SEO-001 | Title & description | View page source | Branded `<title>` + meta description | | ⬜ NOT TESTED | |
| SEO-002 | Open Graph / Twitter | Inspect meta tags | og:* and twitter:card present | | ⬜ NOT TESTED | |
| SEO-003 | robots.txt | GET `/robots.txt` | Served with rules | | ⬜ NOT TESTED | |
| SEO-004 | sitemap.xml | GET `/sitemap.xml` | Valid `<urlset>` | | ⬜ NOT TESTED | |
| SEO-005 | Manifest & icons | GET `/manifest.webmanifest`, `/icon.svg` | Served; theme color set | | ⬜ NOT TESTED | |
| SEO-006 | OG image | GET `/opengraph-image` | Dynamic image renders | | ⬜ NOT TESTED | |

## 34. Performance

| ID | Scenario | Steps | Expected Result | Actual | Status | Notes |
|----|----------|-------|-----------------|--------|--------|-------|
| PERF-001 | Cold page load | Load dashboard on a fresh session | Reasonable TTFB/LCP (<3s dev) | | ⬜ NOT TESTED | |
| PERF-002 | Pagination cap | Request `size=10000` via API | Capped at 100 (no unbounded pages) | | ⬜ NOT TESTED | |
| PERF-003 | List rendering | Load large lists | Paginated; no jank | | ⬜ NOT TESTED | |
| PERF-004 | N+1 avoidance | Load the agents list | Single batched credential-status query | | ⬜ NOT TESTED | |

## 35. Security

| ID | Scenario | Steps | Expected Result | Actual | Status | Notes |
|----|----------|-------|-----------------|--------|--------|-------|
| SEC-001 | Deny by default | Call any business endpoint without a token | 401 | | ⬜ NOT TESTED | |
| SEC-002 | Tenant isolation | Access another org's resource | 404 (not 403 leak) | | ⬜ NOT TESTED | |
| SEC-003 | Role enforcement | MEMBER attempts an admin-only action | 403 | | ⬜ NOT TESTED | |
| SEC-004 | Secret confidentiality | Inspect all credential responses | No plaintext/ciphertext ever returned | | ⬜ NOT TESTED | |
| SEC-005 | SSRF guard | Register an agent with a `169.254.169.254`/localhost URL and probe | Blocked by network policy (prod) | | ⬜ NOT TESTED | |
| SEC-006 | Rate limiting | Hammer `/auth/login` | Throttled (429) after the window limit | | ⬜ NOT TESTED | |
| SEC-007 | JWT tampering | Alter a token signature | Rejected (401) | | ⬜ NOT TESTED | |
| SEC-008 | CORS | Call the API from a disallowed origin | Blocked by CORS | | ⬜ NOT TESTED | |
| SEC-009 | Error hygiene | Trigger a server error | No stack trace / internals leaked | | ⬜ NOT TESTED | |
| SEC-010 | Actuator exposure | GET `/actuator/prometheus` unauthenticated | ADMIN-guarded | | ⬜ NOT TESTED | |

## 36. Docker

| ID | Scenario | Steps | Expected Result | Actual | Status | Notes |
|----|----------|-------|-----------------|--------|--------|-------|
| DOCKER-001 | Full stack up | `docker compose up --build` | postgres, redis, backend, frontend all healthy | | ⬜ NOT TESTED | |
| DOCKER-002 | Backend healthcheck | Check container health | Healthy once `/actuator/health` = UP | | ⬜ NOT TESTED | |
| DOCKER-003 | Data persistence | Restart the stack | Postgres/Redis volumes persist data | | ⬜ NOT TESTED | |
| DOCKER-004 | Env secrets required | Start without JWT/ENCRYPTION keys | Backend refuses to start (no insecure default) | | ⬜ NOT TESTED | |

## 37. API

| ID | Scenario | Steps | Expected Result | Actual | Status | Notes |
|----|----------|-------|-----------------|--------|--------|-------|
| API-001 | Postman collection | Run the collection (Runner/Newman) | All assertions pass | | ⬜ NOT TESTED | |
| API-002 | Consistent error shape | Trigger errors | Typed `ErrorCode` → correct HTTP status | | ⬜ NOT TESTED | |
| API-003 | Pagination contract | List with page/size | `PageResponse` shape correct | | ⬜ NOT TESTED | |

## 38. Swagger

| ID | Scenario | Steps | Expected Result | Actual | Status | Notes |
|----|----------|-------|-----------------|--------|--------|-------|
| SWAG-001 | UI loads | Open `/swagger-ui.html` | Renders grouped endpoints | | ⬜ NOT TESTED | |
| SWAG-002 | OpenAPI doc | GET `/v3/api-docs` | Valid OpenAPI JSON | | ⬜ NOT TESTED | |
| SWAG-003 | Auth in Swagger | Authorize with a bearer token | Protected endpoints callable | | ⬜ NOT TESTED | |

## 39. Deployment

| ID | Scenario | Steps | Expected Result | Actual | Status | Notes |
|----|----------|-------|-----------------|--------|--------|-------|
| DEPLOY-001 | Clean build | `mvn clean verify` + `npm run build` | Both succeed | | ⬜ NOT TESTED | |
| DEPLOY-002 | Flyway on boot | Start against an empty DB | Migrations V1…V30 apply; ddl-validate passes | | ⬜ NOT TESTED | |
| DEPLOY-003 | Prod profile email | Start with `SPRING_PROFILES_ACTIVE=prod` + SMTP | SmtpEmailService used | | ⬜ NOT TESTED | |
| DEPLOY-004 | Graceful shutdown | Stop the backend | In-flight requests drain | | ⬜ NOT TESTED | |
| DEPLOY-005 | Liveness/readiness | Hit `/actuator/health/{liveness,readiness}` | Both UP; readiness includes DB | | ⬜ NOT TESTED | |

---

## Release Readiness Report

Fill in after executing the relevant sections.

```
Section                 Ready?
--------------------------------
Authentication          ⬜
Organizations           ⬜
Projects                ⬜
Agents                  ⬜
Credentials             ⬜
Health Checks           ⬜
Versions                ⬜
Advisor                 ⬜
Datasets                ⬜
Prompts                 ⬜
Evaluations             ⬜
Benchmarks              ⬜
Analytics               ⬜
Knowledge               ⬜
Insights                ⬜
Settings                ⬜
Accessibility           ⬜
Responsive UI           ⬜
Dark/Light Mode         ⬜
Forms & Validation      ⬜
Navigation              ⬜
SEO                     ⬜
Performance             ⬜
Security                ⬜
Docker                  ⬜
API & Swagger           ⬜
Deployment              ⬜

TOTAL cases:    ____
Passed:         ____
Failed:         ____
Blocked:        ____
Not tested:     ____

Ready for deployment (all critical sections PASS, no open FAIL): ⬜ YES   ⬜ NO

Sign-off: ______________________    Date: __________
```
