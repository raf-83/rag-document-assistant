package com.aidocumentvault.backend.ai.embedding.service;

import com.aidocumentvault.backend.ai.embedding.EmbeddingVector;
import com.aidocumentvault.backend.ai.embedding.exception.EmbeddingGenerationException;
import com.aidocumentvault.backend.document.entity.DocumentChunk;
import com.aidocumentvault.backend.document.entity.DocumentChunkEmbedding;
import com.aidocumentvault.backend.document.entity.EmbeddingStatus;
import com.aidocumentvault.backend.document.repository.DocumentChunkEmbeddingRepository;
import com.aidocumentvault.backend.document.repository.DocumentChunkRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DocumentChunkEmbeddingPersistenceServiceImpl
    implements DocumentChunkEmbeddingPersistenceService {

    private final DocumentChunkRepository documentChunkRepository;
    private final DocumentChunkEmbeddingRepository documentChunkEmbeddingRepository;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markAsInProgress(List<UUID> chunkIds) {
        if (chunkIds == null || chunkIds.isEmpty()) {
            return;
        }

        documentChunkRepository.updateEmbeddingStatusByIdIn(
            chunkIds,
            EmbeddingStatus.IN_PROGRESS
        );
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveEmbeddingsAndMarkAsReady(
        List<DocumentChunk> chunks,
        List<EmbeddingVector> vectors,
        String model,
        int dimensions
    ) {
        if (chunks == null || chunks.isEmpty()) {
            return;
        }

        if (vectors == null || vectors.size() != chunks.size()) {
            throw new EmbeddingGenerationException(
                "Embedding vectors count must match chunks count"
            );
        }

        List<UUID> chunkIds = chunks.stream()
            .map(DocumentChunk::getId)
            .toList();

        Map<UUID, DocumentChunk> managedChunksById = documentChunkRepository.findAllById(chunkIds)
            .stream()
            .collect(Collectors.toMap(
                DocumentChunk::getId,
                Function.identity(),
                (left, right) -> left,
                LinkedHashMap::new
            ));

        if (managedChunksById.size() != chunkIds.size()) {
            throw new EmbeddingGenerationException(
                "Some document chunks were not found while saving embeddings"
            );
        }

        Map<UUID, DocumentChunkEmbedding> existingEmbeddingsByChunkId =
            documentChunkEmbeddingRepository.findAllByDocumentChunkIdIn(chunkIds)
                .stream()
                .collect(Collectors.toMap(
                    embedding -> embedding.getDocumentChunk().getId(),
                    Function.identity()
                ));

        for (int index = 0; index < chunks.size(); index++) {
            UUID chunkId = chunks.get(index).getId();
            DocumentChunk managedChunk = managedChunksById.get(chunkId);
            EmbeddingVector vector = vectors.get(index);

            DocumentChunkEmbedding existingEmbedding = existingEmbeddingsByChunkId.get(chunkId);

            if (existingEmbedding == null) {
                documentChunkEmbeddingRepository.save(
                    new DocumentChunkEmbedding(
                        managedChunk,
                        vector.values(),
                        model,
                        dimensions
                    )
                );
            } else {
                existingEmbedding.replaceEmbedding(
                    vector.values(),
                    model,
                    dimensions
                );
            }
        }

        documentChunkRepository.flush();
        documentChunkEmbeddingRepository.flush();
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markAsFailed(List<UUID> chunkIds) {
        if (chunkIds == null || chunkIds.isEmpty()) {
            return;
        }

        documentChunkRepository.updateEmbeddingStatusByIdIn(
            chunkIds,
            EmbeddingStatus.FAILED
        );
    }
}
