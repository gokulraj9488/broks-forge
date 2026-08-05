package com.broksforge.fkge.query;

import com.broksforge.kernel.api.EdgeFamily;

import java.util.EnumSet;
import java.util.Set;

/**
 * A lens gives engineering meaning to a set of edge families plus a traversal direction. Lenses are
 * <em>data</em>, not code branches — a question is answered by choosing a lens, so new engineering
 * questions are added by registering new lenses through the SPI, never by editing the engine.
 */
public record Lens(String name, Set<EdgeFamily> families, Direction direction) {

    public Lens {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name");
        if (families == null || families.isEmpty()) throw new IllegalArgumentException("families");
        if (direction == null) throw new IllegalArgumentException("direction");
        families = Set.copyOf(families);
    }

    public static Lens of(String name, Direction direction, EdgeFamily first, EdgeFamily... rest) {
        EnumSet<EdgeFamily> set = EnumSet.of(first, rest);
        return new Lens(name, set, direction);
    }
}
