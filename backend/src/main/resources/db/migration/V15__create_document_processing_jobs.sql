CREATE TABLE document_processing_jobs (
                                        id UUID PRIMARY KEY,
                                        document_id UUID NOT NULL,
                                        correlation_id UUID NOT NULL,
                                        status VARCHAR(30) NOT NULL,
                                        created_at TIMESTAMPTZ NOT NULL,
                                        updated_at TIMESTAMPTZ NOT NULL,

                                        CONSTRAINT fk_document_processing_jobs_document
                                          FOREIGN KEY (document_id) REFERENCES documents (id) ON DELETE CASCADE,

                                        CONSTRAINT chk_document_processing_jobs_status
                                          CHECK (status IN ('PENDING', 'RUNNING', 'SUCCEEDED', 'FAILED'))
);

CREATE INDEX idx_document_processing_jobs_document_id
  ON document_processing_jobs (document_id);

CREATE UNIQUE INDEX uq_document_processing_jobs_active_document
  ON document_processing_jobs (document_id)
  WHERE status IN ('PENDING', 'RUNNING');
