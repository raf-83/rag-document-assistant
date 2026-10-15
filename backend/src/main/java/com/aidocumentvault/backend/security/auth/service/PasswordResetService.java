package com.aidocumentvault.backend.security.auth.service;

import com.aidocumentvault.backend.security.auth.entity.PasswordResetToken;
import com.aidocumentvault.backend.user.entity.User;

public interface PasswordResetService {

    String createResetToken(User user);

    PasswordResetToken findValidToken(String rawToken);
}
