package com.broksforge.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.net.URI;
import java.util.Locale;
import java.util.Set;

public class EndpointUrlValidator implements ConstraintValidator<ValidEndpointUrl, String> {

    private static final Set<String> ALLOWED_SCHEMES = Set.of("http", "https");
    private static final int MAX_LENGTH = 2048;

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return true; // presence is enforced separately via @NotBlank where required
        }
        if (value.isBlank() || value.length() > MAX_LENGTH) {
            return false;
        }
        try {
            URI uri = URI.create(value.trim());
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            return ALLOWED_SCHEMES.contains(scheme)
                    && uri.getHost() != null
                    && !uri.getHost().isBlank()
                    && uri.getUserInfo() == null;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
