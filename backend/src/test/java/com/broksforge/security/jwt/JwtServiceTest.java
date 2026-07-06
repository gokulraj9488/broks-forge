package com.broksforge.security.jwt;

import com.broksforge.config.properties.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("JwtService (HS256 access tokens)")
class JwtServiceTest {

    private static final String SECRET =
            "vTjUOFXzCdn9qMirrdcjFw2QIEFjVJcHzj1clbCiCamwWRK/kOeC/MRyVGdrahed"; // 48 bytes
    private static final String OTHER_SECRET = "bmsy5fVfCCs4bmfYnV5sGmdlh3edbbnP3vZxvX2fchI="; // 32 bytes

    private JwtService service(long expiryMs, String secret) {
        return new JwtService(new JwtProperties(secret, "broks-forge", expiryMs, 2_592_000_000L));
    }

    @Test
    @DisplayName("issues a token whose claims round-trip")
    void issuesAndParses() {
        JwtService jwt = service(900_000L, SECRET);
        UUID userId = UUID.randomUUID();
        String token = jwt.generateAccessToken(userId, "user@example.com", List.of("ROLE_USER"));

        Claims claims = jwt.parseClaims(token);
        assertThat(jwt.extractUserId(claims)).isEqualTo(userId);
        assertThat(jwt.extractEmail(claims)).isEqualTo("user@example.com");
        assertThat(jwt.extractRoles(claims)).containsExactly("ROLE_USER");
        assertThat(jwt.isAccessToken(claims)).isTrue();
    }

    @Test
    @DisplayName("rejects a token signed with a different secret")
    void rejectsForeignSignature() {
        String token = service(900_000L, SECRET)
                .generateAccessToken(UUID.randomUUID(), "u@e.com", List.of());
        JwtService other = service(900_000L, OTHER_SECRET);
        assertThatThrownBy(() -> other.parseClaims(token)).isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("rejects an expired token")
    void rejectsExpired() {
        // Negative lifetime → the token is already expired the moment it is issued.
        JwtService jwt = service(-1_000L, SECRET);
        String token = jwt.generateAccessToken(UUID.randomUUID(), "u@e.com", List.of());
        assertThatThrownBy(() -> jwt.parseClaims(token)).isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("rejects a secret shorter than 256 bits")
    void rejectsWeakSecret() {
        assertThatThrownBy(() -> service(900_000L, "c2hvcnQ=")) // "short"
                .isInstanceOf(IllegalStateException.class);
    }
}
