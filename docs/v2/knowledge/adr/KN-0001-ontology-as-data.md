# KN-0001. The ontology is open data, not a class hierarchy

- Status: Accepted
- Phase: 2 (Knowledge System)

## Context

A modern AI platform needs dozens of knowledge object types, and the set will grow. The kernel already
made the deep decision: kinds and families are **closed**, subtypes and verbs are **open data**. The
Knowledge System must decide how to represent its object types and relationships.

## Alternatives

- **A Java class per object type** (`class Prompt extends KnowledgeObject`). Encodes the ontology in
  code; every new type is a code change, a release, a migration. It also tempts inheritance, which the
  kernel deliberately lacks.
- **An external schema file (JSON/YAML) loaded at runtime.** Flexible, but severs the ontology from
  compile-time checking and from the kernel's value types.
- **An in-code registry of `ObjectType` values (data), with a small closed framework.** The ontology is
  a catalog of immutable `ObjectType`/`RelationType` values; the framework that validates and stores
  them never changes when a type is added.

## Decision

The ontology is **data**: a registry of `ObjectType` and `RelationType` values, each binding a kernel
`Kind` + subtype + payload schema + legal edges. Adding an object type or verb is adding a registry
entry, not changing the framework and never touching the kernel. This mirrors the kernel's "open
subtypes/verbs" exactly one layer up, and keeps the framework minimal and stable.

## Consequences

- New types ship as data; the validator, façade, and projection are written once.
- The ontology can be **exported** (it is data) for tooling, docs, and cross-system exchange.
- No inheritance; composition and roles express variation (KN-0002).
- Compile-time ergonomics are recovered by exposing well-known types as constants (`ObjectTypes.AGENT`).
