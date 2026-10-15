package com.aidocumentvault.backend.processing.service;

import com.aidocumentvault.backend.document.entity.Document;
import com.aidocumentvault.backend.document.entity.DocumentContent;
import com.aidocumentvault.backend.document.repository.DocumentRepository;
import com.aidocumentvault.backend.processing.chunking.event.DocumentContentReadyEvent;
import com.aidocumentvault.backend.processing.dto.TextExtractionResult;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DocumentContentCompletionServiceImpl
    implements DocumentContentCompletionService {

    private final DocumentRepository documentRepository;
    private final DocumentContentPersistenceService documentContentPersistenceService;
    private final ApplicationEventPublisher publisher;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public DocumentContent completeContentProcessing(
            UUID documentId,
            UUID userId,
            String sanitizedText,
            TextExtractionResult extractionResult
    ) {
        Document document = documentRepository.findByIdAndOwnerId(documentId, userId)
                .orElseThrow(() -> new IllegalStateException(
                        "Document not found for documentId=" + documentId
                ));

        DocumentContent savedContent = documentContentPersistenceService.saveDocumentContent(
                document,
                sanitizedText,
                extractionResult
        );

        document.markAsContentReady();

        publisher.publishEvent(new DocumentContentReadyEvent(
                documentId,
                savedContent.getId(),
                userId
        ));

        return savedContent;
    }
}
