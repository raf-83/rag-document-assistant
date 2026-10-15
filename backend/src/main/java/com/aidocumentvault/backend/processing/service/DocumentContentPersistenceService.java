package com.aidocumentvault.backend.processing.service;

import com.aidocumentvault.backend.document.entity.Document;
import com.aidocumentvault.backend.document.entity.DocumentContent;
import com.aidocumentvault.backend.processing.dto.TextExtractionResult;
import java.util.UUID;

public interface DocumentContentPersistenceService {

    DocumentContent saveDocumentContent(
            Document document,
            String sanitizedText,
            TextExtractionResult extractionResult
    );
}
