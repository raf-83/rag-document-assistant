package com.aidocumentvault.backend.storage.service;

import com.aidocumentvault.backend.storage.entity.StoredFile;
import com.aidocumentvault.backend.storage.repository.StoredFileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class StoredFileService {

    private final StoredFileRepository storedFileRepository;

    public StoredFileService(StoredFileRepository storedFileRepository) {
        this.storedFileRepository = storedFileRepository;
    }

    public Optional<StoredFile> findById(UUID id) {
        return storedFileRepository.findById(id);
    }

    public Optional<StoredFile> findByStorageKey(String storageKey) {
        return storedFileRepository.findByStorageKey(storageKey);
    }

    public boolean existsByStorageKey(String storageKey) {
        return storedFileRepository.existsByStorageKey(storageKey);
    }

    @Transactional
    public StoredFile save(StoredFile storedFile) {
        return storedFileRepository.save(storedFile);
    }
}
