package com.aidocumentvault.backend.storage.dto;

import org.springframework.core.io.Resource;

public record StoredFileDownload(
        Resource resource,
        String originalFileName,
        String mimeType,
        Long sizeInBytes
) {
}
