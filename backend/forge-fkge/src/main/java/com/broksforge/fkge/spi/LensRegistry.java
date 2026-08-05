package com.broksforge.fkge.spi;

import com.broksforge.fkge.query.Lens;
import com.broksforge.fkge.query.Lenses;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The registry a {@link LensModule} contributes lenses into. Seeded with the built-ins; modules add more
 * additively. This is the sanctioned extension path — new engineering questions are new lenses, never
 * edits to the engine core.
 */
public final class LensRegistry {

    private final Map<String, Lens> lenses = new LinkedHashMap<>();

    private LensRegistry() {}

    /** A registry pre-populated with the built-in lenses. */
    public static LensRegistry withBuiltins() {
        LensRegistry r = new LensRegistry();
        Lenses.builtins().forEach(r::register);
        return r;
    }

    public LensRegistry register(Lens lens) {
        if (lens == null) throw new IllegalArgumentException("lens");
        lenses.put(lens.name(), lens);
        return this;
    }

    public Optional<Lens> lens(String name) {
        return Optional.ofNullable(lenses.get(name));
    }

    public List<Lens> all() {
        return List.copyOf(lenses.values());
    }
}
