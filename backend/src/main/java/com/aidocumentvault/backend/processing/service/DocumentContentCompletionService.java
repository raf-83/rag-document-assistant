package com.aidocumentvault.backend.processing.service;


import com.aidocumentvault.backend.document.entity.DocumentContent;
import com.aidocumentvault.backend.processing.dto.TextExtractionResult;
import java.util.UUID;


public interface DocumentContentCompletionService {

        DocumentContent completeContentProcessing(
            UUID documentId,
            UUID userId,
            String sanitizedText,
            TextExtractionResult extractionResult
        );
}
