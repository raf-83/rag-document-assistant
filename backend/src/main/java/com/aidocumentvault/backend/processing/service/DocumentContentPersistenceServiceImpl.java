package com.aidocumentvault.backend.processing.service;

import com.aidocumentvault.backend.document.entity.Document;
import com.aidocumentvault.backend.document.entity.DocumentContent;
import com.aidocumentvault.backend.document.repository.DocumentContentRepository;
import com.aidocumentvault.backend.document.repository.DocumentRepository;
import com.aidocumentvault.backend.processing.dto.TextExtractionResult;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DocumentContentPersistenceServiceImpl
        implements DocumentContentPersistenceService {

    private final DocumentRepository documentRepository;
    private final DocumentContentRepository documentContentRepository;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public DocumentContent saveDocumentContent(
            Document document,
            String sanitizedText,
            TextExtractionResult extractionResult
    ) {
        DocumentContent content = new DocumentContent(
                document,
                sanitizedText,
                extractionResult.extractionMethod(),
                extractionResult.pageCount()
        );

        return documentContentRepository.save(content);
    }
}
