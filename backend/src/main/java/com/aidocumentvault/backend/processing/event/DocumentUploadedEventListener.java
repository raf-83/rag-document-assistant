package com.aidocumentvault.backend.processing.event;

import com.aidocumentvault.backend.processing.service.DocumentProcessingService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class DocumentUploadedEventListener {

    private final DocumentProcessingService documentProcessingService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(DocumentUploadedEvent event) {
        documentProcessingService.processDocument(
                event.documentId(),
                event.userId()
        );
    }
}
