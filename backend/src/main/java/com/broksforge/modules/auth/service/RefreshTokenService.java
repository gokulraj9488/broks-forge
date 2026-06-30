package com.broksforge.modules.auth.service;

import com.broksforge.common.exception.ErrorCode;
import com.broksforge.common.exception.UnauthorizedException;
import com.broksforge.common.util.SecureTokens;
import com.broksforge.config.properties.JwtProperties;
import com.broksforge.modules.auth.domain.RefreshToken;
import com.broksforge.modules.auth.repository.RefreshTokenRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Issues, rotates and revokes opaque refresh tokens. Raw tokens are returned to
 * the caller exactly once; only their SHA-256 hashes are stored.
 */
@Slf4j
@Service
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtProperties jwtProperties;

    public RefreshTokenService(RefreshTokenRepository refreshTokenRepository, JwtProperties jwtProperties) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.jwtProperties = jwtProperties;
    }

    /**
     * Issues a new refresh token for the given user.
     */
    @Transactional
    public IssuedRefreshToken issue(UUID userId, String userAgent, String ipAddress) {
        String rawToken = SecureTokens.generateToken();
        Instant expiresAt = Instant.now().plusMillis(jwtProperties.refreshTokenExpirationMs());

        RefreshToken entity = new RefreshToken();
        entity.setUserId(userId);
        entity.setTokenHash(SecureTokens.sha256Hex(rawToken));
        entity.setExpiresAt(expiresAt);
        entity.setUserAgent(truncate(userAgent, 512));
        entity.setIpAddress(truncate(ipAddress, 64));
        refreshTokenRepository.save(entity);

        return new IssuedRefreshToken(rawToken, expiresAt);
    }

    /**
     * Verifies a presented refresh token is known, unrevoked and unexpired.
     */
    @Transactional(readOnly = true)
    public RefreshToken validate(String rawToken) {
        RefreshToken token = refreshTokenRepository.findByTokenHash(SecureTokens.sha256Hex(rawToken))
                .orElseThrow(() -> new UnauthorizedException(ErrorCode.TOKEN_INVALID, "Invalid refresh token"));
        if (!token.isActive()) {
            throw new UnauthorizedException(ErrorCode.TOKEN_EXPIRED, "Refresh token is expired or revoked");
        }
        return token;
    }

    /**
     * Rotates a refresh token: the presented token is revoked and a fresh one
     * issued. Rotation limits the blast radius of a leaked token.
     */
    @Transactional
    public IssuedRefreshToken rotate(RefreshToken current, String userAgent, String ipAddress) {
        current.revoke();
        return issue(current.getUserId(), userAgent, ipAddress);
    }

    @Transactional
    public void revoke(String rawToken) {
        refreshTokenRepository.findByTokenHash(SecureTokens.sha256Hex(rawToken))
                .ifPresent(RefreshToken::revoke);
    }

    @Transactional
    public void revokeAllForUser(UUID userId) {
        int revoked = refreshTokenRepository.revokeAllForUser(userId, Instant.now());
        if (revoked > 0) {
            log.info("Revoked {} refresh token(s) for user {}", revoked, userId);
        }
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    /**
     * A freshly issued refresh token together with its absolute expiry.
     */
    public record IssuedRefreshToken(String token, Instant expiresAt) {
    }
}
