package com.aidocumentvault.backend.security.auth.service;

import com.aidocumentvault.backend.config.properties.SecurityProperties;
import com.aidocumentvault.backend.security.auth.entity.EmailVerificationToken;
import com.aidocumentvault.backend.security.auth.repository.EmailVerificationTokenRepository;
import com.aidocumentvault.backend.user.entity.User;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class EmailVerificationServiceImpl implements EmailVerificationService {

    private final EmailVerificationTokenRepository emailVerificationTokenRepository;
    private final SecurityProperties securityProperties;

    @Override
    public String createVerificationToken(User user) {
        String rawToken = UUID.randomUUID() + "." + UUID.randomUUID();
        String tokenHash = hash(rawToken);

        EmailVerificationToken verificationToken = new EmailVerificationToken(
                user,
                tokenHash,
                OffsetDateTime.now().plusSeconds(securityProperties.getEmailVerificationToken().getExpirationSeconds())
        );

        emailVerificationTokenRepository.save(verificationToken);
        return rawToken;
    }

    @Override
    @Transactional(readOnly = true)
    public EmailVerificationToken findValidToken(String rawToken) {
        String tokenHash = hash(rawToken);

        EmailVerificationToken token = emailVerificationTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new IllegalArgumentException("Nieprawidłowy token weryfikacyjny."));

        if (!token.canBeUsed(OffsetDateTime.now())) {
            throw new IllegalStateException("Token weryfikacyjny jest nieważny albo wygasł.");
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
