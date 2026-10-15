CREATE INDEX idx_document_chunk_embeddings_embedding_hnsw
  ON document_chunk_embeddings
  USING hnsw (embedding vector_cosine_ops);
