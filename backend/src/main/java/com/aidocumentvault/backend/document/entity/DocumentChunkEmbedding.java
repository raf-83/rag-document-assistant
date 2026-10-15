package com.aidocumentvault.backend.document.entity;

import com.aidocumentvault.backend.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Array;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Entity
@Table(
    name = "document_chunk_embeddings",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uq_document_chunk_embeddings_chunk_id",
            columnNames = "document_chunk_id"
        )
    }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DocumentChunkEmbedding extends BaseEntity {

    public static final int SUPPORTED_DIMENSIONS = 1536;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_chunk_id", nullable = false, unique = true)
    private DocumentChunk documentChunk;

    @JdbcTypeCode(SqlTypes.VECTOR)
    @Array(length = SUPPORTED_DIMENSIONS)
    @Column(name = "embedding", nullable = false, columnDefinition = "vector(1536)")
    private float[] embedding;

    @Column(name = "model", nullable = false, length = 100)
    private String model;

    @Column(name = "dimensions", nullable = false)
    private Integer dimensions;

    @Column(name = "generated_at", nullable = false)
    private OffsetDateTime generatedAt;

    public DocumentChunkEmbedding(
        DocumentChunk documentChunk,
        float[] embedding,
        String model,
        Integer dimensions
    ) {
        setDocumentChunk(documentChunk);
        setEmbedding(embedding, dimensions);
        setModel(model);
        this.generatedAt = OffsetDateTime.now();

        documentChunk.attachEmbedding(this);
    }

    public float[] getEmbedding() {
        return Arrays.copyOf(embedding, embedding.length);
    }

    public void replaceEmbedding(
        float[] embedding,
        String model,
        Integer dimensions
    ) {
        setEmbedding(embedding, dimensions);
        setModel(model);
        this.generatedAt = OffsetDateTime.now();

        if (documentChunk != null) {
            documentChunk.markEmbeddingReady();
        }
    }

    void setDocumentChunk(DocumentChunk documentChunk) {
        this.documentChunk = Objects.requireNonNull(
            documentChunk,
            "documentChunk must not be null"
        );
    }

    void clearDocumentChunk() {
        this.documentChunk = null;
    }

    private void setEmbedding(float[] embedding, Integer dimensions) {
        if (embedding == null || embedding.length == 0) {
            throw new IllegalArgumentException("embedding must not be empty");
        }

        if (dimensions == null) {
            throw new IllegalArgumentException("dimensions must not be null");
        }

        if (dimensions != SUPPORTED_DIMENSIONS) {
            throw new IllegalArgumentException(
                "dimensions must be " + SUPPORTED_DIMENSIONS
            );
        }

        if (embedding.length != dimensions) {
            throw new IllegalArgumentException(
                "embedding length must match dimensions"
            );
        }

        this.embedding = Arrays.copyOf(embedding, embedding.length);
        this.dimensions = dimensions;
    }

    private void setModel(String model) {
        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("model must not be blank");
        }

        this.model = model.trim();
    }
}
