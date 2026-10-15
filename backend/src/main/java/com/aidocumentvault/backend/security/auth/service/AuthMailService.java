package com.aidocumentvault.backend.security.auth.service;

import com.aidocumentvault.backend.user.entity.User;

import java.util.UUID;

public interface AuthMailService {

    void sendVerificationEmail(UUID userId, String email, String rawVerificationToken);

    void sendPasswordResetEmail(User user, String rawPasswordResetToken);
}
