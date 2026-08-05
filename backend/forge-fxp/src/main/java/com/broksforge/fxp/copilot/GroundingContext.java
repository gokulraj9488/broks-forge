package com.broksforge.fxp.copilot;

import java.util.List;

/**
 * The only thing a {@link LanguageModel} ever sees: the intent, a subject label, and the platform facts to
 * narrate. It never receives the graph, so it is structurally incapable of inventing engineering truth — it
 * can only phrase what the platform already proved.
 */
public record GroundingContext(Intent intent, String subjectLabel, List<String> facts) {

    public GroundingContext {
        facts = List.copyOf(facts);
    }
}
