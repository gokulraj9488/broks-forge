package com.broksforge.platform.provider;

import com.broksforge.fxp.copilot.GroundingContext;
import com.broksforge.fxp.copilot.LanguageModel;
import com.broksforge.modules.model.ModelInvocationRequest;
import com.broksforge.modules.model.ModelInvocationResult;
import com.broksforge.modules.model.ModelInvocationService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Bridges the existing provider system to Platform V2's {@link LanguageModel} abstraction, so that when a
 * Brok consumer arrives (P4) it narrates through the platform's real providers via
 * {@link ModelInvocationService}. Consumes only public Platform V2 APIs.
 *
 * <p><b>P1 is dormant:</b> no Brok consumer wires this yet, and a {@link GroundingContext} carries no
 * org/provider/model, so no invoker matches — {@link #narrate} therefore returns a deterministic rendering
 * of the proof facts. It never throws and never changes existing behavior. When P4 supplies a configured
 * assistant model and org context, the same method routes narration to a real provider.
 */
@Component
@ConditionalOnProperty(prefix = "broksforge.platform.v2", name = "enabled", havingValue = "true")
public class ForgeLanguageModel implements LanguageModel {

    private final ModelInvocationService modelInvocationService;

    public ForgeLanguageModel(ModelInvocationService modelInvocationService) {
        this.modelInvocationService = modelInvocationService;
    }

    @Override
    public String narrate(GroundingContext context) {
        try {
            ModelInvocationRequest request = new ModelInvocationRequest(
                    null, null, null, null, factsAsPrompt(context), Map.of(), null);
            if (modelInvocationService.canInvoke(request)) {
                ModelInvocationResult result = modelInvocationService.invoke(request);
                if (result.success() && result.output() != null && !result.output().isBlank()) {
                    return result.output();
                }
            }
        } catch (RuntimeException ignored) {
            // best-effort: fall through to the deterministic rendering below
        }
        return factsAsPrompt(context);
    }

    private static String factsAsPrompt(GroundingContext context) {
        StringBuilder sb = new StringBuilder();
        sb.append(context.subjectLabel()).append(" — ").append(context.intent().human()).append(":");
        for (String fact : context.facts()) {
            sb.append("\n  - ").append(fact);
        }
        return sb.toString();
    }
}
