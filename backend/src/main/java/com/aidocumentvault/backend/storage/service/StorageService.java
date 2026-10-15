package com.aidocumentvault.backend.storage.service;

import com.aidocumentvault.backend.storage.dto.StoredFileDownload;
import com.aidocumentvault.backend.storage.dto.StoredFileStorageResult;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;

public interface StorageService {

    StoredFileStorageResult store(MultipartFile file);

    StoredFileDownload load(
        String storageKey,
        String originalFileName,
        String mimeType,
        Long sizeInBytes
    );

    Path resolvePathForProcessing(String storageKey);

}
