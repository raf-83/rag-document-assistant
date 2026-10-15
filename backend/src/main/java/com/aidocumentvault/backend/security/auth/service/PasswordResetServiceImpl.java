package com.aidocumentvault.backend.security.auth.service;

import com.aidocumentvault.backend.config.properties.SecurityProperties;
import com.aidocumentvault.backend.security.auth.entity.PasswordResetToken;
import com.aidocumentvault.backend.security.auth.repository.PasswordResetTokenRepository;
import com.aidocumentvault.backend.user.entity.User;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class PasswordResetServiceImpl implements PasswordResetService {

    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final SecurityProperties securityProperties;

    @Override
    public String createResetToken(User user) {
        String rawToken = UUID.randomUUID() + "." + UUID.randomUUID();
        String tokenHash = hash(rawToken);

        PasswordResetToken resetToken = new PasswordResetToken(
                user,
                tokenHash,
                OffsetDateTime.now().plusSeconds(securityProperties.getPasswordResetToken().getExpirationSeconds())
        );

        passwordResetTokenRepository.save(resetToken);
        return rawToken;
    }

    @Override
    @Transactional(readOnly = true)
    public PasswordResetToken findValidToken(String rawToken) {
        String tokenHash = hash(rawToken);

        PasswordResetToken token = passwordResetTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new IllegalArgumentException("Nieprawidłowy token resetu hasła."));

        if (!token.canBeUsed(OffsetDateTime.now())) {
            throw new IllegalStateException("Token resetu hasła jest nieważny albo wygasł.");
        }

        return token;
    }

    private String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hashed);
        } catch (Exception exception) {
            throw new IllegalStateException("Nie udało się obliczyć hash tokena.", exception);
        }
    }
}
