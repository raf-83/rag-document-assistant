package com.aidocumentvault.backend.document.service;

import com.aidocumentvault.backend.common.exception.ResourceNotFoundException;
import com.aidocumentvault.backend.document.dto.DocumentTypeResponse;
import com.aidocumentvault.backend.document.entity.DocumentType;
import com.aidocumentvault.backend.document.repository.DocumentTypeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class DocumentTypeService {

    private final DocumentTypeRepository documentTypeRepository;

    public DocumentTypeService(DocumentTypeRepository documentTypeRepository) {
        this.documentTypeRepository = documentTypeRepository;
    }

    public DocumentTypeResponse getResponseByIdOrThrow(UUID id) {
        return mapToResponse(getByIdOrThrow(id));
    }

    public List<DocumentTypeResponse> findAllResponses(){
        return findAll().stream()
               .map(this::mapToResponse)
               .toList();
    }
    public List<DocumentType> findAll() {
        return documentTypeRepository.findAll();
    }

    public Optional<DocumentType> findById(UUID id) {
        return documentTypeRepository.findById(id);
    }

    public DocumentType getByIdOrThrow(UUID id) {
        return documentTypeRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Document type not found: " + id));
    }

    public Optional<DocumentType> findByCode(String code) {
        return documentTypeRepository.findByCode(code);
    }

    public DocumentType getByCodeOrThrow(String code) {
        return documentTypeRepository.findByCode(code)
            .orElseThrow(() -> new ResourceNotFoundException("Document type not found: " + code));
    }

    public boolean existsByCode(String code) {
        return documentTypeRepository.existsByCode(code);
    }


    private DocumentTypeResponse mapToResponse(DocumentType documentType) {
        DocumentTypeResponse response = new DocumentTypeResponse();
        response.setId(documentType.getId());
        response.setCode(documentType.getCode());
        response.setName(documentType.getName());
        response.setDescription(documentType.getDescription());
        response.setActive(documentType.isActive());
        return response;
    }
}
