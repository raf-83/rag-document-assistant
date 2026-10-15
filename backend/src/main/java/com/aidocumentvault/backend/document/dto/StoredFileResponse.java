package com.aidocumentvault.backend.document.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class StoredFileResponse {

    private String originalFileName;
    private String mimeType;
    private String fileExtension;
    private Long sizeInBytes;
}
