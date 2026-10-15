ALTER TABLE document_chunks
  ALTER COLUMN start_char_index SET NOT NULL,
ALTER COLUMN end_char_index SET NOT NULL;

ALTER TABLE document_chunks
  ADD CONSTRAINT chk_document_chunks_start_char_index_non_negative
    CHECK (start_char_index >= 0);

ALTER TABLE document_chunks
  ADD CONSTRAINT chk_document_chunks_end_char_index_after_start
    CHECK (end_char_index > start_char_index);
