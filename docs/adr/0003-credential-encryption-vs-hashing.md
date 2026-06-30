# 3. Encrypt agent credentials instead of hashing

- Status: Accepted
- Date: 2026-07-01

## Context

The platform stores two very different classes of secret:

1. **Verification secrets** — values we only ever need to *check*, never reproduce: user
   passwords, API key secrets, refresh/reset/verification tokens. Phase 1 hashes these
   (BCrypt for passwords; SHA-256 for high-entropy tokens). A hash is one-way, so a database
   leak does not reveal the original.
2. **Usage secrets** — values the platform must later *present to a third party*: an agent's
   API key, bearer token, basic-auth password or custom-header value. The health checker (and,
   later, agent invocation, evaluation and benchmarking) must send the original secret to the
   agent endpoint.

A one-way hash is unusable for class 2: you cannot send a hash as the upstream credential.
Therefore these secrets must be stored in a **recoverable** form — which means encryption, with
all the key-management responsibility that entails.

## Decision

- **Hash** class-1 secrets (unchanged from Phase 1).
- **Encrypt** class-2 agent credentials with **AES-256-GCM** via `CredentialEncryptionService`:
  - A fresh random 96-bit IV per value; 128-bit GCM auth tag (authenticated encryption →
    confidentiality *and* integrity/tamper-evidence).
  - The 256-bit key is supplied **only** through the environment
    (`BROKSFORGE_SECURITY_ENCRYPTION_KEY`), never hardcoded, never logged.
  - Ciphertext is self-describing: `v<keyVersion>:<base64(iv)>:<base64(ciphertext+tag)>`. The
    stamped `keyVersion` enables **key rotation** without re-encrypting history.
- **Never expose** a decrypted secret through the API. Responses carry only metadata (auth type,
  username, header name, a masked `secretHint`, key version). Decryption happens solely for
  internal outbound calls in `AgentCredentialService.resolveAuthHeaders`.
- Credentials are write-only over the API and the agent's declared `authType` is kept aligned with
  its active credential.

## Consequences

**Positive**
- The platform can actually authenticate to agents, which hashing makes impossible.
- GCM gives tamper detection for free; a corrupted/edited ciphertext fails to decrypt.
- Versioned ciphertext makes key rotation a future operational task, not a schema change.

**Negative / trade-offs**
- Encryption is reversible: anyone with the key and DB can recover secrets. This raises the
  importance of **key custody** — the key must live in a secrets manager / KMS in production, with
  least-privilege access and rotation. This is a deliberately accepted, well-understood cost.
- We hold key material in process memory. Mitigations (future): envelope encryption with a KMS,
  per-tenant data keys, and HSM-backed master keys.

**Security guarantees today**
- Secrets are never stored in plaintext, never returned by the API, never logged.
- A read-only database compromise does not by itself reveal secrets without the separately-held key.
