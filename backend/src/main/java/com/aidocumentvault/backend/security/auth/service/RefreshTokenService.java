package com.aidocumentvault.backend.security.auth.service;

import com.aidocumentvault.backend.security.auth.entity.RefreshToken;
import com.aidocumentvault.backend.user.entity.User;
import java.util.List;

public interface RefreshTokenService {

    String createRefreshToken(User user);

    RefreshToken findValidToken(String rawRefreshToken);

    void revoke(RefreshToken refreshToken);

    void revokeAllActiveTokens(User user);

    List<RefreshToken> findAllActiveTokens(User user);
}
