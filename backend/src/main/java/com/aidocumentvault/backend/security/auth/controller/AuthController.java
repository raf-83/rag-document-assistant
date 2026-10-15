package com.aidocumentvault.backend.security.auth.controller;

import com.aidocumentvault.backend.common.api.ApiMessageResponse;
import com.aidocumentvault.backend.security.auth.dto.AuthResponse;
import com.aidocumentvault.backend.security.auth.dto.ForgotPasswordRequest;
import com.aidocumentvault.backend.security.auth.dto.LoginRequest;
import com.aidocumentvault.backend.security.auth.dto.LogoutRequest;
import com.aidocumentvault.backend.security.auth.dto.RefreshTokenRequest;
import com.aidocumentvault.backend.security.auth.dto.RegisterRequest;
import com.aidocumentvault.backend.security.auth.dto.ResendVerificationEmailRequest;
import com.aidocumentvault.backend.security.auth.dto.ResetPasswordRequest;
import com.aidocumentvault.backend.security.auth.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<ApiMessageResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.ok(authService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(authService.refresh(request));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiMessageResponse> logout(@Valid @RequestBody LogoutRequest request) {
        return ResponseEntity.ok(authService.logout(request));
    }

    @GetMapping("/verify-email")
    public ResponseEntity<ApiMessageResponse> verifyEmail(@RequestParam("token") String token) {
        return ResponseEntity.ok(authService.verifyEmail(token));
    }

    @PostMapping("/resend-verification-email")
    public ResponseEntity<ApiMessageResponse> resendVerificationEmail(
            @Valid @RequestBody ResendVerificationEmailRequest request) {
        return ResponseEntity.ok(authService.resendVerificationEmail(request));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<ApiMessageResponse> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request) {
        return ResponseEntity.ok(authService.forgotPassword(request));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<ApiMessageResponse> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request) {
        return ResponseEntity.ok(authService.resetPassword(request));
    }
}
