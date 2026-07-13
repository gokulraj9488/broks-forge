package com.broksforge.modules.model.adapter;

import com.broksforge.modules.agent.domain.LlmProvider;
import org.springframework.stereotype.Component;

/** Groq's OpenAI-compatible {@code /openai/v1/chat/completions} API. */
@Component
public class GroqAdapter extends OpenAiCompatibleAdapter {

    @Override
    public LlmProvider providerType() {
        return LlmProvider.GROQ;
    }
}
