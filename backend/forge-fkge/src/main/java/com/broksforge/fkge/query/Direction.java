package com.broksforge.fkge.query;

/**
 * Traversal direction over the reasoning graph.
 *
 * <p>Kernel references point from a node toward what it rests on, so for the "rests-on" families
 * (composition, derivation, evidence, intent):
 * <ul>
 *   <li>{@link #OUT} = follow outgoing references = <em>upstream</em> (provenance, dependency, evidence, "why").</li>
 *   <li>{@link #IN}  = follow incoming references = <em>downstream</em> (impact, blast radius, "what depends on this").</li>
 * </ul>
 * The duality law {@code X ∈ impact(N) ⟺ N ∈ provenance(X)} is exactly: {@code IN} is the transpose of {@code OUT}.
 *
 * <p>Causal edges are the exception: {@code caused}/{@code triggered}/{@code regressed} point from cause to
 * effect, so the cause of an effect is reached by {@code IN} and the effects of a cause by {@code OUT}.
 */
public enum Direction {
    OUT,
    IN,
    BOTH
}
