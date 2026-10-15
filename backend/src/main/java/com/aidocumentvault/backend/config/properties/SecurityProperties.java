package com.aidocumentvault.backend.config.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.security")
public class SecurityProperties {

    private Jwt jwt = new Jwt();
    private RefreshToken refreshToken = new RefreshToken();
    private EmailVerificationToken emailVerificationToken = new EmailVerificationToken();
    private PasswordResetToken passwordResetToken = new PasswordResetToken();

    @Getter
    @Setter
    public static class Jwt {
        private String secret;
        private Long accessTokenExpirationSeconds;
    }

    @Getter
    @Setter
    public static class RefreshToken {
        private Long expirationSeconds;
    }

    @Getter
    @Setter
    public static class EmailVerificationToken {
        private Long expirationSeconds;
    }

    @Getter
    @Setter
    public static class PasswordResetToken {
        private Long expirationSeconds;
    }
}
