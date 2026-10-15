package com.aidocumentvault.backend.document.dto;

import com.aidocumentvault.backend.document.entity.DocumentStatus;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DocumentResponse {

    private UUID id;
    private String title;
    private UUID ownerId;
    private UUID documentTypeId;
    private String documentTypeCode;
    private DocumentStatus status;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private StoredFileResponse storedFile;
}
