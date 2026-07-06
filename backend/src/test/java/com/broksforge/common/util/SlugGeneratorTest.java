package com.broksforge.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SlugGenerator")
class SlugGeneratorTest {

    @Test
    @DisplayName("slugifies a display name to lowercase, hyphenated ASCII")
    void slugifiesDisplayName() {
        assertThat(SlugGenerator.slugify("Customer Support Agent")).isEqualTo("customer-support-agent");
        assertThat(SlugGenerator.slugify("  Groq   Llama  3.3 ")).isEqualTo("groq-llama-33");
    }

    @Test
    @DisplayName("strips diacritics to ASCII")
    void stripsDiacritics() {
        assertThat(SlugGenerator.slugify("Café Münchèn")).isEqualTo("cafe-munchen");
    }

    @Test
    @DisplayName("falls back to 'untitled' for blank or symbol-only input")
    void fallsBackForBlank() {
        assertThat(SlugGenerator.slugify(null)).isEqualTo("untitled");
        assertThat(SlugGenerator.slugify("   ")).isEqualTo("untitled");
        assertThat(SlugGenerator.slugify("!!!")).isEqualTo("untitled");
    }

    @Test
    @DisplayName("caps the base slug length")
    void capsLength() {
        String longName = "a".repeat(200);
        assertThat(SlugGenerator.slugify(longName).length()).isLessThanOrEqualTo(48);
    }

    @Test
    @DisplayName("uniqueSlug returns the base when free")
    void uniqueSlugReturnsBaseWhenFree() {
        assertThat(SlugGenerator.uniqueSlug("My Agent", candidate -> false)).isEqualTo("my-agent");
    }

    @Test
    @DisplayName("uniqueSlug appends an incrementing suffix when taken")
    void uniqueSlugAppendsSuffix() {
        Set<String> taken = Set.of("my-agent", "my-agent-2");
        assertThat(SlugGenerator.uniqueSlug("My Agent", taken::contains)).isEqualTo("my-agent-3");
    }
}
