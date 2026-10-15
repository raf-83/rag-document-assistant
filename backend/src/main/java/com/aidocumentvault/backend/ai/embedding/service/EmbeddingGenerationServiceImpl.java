package com.aidocumentvault.backend.ai.embedding.service;

import com.aidocumentvault.backend.ai.config.AiEmbeddingProperties;
import com.aidocumentvault.backend.ai.embedding.EmbeddingClient;
import com.aidocumentvault.backend.ai.embedding.EmbeddingVector;
import com.aidocumentvault.backend.ai.embedding.exception.EmbeddingGenerationException;
import com.aidocumentvault.backend.document.entity.DocumentChunk;
import com.aidocumentvault.backend.document.entity.EmbeddingStatus;
import com.aidocumentvault.backend.document.repository.DocumentChunkRepository;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmbeddingGenerationServiceImpl implements EmbeddingGenerationService {

    private static final Set<EmbeddingStatus> EMBEDDING_STATUSES_TO_PROCESS =
        EnumSet.of(EmbeddingStatus.NOT_CREATED);

    private final DocumentChunkRepository documentChunkRepository;
    private final AiEmbeddingProperties aiEmbeddingProperties;
    private final EmbeddingClient embeddingClient;
    private final DocumentChunkEmbeddingPersistenceService embeddingPersistenceService;

    @Override
    public void generateEmbeddingsForDocumentContent(
        UUID documentId,
        UUID documentContentId,
        UUID userId
    ) {
        log.info(
            "DOCUMENT_EMBEDDING_GENERATION_STARTED documentId={}, documentContentId={}, userId={}, model={}, dimensions={}, batchSize={}",
            documentId,
            documentContentId,
            userId,
            aiEmbeddingProperties.model(),
            aiEmbeddingProperties.dimensions(),
            aiEmbeddingProperties.batchSize()
        );

        List<DocumentChunk> chunks = documentChunkRepository.findAllForEmbeddingGeneration(
            documentId,
            documentContentId,
            userId,
            EMBEDDING_STATUSES_TO_PROCESS
        );

        if (chunks.isEmpty()) {
            log.info(
                "DOCUMENT_EMBEDDING_GENERATION_SKIPPED_NO_CHUNKS documentId={}, documentContentId={}, userId={}",
                documentId,
                documentContentId,
                userId
            );
            return;
        }

        List<List<DocumentChunk>> batches = partition(
            chunks,
            aiEmbeddingProperties.batchSize()
        );

        int successfulBatches = 0;
        int failedBatches = 0;

        for (List<DocumentChunk> batch : batches) {
            boolean batchSucceeded = processBatch(
                documentId,
                documentContentId,
                userId,
                batch
            );

            if (batchSucceeded) {
                successfulBatches++;
            } else {
                failedBatches++;
            }
        }

        log.info(
            "DOCUMENT_EMBEDDING_GENERATION_FINISHED documentId={}, documentContentId={}, userId={}, chunkCount={}, successfulBatches={}, failedBatches={}",
            documentId,
            documentContentId,
            userId,
            chunks.size(),
            successfulBatches,
            failedBatches
        );
    }

    private boolean processBatch(
        UUID documentId,
        UUID documentContentId,
        UUID userId,
        List<DocumentChunk> batch
    ) {
        List<UUID> chunkIds = batch.stream()
            .map(DocumentChunk::getId)
            .toList();

        try {
            embeddingPersistenceService.markAsInProgress(chunkIds);

            List<String> texts = batch.stream()
                .map(DocumentChunk::getText)
                .toList();

            List<EmbeddingVector> vectors = embeddingClient.embed(texts);

            validateEmbeddingResponse(batch, vectors);

            embeddingPersistenceService.saveEmbeddingsAndMarkAsReady(
                batch,
                vectors,
                aiEmbeddingProperties.model(),
                aiEmbeddingProperties.dimensions()
            );

            log.info(
                "DOCUMENT_EMBEDDING_BATCH_SUCCESS documentId={}, documentContentId={}, userId={}, batchSize={}",
                documentId,
                documentContentId,
                userId,
                batch.size()
            );

            return true;
        } catch (Exception exception) {
            markBatchAsFailedSafely(
                documentId,
                documentContentId,
                userId,
                chunkIds,
                exception
            );

            return false;
        }
    }

    private void validateEmbeddingResponse(
        List<DocumentChunk> batch,
        List<EmbeddingVector> vectors
    ) {
        if (vectors == null) {
            throw new EmbeddingGenerationException(
                "Embedding client returned null response"
            );
        }

        if (vectors.size() != batch.size()) {
            throw new EmbeddingGenerationException(
                "Embedding vectors count does not match chunk count"
            );
        }

        for (EmbeddingVector vector : vectors) {
            if (vector.dimensions() != aiEmbeddingProperties.dimensions()) {
                throw new EmbeddingGenerationException(
                    "Embedding vector dimensions do not match configured dimensions"
                );
            }
        }
    }

    private void markBatchAsFailedSafely(
        UUID documentId,
        UUID documentContentId,
        UUID userId,
        List<UUID> chunkIds,
        Exception originalException
    ) {
        try {
            embeddingPersistenceService.markAsFailed(chunkIds);
        } catch (Exception statusException) {
            log.error(
                "DOCUMENT_EMBEDDING_BATCH_FAILED_STATUS_UPDATE_FAILED documentId={}, documentContentId={}, userId={}, batchSize={}, originalErrorType={}, statusErrorType={}",
                documentId,
                documentContentId,
                userId,
                chunkIds.size(),
                originalException.getClass().getSimpleName(),
                statusException.getClass().getSimpleName()
            );
            return;
        }

        log.error(
            "DOCUMENT_EMBEDDING_BATCH_FAILED documentId={}, documentContentId={}, userId={}, batchSize={}, errorType={}",
            documentId,
            documentContentId,
            userId,
            chunkIds.size(),
            originalException.getClass().getSimpleName()
        );
    }

    private List<List<DocumentChunk>> partition(
        List<DocumentChunk> chunks,
        int batchSize
    ) {
        return java.util.stream.IntStream
            .iterate(0, start -> start < chunks.size(), start -> start + batchSize)
            .mapToObj(start -> chunks.subList(
                start,
                Math.min(start + batchSize, chunks.size())
            ))
            .toList();
    }
}
