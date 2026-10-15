package com.aidocumentvault.backend.ai.embedding.event;

import com.aidocumentvault.backend.ai.embedding.service.EmbeddingGenerationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class DocumentChunksReadyForEmbeddingEventListener {

    private final EmbeddingGenerationService embeddingGenerationService;

    @Async
    @EventListener
    public void handle(DocumentChunksReadyForEmbeddingEvent event) {
        log.info(
            "DOCUMENT_CHUNKS_READY_FOR_EMBEDDING_EVENT_RECEIVED documentId={}, documentContentId={}, userId={}, chunkCount={}",
            event.documentId(),
            event.documentContentId(),
            event.userId(),
            event.chunkCount()
        );

        embeddingGenerationService.generateEmbeddingsForDocumentContent(
            event.documentId(),
            event.documentContentId(),
            event.userId()
        );
    }
}
