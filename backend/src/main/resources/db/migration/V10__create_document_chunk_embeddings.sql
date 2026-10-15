CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE document_chunk_embeddings (
    id UUID PRIMARY KEY,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,

    document_chunk_id UUID NOT NULL,
    embedding vector(1536) NOT NULL,

    model VARCHAR(100) NOT NULL,
    dimensions INTEGER NOT NULL,
    generated_at TIMESTAMPTZ NOT NULL,

    CONSTRAINT uq_document_chunk_embeddings_chunk_id
        UNIQUE (document_chunk_id),

    CONSTRAINT fk_document_chunk_embeddings_document_chunk
        FOREIGN KEY (document_chunk_id)
        REFERENCES document_chunks (id)
        ON DELETE CASCADE,

    CONSTRAINT chk_document_chunk_embeddings_dimensions
        CHECK (dimensions = 1536),

    CONSTRAINT chk_document_chunk_embeddings_vector_dimensions
        CHECK (vector_dims(embedding) = dimensions)
);

CREATE INDEX idx_document_chunk_embeddings_document_chunk_id
    ON document_chunk_embeddings (document_chunk_id);

CREATE INDEX idx_document_chunk_embeddings_model
    ON document_chunk_embeddings (model);
