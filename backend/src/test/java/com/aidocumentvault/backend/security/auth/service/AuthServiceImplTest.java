package com.aidocumentvault.backend.security.auth.service;

import com.aidocumentvault.backend.security.auth.dto.RegisterRequest;
import com.aidocumentvault.backend.security.auth.event.UserRegisteredEvent;
import com.aidocumentvault.backend.user.entity.User;
import com.aidocumentvault.backend.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private EmailVerificationService emailVerificationService;

    @Mock
    private PasswordResetService passwordResetService;

    @Mock
    private AuthMailService authMailService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private AuthServiceImpl authService;

    @Test
    void shouldPublishUserRegisteredEventWhenUserIsRegistered() {
        // given
        RegisterRequest request = new RegisterRequest(
            " Test@Example.com ",
            "Jan",
            "Kowalski",
            "TestPassword123!"
        );

        UUID userId = UUID.randomUUID();
        String encodedPassword = "encoded-password";
        String rawVerificationToken = "raw-verification-token";

        when(userRepository.existsByEmail("test@example.com"))
            .thenReturn(false);

        when(passwordEncoder.encode("TestPassword123!"))
            .thenReturn(encodedPassword);

        when(userRepository.save(any(User.class)))
            .thenAnswer(invocation -> {
                User user = invocation.getArgument(0);
                user.setId(userId);
                return user;
            });

        when(emailVerificationService.createVerificationToken(any(User.class)))
            .thenReturn(rawVerificationToken);

        // when
        authService.register(request);

        // then
        ArgumentCaptor<UserRegisteredEvent> eventCaptor =
            ArgumentCaptor.forClass(UserRegisteredEvent.class);

        verify(eventPublisher).publishEvent(eventCaptor.capture());

        UserRegisteredEvent publishedEvent = eventCaptor.getValue();

        assertEquals(userId, publishedEvent.userId());
        assertEquals("test@example.com", publishedEvent.email());
        assertEquals(rawVerificationToken, publishedEvent.rawVerificationToken());

        verifyNoInteractions(authMailService);
    }
}
