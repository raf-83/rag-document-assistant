package com.aidocumentvault.backend.processing.service;

import com.aidocumentvault.backend.document.entity.Document;
import com.aidocumentvault.backend.document.repository.DocumentRepository;
import com.aidocumentvault.backend.processing.dto.TextExtractionResult;
import com.aidocumentvault.backend.processing.exception.UnsupportedExtractionTypeException;
import com.aidocumentvault.backend.processing.extractor.TextExtractor;
import com.aidocumentvault.backend.processing.sanitizer.TextSanitizer;
import com.aidocumentvault.backend.storage.entity.StoredFile;
import com.aidocumentvault.backend.storage.service.StorageService;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentProcessingServiceImpl implements DocumentProcessingService {

    private final DocumentRepository documentRepository;
    private final StorageService storageService;
    private final List<TextExtractor> textExtractors;
    private final TextSanitizer textSanitizer;
    private final DocumentProcessingStatusService statusService;
    private final DocumentContentCompletionServiceImpl contentCompletionService;

    @Override
    public void processDocument(UUID documentId, UUID userId) {




        try {

            Document document = documentRepository.findWithStoredFileByIdAndOwnerId(documentId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Document not found"));

            statusService.markAsTextProcessing(documentId);

            StoredFile storedFile = document.getStoredFile();

            TextExtractor extractor = findExtractor(storedFile);

            Path filePath = storageService.resolvePathForProcessing(storedFile.getStorageKey());

            TextExtractionResult extractionResult = extractor.extract(filePath);

            String sanitizedText = textSanitizer.sanitize(extractionResult.text());

            contentCompletionService.completeContentProcessing(documentId, userId, sanitizedText, extractionResult );

            log.info(
                    "DOCUMENT_PROCESSING_SUCCESS documentId={} userId={} extractionMethod={}",
                    document.getId(),
                    userId,
                    extractionResult.extractionMethod()
            );
        } catch (Exception exception) {
            statusService.markAsFailed(documentId);

            log.warn(
                    "DOCUMENT_PROCESSING_FAILED documentId={} userId={} errorType={}",
                    documentId,
                    userId,
                    exception.getClass().getSimpleName()
            );
        }
    }

    private TextExtractor findExtractor(StoredFile storedFile) {
        return textExtractors.stream()
                .filter(extractor -> extractor.supports(storedFile))
                .findFirst()
                .orElseThrow(() -> new UnsupportedExtractionTypeException(
                        "No text extractor found for file type: " + storedFile.getMimeType()
                ));
    }
}
