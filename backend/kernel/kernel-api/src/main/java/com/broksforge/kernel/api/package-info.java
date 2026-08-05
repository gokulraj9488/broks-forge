/**
 * The pure vocabulary of the Forge Kernel: identities, hashes, kernel enums, and the value
 * objects every layer above shares. This package has no dependencies beyond the JDK.
 *
 * <h2>Why this package exists</h2>
 * An operating system needs a universal resource abstraction and a single, unambiguous way to
 * name and identify things. This package defines that vocabulary once, as immutable value
 * objects, so that content addressing is deterministic and portable and no framework type can
 * ever leak into an identity.
 *
 * <h2>Constitutional mapping</h2>
 * <ul>
 *   <li><b>Article I (The Substrate)</b> — {@link com.broksforge.kernel.api.NodeId},
 *       {@link com.broksforge.kernel.api.RevisionHash}, {@link com.broksforge.kernel.api.Address},
 *       {@link com.broksforge.kernel.api.Name}, {@link com.broksforge.kernel.api.LogPosition}
 *       realise the identity model.</li>
 *   <li><b>Article II (Epistemic Typing)</b> — {@link com.broksforge.kernel.api.Kind} is the
 *       closed set of the four kernel kinds.</li>
 *   <li><b>Article III (Relationships)</b> — {@link com.broksforge.kernel.api.EdgeFamily} is the
 *       closed set of the five edge families; {@link com.broksforge.kernel.api.Verb} carries an
 *       open verb classified into exactly one family.</li>
 *   <li><b>Law 3 (Content addressing)</b> — {@link com.broksforge.kernel.api.canonical} computes
 *       {@link com.broksforge.kernel.api.RevisionHash} from a canonical byte encoding.</li>
 * </ul>
 *
 * <h2>Invariants protected</h2>
 * <ul>
 *   <li>Every value object is immutable and validates itself at construction.</li>
 *   <li>Identity is opaque and stable ({@code NodeId}); revision identity is content-derived
 *       ({@code RevisionHash}); the two are never conflated.</li>
 *   <li>The kind set (4) and the edge-family set (5) are closed; verbs are open.</li>
 * </ul>
 *
 * @see <a href="file:../../../../../../../../docs/v2/MANIFESTO.md">MANIFESTO.md</a>
 * @see <a href="file:../../../../../../../../docs/v2/DOMAIN_MODEL.md">DOMAIN_MODEL.md</a>
 */
package com.broksforge.kernel.api;
