package com.aidocumentvault.backend.user.entity;

import com.aidocumentvault.backend.common.entity.BaseEntity;
import com.aidocumentvault.backend.document.entity.Document;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseEntity {

    @Column(nullable = false, unique = true, length = 320)
    private String email;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private UserStatus status;

    @Column(name = "email_verified_at")
    private OffsetDateTime emailVerifiedAt;

    @Column(name = "last_login_at")
    private OffsetDateTime lastLoginAt;

    @OneToMany(mappedBy = "owner", fetch = FetchType.LAZY)
    private List<Document> documents = new ArrayList<>();

    public User(String email, String firstName, String lastName, String passwordHash, UserStatus status) {
        this.email = email;
        this.firstName = firstName;
        this.lastName = lastName;
        this.passwordHash = passwordHash;
        this.status = status;
    }

    public void changeEmail(String email) {
        this.email = email;
    }

    public void changeFirstName(String firstName) {
        this.firstName = firstName;
    }

    public void changeLastName(String lastName) {
        this.lastName = lastName;
    }

    public void changePasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public void changeStatus(UserStatus status) {
        this.status = status;
    }

    public void markEmailVerified(OffsetDateTime verifiedAt) {
        this.emailVerifiedAt = verifiedAt;
        this.status = UserStatus.ACTIVE;
    }

    public void markLoginSuccess(OffsetDateTime loginAt) {
        this.lastLoginAt = loginAt;
    }

    public void disable() {
        this.status = UserStatus.DISABLED;
    }

    public void markPendingVerification() {
        this.status = UserStatus.PENDING_VERIFICATION;
    }
}
