package com.aidocumentvault.backend.security.auth.repository;

import com.aidocumentvault.backend.security.auth.entity.PasswordResetToken;
import com.aidocumentvault.backend.user.entity.User;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    List<PasswordResetToken> findAllByUser(User user);

    List<PasswordResetToken> findAllByUserAndUsedAtIsNull(User user);

    void deleteAllByUser(User user);
}
