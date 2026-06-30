# Security Policy

Brok's Forge is a multi-tenant AI engineering platform that handles credentials,
tenant data, and outbound calls to user-supplied endpoints. We take security
seriously and appreciate responsible disclosure.

## Supported versions

Security fixes are provided for the latest minor release line. Older lines are
not maintained — please upgrade to a supported version before reporting.

| Version | Supported          |
| ------- | ------------------ |
| 1.0.x   | :white_check_mark: |
| < 1.0   | :x:                |

## Reporting a vulnerability

**Please do not open a public issue, pull request, or discussion for security
vulnerabilities.** Public disclosure before a fix is available puts every
deployment at risk.

Report privately through GitHub Security Advisories:

1. Go to the repository's **Security** tab.
2. Click **Report a vulnerability** (GitHub Private Vulnerability Reporting).
3. Or open this link directly:
   <https://github.com/your-org/broks-forge/security/advisories/new>

Please include, where possible:

- the affected component, endpoint, or version/tag;
- a clear description of the issue and its impact;
- reproduction steps or a proof of concept;
- any suggested remediation.

**Never include live secrets** (JWTs, API keys, encryption keys, credentials) in
a report — describe them instead.

## What to expect

| Stage                 | Target                                                            |
| --------------------- | ----------------------------------------------------------------- |
| Acknowledgement       | within **3 business days**                                        |
| Initial assessment    | within **7 business days**                                        |
| Fix / mitigation plan | communicated after triage; severity-dependent                     |
| Coordinated disclosure| a CVE/advisory is published once a fix is available               |

Critical issues — authentication bypass, cross-tenant access, SSRF, or secret
disclosure — are triaged with top priority. We will keep you informed of progress
and credit you in the advisory unless you prefer to remain anonymous.

## Scope and hardening details

This document covers **how to report**. For the platform's actual security
architecture — threat model, authentication, RBAC, multi-tenant isolation, secret
management, SSRF defense, injection prevention, export safety, logging, the
per-endpoint review checklist, and the known-limitations / hardening roadmap —
see the detailed engineering reference:

> **[docs/SECURITY_GUIDE.md](./docs/SECURITY_GUIDE.md)**

Maintainers and contributors must run the
[per-endpoint security review checklist](./docs/SECURITY_GUIDE.md#14-per-endpoint-security-review-checklist)
before merging any change that touches an endpoint, query, or outbound call.
