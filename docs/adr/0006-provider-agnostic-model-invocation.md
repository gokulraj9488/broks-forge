# 6. Provider-agnostic model invocation

- Status: Accepted
- Date: 2026-07-01

## Context

Evaluation, benchmarking and the future debugger all need to **invoke a model**. The platform
must support many providers — OpenAI, Anthropic, Groq, Ollama, Gemini, OpenRouter, DeepSeek and
more — without coupling evaluation logic, or any other module, to the SDK or wire format of any
one of them. Providers differ in authentication, request/response shape and capabilities, and new
ones appear frequently; the framework-agnostic stance of ./0002-agent-as-central-entity.md must
extend to model providers.

Crucially, the *primary* execution target is not a provider's API at all: it is **the agent's own
registered HTTP endpoint**. That endpoint is the real, key-free execution path — the platform
already knows how to reach it safely (see ./0004-ssrf-protection-for-agent-endpoints.md) and how
to authenticate to it with encrypted credentials (see ./0003-credential-encryption-vs-hashing.md).
Provider-direct invocation (calling OpenAI/Anthropic/etc. with an API key) is a secondary,
optional path.

## Alternatives considered

- **Hardcode one SDK.** Build everything on a single provider's client. Fastest to ship, but
  every other provider becomes a rewrite and the agent-endpoint path does not fit the model at
  all.
- **A giant switch in the service.** One `ModelInvocationService` with a `switch` over the
  provider enum and inline HTTP for each. Works initially but turns into an untestable god-method
  that must be edited for every new provider.
- **One module per provider.** Maximum isolation, but heavy duplication of cross-cutting concerns
  (URL guarding, credential resolution, error mapping) and far more surface area than the problem
  warrants.

## Decision

Introduce a **`ModelInvoker` service-provider interface (SPI)** with a **`ModelInvocationService`
dispatcher** that selects an implementation **keyed by the `LlmProvider` enum**:

1. **`ModelInvoker` SPI.** A narrow interface: given a normalized invocation request, return a
   normalized response. Each implementation owns exactly one provider's auth, wire format and
   error mapping. Adding a provider is a **code-only addition** of one SPI implementation — no
   change to callers.
2. **`AgentEndpointInvoker` (the concrete, shipped implementation).** Invokes the **agent's own
   registered HTTP endpoint** — the real, key-free execution target. It reuses `OutboundUrlGuard`
   for SSRF safety and the agent's **encrypted credentials** for outbound auth, so it inherits the
   platform's existing network and secret policies rather than re-implementing them.
3. **Provider-direct clients are pluggable via the same SPI.** Direct HTTP clients for OpenAI,
   Anthropic, Groq, Gemini, OpenRouter, DeepSeek, etc. plug in as additional `ModelInvoker`s,
   **config-gated by the presence of API keys**. They are a documented extension point and are
   **not shipped yet**.
4. **`LlmProvider` enum is extended** for the new providers. Because providers are **stored as
   text** (consistent with ./0002-agent-as-central-entity.md), adding one is **additive and
   migration-free**.

## Consequences

**Positive**
- True provider-agnosticism: callers depend on the SPI, never on a provider SDK.
- The agent-endpoint path reuses the existing URL guard and encrypted-credential machinery
  instead of duplicating it.
- Testability: the dispatcher and each invoker are unit-testable in isolation behind the SPI.
- New providers and the BYO-key path are purely additive (new SPI impl + config), with no
  migration.

**Negative / trade-offs**
- An extra layer of indirection (the SPI and the registry/dispatcher) versus a direct call.
- **Provider-direct clients are not shipped yet** — the extension point is documented but, until
  an implementation lands, provider-direct invocation is unavailable.
- The normalized request/response contract must be kept expressive enough not to leak
  provider-specific concepts.

## Future impact

- **Model-vs-model benchmarking** across providers behind one interface.
- **Cost and latency analytics per provider**, captured at the dispatcher seam.
- **Bring-your-own-key (BYO-key)** invocation once the provider-direct SPI implementations and
  key configuration land.
