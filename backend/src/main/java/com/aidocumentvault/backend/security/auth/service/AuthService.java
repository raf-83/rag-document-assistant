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

public interface AuthService {

    ApiMessageResponse register(RegisterRequest request);

    AuthResponse login(LoginRequest request);

    AuthResponse refresh(RefreshTokenRequest request);

    ApiMessageResponse logout(LogoutRequest request);

    ApiMessageResponse verifyEmail(String token);

    ApiMessageResponse resendVerificationEmail(ResendVerificationEmailRequest request);

    ApiMessageResponse forgotPassword(ForgotPasswordRequest request);

    ApiMessageResponse resetPassword(ResetPasswordRequest request);
}
