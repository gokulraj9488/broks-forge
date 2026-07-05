package com.broksforge.modules.auth.repository;

import com.broksforge.modules.auth.domain.PasswordChangeToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PasswordChangeTokenRepository extends JpaRepository<PasswordChangeToken, UUID> {

    Optional<PasswordChangeToken> findByTokenHash(String tokenHash);

    @Modifying
    @Query("update PasswordChangeToken t set t.usedAt = :now "
            + "where t.userId = :userId and t.usedAt is null")
    int invalidateAllForUser(@Param("userId") UUID userId, @Param("now") Instant now);
}
