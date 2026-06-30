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
 * Validates that a password meets the platform's minimum strength policy:
 * 8&ndash;72 characters with at least one uppercase letter, one lowercase
 * letter and one digit. (72 is the BCrypt input limit.)
 */
@Documented
@Constraint(validatedBy = StrongPasswordValidator.class)
@Target({FIELD, PARAMETER, ANNOTATION_TYPE, RECORD_COMPONENT})
@Retention(RUNTIME)
public @interface StrongPassword {

    String message() default
            "Password must be 8-72 characters and include an uppercase letter, a lowercase letter and a digit";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
