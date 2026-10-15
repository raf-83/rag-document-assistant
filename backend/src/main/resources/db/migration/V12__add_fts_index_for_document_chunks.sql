CREATE INDEX IF NOT EXISTS idx_document_chunks_text_fts
  ON document_chunks
  USING gin (to_tsvector('simple', coalesce(text, '')));
