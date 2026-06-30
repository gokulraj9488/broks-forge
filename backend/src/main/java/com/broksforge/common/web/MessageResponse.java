package com.broksforge.common.web;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Simple acknowledgement payload for actions that do not return a resource
 * (logout, password-reset requests, e-mail verification, etc.).
 */
@Schema(name = "MessageResponse", description = "A simple acknowledgement message")
public record MessageResponse(
        @Schema(description = "Human-readable result message", example = "Operation completed successfully")
        String message
) {
    public static MessageResponse of(String message) {
        return new MessageResponse(message);
    }
}
