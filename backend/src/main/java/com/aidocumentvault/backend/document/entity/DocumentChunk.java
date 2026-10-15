package com.aidocumentvault.backend.document.entity;

import com.aidocumentvault.backend.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
    name = "document_chunks",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_document_chunks_content_index",
            columnNames = {"document_content_id", "chunk_index"}
        )
    }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DocumentChunk extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_content_id", nullable = false)
    private DocumentContent documentContent;

    @Column(name = "chunk_index", nullable = false)
    private Integer chunkIndex;

    @Column(name = "text", nullable = false, columnDefinition = "TEXT")
    private String text;

    @Column(name = "start_char_index", nullable = false)
    private Integer startCharIndex;

    @Column(name = "end_char_index", nullable = false)
    private Integer endCharIndex;

    @Column(name = "character_count", nullable = false)
    private Integer characterCount;

    @Column(name = "estimated_token_count")
    private Integer estimatedTokenCount;

    @Column(name = "chunking_strategy", nullable = false, length = 100)
    private String chunkingStrategy;

    @Column(name = "overlap_previous_chars", nullable = false)
    private Integer overlapPreviousChars;

    @Column(name = "overlap_next_chars", nullable = false)
    private Integer overlapNextChars;

    @Enumerated(EnumType.STRING)
    @Column(name = "embedding_status", nullable = false, length = 50)
    private EmbeddingStatus embeddingStatus;

    @OneToOne(
        mappedBy = "documentChunk",
        fetch = FetchType.LAZY,
        cascade = CascadeType.ALL,
        orphanRemoval = true
    )
    private DocumentChunkEmbedding embedding;

    public DocumentChunk(
        DocumentContent documentContent,
        Integer chunkIndex,
        String text,
        int startCharIndex,
        int endCharIndex,
        Integer estimatedTokenCount,
        String chunkingStrategy,
        Integer overlapPreviousChars,
        Integer overlapNextChars
    ) {
        if (documentContent == null) {
            throw new IllegalArgumentException("DocumentContent must not be null");
        }
        if (chunkIndex == null || chunkIndex < 0) {
            throw new IllegalArgumentException("Chunk index must be greater than or equal to 0");
        }
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Chunk text must not be blank");
        }
        if (startCharIndex < 0) {
            throw new IllegalArgumentException(
                "Start char index must be greater than or equal to 0"
            );
        }

        if (endCharIndex <= startCharIndex) {
            throw new IllegalArgumentException(
                "End char index must be greater than start char index"
            );
        }
        if (chunkingStrategy == null || chunkingStrategy.isBlank()) {
            throw new IllegalArgumentException("Chunking strategy must not be blank");
        }

        this.documentContent = documentContent;
        this.chunkIndex = chunkIndex;
        this.text = text;
        this.startCharIndex = startCharIndex;
        this.endCharIndex = endCharIndex;
        this.characterCount = text.length();
        this.estimatedTokenCount = estimatedTokenCount;
        this.chunkingStrategy = chunkingStrategy;
        this.overlapPreviousChars = overlapPreviousChars != null ? overlapPreviousChars : 0;
        this.overlapNextChars = overlapNextChars != null ? overlapNextChars : 0;
        this.embeddingStatus = EmbeddingStatus.NOT_CREATED;
    }

    public void setDocumentContent(DocumentContent documentContent) {
        this.documentContent = documentContent;
    }

    public void markEmbeddingPending() {
        this.embeddingStatus = EmbeddingStatus.PENDING;
    }

    public void markEmbeddingInProgress() {
        this.embeddingStatus = EmbeddingStatus.IN_PROGRESS;
    }

    public void markEmbeddingReady() {
        this.embeddingStatus = EmbeddingStatus.READY;
    }

    public void markEmbeddingFailed() {
        this.embeddingStatus = EmbeddingStatus.FAILED;
    }

    public void markEmbeddingNotCreated() {
        this.embeddingStatus = EmbeddingStatus.NOT_CREATED;
    }

    public void attachEmbedding(DocumentChunkEmbedding embedding) {
        if (embedding == null) {
            throw new IllegalArgumentException("embedding must not be null");
        }

        this.embedding = embedding;

        if (embedding.getDocumentChunk() != this) {
            embedding.setDocumentChunk(this);
        }

        markEmbeddingReady();
    }

    public void detachEmbedding() {
        if (this.embedding == null) {
            return;
        }

        DocumentChunkEmbedding oldEmbedding = this.embedding;
        this.embedding = null;

        if (oldEmbedding.getDocumentChunk() == this) {
            oldEmbedding.clearDocumentChunk();
        }

        markEmbeddingNotCreated();
    }
}
