package com.aidocumentvault.backend.processing.chunking.event;

import com.aidocumentvault.backend.processing.chunking.service.DocumentChunkingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class DocumentContentReadyEventListener {

    private final DocumentChunkingService documentChunkingService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(DocumentContentReadyEvent event) {
        log.info(
            "DOCUMENT_CONTENT_READY_EVENT_RECEIVED documentId={}, documentContentId={}, userId={}",
            event.documentId(),
            event.documentContentId(),
            event.userId()
        );

        documentChunkingService.chunkDocumentContent(
            event.documentId(),
            event.documentContentId(),
            event.userId()
        );
    }
}
