package com.broksforge.modules.user.service;

import com.broksforge.common.exception.ErrorCode;
import com.broksforge.common.exception.ResourceConflictException;
import com.broksforge.common.exception.ResourceNotFoundException;
import com.broksforge.modules.user.domain.Role;
import com.broksforge.modules.user.domain.User;
import com.broksforge.modules.user.repository.UserRepository;
import com.broksforge.modules.user.web.dto.UpdateProfileRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Owns the {@link User} aggregate. All password hashing happens here so that no
 * other module ever handles raw credentials beyond passing them in.
 */
@Slf4j
@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public User createUser(CreateUserCommand command) {
        String normalisedEmail = normaliseEmail(command.email());
        if (userRepository.existsByEmailIgnoreCaseAndDeletedFalse(normalisedEmail)) {
            throw new ResourceConflictException(ErrorCode.EMAIL_ALREADY_EXISTS, "Email is already registered");
        }

        User user = new User();
        user.setEmail(normalisedEmail);
        user.setPasswordHash(passwordEncoder.encode(command.rawPassword()));
        user.setFirstName(trimToNull(command.firstName()));
        user.setLastName(trimToNull(command.lastName()));
        Set<Role> roles = command.roles() == null || command.roles().isEmpty()
                ? EnumSet.of(Role.USER)
                : EnumSet.copyOf(command.roles());
        user.setRoles(roles);

        User saved = userRepository.save(user);
        log.info("Created user {} ({})", saved.getId(), saved.getEmail());
        return saved;
    }

    @Transactional(readOnly = true)
    public Optional<User> findActiveByEmail(String email) {
        return userRepository.findByEmailIgnoreCaseAndDeletedFalse(normaliseEmail(email));
    }

    /**
     * Batch-loads active users by id, keyed by id. Used by other modules to
     * enrich references (e.g. organization member listings) without N+1 queries.
     */
    @Transactional(readOnly = true)
    public Map<UUID, User> getUsersByIds(Collection<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllByIdInAndDeletedFalse(ids).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
    }

    @Transactional(readOnly = true)
    public User getActiveByIdOrThrow(UUID id) {
        return userRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> ResourceNotFoundException.of("User", id));
    }

    @Transactional
    public User updateProfile(UUID userId, UpdateProfileRequest request) {
        User user = getActiveByIdOrThrow(userId);
        if (request.firstName() != null) {
            user.setFirstName(trimToNull(request.firstName()));
        }
        if (request.lastName() != null) {
            user.setLastName(trimToNull(request.lastName()));
        }
        return user;
    }

    /**
     * Verifies the supplied raw password against the stored hash.
     */
    @Transactional(readOnly = true)
    public boolean checkPassword(User user, String rawPassword) {
        return passwordEncoder.matches(rawPassword, user.getPasswordHash());
    }

    @Transactional
    public void setPassword(UUID userId, String newRawPassword) {
        User user = getActiveByIdOrThrow(userId);
        user.setPasswordHash(passwordEncoder.encode(newRawPassword));
        log.info("Password updated for user {}", userId);
    }

    @Transactional
    public void markEmailVerified(UUID userId) {
        User user = getActiveByIdOrThrow(userId);
        if (!user.isEmailVerified()) {
            user.markEmailVerified();
            log.info("Email verified for user {}", userId);
        }
    }

    @Transactional
    public void recordSuccessfulLogin(UUID userId) {
        userRepository.findByIdAndDeletedFalse(userId)
                .ifPresent(user -> user.setLastLoginAt(Instant.now()));
    }

    private String normaliseEmail(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
