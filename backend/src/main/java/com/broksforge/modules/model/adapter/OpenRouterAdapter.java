package com.broksforge.modules.model.adapter;

import com.broksforge.modules.agent.domain.LlmProvider;
import org.springframework.stereotype.Component;

/** OpenRouter's OpenAI-compatible {@code /api/v1/chat/completions} API. */
@Component
public class OpenRouterAdapter extends OpenAiCompatibleAdapter {

    @Override
    public LlmProvider providerType() {
        return LlmProvider.OPENROUTER;
    }
}
