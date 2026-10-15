package com.aidocumentvault.backend.document.repository;

import com.aidocumentvault.backend.document.entity.Document;
import com.aidocumentvault.backend.document.entity.DocumentStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocumentRepository extends JpaRepository<Document, UUID> {

    @EntityGraph(attributePaths = {"owner", "documentType"})
    List<Document> findAllByOwnerId(UUID ownerId);

    @EntityGraph(attributePaths = {"owner", "documentType"})
    List<Document> findAllByOwnerIdAndStatus(UUID ownerId, DocumentStatus status);

    @EntityGraph(attributePaths = {"owner", "documentType"})
    Optional<Document> findByIdAndOwnerId(UUID documentId, UUID ownerId);

    @EntityGraph(attributePaths = "storedFile")
    Optional<Document> findWithStoredFileByIdAndOwnerId(UUID id, UUID ownerId);
}
