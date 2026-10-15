package com.aidocumentvault.backend.processing.chunking.service;

import com.aidocumentvault.backend.ai.embedding.event.DocumentChunksReadyForEmbeddingEvent;
import com.aidocumentvault.backend.document.entity.DocumentContent;
import com.aidocumentvault.backend.document.repository.DocumentContentRepository;
import com.aidocumentvault.backend.processing.chunking.dto.ChunkCandidate;
import com.aidocumentvault.backend.processing.chunking.strategy.TextChunker;
import com.aidocumentvault.backend.processing.service.DocumentProcessingStatusService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentChunkingServiceImpl implements DocumentChunkingService {

    private final DocumentContentRepository documentContentRepository;
    private final TextChunker textChunker;
    private final DocumentChunkPersistenceService documentChunkPersistenceService;
    private final DocumentProcessingStatusService documentProcessingStatusService;
    private final ApplicationEventPublisher applicationEventPublisher;

    @Override
    public void chunkDocumentContent(UUID documentId, UUID documentContentId, UUID userId) {
        log.info(
            "DOCUMENT_CHUNKING_STARTED documentId={}, documentContentId={}, userId={}",
            documentId,
            documentContentId,
            userId
        );

        try {
            documentProcessingStatusService.markAsChunking(documentId);

            DocumentContent documentContent = findDocumentContent(
                documentContentId,
                documentId,
                userId
            );

            List<ChunkCandidate> chunkCandidates = textChunker.chunk(
                documentContent.getSanitizedText()
            );

            if (chunkCandidates.isEmpty()) {
                throw new IllegalStateException("No chunks generated for documentContentId=" + documentContentId);
            }

            int savedChunkCount = documentChunkPersistenceService.replaceChunks(
                documentContent,
                chunkCandidates
            );

            documentProcessingStatusService.markAsReady(documentId);

            applicationEventPublisher.publishEvent(
                new DocumentChunksReadyForEmbeddingEvent(
                    documentId,
                    documentContentId,
                    userId,
                    savedChunkCount
                )
            );


            log.info(
                "DOCUMENT_CHUNKING_SUCCESS documentId={}, documentContentId={}, userId={}, chunkCount={}",
                documentId,
                documentContentId,
                userId,
                savedChunkCount
            );
        } catch (Exception exception) {
            documentProcessingStatusService.markAsFailed(documentId);

            log.error(
                "DOCUMENT_CHUNKING_FAILED documentId={}, documentContentId={}, userId={}, errorType={}",
                documentId,
                documentContentId,
                userId,
                exception.getClass().getSimpleName(),
                exception
            );
        }
    }

    private DocumentContent findDocumentContent(UUID documentContentId, UUID documentId, UUID userId) {
        return documentContentRepository.findByIdAndDocumentIdAndDocumentOwnerId(
                documentContentId,
                documentId,
                userId
            )
            .orElseThrow(() -> new IllegalStateException(
                "Document content not found for documentId=" + documentId
            ));
    }
}
