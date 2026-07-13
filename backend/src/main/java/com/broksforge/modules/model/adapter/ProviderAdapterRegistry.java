package com.broksforge.modules.model.adapter;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Resolves the {@link ProviderAdapter} whose route this endpoint URL matches, if any. Route
 * matching (not just host-based provider detection) is deliberate: a provider host can expose
 * multiple routes — a chat-completions endpoint, a models-list endpoint, a health path — and
 * only the invocation route should be adapted; anything else falls back to
 * {@code AgentEndpointInvoker}'s generic envelope, exactly as it did before adapters existed.
 */
@Component
public class ProviderAdapterRegistry {

    private final List<ProviderAdapter> adapters;

    public ProviderAdapterRegistry(List<ProviderAdapter> adapters) {
        this.adapters = List.copyOf(adapters);
    }

    /** Returns the first adapter that recognises this endpoint's route, or {@code null} if none does. */
    public ProviderAdapter resolve(String endpointUrl) {
        for (ProviderAdapter adapter : adapters) {
            if (adapter.supportsEndpoint(endpointUrl)) {
                return adapter;
            }
        }
        return null;
    }
}
