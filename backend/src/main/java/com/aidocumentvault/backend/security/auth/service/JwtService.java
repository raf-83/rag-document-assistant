package com.aidocumentvault.backend.security.auth.service;

import com.aidocumentvault.backend.user.entity.User;

public interface JwtService {

    String generateAccessToken(User user);

    Long getAccessTokenExpirationSeconds();

    String extractSubject(String token);

    boolean isTokenValid(String token);
}
