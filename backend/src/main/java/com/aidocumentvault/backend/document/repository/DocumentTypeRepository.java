package com.aidocumentvault.backend.document.repository;

import com.aidocumentvault.backend.document.entity.DocumentType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface DocumentTypeRepository extends JpaRepository<DocumentType, UUID> {

    Optional<DocumentType> findByCode(String code);

    boolean existsByCode(String code);
}
