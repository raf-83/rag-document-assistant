package com.aidocumentvault.backend.security.auth.service;

import com.aidocumentvault.backend.user.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthMailServiceImpl implements AuthMailService {

    @Override
    public void sendVerificationEmail(UUID userId, String email, String rawVerificationToken) {
        log.info(
            "SECURITY_EVENT=VERIFICATION_EMAIL_PREPARED userId={} email={}",
            userId,
            email
        );

        // TODO Etap 3/4:
        // tutaj później podepniemy prawdziwą wysyłkę maila
        // bez logowania surowego tokena
    }

    @Override
    public void sendPasswordResetEmail(User user, String rawPasswordResetToken) {
        log.info(
            "SECURITY_EVENT=PASSWORD_RESET_EMAIL_PREPARED userId={} email={}",
            user.getId(),
            user.getEmail()
        );

        // TODO Etap 3/4:
        // tutaj później podepniemy prawdziwą wysyłkę maila
        // bez logowania surowego tokena
    }
}
