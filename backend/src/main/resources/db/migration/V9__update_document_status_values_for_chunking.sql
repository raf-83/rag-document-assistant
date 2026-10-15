ALTER TABLE documents
DROP CONSTRAINT IF EXISTS chk_documents_status;

ALTER TABLE documents
  ADD CONSTRAINT chk_documents_status
    CHECK (status IN (
                      'UPLOADED',
                      'TEXT_PROCESSING',
                      'CONTENT_READY',
                      'CHUNKING',
                      'READY',
                      'FAILED'
      ));

ALTER TABLE document_chunks
  ADD COLUMN IF NOT EXISTS character_count INTEGER;

ALTER TABLE document_chunks
  ADD COLUMN IF NOT EXISTS estimated_token_count INTEGER;

ALTER TABLE document_chunks
  ADD COLUMN IF NOT EXISTS chunking_strategy VARCHAR(100);

ALTER TABLE document_chunks
  ADD COLUMN IF NOT EXISTS overlap_previous_chars INTEGER NOT NULL DEFAULT 0;

ALTER TABLE document_chunks
  ADD COLUMN IF NOT EXISTS overlap_next_chars INTEGER NOT NULL DEFAULT 0;

ALTER TABLE document_chunks
  ADD COLUMN IF NOT EXISTS embedding_status VARCHAR(50) NOT NULL DEFAULT 'NOT_CREATED';


UPDATE document_chunks
SET character_count = length(text)
WHERE character_count IS NULL;

UPDATE document_chunks
SET chunking_strategy = 'PARAGRAPH_AWARE_V1'
WHERE chunking_strategy IS NULL;

ALTER TABLE document_chunks
  ALTER COLUMN character_count SET NOT NULL;

ALTER TABLE document_chunks
  ALTER COLUMN chunking_strategy SET NOT NULL;
