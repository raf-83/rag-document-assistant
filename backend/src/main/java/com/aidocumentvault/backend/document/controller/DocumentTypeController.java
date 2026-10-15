package com.aidocumentvault.backend.document.controller;


import com.aidocumentvault.backend.document.dto.DocumentTypeResponse;
import com.aidocumentvault.backend.document.entity.DocumentType;
import com.aidocumentvault.backend.document.service.DocumentTypeService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/document-types")
public class DocumentTypeController {

    private final DocumentTypeService documentTypeService;

    public DocumentTypeController(DocumentTypeService documentTypeService) {
        this.documentTypeService = documentTypeService;
    }

    @GetMapping
    public List<DocumentTypeResponse> findAll() {
        return documentTypeService.findAllResponses();

    }

    @GetMapping("/{id}")
    public DocumentTypeResponse findById(@PathVariable UUID id) {
        return documentTypeService.getResponseByIdOrThrow(id);
    }


}
