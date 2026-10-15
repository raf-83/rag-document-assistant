package com.aidocumentvault.backend.security.auth.entity;

import com.aidocumentvault.backend.common.entity.BaseEntity;
import com.aidocumentvault.backend.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Entity
@Table(name = "email_verification_tokens")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EmailVerificationToken extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "token_hash", nullable = false, unique = true, length = 255)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "used_at")
    private OffsetDateTime usedAt;

    public EmailVerificationToken(User user, String tokenHash, OffsetDateTime expiresAt) {
        this.user = user;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
    }

    public void markAsUsed(OffsetDateTime usedAt) {
        this.usedAt = usedAt;
    }

    public boolean isUsed() {
        return usedAt != null;
    }

    public boolean isExpired(OffsetDateTime now) {
        return expiresAt.isBefore(now);
    }

    public boolean canBeUsed(OffsetDateTime now) {
        return !isUsed() && !isExpired(now);
    }

    public boolean belongsTo(User user) {
        return this.user != null
                && user != null
                && this.user.getId() != null
                && this.user.getId().equals(user.getId());
    }
}
