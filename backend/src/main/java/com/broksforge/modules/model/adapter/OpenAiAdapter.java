package com.broksforge.modules.model.adapter;

import com.broksforge.modules.agent.domain.LlmProvider;
import org.springframework.stereotype.Component;

/** OpenAI's {@code /v1/chat/completions} API — the canonical shape {@link OpenAiCompatibleAdapter} models. */
@Component
public class OpenAiAdapter extends OpenAiCompatibleAdapter {

    @Override
    public LlmProvider providerType() {
        return LlmProvider.OPENAI;
    }
}
