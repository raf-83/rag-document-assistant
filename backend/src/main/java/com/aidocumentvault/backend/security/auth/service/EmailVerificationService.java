package com.aidocumentvault.backend.security.auth.service;

import com.aidocumentvault.backend.security.auth.entity.EmailVerificationToken;
import com.aidocumentvault.backend.user.entity.User;

public interface EmailVerificationService {

    String createVerificationToken(User user);

    EmailVerificationToken findValidToken(String rawToken);
}
