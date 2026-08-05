# Brok's Forge Kernel

The append-only, content-addressed engineering substrate for Brok's Forge V2 — a **standalone Maven
reactor**, independent of the Spring application in `../`. The kernel builds with nothing but the JDK,
JUnit, and (for the PostgreSQL adapter) the Postgres JDBC driver: no Spring, no ORM, no AI. It could
be released as a standalone open-source library.

Founding documents: [`../../docs/v2/`](../../docs/v2/README.md) — constitution
([MANIFESTO](../../docs/v2/MANIFESTO.md)), ADRs, domain model, and the
[developer guide](../../docs/v2/kernel/DEVELOPER-GUIDE.md).

## Modules

| Module | Purpose | Status |
|--------|---------|--------|
| `kernel-api` | value objects, identities, hashes, kernel enums, canonical serializer **and parser** | ✅ done |
| `kernel-core` | append engine, six operations, storage ports, in-memory backend, log codec, validation layer | ✅ done |
| `kernel-tck` | backend-agnostic storage compatibility contract | ✅ done |
| `kernel-store-postgres` | PostgreSQL persistence adapter (JDBC + dependency-free migrator) | ✅ done; TCK verified against PostgreSQL 16.14 |

## Build

```bash
# from backend/kernel
mvn test                 # compile + all tests (in-memory + TCK); Postgres TCK skips without a DB
mvn verify               # + JaCoCo coverage per module

# Run the PostgreSQL contract against a throwaway database:
KERNEL_TEST_PG_URL=jdbc:postgresql://localhost:5432/forge_kernel_test \
KERNEL_TEST_PG_USER=postgres KERNEL_TEST_PG_PASSWORD=postgres \
  mvn -pl kernel-store-postgres test
```

The build is offline-capable: plugin and dependency versions are pinned to what a standard Brok's
Forge backend build has already cached. The PostgreSQL adapter deliberately avoids Flyway and
Testcontainers (a dependency-free JDBC migrator and an env-gated integration test instead), keeping
the kernel's dependency surface minimal.

## Design in one screen

- **One substrate:** an append-only, content-addressed, hash-chained log per organization — the sole
  source of truth. The revision store, graph index, and name store are projections rebuilt from it.
- **Four kinds** (Artifact/Observation/Claim/Decision), **five edge families**, **six operations**
  (append, resolve, traverse, diff, reproduce, subscribe), **ten laws** enforced by code.
- **Interchangeable storage:** any backend implementing the four ports and passing `kernel-tck` is a
  drop-in. Two backends ship: in-memory (reference) and PostgreSQL (durable).
