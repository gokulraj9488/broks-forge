package com.broksforge.fxp.copilot;

/**
 * A deterministic, dependency-free {@link LanguageModel} that renders the facts as plain text. It proves the
 * grounding contract without any real model: given the same proof it produces the same narration, and it can
 * say nothing the facts do not contain. Production deployments swap in a real LLM adapter behind this SPI.
 */
public final class TemplateLanguageModel implements LanguageModel {

    @Override
    public String narrate(GroundingContext ctx) {
        StringBuilder sb = new StringBuilder();
        sb.append(ctx.subjectLabel()).append(" — ").append(ctx.intent().human()).append(":");
        for (String fact : ctx.facts()) {
            sb.append("\n  - ").append(fact);
        }
        return sb.toString();
    }
}
