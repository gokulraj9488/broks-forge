package com.broksforge.modules.user.service;

import com.broksforge.modules.user.domain.Role;

import java.util.Set;

/**
 * Command used by the auth module to create a new user. The password is passed
 * in clear text and hashed inside {@code UserService}; it is never logged or
 * stored in clear text.
 */
public record CreateUserCommand(
        String email,
        String rawPassword,
        String firstName,
        String lastName,
        Set<Role> roles
) {
    public CreateUserCommand(String email, String rawPassword, String firstName, String lastName) {
        this(email, rawPassword, firstName, lastName, Set.of(Role.USER));
    }
}
