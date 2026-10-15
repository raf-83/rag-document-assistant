package com.aidocumentvault.backend.ai.retrieval.context;

import com.aidocumentvault.backend.ai.config.AiChatProperties;
import com.aidocumentvault.backend.ai.config.AiHybridSearchProperties;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import com.aidocumentvault.backend.ai.retrieval.model.ChunkRetrievalCriteria;
import com.aidocumentvault.backend.ai.retrieval.hybrid.HybridRetrievalService;
import com.aidocumentvault.backend.ai.retrieval.model.HybridRetrievedChunk;
import com.aidocumentvault.backend.ai.retrieval.model.RetrievedDocumentChunk;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DocumentChunkRetrievalServiceImpl
    implements DocumentChunkRetrievalService {

    private final HybridRetrievalService hybridRetrievalService;
    private final AiChatProperties aiChatProperties;
    private final AiHybridSearchProperties hybridSearchProperties;

    @Override
    public List<RetrievedDocumentChunk> retrieveRelevantChunks(
        UUID documentId,
        UUID ownerId,
        String question
    ) {
        Objects.requireNonNull(
            documentId,
            "Document id must not be null"
        );

        Objects.requireNonNull(
            ownerId,
            "Owner id must not be null"
        );

        String normalizedQuestion =
            normalizeQuestion(question);

        int finalChunkLimit =
            aiChatProperties.maxRetrievedChunks();

        int retrievedChunkLimit =
            hybridSearchProperties
                .candidateLimitForChunks(finalChunkLimit);

        ChunkRetrievalCriteria criteria =
            new ChunkRetrievalCriteria(
                normalizedQuestion,
                ownerId,
                null,
                null,
                null,
                Set.of(documentId),
                retrievedChunkLimit
            );

        List<HybridRetrievedChunk> hybridChunks =
            Objects.requireNonNull(
                hybridRetrievalService.retrieve(criteria),
                "Hybrid retrieval results must not be null"
            );

        verifyDocumentScope(
            documentId,
            hybridChunks
        );

        List<RetrievedDocumentChunk> selectedChunks =
            hybridChunks
                .stream()
                .limit(finalChunkLimit)
                .map(this::mapToRetrievedChunk)
                .toList();

        return limitByContextCharacters(
            selectedChunks
        );
    }

    private String normalizeQuestion(String question) {
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException(
                "Question must not be blank"
            );
        }

        return question.trim();
    }

    private void verifyDocumentScope(
        UUID expectedDocumentId,
        List<HybridRetrievedChunk> chunks
    ) {
        for (HybridRetrievedChunk chunk : chunks) {
            if (!expectedDocumentId.equals(
                chunk.documentId()
            )) {
                throw new IllegalStateException(
                    "Hybrid retrieval returned a chunk "
                        + "outside the requested document scope"
                );
            }
        }
    }

    private RetrievedDocumentChunk mapToRetrievedChunk(
        HybridRetrievedChunk chunk
    ) {
        return new RetrievedDocumentChunk(
            chunk.chunkId(),
            chunk.chunkIndex(),
            chunk.text(),
            chunk.startCharIndex(),
            chunk.endCharIndex(),
            chunk.score()
        );
    }

    private List<RetrievedDocumentChunk>
    limitByContextCharacters(
        List<RetrievedDocumentChunk> chunks
    ) {
        List<RetrievedDocumentChunk> limitedChunks =
            new ArrayList<>();

        int usedCharacters = 0;
        int maxContextChars =
            aiChatProperties.maxContextChars();

        for (RetrievedDocumentChunk chunk : chunks) {
            int chunkLength =
                chunk.text().length();

            if (usedCharacters + chunkLength
                > maxContextChars) {

                break;
            }

            limitedChunks.add(chunk);
            usedCharacters += chunkLength;

            if (usedCharacters >= maxContextChars) {
                break;
            }
        }

        return List.copyOf(limitedChunks);
    }
}
