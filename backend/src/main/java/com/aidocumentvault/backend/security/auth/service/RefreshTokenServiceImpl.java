package com.aidocumentvault.backend.security.auth.service;

import com.aidocumentvault.backend.config.properties.SecurityProperties;
import com.aidocumentvault.backend.security.auth.entity.RefreshToken;
import com.aidocumentvault.backend.security.auth.repository.RefreshTokenRepository;
import com.aidocumentvault.backend.user.entity.User;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class RefreshTokenServiceImpl implements RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final SecurityProperties securityProperties;

    @Override
    public String createRefreshToken(User user) {
        String rawToken = UUID.randomUUID() + "." + UUID.randomUUID();
        String tokenHash = hash(rawToken);

        RefreshToken refreshToken = new RefreshToken(
                user,
                tokenHash,
                OffsetDateTime.now().plusSeconds(securityProperties.getRefreshToken().getExpirationSeconds())
        );

        refreshTokenRepository.save(refreshToken);
        return rawToken;
    }

    @Override
    @Transactional(readOnly = true)
    public RefreshToken findValidToken(String rawRefreshToken) {
        String tokenHash = hash(rawRefreshToken);

        RefreshToken refreshToken = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new IllegalArgumentException("Nieprawidłowy refresh token."));

        if (!refreshToken.isActive(OffsetDateTime.now())) {
            throw new IllegalStateException("Refresh token jest nieważny albo wygasł.");
        }

        return refreshToken;
    }

    @Override
    public void revoke(RefreshToken refreshToken) {
        if (!refreshToken.isRevoked()) {
            refreshToken.revoke(OffsetDateTime.now());
        }
    }

    @Override
    public void revokeAllActiveTokens(User user) {
        List<RefreshToken> activeTokens = refreshTokenRepository.findAllByUserAndRevokedAtIsNull(user);
        OffsetDateTime now = OffsetDateTime.now();

        for (RefreshToken token : activeTokens) {
            if (!token.isExpired(now)) {
                token.revoke(now);
            }
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<RefreshToken> findAllActiveTokens(User user) {
        List<RefreshToken> tokens = refreshTokenRepository.findAllByUserAndRevokedAtIsNull(user);
        OffsetDateTime now = OffsetDateTime.now();
        return tokens.stream()
                .filter(token -> !token.isExpired(now))
                .toList();
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
