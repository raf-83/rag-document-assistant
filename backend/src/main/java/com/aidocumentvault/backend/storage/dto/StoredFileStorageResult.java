package com.aidocumentvault.backend.storage.dto;

import com.aidocumentvault.backend.storage.entity.StorageProvider;

public record StoredFileStorageResult(
        String originalFileName,
        String storageKey,
        String storedFileName,
        String mimeType,
        String fileExtension,
        Long sizeInBytes,
        String checksum,
        StorageProvider storageProvider
) {
}
