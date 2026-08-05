package com.broksforge.modules.model;

import com.broksforge.common.exception.ApiException;
import com.broksforge.common.exception.ErrorCode;
import com.broksforge.platform.provider.ForgeProviderBridge;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Dispatches a {@link ModelInvocationRequest} to the first registered
 * {@link ModelInvoker} that supports it. Adding a provider is purely a matter of
 * registering a new invoker bean (see ADR 0006); this dispatcher and all callers
 * stay unchanged.
 */
@Service
public class ModelInvocationService {

    private final List<ModelInvoker> invokers;
    private final ObjectProvider<ForgeProviderBridge> providerBridge;

    public ModelInvocationService(List<ModelInvoker> invokers,
                                  ObjectProvider<ForgeProviderBridge> providerBridge) {
        this.invokers = invokers;
        this.providerBridge = providerBridge;
    }

    public ModelInvocationResult invoke(ModelInvocationRequest request) {
        ModelInvocationResult result = invokers.stream()
                .filter(invoker -> invoker.supports(request))
                .findFirst()
                .orElseThrow(() -> new ApiException(ErrorCode.MODEL_PROVIDER_UNSUPPORTED,
                        "No model invoker is available for this request"))
                .invoke(request);
        // P1: route the completed execution through the Platform V2 provider bridge. Best-effort and
        // dormant (records nothing until P2 supplies a lawful anchor); it never alters this result.
        ForgeProviderBridge bridge = providerBridge.getIfAvailable();
        if (bridge != null) {
            bridge.onInvocation(request, result);
        }
        return result;
    }

    public boolean canInvoke(ModelInvocationRequest request) {
        return invokers.stream().anyMatch(invoker -> invoker.supports(request));
    }
}
