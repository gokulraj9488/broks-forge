package com.broksforge.fxp.integrate;

/** An external model invocation, as seen by a {@link ModelProviderAdapter} at the platform edge. */
public record ModelInvocation(String model, String prompt, String output, String status) {}
