package com.broksforge.modules.model;

import com.broksforge.common.exception.ApiException;
import com.broksforge.common.exception.ErrorCode;
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

    public ModelInvocationService(List<ModelInvoker> invokers) {
        this.invokers = invokers;
    }

    public ModelInvocationResult invoke(ModelInvocationRequest request) {
        return invokers.stream()
                .filter(invoker -> invoker.supports(request))
                .findFirst()
                .orElseThrow(() -> new ApiException(ErrorCode.MODEL_PROVIDER_UNSUPPORTED,
                        "No model invoker is available for this request"))
                .invoke(request);
    }

    public boolean canInvoke(ModelInvocationRequest request) {
        return invokers.stream().anyMatch(invoker -> invoker.supports(request));
    }
}
