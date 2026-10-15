package com.aidocumentvault.backend.storage.repository;

import com.aidocumentvault.backend.storage.entity.StoredFile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface StoredFileRepository extends JpaRepository<StoredFile, UUID> {

    Optional<StoredFile> findByStorageKey(String storageKey);

    boolean existsByStorageKey(String storageKey);
}
