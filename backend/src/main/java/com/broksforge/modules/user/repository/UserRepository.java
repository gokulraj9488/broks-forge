package com.broksforge.modules.user.repository;

import com.broksforge.modules.user.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmailIgnoreCase(String email);

    Optional<User> findByEmailIgnoreCaseAndDeletedFalse(String email);

    Optional<User> findByIdAndDeletedFalse(UUID id);

    boolean existsByEmailIgnoreCaseAndDeletedFalse(String email);

    List<User> findAllByIdInAndDeletedFalse(Collection<UUID> ids);
}
