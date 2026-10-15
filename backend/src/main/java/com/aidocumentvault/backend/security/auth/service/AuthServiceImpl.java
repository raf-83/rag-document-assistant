package com.aidocumentvault.backend.security.auth.service;

import com.aidocumentvault.backend.common.api.ApiMessageResponse;
import com.aidocumentvault.backend.security.auth.dto.AuthResponse;
import com.aidocumentvault.backend.security.auth.dto.ForgotPasswordRequest;
import com.aidocumentvault.backend.security.auth.dto.LoginRequest;
import com.aidocumentvault.backend.security.auth.dto.LogoutRequest;
import com.aidocumentvault.backend.security.auth.dto.RefreshTokenRequest;
import com.aidocumentvault.backend.security.auth.dto.RegisterRequest;
import com.aidocumentvault.backend.security.auth.dto.ResendVerificationEmailRequest;
import com.aidocumentvault.backend.security.auth.dto.ResetPasswordRequest;
import com.aidocumentvault.backend.security.auth.entity.EmailVerificationToken;
import com.aidocumentvault.backend.security.auth.entity.PasswordResetToken;
import com.aidocumentvault.backend.security.auth.entity.RefreshToken;
import com.aidocumentvault.backend.user.entity.User;
import com.aidocumentvault.backend.user.entity.UserStatus;
import com.aidocumentvault.backend.security.auth.event.UserRegisteredEvent;
import com.aidocumentvault.backend.user.repository.UserRepository;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final EmailVerificationService emailVerificationService;
    private final PasswordResetService passwordResetService;
    private final AuthMailService authMailService;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public ApiMessageResponse register(RegisterRequest request) {
        String normalizedEmail = normalizeEmail(request.getEmail());

        if (userRepository.existsByEmail(normalizedEmail)) {
            log.warn("SECURITY_EVENT=REGISTER_FAILED email={} reason=EMAIL_ALREADY_EXISTS", normalizedEmail);
            throw new IllegalArgumentException("Użytkownik z takim adresem email już istnieje.");
        }

        User user = new User(
                normalizedEmail,
                request.getFirstName(),
                request.getLastName(),
                passwordEncoder.encode(request.getPassword()),
                UserStatus.PENDING_VERIFICATION
        );

        userRepository.save(user);

        String rawVerificationToken = emailVerificationService.createVerificationToken(user);

        eventPublisher.publishEvent(new UserRegisteredEvent(
            user.getId(),
            user.getEmail(),
            rawVerificationToken
        ));



        return new ApiMessageResponse("Konto zostało utworzone. Sprawdź email i potwierdź adres.");
    }

    @Override
    @Transactional
    public AuthResponse login(LoginRequest request) {
        String normalizedEmail = normalizeEmail(request.getEmail());

        User user = userRepository.findByEmail(normalizedEmail)
            .orElseThrow(() -> {
                log.warn("SECURITY_EVENT=LOGIN_FAILED email={} reason=INVALID_CREDENTIALS", normalizedEmail);
                return new IllegalArgumentException("Nieprawidłowy email lub hasło.");
            });

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            log.warn("SECURITY_EVENT=LOGIN_FAILED email={} reason=INVALID_CREDENTIALS", normalizedEmail);
            throw new IllegalArgumentException("Nieprawidłowy email lub hasło.");
        }

        if (user.getStatus() == UserStatus.PENDING_VERIFICATION) {
            log.warn("SECURITY_EVENT=LOGIN_FAILED userId={} email={} reason=PENDING_VERIFICATION", user.getId(), user.getEmail());
            throw new IllegalStateException("Konto nie zostało jeszcze potwierdzone.");
        }

        if (user.getStatus() == UserStatus.DISABLED) {
            log.warn("SECURITY_EVENT=LOGIN_FAILED userId={} email={} reason=DISABLED", user.getId(), user.getEmail());
            throw new IllegalStateException("Konto jest wyłączone.");
        }

        user.markLoginSuccess(OffsetDateTime.now());

        String accessToken = jwtService.generateAccessToken(user);
        String refreshToken = refreshTokenService.createRefreshToken(user);

        log.info("SECURITY_EVENT=LOGIN_SUCCESS userId={} ", user.getId());

        return new AuthResponse(
                accessToken,
                refreshToken,
                "Bearer",
                jwtService.getAccessTokenExpirationSeconds()
        );
    }

    @Override
    public AuthResponse refresh(RefreshTokenRequest request) {
        RefreshToken currentToken = refreshTokenService.findValidToken(request.getRefreshToken());
        User user = currentToken.getUser();

        if (user.getStatus() != UserStatus.ACTIVE) {
            log.warn("SECURITY_EVENT=REFRESH_FAILED userId={} reason=USER_NOT_ACTIVE", user.getId());
            throw new IllegalStateException("Użytkownik nie jest aktywny.");
        }

        currentToken.revoke(OffsetDateTime.now());

        String newAccessToken = jwtService.generateAccessToken(user);
        String newRefreshToken = refreshTokenService.createRefreshToken(user);

        log.info("SECURITY_EVENT=REFRESH_SUCCESS userId={}", user.getId());

        return new AuthResponse(
                newAccessToken,
                newRefreshToken,
                "Bearer",
                jwtService.getAccessTokenExpirationSeconds()
        );
    }

    @Override
    public ApiMessageResponse logout(LogoutRequest request) {
        RefreshToken refreshToken = refreshTokenService.findValidToken(request.getRefreshToken());
        refreshTokenService.revoke(refreshToken);

        log.info("SECURITY_EVENT=LOGOUT_SUCCESS userId={}", refreshToken.getUser().getId());

        return new ApiMessageResponse("Wylogowano pomyślnie.");
    }

    @Override
    public ApiMessageResponse verifyEmail(String token) {
        EmailVerificationToken verificationToken = emailVerificationService.findValidToken(token);
        User user = verificationToken.getUser();

        verificationToken.markAsUsed(OffsetDateTime.now());
        user.markEmailVerified(OffsetDateTime.now());

        log.info("SECURITY_EVENT=VERIFY_EMAIL_SUCCESS userId={}", user.getId());

        return new ApiMessageResponse("Adres email został potwierdzony.");
    }

    @Override
    public ApiMessageResponse resendVerificationEmail(ResendVerificationEmailRequest request) {
        String normalizedEmail = normalizeEmail(request.getEmail());

        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> {
                    log.warn("SECURITY_EVENT=LOGIN_FAILED email={} reason=USER_NOT_FOUND", normalizedEmail);
                    return new IllegalArgumentException("Nie znaleziono użytkownika.");
                });

        if (user.getStatus() != UserStatus.PENDING_VERIFICATION) {
            log.warn("SECURITY_EVENT=LOGIN_FAILED USERid={} reason=ACOUNT_IS_ACTIVE_OR_DONT_NEED_VERIFICATION", user.getId());
            return new ApiMessageResponse("Konto jest już aktywne albo nie wymaga ponownej weryfikacji.");
        }

        String rawVerificationToken = emailVerificationService.createVerificationToken(user);
        authMailService.sendVerificationEmail(user.getId(), user.getEmail(), rawVerificationToken);

        log.info("SECURITY_EVENT=RESEND_VERIFICATION_EMAIL_PREPARED userId={}", user.getId());

        return new ApiMessageResponse("Wysłano ponownie email weryfikacyjny.");
    }

    @Override
    public ApiMessageResponse forgotPassword(ForgotPasswordRequest request) {
        String normalizedEmail = normalizeEmail(request.getEmail());

        userRepository.findByEmail(normalizedEmail).ifPresent(user -> {
            String rawResetToken = passwordResetService.createResetToken(user);
            authMailService.sendPasswordResetEmail(user, rawResetToken);
            log.info("SECURITY_EVENT=FORGOT_PASSWORD_EMAIL_PREPARED userId={}", user.getId());
        });

        return new ApiMessageResponse("Jeśli konto istnieje, został wysłany email do resetu hasła.");
    }

    @Override
    public ApiMessageResponse resetPassword(ResetPasswordRequest request) {
        PasswordResetToken resetToken = passwordResetService.findValidToken(request.getToken());
        User user = resetToken.getUser();

        user.changePasswordHash(passwordEncoder.encode(request.getNewPassword()));
        resetToken.markAsUsed(OffsetDateTime.now());
        refreshTokenService.revokeAllActiveTokens(user);

        log.info("SECURITY_EVENT=PASSWORD_RESET_SUCCESS userId={}", user.getId());

        return new ApiMessageResponse("Hasło zostało zmienione. Zaloguj się ponownie.");
    }

    private String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }
}
