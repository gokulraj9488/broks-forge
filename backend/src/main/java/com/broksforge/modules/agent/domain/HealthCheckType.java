package com.broksforge.modules.agent.domain;

/**
 * Origin of a health check. Manual checks are user-triggered; the SCHEDULED
 * value exists so the future scheduler can record automated checks without a
 * schema change.
 */
public enum HealthCheckType {

    MANUAL,
    SCHEDULED
}
