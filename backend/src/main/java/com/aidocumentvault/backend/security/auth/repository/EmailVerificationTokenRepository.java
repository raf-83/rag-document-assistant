package com.aidocumentvault.backend.security.auth.repository;

import com.aidocumentvault.backend.security.auth.entity.EmailVerificationToken;
import com.aidocumentvault.backend.user.entity.User;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, UUID> {

    Optional<EmailVerificationToken> findByTokenHash(String tokenHash);

    List<EmailVerificationToken> findAllByUser(User user);

    List<EmailVerificationToken> findAllByUserAndUsedAtIsNull(User user);

    void deleteAllByUser(User user);
}
