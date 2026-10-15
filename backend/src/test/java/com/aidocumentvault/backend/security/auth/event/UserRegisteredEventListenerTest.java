package com.aidocumentvault.backend.security.auth.event;

import com.aidocumentvault.backend.security.auth.service.AuthMailService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UserRegisteredEventListenerTest {

    @Mock
    private AuthMailService authMailService;

    @InjectMocks
    private UserRegisteredEventListener listener;

    @Test
    void shouldSendVerificationEmailWhenUserRegisteredEventIsHandled() {
        // given
        UUID userId = UUID.randomUUID();
        String email = "test@example.com";
        String rawVerificationToken = "verification-token";

        UserRegisteredEvent event = new UserRegisteredEvent(
            userId,
            email,
            rawVerificationToken
        );

        // when
        listener.handle(event);

        // then
        verify(authMailService).sendVerificationEmail(
            userId,
            email,
            rawVerificationToken
        );
    }
}
