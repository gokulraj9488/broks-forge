package com.broksforge.modules.auth.repository;

import com.broksforge.modules.auth.domain.PasswordChangeOtp;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PasswordChangeOtpRepository extends JpaRepository<PasswordChangeOtp, UUID> {

    /** The most recent still-checkable OTP for a user (not yet verified or consumed). */
    Optional<PasswordChangeOtp> findFirstByUserIdAndConsumedAtIsNullAndVerifiedAtIsNullOrderByCreatedAtDesc(
            UUID userId);

    Optional<PasswordChangeOtp> findByTicketHash(String ticketHash);

    @Modifying
    @Query("update PasswordChangeOtp o set o.consumedAt = :now "
            + "where o.userId = :userId and o.consumedAt is null")
    int invalidateAllForUser(@Param("userId") UUID userId, @Param("now") Instant now);
}
