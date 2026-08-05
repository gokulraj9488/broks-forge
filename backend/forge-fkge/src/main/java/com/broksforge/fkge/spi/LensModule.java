package com.broksforge.fkge.spi;

/**
 * The FKGE extension SPI. A module contributes lenses (and thus engineering questions) additively — the
 * same composition discipline the knowledge system uses for {@code OntologyModule}. The engine core is
 * never modified to add a capability expressible as a lens.
 */
@FunctionalInterface
public interface LensModule {
    void contribute(LensRegistry registry);
}
