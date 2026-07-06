package com.broksforge.modules.user.repository;

import com.broksforge.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Repository slice against the real schema: a registered user is found case-insensitively and
 * excluding soft-deleted rows, and an unknown e-mail is not.
 */
@DisplayName("UserRepository")
class UserRepositoryIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("finds a registered user by e-mail (case-insensitive, non-deleted)")
    void findsRegisteredUser() throws Exception {
        String email = uniqueEmail();
        registerAndGetToken(email, "StrongPass!2026");

        assertThat(userRepository.existsByEmailIgnoreCaseAndDeletedFalse(email)).isTrue();
        assertThat(userRepository.existsByEmailIgnoreCaseAndDeletedFalse(email.toUpperCase())).isTrue();
        assertThat(userRepository.existsByEmailIgnoreCaseAndDeletedFalse("nobody-" + email)).isFalse();
    }
}
