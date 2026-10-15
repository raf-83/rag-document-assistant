CREATE TABLE users (
    id UUID PRIMARY KEY,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    email VARCHAR(320) NOT NULL,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL,

    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT chk_users_status
        CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE TABLE document_types (
    id UUID PRIMARY KEY,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    code VARCHAR(50) NOT NULL,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    active BOOLEAN NOT NULL,

    CONSTRAINT uq_document_types_code UNIQUE (code)
);

CREATE TABLE stored_files (
    id UUID PRIMARY KEY,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    original_file_name VARCHAR(255) NOT NULL,
    storage_key VARCHAR(500) NOT NULL,
    stored_file_name VARCHAR(255),
    mime_type VARCHAR(100) NOT NULL,
    file_extension VARCHAR(20),
    size_in_bytes BIGINT NOT NULL,
    checksum VARCHAR(128),
    storage_provider VARCHAR(30) NOT NULL,

    CONSTRAINT uq_stored_files_storage_key UNIQUE (storage_key),
    CONSTRAINT chk_stored_files_storage_provider
        CHECK (storage_provider IN ('LOCAL_PRIVATE')),
    CONSTRAINT chk_stored_files_size_in_bytes
        CHECK (size_in_bytes > 0)
);

CREATE TABLE documents (
    id UUID PRIMARY KEY,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    title VARCHAR(255) NOT NULL,
    owner_id UUID NOT NULL,
    document_type_id UUID NOT NULL,
    stored_file_id UUID NOT NULL,
    status VARCHAR(30) NOT NULL,

    CONSTRAINT uq_documents_stored_file_id UNIQUE (stored_file_id),
    CONSTRAINT fk_documents_owner
        FOREIGN KEY (owner_id) REFERENCES users (id),
    CONSTRAINT fk_documents_document_type
        FOREIGN KEY (document_type_id) REFERENCES document_types (id),
    CONSTRAINT fk_documents_stored_file
        FOREIGN KEY (stored_file_id) REFERENCES stored_files (id),
    CONSTRAINT chk_documents_status
        CHECK (status IN ('UPLOADED', 'PROCESSING', 'READY', 'FAILED'))
);

CREATE TABLE document_contents (
    id UUID PRIMARY KEY,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    document_id UUID NOT NULL,
    sanitized_text TEXT NOT NULL,
    sanitization_version VARCHAR(50),

    CONSTRAINT uq_document_contents_document_id UNIQUE (document_id),
    CONSTRAINT fk_document_contents_document
        FOREIGN KEY (document_id) REFERENCES documents (id)
);

CREATE TABLE document_chunks (
    id UUID PRIMARY KEY,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    document_content_id UUID NOT NULL,
    chunk_index INTEGER NOT NULL,
    text TEXT NOT NULL,
    start_char_index INTEGER,
    end_char_index INTEGER,

    CONSTRAINT fk_document_chunks_document_content
        FOREIGN KEY (document_content_id) REFERENCES document_contents (id),
    CONSTRAINT uq_document_chunks_content_chunk_index
        UNIQUE (document_content_id, chunk_index),
    CONSTRAINT chk_document_chunks_chunk_index
        CHECK (chunk_index >= 0)
);

CREATE TABLE document_summaries (
    id UUID PRIMARY KEY,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    document_id UUID NOT NULL,
    summary_text TEXT NOT NULL,
    summary_type VARCHAR(30) NOT NULL,
    model_name VARCHAR(100),

    CONSTRAINT fk_document_summaries_document
        FOREIGN KEY (document_id) REFERENCES documents (id),
    CONSTRAINT chk_document_summaries_summary_type
        CHECK (summary_type IN ('SHORT', 'DETAILED'))
);

CREATE INDEX idx_documents_owner_id
    ON documents (owner_id);

CREATE INDEX idx_documents_document_type_id
    ON documents (document_type_id);

CREATE INDEX idx_documents_status
    ON documents (status);

CREATE INDEX idx_document_chunks_document_content_id
    ON document_chunks (document_content_id);

CREATE INDEX idx_document_summaries_document_id
    ON document_summaries (document_id);
