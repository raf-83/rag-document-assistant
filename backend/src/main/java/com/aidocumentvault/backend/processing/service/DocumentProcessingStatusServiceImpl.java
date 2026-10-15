package com.aidocumentvault.backend.processing.service;

import com.aidocumentvault.backend.document.entity.Document;
import com.aidocumentvault.backend.document.repository.DocumentRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DocumentProcessingStatusServiceImpl implements DocumentProcessingStatusService {

    private final DocumentRepository documentRepository;


    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markAsTextProcessing(UUID documentId) {
        Document document = findDocument(documentId);
        document.markAsTextProcessing();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markAsContentReady(UUID documentId) {
        Document document = findDocument(documentId);
        document.markAsContentReady();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markAsChunking(UUID documentId) {
        Document document = findDocument(documentId);
        document.markAsChunking();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markAsReady(UUID documentId) {
        Document document = findDocument(documentId);
        document.markAsReady();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markAsFailed(UUID documentId) {
        Document document = findDocument(documentId);
        document.markAsFailed();
    }

    private Document findDocument(UUID documentId) {
        return documentRepository.findById(documentId)
            .orElseThrow(() -> new IllegalArgumentException("Document not found"));
    }
}
