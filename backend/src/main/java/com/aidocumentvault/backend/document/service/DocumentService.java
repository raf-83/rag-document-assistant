package com.aidocumentvault.backend.document.service;

import com.aidocumentvault.backend.common.exception.ResourceNotFoundException;
import com.aidocumentvault.backend.document.dto.DocumentContentResponse;
import com.aidocumentvault.backend.document.dto.DocumentResponse;
import com.aidocumentvault.backend.document.dto.StoredFileResponse;
import com.aidocumentvault.backend.document.dto.UploadDocumentRequest;
import com.aidocumentvault.backend.document.entity.Document;
import com.aidocumentvault.backend.document.entity.DocumentContent;
import com.aidocumentvault.backend.document.entity.DocumentStatus;
import com.aidocumentvault.backend.document.entity.DocumentType;
import com.aidocumentvault.backend.document.repository.DocumentContentRepository;
import com.aidocumentvault.backend.document.repository.DocumentRepository;
import com.aidocumentvault.backend.document.repository.DocumentTypeRepository;
import com.aidocumentvault.backend.processing.event.DocumentUploadedEvent;
import com.aidocumentvault.backend.security.context.CurrentUserProvider;
import com.aidocumentvault.backend.storage.dto.StoredFileDownload;
import com.aidocumentvault.backend.storage.dto.StoredFileStorageResult;
import com.aidocumentvault.backend.storage.entity.StoredFile;
import com.aidocumentvault.backend.storage.service.StorageService;
import com.aidocumentvault.backend.user.entity.User;
import com.aidocumentvault.backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final CurrentUserProvider currentUserProvider;
    private final UserRepository userRepository;
    private final DocumentTypeRepository documentTypeRepository;
    private final StorageService storageService;
    private final ApplicationEventPublisher eventPublisher;
    private final DocumentContentRepository documentContentRepository;

    @Transactional
    public DocumentResponse uploadDocument(MultipartFile file, UploadDocumentRequest request) {
        UUID currentUserId = currentUserProvider.getCurrentUserId();

        User owner = userRepository.findById(currentUserId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found: " + currentUserId));

        DocumentType documentType = documentTypeRepository.findById(request.getDocumentTypeId())
            .orElseThrow(() -> new ResourceNotFoundException("Document type not found: " + request.getDocumentTypeId()));

        StoredFileStorageResult storageResult = storageService.store(file);

        StoredFile storedFile = new StoredFile(
            storageResult.originalFileName(),
            storageResult.storageKey(),
            storageResult.storedFileName(),
            storageResult.mimeType(),
            storageResult.fileExtension(),
            storageResult.sizeInBytes(),
            storageResult.checksum(),
            storageResult.storageProvider()
        );

        Document document = new Document(
            request.getTitle(),
            owner,
            documentType,
            storedFile
        );

        Document savedDocument = documentRepository.save(document);

        eventPublisher.publishEvent(new DocumentUploadedEvent(
            savedDocument.getId(),
            currentUserId
        ));

        return mapToResponse(savedDocument);
    }

    @Transactional
    public StoredFileDownload downloadMyDocumentFile(UUID documentId) {
        UUID currentUserId = currentUserProvider.getCurrentUserId();

        Document document = documentRepository.findByIdAndOwnerId(documentId, currentUserId)
            .orElseThrow(() -> new ResourceNotFoundException("Document not found: " + documentId));

        StoredFile storedFile = document.getStoredFile();

        return storageService.load(
            storedFile.getStorageKey(),
            storedFile.getOriginalFileName(),
            storedFile.getMimeType(),
            storedFile.getSizeInBytes()
        );
    }

    public Optional<DocumentResponse> findMyResponseById(UUID documentId) {
        UUID currentUserId = currentUserProvider.getCurrentUserId();

        return documentRepository.findByIdAndOwnerId(documentId, currentUserId)
            .map(this::mapToResponse);
    }

    public DocumentResponse getMyResponseByIdOrThrow(UUID documentId) {
        UUID currentUserId = currentUserProvider.getCurrentUserId();

        return documentRepository.findByIdAndOwnerId(documentId, currentUserId)
            .map(this::mapToResponse)
            .orElseThrow(() -> new ResourceNotFoundException("Document not found: " + documentId));
    }

    public List<DocumentResponse> findAllMyResponses() {
        UUID currentUserId = currentUserProvider.getCurrentUserId();

        return documentRepository.findAllByOwnerId(currentUserId)
            .stream()
            .map(this::mapToResponse)
            .toList();
    }

    public List<DocumentResponse> findAllMyResponsesByStatus(DocumentStatus status) {
        UUID currentUserId = currentUserProvider.getCurrentUserId();

        return documentRepository.findAllByOwnerIdAndStatus(currentUserId, status)
            .stream()
            .map(this::mapToResponse)
            .toList();
    }

    public Optional<Document> findMyDocumentById(UUID documentId) {
        UUID currentUserId = currentUserProvider.getCurrentUserId();

        return documentRepository.findByIdAndOwnerId(documentId, currentUserId);
    }

    @Transactional
    public Document save(Document document) {
        return documentRepository.save(document);
    }

    @Transactional
    public void deleteMyDocumentById(UUID documentId) {
        UUID currentUserId = currentUserProvider.getCurrentUserId();

        Document document = documentRepository.findByIdAndOwnerId(documentId, currentUserId)
            .orElseThrow(() -> new ResourceNotFoundException("Document not found: " + documentId));

        documentRepository.delete(document);
    }

    @Transactional(readOnly = true)
    public DocumentContentResponse getDocumentContent(UUID documentId) {
        UUID currentUserId = currentUserProvider.getCurrentUserId();

        DocumentContent content = documentContentRepository
            .findByDocumentIdAndDocumentOwnerId(documentId, currentUserId)
            .orElseThrow(() -> new ResourceNotFoundException("Document not found: " + documentId));

        return new DocumentContentResponse(
            content.getDocument().getId(),
            content.getSanitizedText(),
            content.getExtractionMethod(),
            content.getCharacterCount(),
            content.getPageCount(),
            content.getExtractedAt(),
            content.getSanitizedAt()
        );
    }


    private DocumentResponse mapToResponse(Document document) {
        DocumentResponse response = new DocumentResponse();
        response.setId(document.getId());
        response.setTitle(document.getTitle());
        response.setOwnerId(document.getOwner().getId());
        response.setDocumentTypeId(document.getDocumentType().getId());
        response.setDocumentTypeCode(document.getDocumentType().getCode());
        response.setStatus(document.getStatus());
        response.setCreatedAt(document.getCreatedAt());
        response.setUpdatedAt(document.getUpdatedAt());

        response.setStoredFile(new StoredFileResponse(
            document.getStoredFile().getOriginalFileName(),
            document.getStoredFile().getMimeType(),
            document.getStoredFile().getFileExtension(),
            document.getStoredFile().getSizeInBytes()
        ));

        return response;
    }
}
