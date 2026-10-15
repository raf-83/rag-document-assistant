package com.aidocumentvault.backend.security.auth.repository;

import com.aidocumentvault.backend.security.auth.entity.RefreshToken;
import com.aidocumentvault.backend.user.entity.User;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    List<RefreshToken> findAllByUser(User user);

    List<RefreshToken> findAllByUserAndRevokedAtIsNull(User user);

    void deleteAllByUser(User user);

    long countByUserAndRevokedAtIsNullAndExpiresAtAfter(User user, OffsetDateTime now);
}
