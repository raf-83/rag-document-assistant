package com.aidocumentvault.backend.document.controller;

import com.aidocumentvault.backend.document.dto.DocumentSearchPageResponse;
import com.aidocumentvault.backend.document.service.DocumentSearchService;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class DocumentSearchController {

    private final DocumentSearchService documentSearchService;

    @GetMapping("/api/documents/search")
    public DocumentSearchPageResponse searchDocuments(
            @RequestParam String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return documentSearchService.searchDocuments(
                query,
                page,
                size
        );
    }
}
