package com.broksforge.platform.provider;

import com.broksforge.fxp.copilot.GroundingContext;
import com.broksforge.fxp.copilot.Intent;
import com.broksforge.modules.model.ModelInvocationService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** The LanguageModel bridge: dormant fallback when no provider matches, and it never throws. */
class ForgeLanguageModelTest {

    @Test
    void narrateFallsBackToFactsWhenNoInvokerMatches() {
        ModelInvocationService service = mock(ModelInvocationService.class);
        when(service.canInvoke(any())).thenReturn(false); // P1: no provider-direct invoker matches

        ForgeLanguageModel model = new ForgeLanguageModel(service);
        GroundingContext ctx = new GroundingContext(Intent.PROVENANCE, "Agent:support-bot",
                List.of("derives from Model", "derives from Provider"));

        String narrative = model.narrate(ctx);
        assertNotNull(narrative);
        assertTrue(narrative.contains("support-bot"), narrative);
        assertTrue(narrative.contains("derives from Model"), narrative);
    }
}
