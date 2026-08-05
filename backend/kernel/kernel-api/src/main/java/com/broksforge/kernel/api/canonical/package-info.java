/**
 * Canonical serialization and content hashing — the correctness bedrock of content addressing.
 *
 * <h2>Why this package exists</h2>
 * Content addressing (Law 3) is only sound if identical content produces identical bytes, always,
 * on every machine, forever. This package defines the one canonical byte encoding of structured
 * content and derives a {@link com.broksforge.kernel.api.RevisionHash} from it. If two
 * representations of the same value could serialize to different bytes, deduplication and Merkle
 * identity would silently break; everything here exists to make that impossible.
 *
 * <h2>The encoding</h2>
 * A profile of RFC 8785 (JSON Canonicalization Scheme):
 * <ul>
 *   <li>UTF-8 output; strings normalized to Unicode NFC.</li>
 *   <li>Object keys sorted by UTF-16 code unit; no insignificant whitespace.</li>
 *   <li>RFC 8785 string escaping (only {@code "}, {@code \\}, and control characters).</li>
 *   <li><b>Numbers are arbitrary-precision decimals with a strict canonical textual form; binary
 *       floating point is intentionally unsupported.</b> This is the one deliberate deviation from
 *       RFC 8785: IEEE-754 double formatting is a determinism hazard (two byte-representations of
 *       the same number), which a content-addressed system must never admit. Confidence values and
 *       the like are represented exactly as decimals. See
 *       {@link com.broksforge.kernel.api.canonical.CanonicalValue.Num}.</li>
 * </ul>
 *
 * <h2>Constitutional mapping</h2>
 * Implements Law 3 (Content addressing) and underpins Law 5's claim law (a claim's evidence,
 * method, and confidence are part of its canonical content, so an unexplained claim is
 * unrepresentable). Justified by docs/v2/KERNEL_IMPLEMENTATION_PLAN.md §7.
 *
 * <h2>Invariants protected</h2>
 * <ul>
 *   <li>Determinism: equal values ⇒ byte-identical output ⇒ equal hash.</li>
 *   <li>No wall-clock, locale, RNG, or map-iteration-order may influence output.</li>
 * </ul>
 */
package com.broksforge.kernel.api.canonical;
