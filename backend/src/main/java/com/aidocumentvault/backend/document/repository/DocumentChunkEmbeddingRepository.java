package com.aidocumentvault.backend.document.repository;

import com.aidocumentvault.backend.document.entity.DocumentChunkEmbedding;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentChunkEmbeddingRepository
    extends JpaRepository<DocumentChunkEmbedding, UUID> {

    Optional<DocumentChunkEmbedding> findByDocumentChunkId(UUID documentChunkId);

    boolean existsByDocumentChunkId(UUID documentChunkId);

    List<DocumentChunkEmbedding> findAllByDocumentChunkIdIn(
        Collection<UUID> documentChunkIds
    );

    void deleteByDocumentChunkId(UUID documentChunkId);



}
