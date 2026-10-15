package com.aidocumentvault.backend.security.auth.event;

import com.aidocumentvault.backend.security.auth.service.AuthMailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class UserRegisteredEventListener {

    private final AuthMailService authMailService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(UserRegisteredEvent event) {

        log.info("SECURITY_EVENT=REGISTER_SUCCESS userId={}", event.userId());

        authMailService.sendVerificationEmail(
            event.userId(),
            event.email(),
            event.rawVerificationToken()
        );
    }

}
