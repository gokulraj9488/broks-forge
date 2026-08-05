# Forge Kernel — Milestone 1 Report (`kernel-api`)

**Milestone:** 1 of the roadmap in [KERNEL_IMPLEMENTATION_PLAN.md](../KERNEL_IMPLEMENTATION_PLAN.md) §23.
**Scope:** the pure vocabulary of the kernel — value objects, identities, hashes, kernel enums,
the canonical serializer, and their tests. No storage, no persistence, no infrastructure.
**Status:** complete. Compiles clean; all tests pass; no placeholders, no TODOs.

---

## 1. Implementation summary

`kernel-api` is the JDK-only foundation every other kernel module builds on. It delivers the
identity model, the closed kind and edge-family sets, content-hash types, and — the correctness
bedrock — a deterministic canonical serializer that turns structured content into the single byte
representation from which a `RevisionHash` is derived.

Design decisions worth recording:

- **Standalone Maven reactor at `backend/kernel/`, with no parent.** The kernel does not inherit
  `spring-boot-starter-parent`; it compiles with only the JDK and JUnit. This proves framework
  independence at the build level and leaves the live application build untouched (plan §1, §10;
  risk R1 handled by not converting the app build in this milestone).
- **Revision identity excludes the continuant identity.** A `RevisionHash` is a pure function of
  content; the `NodeId` a revision belongs to is carried by the *fact* (a Milestone-2 concept),
  not by the content hash. This maximizes deduplication (byte-identical content is one revision)
  and matches Git's blob model. `NodeId` minting is therefore deferred to the append engine, which
  keeps `kernel-api` free of clocks and randomness (determinism).
- **Canonical numbers are arbitrary-precision decimals; binary floating point is excluded.** This
  is the one deliberate deviation from RFC 8785: IEEE-754 double formatting is a determinism hazard
  for a content-addressed system. Confidence values and the like are represented exactly, and
  `1`, `1.0`, `1.00` all encode to `1` (plan §7; documented in `canonical/package-info.java`).
- **Canonical serializer placed in `kernel-api`, not `kernel-core`.** The plan sketched it in
  core; the Milestone-1 deliverable list places it here so value objects are self-sufficient (a
  `RevisionHash` is computable from content without a core dependency). This is a module-placement
  refinement, not a change to any kernel concept — no constitutional impact, no amendment.

## 2. Files created

**Build (3)**
- `backend/kernel/pom.xml` — reactor parent (no framework parent; pinned offline plugins; JaCoCo)
- `backend/kernel/kernel-api/pom.xml` — module (zero compile deps; JUnit test-only)
- `backend/kernel/README.md` — module map + build instructions

**Main sources (14)**
- `.../api/package-info.java` — package doc + constitutional mapping
- `.../api/Kind.java` — the four kinds (Article II, Law 4)
- `.../api/EdgeFamily.java` — the five families (Article III)
- `.../api/Verb.java` — open verb classified into one family
- `.../api/OrgId.java` — the graph boundary
- `.../api/NodeId.java` — continuant identity
- `.../api/HashAlgorithm.java` — algorithm tag (multihash agility)
- `.../api/RevisionHash.java` — content-derived revision identity
- `.../api/LogPosition.java` — the causal clock
- `.../api/Name.java` — the only mutable concept's immutable path
- `.../api/Address.java` — sealed URI hierarchy (Node / Revision / NamePointer)
- `.../api/canonical/package-info.java` — canonical package doc
- `.../api/canonical/CanonicalValue.java` — the sealed content data model
- `.../api/canonical/CanonicalSerializer.java` — the RFC 8785 profile encoder
- `.../api/canonical/ContentHash.java` — content → `RevisionHash`

**Tests (8)**
- `CanonicalSerializerGoldenTest` (9), `CanonicalSerializerPropertyTest` (4),
  `ContentHashTest` (7), `AddressTest` (6), `RevisionHashTest` (7), `IdentityTypesTest` (4),
  `KernelEnumsTest` (5), `NameTest` (3)

## 3. Files modified

None outside `backend/kernel/`. The live application (`backend/pom.xml`, `backend/src/**`), CI,
Dockerfile, and V1 database are untouched by this milestone — verified by scope.

## 4. Test results

```
Tests run: 45, Failures: 0, Errors: 0, Skipped: 0   — BUILD SUCCESS
```

Breakdown: AddressTest 6 · CanonicalSerializerGoldenTest 9 · CanonicalSerializerPropertyTest 4 ·
ContentHashTest 7 · IdentityTypesTest 4 · KernelEnumsTest 5 · NameTest 3 · RevisionHashTest 7.

The property tests each exercise 2,000 seeded random inputs (determinism, key-order independence,
numeric canonicality, NFC idempotence). Hashing is pinned by SHA-256 known-answer tests
(empty input → `e3b0c442…`; `"abc"` → `ba7816bf…`) plus determinism and sensitivity properties.

## 5. Coverage summary

`mvn verify` (JaCoCo 0.8.12):

| Metric | Coverage |
|--------|----------|
| Instructions | 87.7% (1371/1563) |
| Branches | 82.0% (146/178) |
| Lines | 88.1% (290/329) |

Every public type and every public behavior has at least one test. The uncovered remainder is
defensive/rarely-hit code (a few `toString`/convenience accessors and unreachable-in-practice
guard branches); no functional path is untested.

## 6. Constitutional validation report

| Check | Result | Evidence |
|-------|--------|----------|
| No duplicate sources of truth | ✅ n/a this milestone | No storage yet; value types hold no state to duplicate |
| Log remains authoritative | ✅ n/a this milestone | Log is Milestone 2 |
| Kernel laws remain enforced | ✅ | Kind set = 4 and family set = 5 asserted by test; identities/hashes/names/addresses self-validate at construction; `RevisionHash` is engine-computed (Law 3) |
| Storage remains replaceable | ✅ | No storage coupling exists to violate |
| Kernel has no AI knowledge | ✅ | Zero AI types in code; AI words appear only as illustrative examples in Javadoc. Removing every such word changes nothing functional |
| No framework leakage | ✅ | `mvn dependency:tree -Dscope=compile` is empty; the module compiles with only the JDK |
| No mutable revisions | ✅ | All value objects immutable; collections defensively copied; `RevisionHash` is final; only `Name` models a (path of a) mutable pointer, and the path itself is immutable |
| All constitutional articles remain satisfied | ✅ | Articles I, II, III and Law 3 realised; nothing renamed, simplified, or added |

## 7. Risks discovered

1. **`RevisionHash` excludes `NodeId` — a decision the domain model implied but did not state
   outright.** Recorded here explicitly (§1) so Milestone 2's fact model treats "the immutable
   value" and "the act of asserting it for a continuant" as distinct. Low risk; consistent with
   the constitution; flagged for visibility.
2. **Canonical number model forbids binary floating point.** A constraint userspace must respect
   (represent confidences etc. as decimals). Documented in the package; a userspace convenience
   that needs doubles must convert deliberately, never silently. Low risk, high importance.
3. **The reactor is separate from the application build.** Convenient and safe now, but CI does not
   yet build `backend/kernel/`. Before the kernel is depended upon, a CI step (or the reactor
   merge) must run its tests. Tracked for the `kernel-spring` milestone; not blocking Milestone 1.

No contradiction with the constitution was discovered. No amendment is proposed.

---

**Milestone 1 is complete and ready for review.** Next: Milestone 2 (`kernel-core`) — the append
engine and the six operations — to begin only after this review.
