package com.broksforge.fkge.query;

import com.broksforge.kernel.api.EdgeFamily;

import java.util.List;

/**
 * The built-in lenses — the canonical engineering questions, expressed as family-sets over the five
 * kernel edge families. Additional lenses are contributed additively through
 * {@link com.broksforge.fkge.spi.LensModule}.
 */
public final class Lenses {

    private Lenses() {}

    /** "Where did this come from?" — full justification, upstream. */
    public static final Lens PROVENANCE = Lens.of("provenance", Direction.OUT,
            EdgeFamily.COMPOSITION, EdgeFamily.DERIVATION, EdgeFamily.EVIDENCE, EdgeFamily.INTENT);

    /** "What must I have to rebuild this?" — reproduction-bearing families only, upstream. */
    public static final Lens DEPENDENCY = Lens.of("dependency", Direction.OUT,
            EdgeFamily.COMPOSITION, EdgeFamily.DERIVATION);

    /** "What breaks if this changes?" — the forward dual of provenance, downstream. */
    public static final Lens IMPACT = Lens.of("impact", Direction.IN,
            EdgeFamily.COMPOSITION, EdgeFamily.DERIVATION, EdgeFamily.EVIDENCE, EdgeFamily.INTENT);

    /** "What supports this belief?" — evidence, upstream. */
    public static final Lens EVIDENCE = Lens.of("evidence", Direction.OUT, EdgeFamily.EVIDENCE);

    /** "Why was this decided?" — intent, upstream. */
    public static final Lens INTENT = Lens.of("intent", Direction.OUT, EdgeFamily.INTENT);

    /** "What caused this?" — causal edges point cause→effect, so causes are reached by IN. */
    public static final Lens CAUSES = Lens.of("causes", Direction.IN, EdgeFamily.CAUSALITY);

    /** "What did this cause?" — effects reached by OUT. */
    public static final Lens EFFECTS = Lens.of("effects", Direction.OUT, EdgeFamily.CAUSALITY);

    /** "What is this made of?" — composition, upstream. */
    public static final Lens COMPOSITION = Lens.of("composition", Direction.OUT, EdgeFamily.COMPOSITION);

    /** "What was this derived from?" — derivation, upstream. */
    public static final Lens LINEAGE = Lens.of("lineage", Direction.OUT, EdgeFamily.DERIVATION);

    public static List<Lens> builtins() {
        return List.of(PROVENANCE, DEPENDENCY, IMPACT, EVIDENCE, INTENT, CAUSES, EFFECTS, COMPOSITION, LINEAGE);
    }
}
