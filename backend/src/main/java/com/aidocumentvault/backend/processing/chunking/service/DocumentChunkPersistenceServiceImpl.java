package com.aidocumentvault.backend.processing.chunking.service;

import com.aidocumentvault.backend.document.entity.DocumentChunk;
import com.aidocumentvault.backend.document.entity.DocumentContent;
import com.aidocumentvault.backend.document.repository.DocumentChunkRepository;
import com.aidocumentvault.backend.processing.chunking.config.ChunkingProperties;
import com.aidocumentvault.backend.processing.chunking.dto.ChunkCandidate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DocumentChunkPersistenceServiceImpl implements DocumentChunkPersistenceService {

    private final DocumentChunkRepository documentChunkRepository;
    private final ChunkingProperties chunkingProperties;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int replaceChunks(DocumentContent documentContent, List<ChunkCandidate> chunkCandidates) {
        documentChunkRepository.deleteAllByDocumentContentId(documentContent.getId());
        documentChunkRepository.flush();

        List<DocumentChunk> chunks = chunkCandidates.stream()
            .map(chunkCandidate -> toDocumentChunk(documentContent, chunkCandidate))
            .toList();

        documentChunkRepository.saveAll(chunks);
        documentChunkRepository.flush();

        return chunks.size();
    }

    private DocumentChunk toDocumentChunk(DocumentContent documentContent, ChunkCandidate chunkCandidate) {
        return new DocumentChunk(
                documentContent,
                chunkCandidate.chunkIndex(),
                chunkCandidate.text(),
                chunkCandidate.startCharIndex(),
                chunkCandidate.endCharIndex(),
                chunkCandidate.estimatedTokenCount(),
                chunkingProperties.strategy(),
                chunkCandidate.overlapPreviousChars(),
                chunkCandidate.overlapNextChars()
        );
    }
}
