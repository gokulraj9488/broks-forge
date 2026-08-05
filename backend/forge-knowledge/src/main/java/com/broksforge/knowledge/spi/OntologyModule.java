package com.broksforge.knowledge.spi;

import com.broksforge.knowledge.ontology.Ontology;

/**
 * The extension SPI for the ontology: a module contributes object types and relationship types to an
 * {@link Ontology.Builder} (KN-0001 — new types are data, added by modules, never framework or kernel
 * changes). The canonical Forge ontology is itself expressed as one module; third parties compose more
 * on top.
 *
 * <p>Dependency direction is one-way ({@code spi → ontology}), keeping the module graph acyclic.
 */
@FunctionalInterface
public interface OntologyModule {

    /**
     * Contributes this module's object and relation types to the builder.
     *
     * @param builder the ontology builder being assembled
     */
    void contribute(Ontology.Builder builder);
}
