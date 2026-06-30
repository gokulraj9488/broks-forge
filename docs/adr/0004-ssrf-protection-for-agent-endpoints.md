# 4. SSRF protection for outbound agent calls

- Status: Accepted
- Date: 2026-07-01

## Context

An agent is registered with a user-supplied `endpointUrl`, and the platform makes outbound HTTP
requests to it (health checks now; invocation, evaluation and benchmarking later). User-controlled
outbound URLs are a classic **Server-Side Request Forgery (SSRF)** vector: an attacker could point
an agent at `http://169.254.169.254/...` (cloud metadata), `http://localhost:8080` (the platform
itself), or other internal-only hosts, and use the platform as a confused deputy.

We also must not break legitimate use: during local development, agents genuinely run on
`localhost`/private addresses.

## Decision

Apply defence in depth across two layers with distinct responsibilities:

1. **Request-layer (syntactic) validation** — `@ValidEndpointUrl` on the DTO ensures the URL is a
   well-formed `http`/`https` URL with no embedded credentials and a sane length. This runs on
   register/update and prevents malformed or `file://`/`gopher://` style inputs. It deliberately
   *allows* private hostnames to be stored, because an organization may legitimately register an
   internal agent.
2. **Runtime (network-policy) guard** — `OutboundUrlGuard` is consulted immediately before every
   outbound call. It re-validates the scheme, rejects embedded credentials, resolves the host, and
   **blocks** loopback, link-local, site-local/private, unique-local (IPv6 `fc00::/7`),
   any-local, multicast addresses and known metadata hostnames. This is the control that actually
   prevents SSRF at call time.
   - It is **configurable**: `broksforge.agent.health.allow-private-targets` (default **false** in
     production, **true** in the `dev` profile) lets local development probe `localhost` agents
     while keeping SaaS deployments safe by default.

Blocked probes do not throw a 400 mid-operation; they are recorded as a failed health check with a
clear reason, so the registry reflects "unreachable by policy" rather than erroring.

## Consequences

**Positive**
- The platform cannot be coerced into reaching cloud metadata endpoints or internal services in
  production.
- Local development still works (opt-in to private targets in the `dev` profile only).
- The guard is reusable by every future module that calls agent endpoints.

**Negative / trade-offs**
- DNS is resolved at probe time to inspect the target IPs, adding a small latency before the call.
- This baseline does not by itself defeat **DNS rebinding** (a host that resolves to a public IP at
  check time and a private IP at connect time). Recommended hardening for a later phase: pin the
  resolved IP for the connection, or route outbound agent traffic through an egress proxy/allowlist.
