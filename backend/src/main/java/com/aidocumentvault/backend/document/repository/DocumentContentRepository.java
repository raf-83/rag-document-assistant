package com.aidocumentvault.backend.document.repository;

import com.aidocumentvault.backend.document.entity.DocumentContent;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface DocumentContentRepository extends JpaRepository<DocumentContent, UUID> {

    Optional<DocumentContent> findByDocumentId(UUID documentId);

    @EntityGraph(attributePaths = "document")
    Optional<DocumentContent> findByDocumentIdAndDocumentOwnerId(UUID documentId, UUID ownerId);

    Optional<DocumentContent> findByIdAndDocumentIdAndDocumentOwnerId(UUID id, UUID documentId, UUID ownerId );
}
