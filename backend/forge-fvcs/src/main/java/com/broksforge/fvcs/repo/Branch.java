package com.broksforge.fvcs.repo;

import com.broksforge.kernel.api.Name;

/**
 * A branch — a line of development. It is nothing but a kernel {@link Name} (the only mutable state):
 * its head is {@code resolve(name)}, and advancing it is a compare-and-swap repoint. Convention:
 * {@code branch/<line>}.
 *
 * @param line the branch line (e.g. {@code main}, {@code exp/cheaper-model})
 */
public record Branch(String line) {

    public Branch {
        if (line == null || line.isBlank()) {
            throw new IllegalArgumentException("branch line must not be blank");
        }
    }

    /** @return the kernel name that is this branch's mutable head pointer */
    public Name name() {
        return Name.of("branch/" + line);
    }
}
