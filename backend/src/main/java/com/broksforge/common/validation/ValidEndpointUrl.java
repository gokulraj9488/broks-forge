package com.broksforge.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.RECORD_COMPONENT;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Validates that a value is a syntactically valid {@code http}/{@code https}
 * endpoint URL with no embedded credentials and a sane length.
 *
 * <p>This is the request-layer (syntactic) check. Runtime network policy
 * (SSRF protection against private targets) is enforced separately by
 * {@code OutboundUrlGuard} when an outbound call is actually made.</p>
 *
 * <p>{@code null} is considered valid so the annotation can be used on optional
 * (e.g. PATCH) fields; combine with {@code @NotBlank} where presence is required.</p>
 */
@Documented
@Constraint(validatedBy = EndpointUrlValidator.class)
@Target({FIELD, PARAMETER, ANNOTATION_TYPE, RECORD_COMPONENT})
@Retention(RUNTIME)
public @interface ValidEndpointUrl {

    String message() default "Must be a valid http(s) URL without embedded credentials";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
