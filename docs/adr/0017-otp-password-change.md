# 17. Email OTP for password change (supersedes the emailed confirmation link)

- Status: Accepted
- Date: 2026-07-05
- Supersedes: the emailed-link password change (V26 `password_change_tokens`, retained but no longer the default UX)

## Context

Changing a password from an authenticated session must re-verify the user (an
access token alone is not proof the person at the keyboard is the account owner)
and then invalidate every session. The previous implementation emailed a
long-lived, high-entropy **link**; clicking it opened a page (outside the guarded
route groups) that set the new password.

That link flow works but has three drawbacks for an in-app "change my password"
action: it forces a context switch out of the app into email and back via a
browser; the confirmation page is **public** (the token is the only proof), so
the whole capability is exercised by an unauthenticated request carrying a
URL-borne secret; and it does not match the familiar "we sent you a code" pattern
users expect for step-up verification.

## Decision

Replace the link with an **in-session, three-step email OTP** (all endpoints
authenticated), while leaving the link endpoints in place for backward
compatibility:

1. **Request** — `POST /api/v1/auth/password-change/request` re-checks the
   current password and e-mails a **6-digit code**. Only the SHA-256 hash of the
   code is stored (`password_change_otps`, V27). Rate-limited per user
   (`RateLimiterService`) on top of the per-IP interceptor, so a stolen access
   token cannot flood the victim's inbox.
2. **Verify** — `POST /api/v1/auth/password-change/verify` checks the code in
   constant time. The code is **low-entropy**, so the row carries an **attempt
   counter** (max 5): wrong codes are burned and lock the row. On success it
   mints a **single-use, high-entropy ticket** (stored hashed) and returns it.
   Because the increment/burn must persist even though a wrong code throws, the
   service method uses `@Transactional(noRollbackFor = ApiException.class)`.
3. **Complete** — `POST /api/v1/auth/password-change/complete` consumes the
   ticket (bound to the caller as defence in depth), sets the new password and
   **revokes every refresh token**.

The code expires after **5 minutes**
(`broksforge.security.tokens.password-change-otp-expiration-ms`); the ticket
after **10 minutes**. The existing `EmailService` port gains one
`sendPasswordChangeOtp` method with a branded template in `EmailContentFactory`,
so the console (`LoggingEmailService`) and SMTP (`SmtpEmailService`) transports
are unchanged in every other respect (see [ADR 0016](./0016-pluggable-email-transport.md)).

## Consequences

**Positive**
- Step-up verification stays **inside the app** — no dead-end email round trip —
  and every step is **authenticated**, so there is no public secret-bearing page.
- The two-secret design keeps the low-entropy code brute-force-safe (attempt cap +
  5-minute expiry) while the high-entropy ticket authorises the actual change, so
  the new password is never submitted together with the code.
- Sessions are still fully revoked on success; the security posture is unchanged
  or stronger.

**Negative / trade-offs**
- A 6-digit code is inherently lower entropy than a 256-bit link token; this is
  mitigated by the attempt cap, short expiry, single active code per user, and
  rate-limited generation.
- The verify step returns a continuation ticket to the client. It is short-lived,
  single-use and stored only as a hash server-side — a capability token for the
  very next call, not a stored credential — but it is the one value the flow
  returns to the browser (over TLS, to an already-authenticated caller).

## Future impact

- The link endpoints (`/change-password`, `/confirm-password-change`) remain for
  compatibility and can be removed in a future major version once no client uses
  them.
- The same OTP primitive (`password_change_otps` + `SecureTokens.generateNumericCode`)
  can back other step-up actions (e.g. deleting an organization) without new
  infrastructure.
